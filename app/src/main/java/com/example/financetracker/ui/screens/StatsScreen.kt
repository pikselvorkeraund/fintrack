@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.financetracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
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
import com.example.financetracker.data.model.Currency
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.TransactionEntity
import com.example.financetracker.ui.locale.LocalStrings
import com.example.financetracker.ui.locale.Strings
import com.example.financetracker.ui.locale.cat
import com.example.financetracker.ui.locale.periodTitle
import com.example.financetracker.ui.viewmodel.BarItem
import com.example.financetracker.ui.viewmodel.StatsTab
import com.example.financetracker.ui.viewmodel.StatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Цвет положительных сумм (как на дашборде). */
private val StatsGreen = Color(0xFF81C784)

/**
 * Экран «Периоды»: переключатель типа периода, листание периодов в пределах
 * истории и две вкладки — «Статистика» (горизонтальные bar-чарты по
 * категориям) и «Операции» (список записей выбранного периода с
 * keyset-ленивой подгрузкой и удалением через подтверждение).
 * Вертикальные отступы сжаты для экономии места.
 */
@Composable
fun StatsScreen(
    vm: StatsViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val st by vm.state.collectAsState()

    // Подтверждение удаления и просмотр заметки (как на дашборде)
    var toDelete by remember { mutableStateOf<TransactionEntity?>(null) }
    var viewed by remember { mutableStateOf<TransactionEntity?>(null) }

    fun labelFor(id: Int): String = s.cat(st.catNames[id] ?: "")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.periodsTitle) },
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
                .padding(horizontal = 12.dp)
        ) {
            // Переключатель типа статистики: 4 чипа в ряд (каждый weight=1f).
            // Подписи — labelSmall + одна строка, иначе длинные слова
            // («Неделя», «Месяц», «Year») не вмещаются в четверть ширины
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
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
            Spacer(Modifier.height(8.dp))

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
            Spacer(Modifier.height(4.dp))

            // Вкладки: Статистика (графики) / Операции (список периода).
            // Компактный сегментированный переключатель вместо TabRow —
            // экономит вертикальное пространство.
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = st.tab == StatsTab.STATS,
                    onClick = { vm.setTab(StatsTab.STATS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text(s.tabStats, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                SegmentedButton(
                    selected = st.tab == StatsTab.OPS,
                    onClick = { vm.setTab(StatsTab.OPS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text(s.tabOps, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
            Spacer(Modifier.height(8.dp))

            if (st.tab == StatsTab.STATS) {
                // Вкладка «Статистика»: два bar-чарта, прокручиваемая колонка
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
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
                        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            } else {
                // Вкладка «Операции»: записи выбранного периода, keyset-
                // ленивая подгрузка по 20 (как на дашборде), удаление
                // через подтверждение. Отдельная загрузка при пустом списке.
                val listState = rememberLazyListState()
                LaunchedEffect(listState, st.opsHasMore, st.opsLoadingMore) {
                    snapshotFlow {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        val total = listState.layoutInfo.totalItemsCount
                        last >= total - 3
                    }.collect { nearEnd ->
                        if (nearEnd && st.opsHasMore && !st.opsLoadingMore) vm.loadOpsMore()
                    }
                }
                Box(Modifier.weight(1f)) {
                    if (st.opsLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (st.ops.isEmpty()) {
                                item(key = "empty") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            s.noStatsData,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            items(st.ops, key = { it.id }) { t ->
                                Card(Modifier.fillMaxWidth().clickable { viewed = t }) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
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
                                        val color = if (t.isIncome) StatsGreen else MaterialTheme.colorScheme.error
                                        Text(
                                            (if (t.isIncome) "+" else "-") + fmt(t.amount, Currency.fromCode(t.currencyCode)),
                                            fontWeight = FontWeight.Bold,
                                            color = color
                                        )
                                        IconButton(onClick = { toDelete = t }) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                            if (st.opsLoadingMore) {
                                item(key = "loader") {
                                    Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Просмотр комментария записи
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

    // Простое подтверждение удаления (правило AGENTS.md: без мгновенного удаления)
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
}

/**
 * Горизонтальный bar-чарт по категориям (не больше 7 столбиков).
 * Формат строки категории: строка 1 — название слева + сумма справа,
 * строка 2 — бар на всю ширину.
 */
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
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
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
            Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(6.dp))
                // Строка 1: категория слева, сумма справа
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        s.cat(b.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        String.format("%,.0f", b.value),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                // Строка 2: бар на всю ширину строки
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(b.color.toInt()))
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