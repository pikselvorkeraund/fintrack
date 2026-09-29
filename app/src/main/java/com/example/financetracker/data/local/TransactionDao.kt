package com.example.financetracker.data.local
import androidx.room.*
import com.example.financetracker.data.model.TransactionEntity
@Dao
interface TransactionDao {
    /**
     * Keyset-пагинация: страница из `limit` записей только активного
     * счёта и активной валюты, строго «старее» последнего загруженного
     * id (lastId = 0 — первая страница). Запрос использует индекс
     * (accountId, currencyCode, id) и не сканирует всю таблицу.
     */
    @Query("SELECT * FROM transactions WHERE accountId = :acc AND currencyCode = :cur AND (:lastId = 0 OR id < :lastId) ORDER BY id DESC LIMIT :limit")
    suspend fun getPage(acc: Int, cur: String, lastId: Long, limit: Int): List<TransactionEntity>

    /**
     * Keyset-страница транзакций за период [from; to) активного счёта и
     * валюты, строго «старее» последней загруженной пары (lastTs, lastId)
     * (lastTs = 0 — первая страница). Порядок timestamp DESC, id DESC и
     * продолжение пагинации по паре обслуживаются индексом
     * (accountId, currencyCode, timestamp, id) — таблица не сканируется.
     *
     * Фильтры вкладки «Операции» передаются sentinel'ом -1 (нет фильтра):
     * `inc` — -1/0/1 (все / расход / доход), `cat` — -1 или categoryId.
     * При выбранном фильтре по типу используется индекс
     * (accountId, currencyCode, isIncome, timestamp, id), по категории —
     * (accountId, currencyCode, categoryId, timestamp, id); при обоих
     * SQLite выбирает более полезный из имеющихся.
     */
    @Query("SELECT * FROM transactions WHERE accountId = :acc AND currencyCode = :cur AND timestamp >= :from AND timestamp < :to AND (:inc = -1 OR isIncome = :inc) AND (:cat = -1 OR categoryId = :cat) AND (:lastTs = 0 OR timestamp < :lastTs OR (timestamp = :lastTs AND id < :lastId)) ORDER BY timestamp DESC, id DESC LIMIT :limit")
    suspend fun getPeriodPage(acc: Int, cur: String, from: Long, to: Long, inc: Int, cat: Int, lastTs: Long, lastId: Long, limit: Int): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :acc AND currencyCode = :cur")
    suspend fun countFor(acc: Int, cur: String): Int

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(t: TransactionEntity): Long

    /**
     * Обновление существующей строки по id (редактирование записи).
     * Используется TransactionRepository.replace в одной транзакции
     * с дельтами stats — сам по себе update статистики не трогает.
     */
    @Update
    suspend fun update(t: TransactionEntity)

    @Delete
    suspend fun delete(t: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE accountId = :acc")
    suspend fun deleteByAccount(acc: Int)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /** Полный дамп для экспорта/импорта. Не использовать в UI-горячем пути. */
    @Query("SELECT * FROM transactions ORDER BY id ASC")
    suspend fun all(): List<TransactionEntity>

    /** Вся история счёта в хронологическом порядке (для CSV-экспорта). */
    @Query("SELECT * FROM transactions WHERE accountId = :acc ORDER BY timestamp ASC, id ASC")
    suspend fun forAccount(acc: Int): List<TransactionEntity>
}