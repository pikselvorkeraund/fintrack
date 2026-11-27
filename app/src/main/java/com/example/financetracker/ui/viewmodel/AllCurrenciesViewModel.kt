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
    val expense: Double,
    /** null — курс не задан; не null — 1 единица currency = baseRate единиц baseCurrency. */
    val baseRate: Double? = null,
    /** Пересчитанная сумма в основной валюте (null, если baseRate == null). */
    val baseIncome: Double? = null,
    val baseExpense: Double? = null,
    val baseBalance: Double? = null
) {
    val balance: Double get() = income - expense
}

/** Состояние сводной статистики по всем валютам активного счёта. */
data class AllCurrenciesUiState(
    val currencies: List<CurrencyStat> = emptyList(),
    /** Основная валюта (для пересчёта); null — пересчёт не выполняется. */
    val baseCurrency: Currency? = null,
    /** Итоговая сумма всех валют в основной валюте (только те, у которых задан курс). */
    val totalBase: Double? = null,
    /** Сколько валют без курса (показываются пометкой в итоговой строке). */
    val missingCount: Int = 0,
    val loading: Boolean = false
)

/**
 * Сводная статистика по ВСЕМ валютам активного счёта (issue #3 + #5):
 * каждая валюта, по которой есть ненулевые записи, со своим суммарным
 * доходом/расходом/балансом за всё время и, при заданном кросс-курсе,
 * пересчёт в основную валюту (выбранную в дашборде).
 */
@HiltViewModel
class AllCurrenciesViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _ui = MutableStateFlow(AllCurrenciesUiState())
    val ui: StateFlow<AllCurrenciesUiState> = _ui.asStateFlow()

    /** Основная валюта — из FinanceViewModel (общий StateFlow через SettingsRepository). */
    private var _baseCurrency = MutableStateFlow<Currency?>(null)

    init {
        reload()
    }

    /** Вызывается при смене основной валюты (из FinanceViewModel). */
    fun setBaseCurrency(c: Currency) {
        _baseCurrency.value = c
        reload()
    }

    /** Установка/замена кросс-курса (from → to = rate). */
    fun setRate(from: String, to: String, rate: Double) {
        viewModelScope.launch {
            repo.setCrossRate(from, to, rate)
            reload()
        }
    }

    /** Перечитывает сводку по всем валютам активного счёта + кросс-курсы. */
    fun reload() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val acc = settings.currentAccountId()
            val base = _baseCurrency.value
            try {
                val rows: List<CurrencyTotalRow> = repo.currencyTotals(acc)
                // Если основная валюта задана — читаем все курсы в неё
                val rates: Map<String, Double> =
                    if (base != null) repo.crossRatesFor(base.code) else emptyMap()

                val stats = rows.map { r ->
                    val cur = Currency.fromCode(r.currencyCode)
                    val rate = if (base != null && cur != base) rates[cur.code] else null
                    CurrencyStat(
                        currency = cur,
                        income = r.income,
                        expense = r.expense,
                        baseRate = rate,
                        baseIncome = rate?.let { r.income * it },
                        baseExpense = rate?.let { r.expense * it },
                        baseBalance = rate?.let { (r.income - r.expense) * it }
                    )
                }

                // Итог в основной валюте: только валюты с заданным курсом
                val convertible = stats.filter { it.baseRate != null && it.currency != base }
                val totalBase = if (base != null && convertible.isNotEmpty())
                    convertible.sumOf { it.baseBalance!! }
                else null
                val missingCount = stats.count { it.baseRate == null && it.currency != base }

                _ui.update { prev ->
                    prev.copy(
                        currencies = stats,
                        baseCurrency = base,
                        totalBase = totalBase,
                        missingCount = missingCount,
                        loading = false
                    )
                }
            } catch (_: Throwable) {
                _ui.update { it.copy(loading = false) }
            }
        }
    }
}
