@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.viewmodel.AllCurrenciesViewModel
import com.example.financetracker.ui.viewmodel.CurrencyStat
import com.example.financetracker.data.model.Currency
import java.text.NumberFormat
import java.util.Locale

/** Форматирование суммы валюты в компактном виде (символ + число). */
private fun fmtCurrency(v: Double, c: Currency): String =
    "${c.symbol} ${
        NumberFormat.getNumberInstance(
            if (c.code == "RUB") Locale.forLanguageTag("ru") else Locale.US
        ).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }.format(v)
    }"

/** Форматирование пересчитанной суммы (с дробями до 2 знаков). */
private fun fmtBase(v: Double, c: Currency): String =
    "${c.symbol} ${
        NumberFormat.getNumberInstance(
            if (c.code == "RUB") Locale.forLanguageTag("ru") else Locale.US
        ).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 0
        }.format(v)
    }"

/** Экран сводной статистики по всем валютам активного счёта (issue #3 + #5). */
@Composable
fun AllCurrenciesScreen(
    onBack: () -> Unit,
    vm: AllCurrenciesViewModel = hiltViewModel()
) {
    val s = LocalStrings.current
    val state by vm.ui.collectAsState()
    // Диалог курса: валюта, для которой редактируем курс (null — диалог закрыт)
    var rateDlgCurrency by remember { mutableStateOf<Currency?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.allCurrenciesTitle, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back)
                    }
                }
            )
        }
    ) { p ->
        when {
            state.loading -> {
                Column(
                    Modifier.fillMaxSize().padding(p),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            }
            state.currencies.isEmpty() -> {
                Column(
                    Modifier.fillMaxSize().padding(p),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        s.allCurrenciesEmpty,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(p),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.currencies, key = { it.currency.code }) { stat ->
                        CurrencyCard(
                            stat = stat,
                            baseCurrency = state.baseCurrency,
                            onEditRate = { rateDlgCurrency = stat.currency }
                        )
                    }
                    // Итоговая строка (только если есть основная валюта и конвертируемые)
                    if (state.baseCurrency != null && state.totalBase != null) {
                        item {
                            TotalCard(
                                total = state.totalBase!!,
                                base = state.baseCurrency!!,
                                missing = state.missingCount
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    // Диалог ввода/редактирования кросс-курса
    rateDlgCurrency?.let { cur ->
        RateDialog(
            from = cur,
            to = state.baseCurrency ?: return@let,
            existingRate = state.currencies.firstOrNull { it.currency == cur }?.baseRate,
            onSave = { rate -> vm.setRate(cur.code, state.baseCurrency!!.code, rate) },
            onDismiss = { rateDlgCurrency = null }
        )
    }
}

/**
 * Карточка одной валюты: символ, код, сумма доходов/расходов/баланса за всё время.
 * Если курс в основную валюту задан — в скобках рядом с балансом пересчёт,
 * справа — кнопка редактирования (карандаш). Если курс не задан — только кнопка.
 */
@Composable
private fun CurrencyCard(
    stat: CurrencyStat,
    baseCurrency: Currency?,
    onEditRate: () -> Unit
) {
    val s = LocalStrings.current
    val c = stat.currency
    val isBase = baseCurrency == c

    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            // Строка 1: символ + код | баланс (+ скобки пересчёта) + кнопка
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.symbol,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        c.code,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        fmtCurrency(stat.balance, c),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (stat.balance >= 0) Color(0xFF81C784) else Color(0xFFEF5350)
                    )
                    // Пересчёт в основную валюту (скобки) — только если курс задан и валюта != основная
                    if (stat.baseRate != null && !isBase) {
                        Text(
                            " (${fmtBase(stat.balance, baseCurrency!!)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    // Кнопка редактирования курса — только для не-основных валют
                    if (!isBase && baseCurrency != null) {
                        IconButton(
                            onClick = onEditRate,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = s.editRate,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            // Строка 2: доход / расход
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.income,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        fmtCurrency(stat.income, c),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF81C784)
                    )
                }
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        s.expenses,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        fmtCurrency(stat.expense, c),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFEF5350)
                    )
                }
            }
        }
    }
}

/** Итоговая карточка: сумма всех валют в основной валюте + пометка о пропущенных. */
@Composable
private fun TotalCard(
    total: Double,
    base: Currency,
    missing: Int
) {
    val s = LocalStrings.current
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    s.totalInBase(base.code),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (missing > 0) {
                    Text(
                        s.missingRates(missing),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                fmtBase(total, base),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * Диалог ввода/редактирования кросс-курса:
 * из какой валюты → в какую (основная), 1 от = ? в.
 * [existingRate] != null — режим редактирования (поле заполнено).
 */
@Composable
private fun RateDialog(
    from: Currency,
    to: Currency,
    existingRate: Double?,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    val s = LocalStrings.current
    var input by remember {
        mutableStateOf(existingRate?.let {
            if (it % 1.0 == 0.0) it.toLong().toString()
            else String.format(Locale.US, "%.4f", it)
        } ?: "")
    }
    val parsed = input.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.rateDialogTitle(from.code, to.code)) },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Направление
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${s.rateFromLabel}: ${from.code} ${from.symbol}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "→",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${s.rateToLabel}: ${to.code} ${to.symbol}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Поле ввода курса
                OutlinedTextField(
                    value = input,
                    onValueChange = { v -> input = v.filter { it.isDigit() || it == ',' || it == '.' } },
                    label = { Text("1 ${from.symbol} = ${to.symbol}") },
                    singleLine = true,
                    isError = input.isNotEmpty() && parsed == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                // Пример
                Text(
                    if (existingRate != null) s.rateExample(from.symbol, existingRate, to.symbol)
                    else s.rateUnknown(from.symbol, to.symbol),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null,
                onClick = { parsed?.let(onSave); onDismiss() }
            ) {
                Text(if (existingRate != null) s.save else s.setRate)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(s.cancel) }
        }
    )
}
