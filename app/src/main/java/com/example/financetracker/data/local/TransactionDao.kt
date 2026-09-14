package com.example.financetracker.data.local
import androidx.room.*
import com.example.financetracker.data.model.TransactionEntity
@Dao
interface TransactionDao {
    /**
     * Keyset-пагинация: страница из `limit` записей только активной валюты,
     * строго «старее» последнего загруженного id (lastId = 0 — первая страница).
     * Запрос использует PK-индекс и не сканирует всю таблицу.
     */
    @Query("SELECT * FROM transactions WHERE currencyCode = :cur AND (:lastId = 0 OR id < :lastId) ORDER BY id DESC LIMIT :limit")
    suspend fun getPage(cur: String, lastId: Long, limit: Int): List<TransactionEntity>
    @Query("SELECT COUNT(*) FROM transactions WHERE currencyCode=:cur")
    suspend fun countFor(cur: String): Int
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: TransactionEntity): Long
    @Delete
    suspend fun delete(t: TransactionEntity)
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}