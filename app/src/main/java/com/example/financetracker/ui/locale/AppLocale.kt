package com.example.financetracker.ui.locale

import androidx.compose.runtime.compositionLocalOf
import com.example.financetracker.data.model.PeriodType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Language { EN, RU }

data class Strings(
    val appTitle: String,
    val balance: String,
    val income: String,
    val expenses: String,
    val addIncome: String,
    val addExpense: String,
    val amount: String,
    val category: String,
    val note: String,
    val expenseChip: String,
    val incomeChip: String,
    val add: String,
    val cancel: String,
    val delete: String,
    val deleteTitle: String,
    val close: String,
    val noNote: String,
    val noRecords: String,
    val periodDay: String,
    val periodWeek: String,
    val periodMonth: String,
    val periodYear: String,
    val settings: String,
    val language: String,
    val english: String,
    val russian: String,
    val drawPattern: String,
    val enterPattern: String,
    val hintSetup: String,
    val hintEnter: String,
    val min4: String,
    val wrongPrefix: String,
    val wiped: String,
    val newPattern: String,
    val dbError: String,
    val exitHint: String,
    val back: String,
    val accounts: String,
    val addAccount: String,
    val accountName: String,
    val rename: String,
    val deleteAccount: String,
    val deleteAccountMsg: String,
    val currentBadge: String,
    val errLastAccount: String,
    val errCurrentAccount: String,
    val errNameEmpty: String,
    val changeAccount: String,
    val addNewCategory: String,
    val newCategoryTitle: String,
    val categoryName: String,
    val ok: String,
    val pickDate: String,
    val pickTime: String,
    val dateTime: String,
    val statsTitle: String,
    val noStatsData: String,
    val total: String,
    /** Тег языка для java.time-форматирования заголовков периодов ("en"/"ru"). */
    val langCode: String,
    val categories: Map<String, String>
)

private val catsEn = mapOf(
    "Food" to "Food", "Transport" to "Transport", "Housing" to "Housing",
    "Fun" to "Fun", "Health" to "Health", "Other" to "Other",
    "Salary" to "Salary", "Freelance" to "Freelance", "Invest" to "Invest",
    "Gift" to "Gift"
)

private val catsRu = mapOf(
    "Food" to "Еда", "Transport" to "Транспорт", "Housing" to "Жильё",
    "Fun" to "Развлечения", "Health" to "Здоровье", "Other" to "Прочее",
    "Salary" to "Зарплата", "Freelance" to "Фриланс", "Invest" to "Инвестиции",
    "Gift" to "Подарок"
)

val StringsEn = Strings(
    appTitle = "My Finances",
    balance = "Balance",
    income = "Income",
    expenses = "Expenses",
    addIncome = "Add Income",
    addExpense = "Add Expense",
    amount = "Amount",
    category = "Category",
    note = "Note",
    expenseChip = "Expense",
    incomeChip = "Income",
    add = "Add",
    cancel = "Cancel",
    delete = "Delete",
    deleteTitle = "Delete this record?",
    close = "Close",
    noNote = "No note",
    noRecords = "No records for {CURRENCY}",
    periodDay = "Day",
    periodWeek = "Week",
    periodMonth = "Month",
    periodYear = "Year",
    settings = "Settings",
    language = "Language",
    english = "English",
    russian = "Русский",
    drawPattern = "Draw pattern (min 4 dots)",
    enterPattern = "Enter pattern",
    hintSetup = "Set an encryption key to get started",
    hintEnter = "Enter your key to unlock",
    min4 = "Min 4 dots",
    wrongPrefix = "Wrong. Left: ",
    wiped = "Data wiped after 3 failed attempts. Create new pattern.",
    newPattern = "New pattern",
    dbError = "Database error",
    exitHint = "Press back again to exit the app",
    back = "Back",
    accounts = "Accounts",
    addAccount = "Add Account",
    accountName = "Name",
    rename = "Rename",
    deleteAccount = "Delete Account",
    deleteAccountMsg = "Delete \"%1\$s\" and all its records?",
    currentBadge = "Current",
    errLastAccount = "Cannot delete the last account",
    errCurrentAccount = "Cannot delete the current account. Switch first.",
    errNameEmpty = "Name cannot be empty",
    changeAccount = "Change account",
    addNewCategory = "+ Add new",
    newCategoryTitle = "New category",
    categoryName = "Name",
    ok = "OK",
    pickDate = "Select date",
    pickTime = "Select time",
    dateTime = "Date & time",
    statsTitle = "Statistics",
    noStatsData = "No data for this period",
    total = "Total",
    langCode = "en",
    categories = catsEn
)

