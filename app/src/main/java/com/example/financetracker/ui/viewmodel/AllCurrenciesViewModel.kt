package com.example.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.local.CurrencyTotalRow
import com.example.financetracker.data.model.Currency
import com.example.financetracker.data.repository.TransactionRepository
import com.example.financetracker.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Одна валюта в сводной статистике: валюта + суммарный доход/расход/баланс за всё время. */
data class CurrencyStat(
    val currency: Currency,
    val income: Double,
    val expense: Double
) {
    val balance: Double get() = income - expense
}

/** Состояние сводной статистики по всем валютам активного счёта. */
data class AllCurrenciesUiState(
    val currencies: List<CurrencyStat> = emptyList(),
    val loading: Boolean = false
)

/**
 * Сводная статистика по ВСЕМ валютам активного счёта (issue #3):
 * каждая валюта, по которой есть ненулевые записи, со своим суммарным
 * доходом/расходом/балансом за всё время. Чтение — один точечный запрос
 * из таблицы `stats` (TOTAL-строки, агрегирующая categoryId = 0).
 */
@HiltViewModel
class AllCurrenciesViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(AllCurrenciesUiState())
    val ui: StateFlow<AllCurrenciesUiState> = _ui.asStateFlow()

    init {
        reload()
    }

    /** Перечитывает сводку по всем валютам активного счёта. */
    fun reload() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val acc = settings.currentAccountId()
            try {
                val rows: List<CurrencyTotalRow> = repo.currencyTotals(acc)
                _ui.update { prev ->
                    prev.copy(
                        currencies = rows.map { r ->
                            CurrencyStat(
                                currency = Currency.fromCode(r.currencyCode),
                                income = r.income,
                                expense = r.expense
                            )
                        },
                        loading = false
                    )
                }
            } catch (_: Throwable) {
                _ui.update { it.copy(loading = false) }
            }
        }
    }
}
