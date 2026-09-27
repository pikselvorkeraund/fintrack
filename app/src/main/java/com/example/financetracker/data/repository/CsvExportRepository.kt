package com.example.financetracker.data.repository

import android.content.Context
import android.net.Uri
import com.example.financetracker.data.local.DbHolder
import com.example.financetracker.data.model.TransactionEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Локализованные подписи для CSV. Собираются из Strings во ViewModel:
 * data-layer не зависит от ui-слоя, а разделители/числа здесь фиксированы
 * (Excel-совместимый формат, см. AGENTS.md / DEV.md §13).
 */
data class CsvLabels(
    val date: String,
    val time: String,
    val type: String,
    val category: String,
    val income: String,
    val expense: String,
    val currency: String,
    val note: String,
    val total: String,
    /** Формирует тип записи (true — доход). */
    val typeOf: (Boolean) -> String,
    /** Перевод имени категории из справочника БД (ключ → локализованное имя). */
    val catName: (String) -> String
) {
    /** Порядок и состав колонок (8 колонок). */
    val headers: List<String> = listOf(date, time, type, category, income, expense, currency, note)
}

/**
 * Экспорт истории выбранного счёта в Excel-совместимый CSV.
 *
 * Разделитель — `;`, десятичная — запятая, UTF-8 с BOM, концы строк CRLF:
 * файл открывается двойным кликом в Excel/LibreOffice/1С на русской локали
 * без мастера импорта и без «крякозябр».
 *
 * В файл попадают только транзакции (без stats/справочников): тип операции,
 * локализованное имя категории, даты/время из timestamp записи в системной
 * таймзоне (тот же расчёт, что у PeriodType), суммы раздельно по Приходу/
 * Расходу, валюта и комментарий.
 *
 * Суммы считаются отдельно по каждой валюте — итог «по всем валютам»
 * был бы математически неверен, поэтому после каждой валютной группы
 * пишется своя строка «Итого».
 */
@Singleton
class CsvExportRepository @Inject constructor(
    private val db: DbHolder,
    @ApplicationContext private val ctx: Context
) {
    companion object {
        private const val SEP = ';'
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

        /** Имя файла для экспорта: счёт + дата/время, запрещённые символы → «_». */
        fun suggestFileName(accountName: String, ts: Long): String {
            val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm")
                .format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))
            val safe = accountName
                .map { if (it in "\\/:*?\"<>|" || it.isWhitespace() || it.isISOControl()) '_' else it }
                .joinToString("")
                .trim('_')
                .take(60)
                .ifEmpty { "account" }
            return "$safe-$stamp.csv"
        }
    }

    /**
     * Экспорт всей истории счёта [accountId] (все валюты) в документ [uri].
     * Возвращает число выгруженных записей. Тяжёлые шаги — на IO.
     */
    suspend fun exportCsv(uri: Uri, accountId: Int, labels: CsvLabels): Int =
        withContext(Dispatchers.IO) {
            val txs = db.dao().forAccount(accountId)
            // id категории → имя из справочника (для перевода в UI-имя)
            val names = HashMap<Int, String>()
            db.categoryDao().listAll().forEach { names[it.id] = it.name }

            ctx.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                writeCsv(os, txs, names, labels)
            } ?: throw java.io.IOException("Cannot open output stream")

            txs.size
        }

    private fun writeCsv(
        os: OutputStream,
        txs: List<TransactionEntity>,
        names: Map<Int, String>,
        lb: CsvLabels
    ) {
        val sb = StringBuilder(txs.size * 96 + 512)
        // BOM — единственный способ заставить Excel RU понять UTF-8 без мастера
        sb.append('\uFEFF')
        row(sb, lb.headers)

        // Группировка по валюте: коды в алфавитном порядке, внутри группы —
        // хронология (forAccount уже отсортирован по timestamp, grouping
        // сохраняет порядок появления).
        val zone = ZoneId.systemDefault()
        val groups = HashMap<String, MutableList<TransactionEntity>>()
        for (t in txs) groups.getOrPut(t.currencyCode) { ArrayList() }.add(t)

        for ((cur, list) in groups.entries.sortedBy { it.key }) {
            var inc = 0.0
            var exp = 0.0
            for (t in list) {
                val dt = Instant.ofEpochMilli(t.timestamp).atZone(zone)
                val income = if (t.isIncome) t.amount else 0.0
                val expense = if (t.isIncome) 0.0 else t.amount
                inc += income
                exp += expense
                row(
                    sb,
                    listOf(
                        DATE_FMT.format(dt),
                        TIME_FMT.format(dt),
                        lb.typeOf(t.isIncome),
                        lb.catName(names[t.categoryId] ?: t.categoryId.toString()),
                        num(income),
                        num(expense),
                        t.currencyCode,
                        t.note
                    )
                )
            }
            // Итог по одной валюте: сумма приходов/расходов корректна только
            // в пределах кода валюты (складывать RUB и USD нельзя).
            row(
                sb,
                listOf("", "", lb.total, "", num(inc), num(exp), cur, "")
            )
        }
        os.write(sb.toString().toByteArray(Charsets.UTF_8))
        os.flush()
    }

    /** Один ряд: экранирование + CRLF (Windows/Excel ожидает именно CRLF). */
    private fun row(sb: StringBuilder, cells: List<String>) {
        for (i in cells.indices) {
            if (i > 0) sb.append(SEP)
            appendCell(sb, cells[i])
        }
        sb.append("\r\n")
    }

    /** RFC-подобное экранирование: кавычки при `;`, `"`, переводе строки. */
    private fun appendCell(sb: StringBuilder, raw: String) {
        val needQuote = raw.indexOf(SEP) >= 0 || raw.indexOf('"') >= 0 ||
            raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0 ||
            (raw.isNotEmpty() && (raw.first() == ' ' || raw.last() == ' '))
        if (!needQuote) {
            sb.append(raw)
            return
        }
        sb.append('"')
        for (ch in raw) {
            if (ch == '"') sb.append('"')
            sb.append(if (ch == '\r' || ch == '\n') ' ' else ch)
        }
        sb.append('"')
    }

    /** Сумма: две цифры с десятичной запятой, без группового разделителя. */
    private fun num(v: Double): String =
        if (v == 0.0) ""
        else String.format(Locale.US, "%.2f", v).replace('.', ',')
}