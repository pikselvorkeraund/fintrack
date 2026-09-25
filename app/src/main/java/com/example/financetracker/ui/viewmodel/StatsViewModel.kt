package com.example.financetracker.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.model.CategoryEntity
import com.example.financetracker.data.model.Currency
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.repository.TransactionRepository
import com.example.financetracker.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Один столбик бар-чарта: категория, подписью и цвет из справочника. */
data class BarItem(
    val categoryId: Int,
    val label: String,
    val value: Double,
    val color: Long
)

/** Состояние экрана статистики: тип периода, текущий ключ, границы истории, два графика. */
data class StatsState(
    val type: PeriodType = PeriodType.DAY,
    val key: String = "",
    val minKey: String? = null,
    val maxKey: String? = null,
    val currency: Currency = Currency.RUB,
    val expenseBars: List<BarItem> = emptyList(),
    val incomeBars: List<BarItem> = emptyList(),
    val totalExpense: Double = 0.0,
    val totalIncome: Double = 0.0,
    val loading: Boolean = false,
    val empty: Boolean = false
) {
    /** Есть ли за пределами текущего ключа более ранние/более поздние периоды. */
    val canGoBack: Boolean get() = minKey != null && type.shift(key, -1) >= minKey
    val canGoForward: Boolean get() = maxKey != null && type.shift(key, 1) <= maxKey
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: TransactionRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    /** Максимум столбиков на график — категории с наибольшими значениями. */
    private companion object { const val TOP_N = 7 }

    /** Активная валюта дашборда — передаётся навигационным аргументом. */
    private val curCode: String =
        Currency.fromCode(savedStateHandle.get<String>("currency") ?: Currency.RUB.code).code

    private val _state = MutableStateFlow(StatsState(currency = Currency.fromCode(curCode)))
    val state: StateFlow<StatsState> = _state.asStateFlow()

    init { load(PeriodType.DAY) }

    /** Смена типа статистики (День/Неделя/…): пересчитывает границы и грузит период. */
    fun setType(pt: PeriodType) {
        if (_state.value.type == pt && _state.value.key.isNotEmpty()) return
        load(pt)
    }

    /** Листание на delta периодов назад/вперёд, в границах истории (minKey..maxKey). */
    fun shift(delta: Long) {
        val st = _state.value
        val next = st.type.shift(st.key, delta)
        if (!st.canGoBack && delta < 0) return
        if (!st.canGoForward && delta > 0) return
        _state.update { it.copy(key = next) }
        loadBars()
    }

    private fun load(pt: PeriodType) {
        viewModelScope.launch {
            _state.update { s -> s.copy(type = pt, loading = true) }
            val acc = settings.currentAccountId()
            // Границы истории по агрегирующим строкам (MIN/MAX periodKey);
            // ключи отформатированы так, что строковое сравнение = хронология.
            val min = try { repo.minPeriodKey(acc, curCode, pt) } catch (_: Throwable) { null }
            val max = try { repo.maxPeriodKey(acc, curCode, pt) } catch (_: Throwable) { null }
            // Стартовый ключ: текущий период, но в пределах истории; если
            // истории нет вообще — остаёмся на текущем (покажем пустой график).
            val nowKey = pt.currentKey()
            val start = when {
                min == null || max == null -> nowKey
                nowKey < min -> min
                nowKey > max -> max
                else -> nowKey
            }
            _state.update { s -> s.copy(minKey = min, maxKey = max, key = start) }
            loadBars()
        }
    }

    /** Две разбивки (расход/доход) по категориям за выбранный период, топ-N. */
    private fun loadBars() {
        viewModelScope.launch {
            val st = _state.value
            val acc = settings.currentAccountId()
            _state.update { s -> s.copy(loading = true) }
            val rows = try { repo.periodByCategory(acc, curCode, st.type, st.key) }
                catch (_: Throwable) { emptyList() }
            // Реальные итоги за период — из агрегирующей строки (не топ-N)
            val totExp = try { repo.periodExpense(acc, curCode, st.type, st.key) } catch (_: Throwable) { 0.0 }
            val totInc = try { repo.periodIncome(acc, curCode, st.type, st.key) } catch (_: Throwable) { 0.0 }
            // Имена и цвета категорий — из справочника одним чтением
            val cats = try { repo.listCategories() } catch (_: Throwable) { emptyList() }
            val byId = cats.associateBy { it.id }
            fun bars(valueOf: (com.example.financetracker.data.local.CategorySum) -> Double): List<BarItem> =
                rows.map { r ->
                    val c = byId[r.categoryId]
                    BarItem(
                        categoryId = r.categoryId,
                        label = c?.name ?: "",
                        value = valueOf(r),
                        color = CategoryEntity.colorFor(r.categoryId)
                    )
                }.filter { it.value > 0.0 }
                    .sortedByDescending { it.value }
                    .take(TOP_N)
            val exp = bars { it.expense }
            val inc = bars { it.income }
            _state.update { s ->
                s.copy(
                    expenseBars = exp,
                    incomeBars = inc,
                    totalExpense = totExp,
                    totalIncome = totInc,
                    loading = false,
                    empty = exp.isEmpty() && inc.isEmpty()
                )
            }
        }
    }
}