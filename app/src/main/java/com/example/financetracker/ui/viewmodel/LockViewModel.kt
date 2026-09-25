package com.example.financetracker.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.local.DbHolder
import com.example.financetracker.data.security.PatternLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class LockState {
    object Setup : LockState()
    object Enter : LockState()
    object Unlocked : LockState()
    // key: "min4" | "wrong" (arg = число попыток) | "db" | "unknown"
    data class Error(val key: String, val arg: String = "") : LockState()
    object Wiped : LockState()
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val plm: PatternLockManager,
    private val db: DbHolder
) : ViewModel() {

    private val _s = MutableStateFlow<LockState>(
        if (plm.isSet) LockState.Enter else LockState.Setup
    )
    val state: StateFlow<LockState> = _s

    /**
     * Режим первичной настройки (ключ ещё не задан либо был стёрт после
     * 3 неверных попыток). Определяет подсказку на экране: «задайте ключ
     * для шифрования» vs «введите ключ для входа». Истинно, пока узор не
     * был успешно применён к БД; сбрасывается вайпом.
     */
    var setupHint: Boolean = plm.isSet == false
        private set

    init {
        // Если прошлый процесс умер (в т.ч. нативным SIGSEGV, который не
        // перехватывается в JVM), CrashLog оставил следы на диске. Читаем и
        // очищаем их (иначе файлы копились бы), но на экран НЕ выводим —
        // технический текст мешает пользователю. Детали — в logcat.
        db.debugInfo()?.let { info ->
            Log.d("FinTrack", "prev-run diag: " + info.take(300))
        }
    }

    fun onPattern(p: List<Int>) {
        viewModelScope.launch {
            try {
                when (_s.value) {
                    is LockState.Setup -> {
                        if (p.size >= 4) {
                            plm.save(p)
                            // Ключ задан: даже если БД не открылась, следующие
                            // попытки — это ввод существующего узора (verify)
                            setupHint = false
                            val key = withContext(Dispatchers.Default) { plm.derivePassphrase(p) }
                            val ok = withContext(Dispatchers.IO) {
                                db.unlock(key) || db.recreateWith(key)
                            }
                            _s.value = if (ok) LockState.Unlocked
                                       else {
                                           val diag = ((db.lastError ?: "") + " | " + (db.debugInfo() ?: "")).take(300)
                                           Log.d("FinTrack", "unlock fail (setup): $diag")
                                           LockState.Error("db", diag)
                                       }
                        } else {
                            _s.value = LockState.Error("min4")
                        }
                    }
                    is LockState.Enter, is LockState.Error -> {
                        if (plm.verify(p)) {
                            // Ключ шифрования = PBKDF2(узор). Открываем БД
                            // только правильным узором. Если файл БД не
                            // открывается правильным ключом (наследие старой
                            // версии) — пересоздаём его с нуля.
                            val key = withContext(Dispatchers.Default) { plm.derivePassphrase(p) }
                            val ok = withContext(Dispatchers.IO) {
                                db.unlock(key) || db.recreateWith(key)
                            }
                            _s.value = if (ok) LockState.Unlocked
                                       else {
                                           val diag = ((db.lastError ?: "") + " | " + (db.debugInfo() ?: "")).take(300)
                                           Log.d("FinTrack", "unlock fail (enter): $diag")
                                           LockState.Error("db", diag)
                                       }
                        } else if (plm.shouldWipe()) {
                            db.lock()
                            plm.wipeAll()
                            setupHint = true
                            _s.value = LockState.Wiped
                        } else {
                            _s.value = LockState.Error("wrong", plm.remaining.toString())
                        }
                    }
                    else -> {}
                }
            } catch (e: Throwable) {
                // В release нативные сбои SQLCipher/JNI приходят как Error
                // (UnsatisfiedLinkError и т.п.) и пролетают мимо catch(Exception).
                // Текст сохраняем только в logcat — на экране он не нужен.
                Log.d("FinTrack", "pattern op failed", e)
                _s.value = LockState.Error("unknown", e.toString().take(160))
            }
        }
    }

    fun reset() {
        setupHint = true
        _s.value = LockState.Setup
    }
}
