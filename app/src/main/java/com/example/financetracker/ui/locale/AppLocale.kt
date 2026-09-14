package com.example.financetracker.ui.locale

import androidx.compose.runtime.compositionLocalOf

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
    val min4: String,
    val wrongPrefix: String,
    val wiped: String,
    val newPattern: String,
    val dbError: String,
    val exitHint: String,
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
    min4 = "Min 4 dots",
    wrongPrefix = "Wrong. Left: ",
    wiped = "Data wiped after 3 failed attempts. Create new pattern.",
    newPattern = "New pattern",
    dbError = "Database error",
    exitHint = "Press back again to exit the app",
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
    min4 = "Минимум 4 точки",
    wrongPrefix = "Неверно. Осталось: ",
    wiped = "Данные удалены после 3 неверных попыток. Создайте новый узор.",
    newPattern = "Новый узор",
    dbError = "Ошибка базы данных",
    exitHint = "При повторном нажатии будет выход из приложения",
    categories = catsRu
)

fun Strings.cat(name: String): String = categories[name] ?: name

fun stringsFor(lang: Language): Strings = if (lang == Language.RU) StringsRu else StringsEn

val LocalStrings = compositionLocalOf { StringsEn }
