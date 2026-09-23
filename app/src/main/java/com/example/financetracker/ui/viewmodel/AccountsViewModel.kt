package com.example.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.model.AccountEntity
import com.example.financetracker.data.repository.TransactionRepository
import com.example.financetracker.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _accounts = MutableStateFlow(emptyList<AccountEntity>())
    val accounts: StateFlow<List<AccountEntity>> = _accounts.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    /** Код ошибки: "name_empty", "last_account", "current_account", "db_error". */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init { refresh() }

    fun currentAccountId(): Int = settings.currentAccountId()

    fun refresh() {
        viewModelScope.launch {
            _loading.update { true }
            try {
                _accounts.value = repo.listAccounts()
            } catch (_: Throwable) { }
            // В release-сборке Room/SQLCipher могут бросать Error-подклассы
            // (UnsatisfiedLinkError и т.п.), которые пролетают мимо catch(Exception)
            // и роняют процесс как uncaught в viewModelScope.
            _loading.update { false }
        }
    }

    fun add(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _error.value = "name_empty"
            return false
        }
        viewModelScope.launch {
            try {
                repo.addAccount(trimmed)
                refresh()
                _error.value = null
            } catch (_: Throwable) {
                _error.value = "db_error"
            }
        }
        return true
    }

    fun rename(id: Int, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _error.value = "name_empty"
            return false
        }
        viewModelScope.launch {
            try {
                repo.renameAccount(id, trimmed)
                refresh()
                _error.value = null
            } catch (_: Throwable) {
                _error.value = "db_error"
            }
        }
        return true
    }

    /**
     * Удаление счёта (каскад). Ограничения:
     *  - нельзя удалить последний счёт;
     *  - нельзя удалить текущий счёт.
     */
    fun delete(id: Int): Boolean {
        val list = _accounts.value
        val current = settings.currentAccountId()

        if (list.size <= 1) {
            _error.value = "last_account"
            return false
        }
        if (id == current) {
            _error.value = "current_account"
            return false
        }

        viewModelScope.launch {
            try {
                repo.deleteAccount(id)
                refresh()
                _error.value = null
            } catch (_: Throwable) {
                _error.value = "db_error"
            }
        }
        return true
    }

    fun select(id: Int) {
        settings.setCurrentAccount(id)
    }

    fun clearError() {
        _error.value = null
    }
}