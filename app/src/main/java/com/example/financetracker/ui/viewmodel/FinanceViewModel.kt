package com.example.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.model.AccountEntity
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
import javax.inject.Inject

/** Чистая сумма (доход − расход) за период для компактного блока статистики. */
data class PeriodStat(val type: PeriodType, val net: Double)

data class UiState(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val balance: Double = 0.0,
    val currency: Currency = Currency.RUB,
    val items: List<TransactionEntity> = emptyList(),
    val periods: List<PeriodStat> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false
)

@HiltViewModel
class FinanceViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    companion object { const val PAGE_SIZE = 20 }

    private val _cur = MutableStateFlow(Currency.RUB)
    val currency: StateFlow<Currency> = _cur

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    /** Текущий счёт (null пока не загружен). */
    private val _account = MutableStateFlow<AccountEntity?>(null)
    val account: StateFlow<AccountEntity?> = _account.asStateFlow()

    /** Справочник категорий (расходные и доходные вместе, в порядке id). */
    private val _categories = MutableStateFlow<List<CategoryEntity>>(emptyList())
    val categories: StateFlow<List<CategoryEntity>> = _categories.asStateFlow()

    /**
     * Состояние сворачивания карточки баланса ПО СЧЁТАМ (accountId → expanded).
     * Хранится в ViewModel: ViewModel живёт, пока жив NavHost, — состояние
     * переживает переходы на Настройки/Статистику/Счета в рамках сессии,
     * но при выходе из приложения (смерть процесса) сбрасывается в
     * значение по умолчанию «скрыто» (false).
     */
    private val _balanceExpanded = MutableStateFlow<Map<Int, Boolean>>(emptyMap())
    /** Карта для подписки UI: recompose при каждом toggle. Нет записи для
     *  счёта (или false) — баланс скрыт (значение по умолчанию). */
    val balanceExpandedMap: StateFlow<Map<Int, Boolean>> = _balanceExpanded.asStateFlow()

    /** Переключает видимость баланса для счёта [accId]. */
    fun toggleBalance(accId: Int) {
        _balanceExpanded.update { m ->
            val expanded = m[accId] == true
            m + (accId to !expanded)
        }
    }

    init {
        reload()
    }

    /** Загружает актуальную информацию о текущем счёте и справочник категорий. */
    suspend fun loadAccount() {
        // Гарантируем, что хотя бы один счёт и категория существуют
        // (новый файл БД после recreateWith или первый запуск)
        runCatching { repo.ensureDefaultAccount() }
        runCatching { repo.ensureDefaultCategories() }
        val acc = settings.currentAccountId()
        _account.value = runCatching { repo.listAccounts().firstOrNull { it.id == acc } }.getOrNull()
        _categories.value = runCatching { repo.listCategories() }.getOrDefault(emptyList())
    }

    /**
     * Создаёт категорию и обновляет справочник. Возвращает id новой
     * (или уже существовавшей) категории; null — если имя пустое или БД недоступна.
     */
    suspend fun addCategory(name: String, isIncome: Boolean): Int? {
        val id = runCatching { repo.addCategory(name, isIncome) }.getOrNull() ?: return null
        _categories.value = runCatching { repo.listCategories() }.getOrDefault(_categories.value)
        return id
    }

    /** Переключает активный счёт: обновляет SettingsRepository + перезагружает дашборд. */
    fun switchAccount(id: Int) {
        settings.setCurrentAccount(id)
        reload()
    }

    /** Переопределяет активный счёт без перезагрузки (используется из AccountsScreen). */
    fun setAccount(id: Int) {
        settings.setCurrentAccount(id)
        viewModelScope.launch {
            loadAccount()
            reload()
        }
    }

    fun setCurrency(c: Currency) {
        if (_cur.value == c) return
        _cur.value = c
        reload()
    }

    /** Первая страница записей активного счёта + валюты + суммы из таблицы статистики. */
    fun reload() {
        viewModelScope.launch {
            loadAccount()
            _ui.update { it.copy(loading = true) }
            val c = _cur.value
            val acc = settings.currentAccountId()
            try {
                val items = repo.page(acc, c.code, 0L, PAGE_SIZE)
                _ui.update { s -> s.copy(items = items, hasMore = items.size >= PAGE_SIZE, loading = false) }
            } catch (_: Throwable) {
                // В release сбои Room/SQLCipher могут быть Error-подклассом
                // (UnsatisfiedLinkError), который пролетает мимо catch(Exception)
                // и роняет процесс как uncaught в viewModelScope.
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
            _ui.update { s -> s.copy(loadingMore = true) }
            try {
                val next = repo.page(settings.currentAccountId(), _cur.value.code, st.items.last().id, PAGE_SIZE)
                _ui.update { s -> s.copy(items = s.items + next, hasMore = next.size >= PAGE_SIZE, loadingMore = false) }
            } catch (_: Throwable) {
                _ui.update { s -> s.copy(loadingMore = false) }
            }
        }
    }

    private suspend fun refreshTotals() {
        val c = _cur.value
        val acc = settings.currentAccountId()
        val i = try { repo.income(acc, c.code) } catch (_: Throwable) { 0.0 }
        val e = try { repo.expense(acc, c.code) } catch (_: Throwable) { 0.0 }
        _ui.update { s -> s.copy(income = i, expense = e, balance = i - e, currency = c) }
        refreshPeriods()
    }

    /**
     * Суммы за текущие день/неделю/месяц/год читаются точечно из `stats`
     * по ключам периодов — без скана транзакций.
     */
    private suspend fun refreshPeriods() {
        val c = _cur.value.code
        val acc = settings.currentAccountId()
        val now = System.currentTimeMillis()
        val list = try {
            PeriodType.entries.filter { it != PeriodType.TOTAL }.map { p ->
                val inc = repo.periodIncome(acc, c, p, p.keyOf(now))
                val exp = repo.periodExpense(acc, c, p, p.keyOf(now))
                PeriodStat(p, inc - exp)
            }
        } catch (_: Throwable) {
            emptyList()
        }
        _ui.update { s -> s.copy(periods = list) }
    }

    /**
     * Добавление записи. timestamp — выбранная пользователем дата и время
     * (по умолчанию — текущий момент). categoryId — ссылка на справочник.
     */
    fun add(amount: Double, categoryId: Int, note: String, income: Boolean, timestamp: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            val c = _cur.value
            val acc = settings.currentAccountId()
            try {
                val id = repo.add(
                    TransactionEntity(
                        accountId = acc,
                        amount = amount,
                        currencyCode = c.code,
                        categoryId = categoryId,
                        note = note,
                        isIncome = income,
                        timestamp = timestamp
                    )
                )
                _ui.update { s ->
                    s.copy(
                        items = listOf(
                            TransactionEntity(
                                id = id, accountId = acc, amount = amount,
                                currencyCode = c.code, categoryId = categoryId,
                                note = note, isIncome = income, timestamp = timestamp
                            )
                        ) + s.items
                    )
                }
            } catch (_: Throwable) { }
            refreshTotals()
        }
    }

    fun remove(t: TransactionEntity) {
        viewModelScope.launch {
            try {
                repo.remove(t)
                _ui.update { s -> s.copy(items = s.items.filter { it.id != t.id }) }
                if (_ui.value.items.isEmpty() && _ui.value.hasMore) reload()
                else if (_ui.value.items.size < PAGE_SIZE && _ui.value.hasMore) loadMore()
            } catch (_: Throwable) { }
            refreshTotals()
        }
    }
}