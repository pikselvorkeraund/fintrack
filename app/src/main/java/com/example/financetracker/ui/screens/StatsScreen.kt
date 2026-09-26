@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.locale.Strings
import com.example.financetracker.ui.locale.cat
import com.example.financetracker.ui.locale.periodTitle
import com.example.financetracker.ui.viewmodel.BarItem
import com.example.financetracker.ui.viewmodel.StatsViewModel

/** Цвет положительных сумм (как на дашборде). */
private val StatsGreen = Color(0xFF81C784)

/**
 * Экран статистики: переключатель типа периода, листание периодов в пределах
 * истории и два горизонтальных bar-чарта (расходы/доходы по категориям).
 */
@Composable
fun StatsScreen(
    vm: StatsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val st by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.statsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, s.back)
                    }
                }
            )
        }
    ) { p ->
        Column(
            Modifier
                .padding(p)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Переключатель типа статистики: 4 чипа в ряд (каждый weight=1f).
            // Подписи — labelSmall + одна строка, иначе длинные слова
            // («Неделя», «Месяц», «Year») не вмещаются в четверть ширины
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(PeriodType.DAY, PeriodType.WEEK, PeriodType.MONTH, PeriodType.YEAR).forEach { pt ->
                    FilterChip(
                        selected = st.type == pt,
                        onClick = { vm.setType(pt) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        label = {
                            Text(
                                periodLabelChip(s, pt),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                textAlign = TextAlign.Center
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // Строка периода: назад | заголовок | вперёд (листание до границ БД)
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.shift(-1) }, enabled = st.canGoBack) {
                    Icon(Icons.Default.ChevronLeft, s.back)
                }
                Text(
                    s.periodTitle(st.type, st.key),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = { vm.shift(1) }, enabled = st.canGoForward) {
                    Icon(Icons.Default.ChevronRight, s.back)
                }
            }
            Spacer(Modifier.height(16.dp))

            if (st.loading) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            } else if (st.empty) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        s.noStatsData,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    BarChartCard(
                        s = s,
                        title = s.expenses,
                        total = st.totalExpense,
                        bars = st.expenseBars,
                        valueColor = MaterialTheme.colorScheme.error,
                        currencySymbol = st.currency.symbol
                    )
                    BarChartCard(
                        s = s,
                        title = s.income,
                        total = st.totalIncome,
                        bars = st.incomeBars,
                        valueColor = StatsGreen,
                        currencySymbol = st.currency.symbol
                    )
                }
            }
        }
    }
}

/** Горизонтальный bar-чарт по категориям (не больше 7 столбиков). */
@Composable
private fun BarChartCard(
    s: Strings,
    title: String,
    total: Double,
    bars: List<BarItem>,
    valueColor: Color,
    currencySymbol: String
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(
                    String.format("%,.0f %s", total, currencySymbol),
                    style = MaterialTheme.typography.labelLarge,
                    color = valueColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            if (bars.isEmpty()) {
                Text(
                    "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val max = (bars.maxOfOrNull { it.value } ?: 1.0).coerceAtLeast(1.0)
            bars.forEach { b ->
                val frac = (b.value / max).toFloat().coerceIn(0.02f, 1f)
                Spacer(Modifier.height(8.dp))
                Text(
                    s.cat(b.label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(frac)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(b.color.toInt()))
                        )
                    }
                    Text(
                        String.format("%,.0f", b.value),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun periodLabelChip(s: Strings, p: PeriodType): String =
    when (p) {
        PeriodType.DAY -> s.periodDay
        PeriodType.WEEK -> s.periodWeek
        PeriodType.MONTH -> s.periodMonth
        PeriodType.YEAR -> s.periodYear
        PeriodType.TOTAL -> ""
    }