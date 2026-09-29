package com.example.financetracker.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.model.CategoryEntity
import com.example.financetracker.data.model.Currency
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.TransactionEntity
import com.example.financetracker.data.repository.TransactionRepository
import com.example.financetracker.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

/** Один столбик бар-чарта: категория, подписью и цвет из справочника. */
data class BarItem(
    val categoryId: Int,
    val label: String,
    val value: Double,
    val color: Long
)

/** Вкладки экрана «Периоды»: графики по категориям / список операций. */
enum class StatsTab { STATS, OPS }

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
    val empty: Boolean = false,
    /** Активная вкладка (Статистика/Операции) — живёт до закрытия экрана. */
    val tab: StatsTab = StatsTab.STATS,
    /** id → имя категории (для карточек вкладки «Операции»). */
    val catNames: Map<Int, String> = emptyMap(),
    /** Справочник категорий (список для фильтра «Все категории»). */
    val cats: List<CategoryEntity> = emptyList(),
    /** Фильтр вкладки «Операции» по типу: -1 все операции, 0 расход, 1 доход. */
    val opsType: Int = -1,
    /** Фильтр вкладки «Операции» по категории: -1 все категории, иначе categoryId. */
    val opsCat: Int = -1,
    /** Загруженное окно операций выбранного периода (timestamp DESC, id DESC). */
    val ops: List<TransactionEntity> = emptyList(),
    /** Список загружен для текущего type+key (не перечитывать при возврате на вкладку). */
    val opsLoaded: Boolean = false,
    val opsLoading: Boolean = false,
    val opsLoadingMore: Boolean = false,
    val opsHasMore: Boolean = false,
    /**
     * Курсор keyset-пагинации — ПОСЛЕДНЯЯ ЗАГРУЖЕННАЯ пара (timestamp, id),
     * а не последний элемент окна: при удалении записи из середины окна
     * дозагрузка от курсора не должна возвращать уже показанные записи.
     */
    val opsCursorTs: Long = 0,
    val opsCursorId: Long = 0
) {
    /** Есть ли за пределами текущего ключа более ранние/более поздние периоды.
     *  Пустой key (первая композиция до загрузки) — false: shift("") упал бы
     *  на LocalDate.parse. */
    val canGoBack: Boolean get() =
        key.isNotEmpty() && minKey != null && type.shift(key, -1) >= minKey
    val canGoForward: Boolean get() =
        key.isNotEmpty() && maxKey != null && type.shift(key, 1) <= maxKey
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: TransactionRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    /** Максимум столбиков на график — категории с наибольшими значениями. */
    /** Размер страницы операций — как на дашборде (AGENTS.md: по 20 записей). */
    private companion object {
        const val TOP_N = 7
        const val PAGE_SIZE = 20
    }

    /** Активная валюта дашборда — передаётся навигационным аргументом. */
    private val curCode: String =
        Currency.fromCode(savedStateHandle.get<String>("currency") ?: Currency.RUB.code).code

    /** Тип периода из маршрута stats/{currency}/{periodType}. */
    private val initialType: PeriodType = runCatching {
        PeriodType.valueOf(savedStateHandle.get<String>("periodType") ?: PeriodType.DAY.name)
    }.getOrDefault(PeriodType.DAY)

    // loading=true на старте: первая композиция не должна рисовать графики
    // и заголовок до первого load()
    private val _state = MutableStateFlow(
        StatsState(currency = Currency.fromCode(curCode), type = initialType, loading = true)
    )
    val state: StateFlow<StatsState> = _state.asStateFlow()

    init { load(initialType) }

    /**
     * Смена типа статистики (День/Неделя/…): сначала СИНХРОННО сбрасываем
     * ключ/границы/графики, чтобы первая перерисовка с новым типом не
     * вычисляла заголовок по старому ключу (periodTitle/shift упали бы на
     * ключе другого формата, напр. DAY-ключ при WEEK). Затем грузим данные.
     */
    fun setType(pt: PeriodType) {
        if (_state.value.type == pt) return
        _state.update {
            it.copy(
                type = pt, key = "", minKey = null, maxKey = null,
                expenseBars = emptyList(), incomeBars = emptyList(), loading = true,
                ops = emptyList(), opsLoaded = false, opsLoading = false,
                opsLoadingMore = false, opsHasMore = false,
                opsCursorTs = 0, opsCursorId = 0
            )
        }
        load(pt)
    }

    /**
     * Переключение вкладки. Состояние вкладки живёт в ViewModel и
     * переживает смену типа периода и листание периодов; при выходе
     * на дашборд ViewModel уничтожается — при следующем входе снова
     * вкладка «Статистика».
     */
    fun setTab(t: StatsTab) {
        if (_state.value.tab == t) return
        _state.update { it.copy(tab = t) }
        if (t == StatsTab.OPS) ensureOpsLoaded()
    }

    /**
     * Фильтр вкладки «Операции» по типу операции (-1 все / 0 расход / 1 доход).
     * Смена типа сбрасывает фильтр категорий — под новый тип подставляется
     * другой список категорий, и «Все категории» остаётся единственным
     * корректным значением по умолчанию. Любое изменение фильтра — сброс
     * окна (новый курсор) и перезагрузка, если вкладка активна.
     */
    fun setOpsType(t: Int) {
        if (_state.value.opsType == t) return
        _state.update {
            it.copy(
                opsType = t, opsCat = -1, ops = emptyList(), opsLoaded = false,
                opsLoading = false, opsLoadingMore = false, opsHasMore = false,
                opsCursorTs = 0, opsCursorId = 0
            )
        }
        if (_state.value.tab == StatsTab.OPS) loadOps(reset = true)
    }

    /** Фильтр вкладки «Операции» по категории (-1 все / categoryId выбранного типа). */
    fun setOpsCat(c: Int) {
        if (_state.value.opsCat == c) return
        _state.update {
            it.copy(
                opsCat = c, ops = emptyList(), opsLoaded = false,
                opsLoading = false, opsLoadingMore = false, opsHasMore = false,
                opsCursorTs = 0, opsCursorId = 0
            )
        }
        if (_state.value.tab == StatsTab.OPS) loadOps(reset = true)
    }

    /**
     * Создание категории из формы редактирования (пункт «Добавить новую»):
     * обновляет справочник в состоянии — фильтр «Все категории» и селектор
     * категории сразу покажут новую. Возвращает id новой/существующей.
     */
    suspend fun addCategory(name: String, isIncome: Boolean): Int? {
        val id = runCatching { repo.addCategory(name, isIncome) }.getOrNull() ?: return null
        val cats = runCatching { repo.listCategories() }.getOrDefault(emptyList())
        _state.update { s -> s.copy(cats = cats, catNames = cats.associate { it.id to it.name }) }
        return id
    }

    /**
     * Редактирование записи вкладки «Операции»: repo.replace атомарно
     * снимает дельту старой строки из `stats`, обновляет транзакцию и
     * добавляет дельту новой. Окно сортировано по timestamp и фильтровано —
     * правка (дата/тип/категория/сумма) может переместить запись или вывести
     * её за фильтр, поэтому страницу перечитываем с нуля (reset), а графики
     * и итоги обновляем из `stats`.
     */
    fun edit(
        old: TransactionEntity,
        amount: Double, categoryId: Int, note: String, income: Boolean, timestamp: Long
    ) {
        viewModelScope.launch {
            try {
                repo.replace(
                    old,
                    old.copy(amount = amount, categoryId = categoryId, note = note, isIncome = income, timestamp = timestamp)
                )
            } catch (_: Throwable) { return@launch }
            loadOps(reset = true)
            loadBars()
        }
    }

    /** Листание на delta периодов назад/вперёд, в границах истории (minKey..maxKey). */
    fun shift(delta: Long) {
        val st = _state.value
        // Пустой ключ = данные ещё не загружены: сдвигать нечего
        if (st.key.isEmpty()) return
        if (!st.canGoBack && delta < 0) return
        if (!st.canGoForward && delta > 0) return
        val next = st.type.shift(st.key, delta)
        // Новый период — окно операций неактуально, сбрасываем до загрузки
        _state.update {
            it.copy(
                key = next, ops = emptyList(), opsLoaded = false, opsLoading = false,
                opsLoadingMore = false, opsHasMore = false, opsCursorTs = 0, opsCursorId = 0
            )
        }
        loadBars()
        if (_state.value.tab == StatsTab.OPS) loadOps(reset = true)
    }

    private fun load(pt: PeriodType) {
        viewModelScope.launch {
            // Гонка: пока эта корутина ждёт БД, пользователь мог выбрать другой тип.
            // Проверяем на каждом шаге, что тип не изменился, иначе отбрасываем
            // устаревший результат (он содержал бы ключ не своего формата).
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
            // Если тип сменился за время запроса — выходим: setType() уже
            // запустил свой load(pt') для актуального ключа, а наш результат
            // содержал бы ключ не своего формата.
            if (_state.value.type != pt) return@launch
            _state.update { s -> s.copy(minKey = min, maxKey = max, key = start) }
            loadBars()
            // Если пользователь уже на вкладке операций — грузим и список
            // (иначе список подтянется лениво при setTab(OPS))
            if (_state.value.tab == StatsTab.OPS) loadOps(reset = true)
        }
    }

    /** Две разбивки (расход/доход) по категориям за выбранный период, топ-N. */
    private fun loadBars() {
        viewModelScope.launch {
            val st0 = _state.value
            // Пустой ключ = ещё не загружен: прежние графики не тронуты,
            // новые придут с данными. Устаревший вызов (другой type/key)
            // отбрасываем — иначе перезапишет актуальные графики.
            if (st0.key.isEmpty()) return@launch
            val acc = settings.currentAccountId()
            // Гонка: пока читаем БД, setType()/shift() могли сменить ключ заново
            _state.update { s -> s.copy(loading = true) }
            val rows = try { repo.periodByCategory(acc, curCode, st0.type, st0.key) }
                catch (_: Throwable) { emptyList() }
            // Реальные итоги за период — из агрегирующей строки (не топ-N)
            val totExp = try { repo.periodExpense(acc, curCode, st0.type, st0.key) } catch (_: Throwable) { 0.0 }
            val totInc = try { repo.periodIncome(acc, curCode, st0.type, st0.key) } catch (_: Throwable) { 0.0 }
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
            // Если за время запроса изменились тип или ключ — результат устарел
            if (_state.value.type != st0.type || _state.value.key != st0.key) return@launch
            _state.update { s ->
                s.copy(
                    expenseBars = exp,
                    incomeBars = inc,
                    totalExpense = totExp,
                    totalIncome = totInc,
                    loading = false,
                    empty = exp.isEmpty() && inc.isEmpty(),
                    catNames = cats.associate { it.id to it.name },
                    cats = cats
                )
            }
        }
    }

    // ---------- Вкладка «Операции» ----------

    /** Ленивая загрузка следующей страницы операций (20 записей от курсора). */
    fun loadOpsMore() {
        val st = _state.value
        if (st.key.isEmpty() || !st.opsHasMore || st.opsLoading || st.opsLoadingMore) return
        loadOps(reset = false)
    }

    /**
     * Удаление записи с вкладки «Операции»: repo.remove атомарно удаляет
     * транзакцию и вычитает дельту из stats; из окна изымаем запись и
     * дозагружаем до полного окна от курсора (курсор при удалении не
     * сдвигается — иначе дозагрузка вернула бы уже показанные записи);
     * графики и итоги перечитываем из stats — они изменились.
     */
    fun remove(t: TransactionEntity) {
        viewModelScope.launch {
            try { repo.remove(t) } catch (_: Throwable) { return@launch }
            _state.update { s -> s.copy(ops = s.ops.filterNot { it.id == t.id }) }
            if (_state.value.ops.size < PAGE_SIZE && _state.value.opsHasMore) {
                loadOps(reset = false)
            }
            loadBars()
        }
    }

    /** Загружает список при первом переходе на вкладку (или после смены периода). */
    private fun ensureOpsLoaded() {
        val st = _state.value
        if (st.key.isEmpty() || st.opsLoaded || st.opsLoading) return
        loadOps(reset = true)
    }

    /** Страница операций выбранного периода: keyset по паре (timestamp, id). */
    private fun loadOps(reset: Boolean) {
        viewModelScope.launch {
            val st0 = _state.value
            if (st0.key.isEmpty()) return@launch
            if (reset) {
                _state.update {
                    it.copy(
                        opsLoading = true, ops = emptyList(), opsHasMore = false,
                        opsLoaded = false, opsCursorTs = 0, opsCursorId = 0
                    )
                }
            } else {
                _state.update { it.copy(opsLoadingMore = true) }
            }
            val acc = settings.currentAccountId()
            val range = runCatching { periodRange(st0.type, st0.key) }.getOrNull()
            if (range == null) {
                _state.update { it.copy(opsLoading = false, opsLoadingMore = false, opsLoaded = true) }
                return@launch
            }
            val lastTs = if (reset) 0 else st0.opsCursorTs
            val lastId = if (reset) 0 else st0.opsCursorId
            val page = try {
                repo.periodPage(acc, curCode, range.first, range.second, st0.opsType, st0.opsCat, lastTs, lastId, PAGE_SIZE)
            } catch (_: Throwable) { emptyList() }
            // Гонка: за время запроса сменились тип/ключ/вкладка — результат устарел
            if (_state.value.type != st0.type || _state.value.key != st0.key) return@launch
            _state.update { s ->
                // distinctBy: защита от редкой гонки «удаление + loadMore» —
                // два параллельных non-reset запроса с одним курсором вернули
                // бы одну и ту же страницу; дубликат id уронил бы LazyColumn
                // («Key was already used»)
                val merged = (if (reset) page else s.ops + page).distinctBy { it.id }
                val last = page.lastOrNull()
                s.copy(
                    ops = merged,
                    opsHasMore = page.size == PAGE_SIZE,
                    opsLoading = false,
                    opsLoadingMore = false,
                    opsLoaded = true,
                    opsCursorTs = last?.timestamp ?: s.opsCursorTs,
                    opsCursorId = last?.id ?: s.opsCursorId
                )
            }
        }
    }

    /** Границы периода [начало; начало следующего) в миллисекундах системной зоны. */
    private fun periodRange(pt: PeriodType, key: String): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val start = pt.startDateOf(key).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = pt.startDateOf(pt.shift(key, 1)).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }
}