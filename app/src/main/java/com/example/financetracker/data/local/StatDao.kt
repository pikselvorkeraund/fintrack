package com.example.financetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financetracker.data.model.StatEntity

@Dao
interface StatDao {
    /**
     * Инкрементальное обновление: дельта (может быть отрицательной)
     * прибавляется к существующей строке периода без чтения и пересчёта.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(s: StatEntity)

    @Query(
        "UPDATE stats SET income = income + :inc, expense = expense + :exp " +
            "WHERE periodType = :pt AND periodKey = :pk AND currencyCode = :cur"
    )
    suspend fun addDelta(pt: String, pk: String, cur: String, inc: Double, exp: Double)

    @Query(
        "SELECT COALESCE(SUM(income), 0) FROM stats " +
            "WHERE currencyCode = :cur AND periodType = :pt AND periodKey = :pk"
    )
    suspend fun periodIncome(cur: String, pt: String, pk: String): Double

    @Query(
        "SELECT COALESCE(SUM(expense), 0) FROM stats " +
            "WHERE currencyCode = :cur AND periodType = :pt AND periodKey = :pk"
    )
    suspend fun periodExpense(cur: String, pt: String, pk: String): Double

    @Query("DELETE FROM stats")
    suspend fun deleteAll()
}