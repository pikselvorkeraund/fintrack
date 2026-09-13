package com.example.financetracker.data.local
import androidx.room.*
import com.example.financetracker.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TransactionEntity>>
    @Query("SELECT SUM(CASE WHEN isIncome=1 THEN amount ELSE 0 END) FROM transactions WHERE currencyCode=:cur")
    suspend fun totalIncome(cur: String): Double?
    @Query("SELECT SUM(CASE WHEN isIncome=0 THEN amount ELSE 0 END) FROM transactions WHERE currencyCode=:cur")
    suspend fun totalExpense(cur: String): Double?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: TransactionEntity)
    @Delete
    suspend fun delete(t: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}