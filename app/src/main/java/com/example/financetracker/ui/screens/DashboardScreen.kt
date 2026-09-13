@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.financetracker.data.model.Currency
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
    var backOnce by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as Activity

    // Двойное нажатие Назад: первое — подсказка, повторное — выход
    BackHandler(enabled = !dlg) {
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
                    Text(s.balance, style = MaterialTheme.typography.bodyMedium)
                    Text(fmt(ui.balance, ui.currency), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(s.income)
                            Text("+${fmt(ui.income, ui.currency)}", color = MaterialTheme.colorScheme.primary)
                        }
                        Column {
                            Text(s.expenses)
                            Text("-${fmt(ui.expense, ui.currency)}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ui.items, key = { it.id }) { t ->
                    Card(Modifier.fillMaxWidth()) {
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
                                "${if (t.isIncome) "+" else "-"}${fmt(t.amount, Currency.fromCode(t.currencyCode))}",
                                fontWeight = FontWeight.Bold,
                                color = if (t.isIncome) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            IconButton(onClick = { vm.remove(t) }) {
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
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
