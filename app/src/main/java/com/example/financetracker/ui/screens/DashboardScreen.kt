@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.financetracker.data.model.Currency
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.TransactionEntity
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.locale.cat
import com.example.financetracker.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(vm: FinanceViewModel = hiltViewModel(), onOpenSettings: () -> Unit) {
    val s = LocalStrings.current
    val ui by vm.ui.collectAsState()
    val cur by vm.currency.collectAsState()
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
            TopAppBar(title = { Text(s.appTitle) }, actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, s.settings)
                }
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

            // Компактная статистика: чистая сумма за день/неделю/месяц/год
            if (balanceExpanded && ui.periods.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ui.periods.forEach { st ->
                        Card(
                            Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(
                                    periodLabel(s, st.type),
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    compact(st.net),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (st.net < 0) MaterialTheme.colorScheme.error
                                    else IncomeGreen
                                )
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
                                Text(s.cat(t.category), fontWeight = FontWeight.Medium)
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
                Text("${s.cat(t.category)}: ${fmt(t.amount, Currency.fromCode(t.currencyCode))}")
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
                Text("${s.cat(t.category)}: ${fmt(t.amount, Currency.fromCode(t.currencyCode))}")
            },
            confirmButton = {
                Button(onClick = { vm.remove(t); toDelete = null }) { Text(s.delete) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text(s.cancel) } }
        )
    }

    if (dlg) {
        AddDlg(cur, { dlg = false }) { a, c, n, i ->
            vm.add(a, c, n, i)
            dlg = false
        }
    }
}

@Composable
fun AddDlg(currency: Currency, dismiss: () -> Unit, ok: (Double, String, String, Boolean) -> Unit) {
    val s = LocalStrings.current
    var amt by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("Food") }
    var note by remember { mutableStateOf("") }
    var inc by remember { mutableStateOf(false) }

    val cats = if (inc) listOf("Salary", "Freelance", "Invest", "Gift", "Other")
               else listOf("Food", "Transport", "Housing", "Fun", "Health", "Other")

    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(if (inc) s.addIncome else s.addExpense) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row {
                    FilterChip(!inc, { inc = false }, { Text(s.expenseChip) })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(inc, { inc = true }, { Text(s.incomeChip) })
                }

                OutlinedTextField(amt, { amt = it }, label = { Text("${s.amount} ${currency.symbol}") }, singleLine = true)

                var ce by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(ce, { ce = it }) {
                    OutlinedTextField(
                        s.cat(cat), {},
                        readOnly = true,
                        label = { Text(s.category) },
                        modifier = Modifier.menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(ce) }
                    )
                    ExposedDropdownMenu(ce, { ce = false }) {
                        cats.forEach { c ->
                            DropdownMenuItem(text = { Text(s.cat(c)) }, onClick = { cat = c; ce = false })
                        }
                    }
                }

                OutlinedTextField(note, { note = it }, label = { Text(s.note) }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = { amt.toDoubleOrNull()?.let { ok(it, cat, note, inc) } }) { Text(s.add) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(s.cancel) } }
    )
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
        append(num, style = SpanStyle(color = numColor))
        if (withSymbol) {
            append(c.symbol, style = SpanStyle(color = Color.White))
        }
    }