val StringsRu = Strings(
    appTitle = "Мои финансы",
    balance = "Баланс",
    income = "Доходы",
    expenses = "Расходы",
    addIncome = "Добавить доход",
    addExpense = "Добавить расход",
    amount = "Сумма",
    category = "Категория",
    note = "Заметка",
    expenseChip = "Расход",
    incomeChip = "Доход",
    add = "Добавить",
    cancel = "Отмена",
    delete = "Удалить",
    deleteTitle = "Удалить эту запись?",
    close = "Закрыть",
    noNote = "Без комментария",
    noRecords = "Нет записей для {CURRENCY}",
    periodDay = "День",
    periodWeek = "Неделя",
    periodMonth = "Месяц",
    periodYear = "Год",
    settings = "Настройки",
    language = "Язык",
    english = "English",
    russian = "Русский",
    drawPattern = "Нарисуйте узор (мин. 4 точки)",
    enterPattern = "Введите узор",
    hintSetup = "Для начала работы задайте ключ для шифрования",
    hintEnter = "Введите ключ для входа",
    min4 = "Минимум 4 точки",
    wrongPrefix = "Неверно. Осталось: ",
    wiped = "Данные удалены после 3 неверных попыток. Создайте новый узор.",
    newPattern = "Новый узор",
    dbError = "Ошибка базы данных",
    exitHint = "При повторном нажатии будет выход из приложения",
    back = "Назад",
    accounts = "Счета",
    addAccount = "Добавить счёт",
    accountName = "Название",
    rename = "Переименовать",
    deleteAccount = "Удалить счёт",
    deleteAccountMsg = "Удалить «%1\$s» и все его записи?",
    currentBadge = "Текущий",
    errLastAccount = "Нельзя удалить последний счёт",
    errCurrentAccount = "Нельзя удалить текущий счёт. Сначала переключитесь.",
    errNameEmpty = "Название не может быть пустым",
    changeAccount = "Сменить счёт",
    addNewCategory = "+ Добавить новую",
    newCategoryTitle = "Новая категория",
    categoryName = "Название",
    ok = "ОК",
    pickDate = "Выберите дату",
    pickTime = "Выберите время",
    dateTime = "Дата и время",
    statsTitle = "Статистика",
    noStatsData = "Нет данных за период",
    total = "Всего",
    langCode = "ru",
    categories = catsRu
)

fun Strings.cat(name: String): String = categories[name] ?: name

fun stringsFor(lang: Language): Strings = if (lang == Language.RU) StringsRu else StringsEn

val LocalStrings = compositionLocalOf { StringsEn }

/**
 * Человекочитаемый заголовок периода для экрана статистики.
 * Названия месяцев/дней форматируются через java.time по langCode строкового
 * набора (RU/EN), поэтому не дублируются как отдельные UI-строки.
 */
fun Strings.periodTitle(pt: PeriodType, key: String): String {
    // Пустой ключ (данные ещё не загружены) — парсинг упал бы: отдаём пусто
    if (key.isEmpty()) return ""
    val loc = Locale(langCode)
    return when (pt) {
        PeriodType.TOTAL -> ""
        PeriodType.DAY -> LocalDate.parse(key, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy", loc))
        PeriodType.WEEK -> {
            val start = pt.startDateOf(key)
            val end = start.plusDays(6)
            val f = DateTimeFormatter.ofPattern("dd.MM.yyyy")
            "${start.format(f)}-${end.format(f)}"
        }
        PeriodType.MONTH -> {
            val y = key.substringBefore("-").toInt()
            val m = key.substringAfter("-").toInt()
            LocalDate.of(y, m, 1).format(DateTimeFormatter.ofPattern("MMMM yyyy", loc))
        }
        PeriodType.YEAR -> if (langCode == "ru") "$key год" else key
    }
}
