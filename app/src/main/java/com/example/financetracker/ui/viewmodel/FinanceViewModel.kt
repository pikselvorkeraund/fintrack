package com.example.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.model.Currency
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.TransactionEntity
import com.example.financetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Чистая сумма (доход − расход) за период для компактного блока статистики. */
data class PeriodStat(val type: PeriodType, val net: Double)

data class UiState(
    val income: Double = 0.0, val expense: Double = 0.0,
    val balance: Double = 0.0, val currency: Currency = Currency.RUB,
    val items: List<TransactionEntity> = emptyList(),
    val periods: List<PeriodStat> = emptyList(),
    val loading: Boolean = false, val loadingMore: Boolean = false,
    val hasMore: Boolean = false
)

@HiltViewModel
class FinanceViewModel @Inject constructor(private val repo: TransactionRepository) : ViewModel() {

    companion object { const val PAGE_SIZE = 20 }

    private val _cur = MutableStateFlow(Currency.RUB)
    val currency: StateFlow<Currency> = _cur
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    init { reload() }

    fun setCurrency(c: Currency) {
        if (_cur.value == c) return
        _cur.value = c
        reload()
    }

    /** Первая страница записей только активной валюты + суммы из таблицы статистики. */
    fun reload() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val c = _cur.value
            try {
                val items = repo.page(c.code, 0L, PAGE_SIZE)
                _ui.value = _ui.value.copy(
                    items = items,
                    hasMore = items.size >= PAGE_SIZE,
                    loading = false
                )
            } catch (e: Exception) {
                // Ошибка БД не должна ронять процесс — показываем пустой дашборд
                _ui.value = UiState(currency = c, loading = false)
            }
            refreshTotals()
        }
    }

    /**
     * Ленивая загрузка: следующая страница «старее» последнего
     * загруженного id (keyset-пагинация, запрос по PK-индексу).
     */
    fun loadMore() {
        val st = _ui.value
        if (st.loadingMore || !st.hasMore || st.items.isEmpty()) return
        viewModelScope.launch {
            _ui.value = st.copy(loadingMore = true)
            try {
                val next = repo.page(_cur.value.code, st.items.last().id, PAGE_SIZE)
                _ui.value = _ui.value.copy(
                    items = _ui.value.items + next,
                    hasMore = next.size >= PAGE_SIZE,
                    loadingMore = false
                )
            } catch (_: Exception) {
                _ui.value = _ui.value.copy(loadingMore = false)
            }
        }
    }

    private suspend fun refreshTotals() {
        val c = _cur.value
        val i = try { repo.income(c.code) } catch (_: Exception) { 0.0 }
        val e = try { repo.expense(c.code) } catch (_: Exception) { 0.0 }
        _ui.value = _ui.value.copy(income = i, expense = e, balance = i - e, currency = c)
        refreshPeriods()
    }

    /**
     * Суммы за текущие день/неделю/месяц/год читаются точечно из `stats`
     * по ключам периодов — без скана транзакций.
     */
    private suspend fun refreshPeriods() {
        val c = _cur.value.code
        val now = System.currentTimeMillis()
        val list = try {
            PeriodType.entries.filter { it != PeriodType.TOTAL }.map { p ->
                val inc = repo.periodIncome(c, p, p.keyOf(now))
                val exp = repo.periodExpense(c, p, p.keyOf(now))
                PeriodStat(p, inc - exp)
            }
        } catch (_: Exception) {
            emptyList()
        }
        _ui.value = _ui.value.copy(periods = list)
    }

    fun add(amount: Double, cat: String, note: String, income: Boolean) {
        viewModelScope.launch {
            val c = _cur.value
            try {
                val id = repo.add(
                    TransactionEntity(
                        amount = amount, currencyCode = c.code,
                        category = cat, note = note, isIncome = income
                    )
                )
                // Запись принадлежит активной валюте — добавляем в начало окна
                _ui.value = _ui.value.copy(
                    items = listOf(
                        TransactionEntity(
                            id = id, amount = amount, currencyCode = c.code,
                            category = cat, note = note, isIncome = income
                        )
                    ) + _ui.value.items
                )
            } catch (_: Exception) {
            }
            refreshTotals()
        }
    }

    fun remove(t: TransactionEntity) {
        viewModelScope.launch {
            try {
                repo.remove(t)
                _ui.value = _ui.value.copy(items = _ui.value.items.filter { it.id != t.id })
                // Если окно опустело, но впереди ещё записи — перечитываем первую
                // страницу; иначе просто дозагружаем до полного окна
                if (_ui.value.items.isEmpty() && _ui.value.hasMore) reload()
                else if (_ui.value.items.size < PAGE_SIZE && _ui.value.hasMore) loadMore()
            } catch (_: Exception) {
            }
            refreshTotals()
        }
    }
}