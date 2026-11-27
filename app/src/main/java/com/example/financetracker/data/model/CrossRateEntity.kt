package com.example.financetracker.data.model

import androidx.room.Entity

/**
 * Направленный кросс-курс между двумя валютами.
 *
 * [fromCode] → [toCode]: 1 единица [fromCode] = [rate] единиц [toCode].
 * Курс А→Б не равен Б→А; обе пары хранятся независимо.
 *
 * Единая таблица для всех счетов (глобальная, не привязана к accountId).
 */
@Entity(tableName = "cross_rates", primaryKeys = ["fromCode", "toCode"])
data class CrossRateEntity(
    val fromCode: String,
    val toCode: String,
    val rate: Double
)
