package com.example.financetracker.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.repository.BackupFormatException
import com.example.financetracker.data.repository.BackupNewerVersionException
import com.example.financetracker.data.repository.BackupRepository
import com.example.financetracker.data.repository.BackupWrongPasswordException
import com.example.financetracker.data.repository.ImportMode
import com.example.financetracker.data.settings.SettingsRepository
import com.example.financetracker.ui.locale.Language
import com.example.financetracker.ui.locale.Strings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Результат длительной операции импорта/экспорта (для показа в Snackbar). */
data class BackupMsg(val text: String, val isError: Boolean)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backup: BackupRepository
) : ViewModel() {
    val lang: StateFlow<Language> = settings.lang
    fun setLanguage(l: Language) = settings.setLanguage(l)

    /** Флаг длительной операции — включает спиннер на экране настроек. */
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Сообщение результата (успех/ошибка) — показывается и очищается. */
    private val _message = MutableStateFlow<BackupMsg?>(null)
    val message: StateFlow<BackupMsg?> = _message.asStateFlow()

    /**
     * Формирует имя файла экспорта. Вызывается ДО запуска ACTION_CREATE_DOCUMENT,
     * чтобы пользователь мог его поправить в системном диалоге.
     */
    fun suggestFileName(): String = "fintrack-backup-%s.json.fintx".format(
        java.time.LocalDate.now().toString()
    )

    fun exportBackup(uri: Uri, password: String, strings: Strings) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            try {
                val n = backup.export(uri, password)
                _message.value = BackupMsg(strings.exportDone.replace("{N}", n.toString()), false)
            } catch (_: Throwable) {
                // IO/безопасность/сбой Room: не роняем процесс, показываем ошибку
                _message.value = BackupMsg(strings.exportErr, true)
            } finally {
                _busy.value = false
            }
        }
    }

    fun importBackup(
        uri: Uri,
        password: String,
        mode: ImportMode,
        strings: Strings,
        onDone: (Boolean) -> Unit
    ) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            var ok = false
            try {
                val n = backup.import(uri, password, mode)
                ok = true
                _message.value = BackupMsg(
                    strings.importDone.replace("{N}", n.toString()), false)
            } catch (_: BackupWrongPasswordException) {
                _message.value = BackupMsg(strings.importErrPassword, true)
            } catch (_: BackupFormatException) {
                _message.value = BackupMsg(strings.importErrFormat, true)
            } catch (_: BackupNewerVersionException) {
                _message.value = BackupMsg(strings.importErrVersion, true)
            } catch (_: Throwable) {
                _message.value = BackupMsg(strings.importErr, true)
            } finally {
                _busy.value = false
                onDone(ok)
            }
        }
    }

    fun clearMessage() { _message.value = null }
}