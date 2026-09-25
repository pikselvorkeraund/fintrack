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
            "WHERE accountId = :acc AND periodType = :pt AND periodKey = :pk " +
            "AND currencyCode = :cur AND categoryId = :cat"
    )
    suspend fun addDelta(acc: Int, pt: String, pk: String, cur: String, cat: Int, inc: Double, exp: Double)

    @Query(
        "SELECT COALESCE(SUM(income), 0) FROM stats " +
            "WHERE accountId = :acc AND currencyCode = :cur AND periodType = :pt AND periodKey = :pk AND categoryId = :cat"
    )
    suspend fun periodIncome(acc: Int, cur: String, pt: String, pk: String, cat: Int): Double

    @Query(
        "SELECT COALESCE(SUM(expense), 0) FROM stats " +
            "WHERE accountId = :acc AND currencyCode = :cur AND periodType = :pt AND periodKey = :pk AND categoryId = :cat"
    )
    suspend fun periodExpense(acc: Int, cur: String, pt: String, pk: String, cat: Int): Double

    /** Границы истории по агрегирующим строкам (categoryId = 0): MIN/MAX periodKey. */
    @Query(
        "SELECT MIN(periodKey) FROM stats " +
            "WHERE accountId = :acc AND currencyCode = :cur AND periodType = :pt AND categoryId = 0 " +
            "AND (income <> 0 OR expense <> 0)"
    )
    suspend fun minPeriodKey(acc: Int, cur: String, pt: String): String?

    @Query(
        "SELECT MAX(periodKey) FROM stats " +
            "WHERE accountId = :acc AND currencyCode = :cur AND periodType = :pt AND categoryId = 0 " +
            "AND (income <> 0 OR expense <> 0)"
    )
    suspend fun maxPeriodKey(acc: Int, cur: String, pt: String): String?

    /** Разбивка по категориям за конкретный период (для бар-чартов). */
    @Query(
        "SELECT categoryId, income, expense FROM stats " +
            "WHERE accountId = :acc AND currencyCode = :cur AND periodType = :pt AND periodKey = :pk " +
            "AND categoryId <> 0 AND (income <> 0 OR expense <> 0)"
    )
    suspend fun periodByCategory(acc: Int, cur: String, pt: String, pk: String): List<CategorySum>

    @Query("DELETE FROM stats WHERE accountId = :acc")
    suspend fun deleteByAccount(acc: Int)

    @Query("DELETE FROM stats")
    suspend fun deleteAll()
}

/** Плоская строка разбивки статистики по категории (для экрана статистики). */
data class CategorySum(
    val categoryId: Int,
    val income: Double,
    val expense: Double
)