package com.example.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.model.Currency
import com.example.financetracker.data.model.TransactionEntity
import com.example.financetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UiState(
    val income: Double = 0.0, val expense: Double = 0.0,
    val balance: Double = 0.0, val currency: Currency = Currency.RUB,
    val items: List<TransactionEntity> = emptyList()
)

@HiltViewModel
class FinanceViewModel @Inject constructor(private val repo: TransactionRepository) : ViewModel() {
    private val _cur = MutableStateFlow(Currency.RUB)
    val currency: StateFlow<Currency> = _cur
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init {
        viewModelScope.launch {
            try {
                repo.getAll().collect { refresh(it) }
            } catch (e: Exception) {
                // Ошибка БД не должна ронять процесс — показываем пустой дашборд
                _ui.value = UiState(currency = _cur.value, items = emptyList())
            }
        }
    }

    fun setCurrency(c: Currency) {
        _cur.value = c
        refreshNow()
    }

    /**
     * Принудительная перечитака БД. Room-Flow обычно сам эмитит изменения,
     * но с SQLCipher invalidation-трекер может не срабатывать, поэтому после
     * записи обновляем состояние явно.
     */
    private fun refreshNow() {
        viewModelScope.launch {
            try {
                refresh(repo.getAll().first())
            } catch (e: Exception) {
                _ui.value = UiState(currency = _cur.value, items = emptyList())
            }
        }
    }

    private suspend fun refresh(list: List<TransactionEntity>) {
        val c = _cur.value
        val i = try { repo.income(c.code) } catch (_: Exception) { 0.0 }
        val e = try { repo.expense(c.code) } catch (_: Exception) { 0.0 }
        _ui.value = UiState(i, e, i - e, c, list)
    }

    fun add(amount: Double, cat: String, note: String, income: Boolean) {
        viewModelScope.launch {
            try {
                repo.add(TransactionEntity(amount = amount, currencyCode = _cur.value.code, category = cat, note = note, isIncome = income))
            } catch (_: Exception) {}
            refreshNow()
        }
    }

    fun remove(t: TransactionEntity) {
        viewModelScope.launch {
            try { repo.remove(t) } catch (_: Exception) {}
            refreshNow()
        }
    }
}
