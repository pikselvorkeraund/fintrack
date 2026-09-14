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
}

private fun dateOf(ts: Long): LocalDate =
    Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

/**
 * Строка статистики: накопленные доходы/расходы по связке
 * (тип периода, ключ периода, валюта). Обновляется дельтой при
 * каждом добавлении/удалении записи, без пересчёта всей таблицы.
 */
@Entity(tableName = "stats", primaryKeys = ["periodType", "periodKey", "currencyCode"])
data class StatEntity(
    val periodType: String,
    val periodKey: String,
    val currencyCode: String,
    val income: Double = 0.0,
    val expense: Double = 0.0
)