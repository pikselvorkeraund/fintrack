@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.border
import com.example.financetracker.data.model.CategoryEntity
import com.example.financetracker.data.model.Currency
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.TransactionEntity
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.locale.cat
import com.example.financetracker.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.*

@Composable
fun DashboardScreen(
    vm: FinanceViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenStats: () -> Unit
) {
    val s = LocalStrings.current
    val ui by vm.ui.collectAsState()
    val cur by vm.currency.collectAsState()
    val acc by vm.account.collectAsState()
    val cats by vm.categories.collectAsState()

    // Имя категории по её id (для карточек списка и диалогов)
    val catById = remember(cats) { cats.associate { it.id to it.name } }
    fun labelFor(id: Int): String = s.cat(catById[id] ?: "")

    // Перезагружаем данные при первом композе дашборда
    // (FinanceViewModel.init отработал ещё до открытия БД)
    LaunchedEffect(Unit) {
        vm.reload()
    }
    var dlg by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var viewed by remember { mutableStateOf<TransactionEntity?>(null) }
    var backOnce by remember { mutableStateOf(false) }
    var balanceExpanded by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as Activity

    // Двойное нажатие Назад: первое — подсказка, повторное — выход
    BackHandler(enabled = !dlg && toDelete == null && viewed == null) {
        if (backOnce) {
            activity.finishAffinity()
        } else {
            backOnce = true
            scope.launch {
                snackbar.showSnackbar(s.exitHint)
                delay(2500)
                backOnce = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(title = {
                // Название текущего счёта — тональная кнопка-пилюля:
                // полупрозрачный фон и обводка в цвет счёта, точка-индикатор,
                // стрелка «открыть список счетов», ripple клипается по форме
                val accColor = Color((acc?.color ?: 0xFF1976D2L).toInt())
                val pillShape = RoundedCornerShape(percent = 50)
                Row(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(accColor.copy(alpha = 0.15f))
                        .border(1.dp, accColor, pillShape)
                        .clickable(role = Role.Button, onClickLabel = s.changeAccount) { onOpenAccounts() }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accColor)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        acc?.name ?: s.appTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = accColor
                    )
                }
            }, actions = {
                // Сначала выбор валюты, затем шестерёнка настроек
                var exp by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { exp = true }) { Text("${cur.code} ${cur.symbol}") }
                    DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                        Currency.entries.forEach { c ->
                            DropdownMenuItem(
                                text = { Text("${c.code} ${c.displayName}") },
                                onClick = { vm.setCurrency(c); exp = false }
                            )
                        }
                    }
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, s.settings)
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dlg = true }) { Icon(Icons.Default.Add, null) }
        }
    ) { p ->
        Column(Modifier.padding(p)) {
            Card(
                Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(s.balance, style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { balanceExpanded = !balanceExpanded }) {
                            Icon(
                                if (balanceExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (balanceExpanded) {
                        Text(
                            amountStr("", ui.balance, ui.currency, if (ui.balance < 0) MaterialTheme.colorScheme.error else IncomeGreen),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(s.income)
                                Text(amountStr("+", ui.income, ui.currency, IncomeGreen))
                            }
                            Column {
                                Text(s.expenses)
                                Text(amountStr("-", ui.expense, ui.currency, MaterialTheme.colorScheme.error))
                            }
                        }
                    }
                }
            }

            // Компактная статистика: карточки-кнопки за день/неделю/месяц/год.
            // Иконка графика + chevron показывают, что карточка ведёт
            // на экран статистики за соответствующим периодом.
            if (balanceExpanded && ui.periods.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ui.periods.forEach { st ->
                        Card(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button, onClickLabel = s.statsTitle) { onOpenStats() },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                // Первая строка — только название периода:
                                // в узкой колонке иконка/стрелка в одном ряду
                                // с подписью не помещаются и съезжают
                                Text(
                                    periodLabel(s, st.type),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                // Вторая строка — иконка, сумма и стрелка
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.BarChart,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        compact(st.net),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                        color = if (st.net < 0) MaterialTheme.colorScheme.error
                                        else IncomeGreen
                                    )
                                    Icon(
                                        Icons.Default.ChevronRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val listState = rememberLazyListState()
            // Ленивая загрузка: при прокрутке к концу списка подгружаем
            // следующую страницу (20 записей) от последнего загруженного id
            LaunchedEffect(listState, ui.hasMore, ui.loadingMore) {
                snapshotFlow {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    val total = listState.layoutInfo.totalItemsCount
                    last >= total - 3
                }.collect { nearEnd ->
                    if (nearEnd && ui.hasMore && !ui.loadingMore) vm.loadMore()
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Пустое состояние: у активной валюты ещё нет записей
                if (ui.items.isEmpty() && !ui.loading) {
                    item(key = "empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                s.noRecords.replace("{CURRENCY}", ui.currency.code),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                items(ui.items, key = { it.id }) { t ->
                    Card(
                        Modifier.fillMaxWidth().clickable { viewed = t }
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(labelFor(t.categoryId), fontWeight = FontWeight.Medium)
                                Text(
                                    SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(t.timestamp)),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(
                                amountStr(if (t.isIncome) "+" else "-", t.amount, Currency.fromCode(t.currencyCode), if (t.isIncome) IncomeGreen else MaterialTheme.colorScheme.error),
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { toDelete = t }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                if (ui.loadingMore) {
                    item(key = "loader") {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    }
                }
            }
        }
    }

    // Просмотр комментария записи (длинный текст прокручивается)
    viewed?.let { t ->
        AlertDialog(
            onDismissRequest = { viewed = null },
            title = {
                Text("${labelFor(t.categoryId)}: ${fmt(t.amount, Currency.fromCode(t.currencyCode))}")
            },
            text = {
                Column(
                    Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(t.timestamp)),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(if (t.note.isBlank()) s.noNote else t.note)
                }
            },
            confirmButton = {
                TextButton(onClick = { viewed = null }) { Text(s.close) }
            }
        )
    }

    // Простое подтверждение удаления
    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(s.deleteTitle) },
            text = {
                Text("${labelFor(t.categoryId)}: ${fmt(t.amount, Currency.fromCode(t.currencyCode))}")
            },
            confirmButton = {
                Button(onClick = { vm.remove(t); toDelete = null }) { Text(s.delete) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(s.cancel) } }
        )
    }

    if (dlg) {
        AddDlg(
            currency = cur,
            categories = cats,
            onCreateCategory = { name, inc -> vm.addCategory(name, inc) },
            dismiss = { dlg = false },
            ok = { a, c, n, i, t -> vm.add(a, c, n, i, t); dlg = false }
        )
    }
}

/**
 * Диалог добавления записи: числовая клавиатура суммы, дата/время (календарь
 * + часы) кнопкой справа от выбора типа, категории из справочника БД
 * с пунктом «+ Добавить новую» и собственным диалогом ввода.
 */
@Composable
fun AddDlg(
    currency: Currency,
    categories: List<CategoryEntity>,
    onCreateCategory: suspend (String, Boolean) -> Int?,
    dismiss: () -> Unit,
    ok: (Double, Int, String, Boolean, Long) -> Unit
) {
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    var amt by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var inc by remember { mutableStateOf(false) }
    var catId by remember { mutableStateOf<Int?>(null) }
    var ts by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var newCat by remember { mutableStateOf(false) }
    var newCatName by remember { mutableStateOf("") }

    // Категории текущего типа; выбранная сбрасывается на первую,
    // если после переключения типа она не подходит
    val catsFor = categories.filter { it.isIncome == inc }
    val effectiveCat = catId?.takeIf { id -> catsFor.any { it.id == id } }
        ?: catsFor.firstOrNull()?.id
    val parsedAmt = amt.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (inc) s.addIncome else s.addExpense) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(!inc, { inc = false }, { Text(s.expenseChip) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(inc, { inc = true }, { Text(s.incomeChip) })
                    Spacer(Modifier.weight(1f))
                    // Текущие дата и время записи — кнопка, открывающая пикеры
                    FilledTonalButton(
                        onClick = { showDate = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ts)),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                // Клавиатура только для чисел: фильтруем ввод, кроме цифр
                // и десятичных разделителей (запятая трактуется как точка)
                OutlinedTextField(
                    amt,
                    { v -> amt = v.filter { it.isDigit() || it == ',' || it == '.' } },
                    label = { Text("${s.amount} ${currency.symbol}") },
                    singleLine = true,
                    isError = amt.isNotEmpty() && parsedAmt == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                var ce by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(ce, { ce = it }) {
                    OutlinedTextField(
                        s.cat(catsFor.firstOrNull { it.id == effectiveCat }?.name ?: ""), {},
                        readOnly = true,
                        label = { Text(s.category) },
                        modifier = Modifier.menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ce) }
                    )
                    ExposedDropdownMenu(ce, { ce = false }) {
                        catsFor.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(s.cat(c.name)) },
                                onClick = { catId = c.id; ce = false }
                            )
                        }
                        // Пункт справочника: создать новую категорию
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(s.addNewCategory)
                                }
                            },
                            onClick = { newCatName = ""; newCat = true; ce = false }
                        )
                    }
                }

                OutlinedTextField(note, { note = it }, label = { Text(s.note) }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = parsedAmt != null && effectiveCat != null,
                onClick = {
                    val a = parsedAmt ?: return@Button
                    val c = effectiveCat ?: return@Button
                    ok(a, c, note, inc, ts)
                }
            ) { Text(s.add) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(s.cancel) } }
    )

    // Пикер даты; после ОК сразу открывается пикер времени
    if (showDate) {
        val initialUtc = Instant.ofEpochMilli(ts)
            .atZone(ZoneId.systemDefault()).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dState = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dState.selectedDateMillis?.let { m ->
                        val d = Instant.ofEpochMilli(m).atZone(ZoneOffset.UTC).toLocalDate()
                        val t = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime()
                        ts = d.atTime(t).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }
                    showDate = false
                    showTime = true
                }) { Text(s.ok) }
            }
        ) {
            DatePicker(state = dState, title = { Text(s.pickDate, Modifier.padding(start = 20.dp, top = 12.dp)) })
        }
    }

    // Пикер времени (часы+минуты), внутри обычного диалога
    if (showTime) {
        val ldt = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val tState = rememberTimePickerState(initialHour = ldt.hour, initialMinute = ldt.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            title = { Text(s.pickTime) },
            text = { TimePicker(state = tState) },
            confirmButton = {
                TextButton(onClick = {
                    ts = ldt.toLocalDate()
                        .atTime(tState.hour, tState.minute)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    showTime = false
                }) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text(s.cancel) } }
        )
    }

    // Создание новой категории: ввод названия, ОК/Отмена
    if (newCat) {
        AlertDialog(
            onDismissRequest = { newCat = false },
            title = { Text(s.newCategoryTitle) },
            text = {
                OutlinedTextField(
                    newCatName,
                    { newCatName = it },
                    label = { Text(s.categoryName) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = newCatName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val id = onCreateCategory(newCatName.trim(), inc)
                            if (id != null) catId = id
                            newCat = false
                        }
                    }
                ) { Text(s.ok) }
            },
            dismissButton = { TextButton(onClick = { newCat = false }) { Text(s.cancel) } }
        )
    }
}

