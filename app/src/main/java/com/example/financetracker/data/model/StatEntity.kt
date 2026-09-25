package com.example.financetracker.data.model

import androidx.room.Entity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

/**
 * Периоды, по которым ведётся инкрементальная статистика.
 * Каждая транзакция вносит вклад ровно в один ключ каждого периода,
 * поэтому сумма по всем ключам одного типа = итог по всей истории.
 * Ключи отформатированы так, что лексикографический порядок совпадает
 * с хронологическим (MIN/MAX по periodKey = границы истории).
 */
enum class PeriodType {
    /** Общая сумма по валюте за всё время (один ключ на валюту). */
    TOTAL {
        override fun keyOf(ts: Long): String = ""
    },
    DAY {
        override fun keyOf(ts: Long): String =
            dateOf(ts).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    },
    WEEK {
        override fun keyOf(ts: Long): String = dateOf(ts).let { d ->
            String.format(
                "%d-W%02d",
                d.get(WeekFields.ISO.weekBasedYear()),
                d.get(WeekFields.ISO.weekOfWeekBasedYear())
            )
        }
    },
    MONTH {
        override fun keyOf(ts: Long): String =
            dateOf(ts).format(DateTimeFormatter.ofPattern("yyyy-MM"))
    },
    YEAR {
        override fun keyOf(ts: Long): String =
            dateOf(ts).format(DateTimeFormatter.ofPattern("yyyy"))
    };

    abstract fun keyOf(ts: Long): String

    /** Первый день периода по его ключу (для сдвига и отображения). */
    fun startDateOf(key: String): LocalDate = when (this) {
        TOTAL -> LocalDate.now()
        DAY -> LocalDate.parse(key, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        WEEK -> {
            val parts = key.split("-W")
            val y = parts[0].toInt()
            val w = parts[1].toInt()
            LocalDate.of(y, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), w.toLong())
                .with(WeekFields.ISO.dayOfWeek(), 1L)
        }
        MONTH -> LocalDate.parse(key + "-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        YEAR -> LocalDate.of(key.toInt(), 1, 1)
    }

    /** Сдвиг ключа на delta периодов (для листания на экране статистики). */
    fun shift(key: String, delta: Long): String = when (this) {
        TOTAL -> key
        DAY -> startDateOf(key).plusDays(delta).let { keyOf(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
        WEEK -> startDateOf(key).plusWeeks(delta).let { keyOf(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
        MONTH -> startDateOf(key).plusMonths(delta).let { keyOf(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
        YEAR -> startDateOf(key).plusYears(delta).let { keyOf(it.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()) }
    }

    /** Ключ текущего периода (по системным дате и времени). */
    fun currentKey(): String = keyOf(System.currentTimeMillis())
}

private fun dateOf(ts: Long): LocalDate =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * Строка статистики: накопленные доходы/расходы по связке
 * (счёт, тип периода, ключ периода, валюта, категория). Обновляется дельтой
 * при каждом добавлении/удалении записи, без пересчёта всей таблицы.
 * categoryId = [StatEntity.AGGREGATE_ID] — агрегирующая строка по всем
 * категориям (для точечного чтения сумм дашбордом), >= 1 — по категории.
 */
@Entity(
    tableName = "stats",
    primaryKeys = ["accountId", "periodType", "periodKey", "currencyCode", "categoryId"]
)
data class StatEntity(
    val accountId: Int,
    val periodType: String,
    val periodKey: String,
    val currencyCode: String,
    val categoryId: Int,
    val income: Double = 0.0,
    val expense: Double = 0.0
) {
    companion object {
        /** Специальный id агрегирующей строки (реальные категории >= 1). */
        const val AGGREGATE_ID = 0
    }
}