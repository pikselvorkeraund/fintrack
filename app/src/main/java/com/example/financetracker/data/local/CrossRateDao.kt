package com.example.financetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financetracker.data.model.CrossRateEntity

@Dao
interface CrossRateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rate: CrossRateEntity)

    @Query("SELECT rate FROM cross_rates WHERE fromCode = :fromCode AND toCode = :toCode")
    suspend fun getRate(fromCode: String, toCode: String): Double?

    /** Все курсы в [toCode] (например, USD→RUB, EUR→RUB, …) для мгновенного отображения. */
    @Query("SELECT fromCode, rate FROM cross_rates WHERE toCode = :toCode")
    suspend fun ratesFor(toCode: String): List<RateRow>

    data class RateRow(
        val fromCode: String,
        val rate: Double
    )
}
