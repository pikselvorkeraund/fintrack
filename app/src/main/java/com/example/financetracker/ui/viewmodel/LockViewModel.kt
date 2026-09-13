package com.example.financetracker.ui.viewmodel

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

    fun onPattern(p: List<Int>) {
        viewModelScope.launch {
            try {
                when (_s.value) {
                    is LockState.Setup -> {
                        if (p.size >= 4) {
                            plm.save(p)
                            val key = withContext(Dispatchers.Default) { plm.derivePassphrase(p) }
                            val ok = withContext(Dispatchers.IO) {
                                db.unlock(key) || db.recreateWith(key)
                            }
                            _s.value = if (ok) LockState.Unlocked
                                       else LockState.Error("db")
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
                                       else LockState.Error("db")
                        } else if (plm.shouldWipe()) {
                            db.lock()
                            plm.wipeAll()
                            _s.value = LockState.Wiped
                        } else {
                            _s.value = LockState.Error("wrong", plm.remaining.toString())
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _s.value = LockState.Error("unknown", e.message?.take(50) ?: "")
            }
        }
    }

    fun reset() {
        _s.value = LockState.Setup
    }
}