fun fmt(v: Double, c: Currency) = String.format("%,.2f %s", v, c.symbol)

private fun periodLabel(s: com.example.financetracker.ui.locale.Strings, p: PeriodType): String =
    when (p) {
        PeriodType.DAY -> s.periodDay
        PeriodType.WEEK -> s.periodWeek
        PeriodType.MONTH -> s.periodMonth
        PeriodType.YEAR -> s.periodYear
        PeriodType.TOTAL -> ""
    }

/** Компактный вид суммы с суффиксами k/m: +12,4k / −48,9k / +318k */
private fun compact(v: Double): String {
    val a = kotlin.math.abs(v)
    val sign = if (v < 0) "-" else "+"
    val (num, suf) = when {
        a >= 1_000_000 -> a / 1_000_000 to "m"
        a >= 1_000 -> a / 1_000 to "k"
        else -> a to ""
    }
    val body =
        if (num >= 100 || num % 1.0 == 0.0) num.toLong().toString()
        else String.format("%.1f", num).replace('.', ',')
    return sign + body + suf
}

private val IncomeGreen = Color(0xFF81C784)

/**
 * @param sign  префикс (+/−/пустая)
 * @param value число
 * @param c     валюта
 * @param numColor  цвет числа и знака
 * @param withSymbol если true — добавить символ валюты белым (использовать только там, где он был в оригинале)
 */
private fun amountStr(sign: String, value: Double, c: Currency, numColor: Color, withSymbol: Boolean = true) =
    buildAnnotatedString {
        val num = sign + String.format("%,.2f", value) + (if (withSymbol) " " else "")
        withStyle(SpanStyle(color = numColor)) {
            append(num)
        }
        if (withSymbol) {
            withStyle(SpanStyle(color = Color.White)) {
                append(c.symbol)
            }
        }
    }