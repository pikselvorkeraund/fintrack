package com.example.financetracker.data.repository

import androidx.room.withTransaction
import com.example.financetracker.data.local.DbHolder
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.StatEntity
import com.example.financetracker.data.model.TransactionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(private val db: DbHolder) {

    /** Страница из `limit` записей только активной валюты (keyset-пагинация по id). */
    suspend fun page(cur: String, lastId: Long, limit: Int): List<TransactionEntity> =
        db.dao().getPage(cur, lastId, limit)

    suspend fun countFor(cur: String): Int = db.dao().countFor(cur)

    /**
     * Суммы берутся из инкрементальной таблицы статистики: период TOTAL —
     * одна накопленная строка на валюту, обновляемая дельтой при каждом
     * добавлении/удалении. Чтение — точечный запрос по PK, без скана.
     */
    suspend fun income(cur: String): Double =
        db.statDao().periodIncome(cur, PeriodType.TOTAL.name, "")
    suspend fun expense(cur: String): Double =
        db.statDao().periodExpense(cur, PeriodType.TOTAL.name, "")

    suspend fun periodIncome(cur: String, pt: PeriodType, key: String): Double =
        db.statDao().periodIncome(cur, pt.name, key)

    suspend fun periodExpense(cur: String, pt: PeriodType, key: String): Double =
        db.statDao().periodExpense(cur, pt.name, key)

    /**
     * Добавление записи и дельта-обновление статистики в одной транзакции.
     * Возвращает id новой записи.
     */
    suspend fun add(t: TransactionEntity): Long =
        db.db().withTransaction {
            val id = db.dao().insert(t)
            applyDelta(t.copy(id = id), 1.0)
            id
        }

    /** Удаление записи и обратная дельта статистики в одной транзакции. */
    suspend fun remove(t: TransactionEntity) =
        db.db().withTransaction {
            db.dao().deleteById(t.id)
            applyDelta(t, -1.0)
        }

    suspend fun wipe() = db.db().withTransaction {
        db.dao().deleteAll()
        db.statDao().deleteAll()
    }

    /**
     * Прибавляет (sign = +1) или вычитает (sign = -1) вклад транзакции
     * в каждый из периодов (день/неделя/месяц/год) её валюты.
     */
    private suspend fun applyDelta(t: TransactionEntity, sign: Double) {
        val inc = if (t.isIncome) t.amount * sign else 0.0
        val exp = if (t.isIncome) 0.0 else t.amount * sign
        for (p in PeriodType.entries) {
            val key = p.keyOf(t.timestamp)
            db.statDao().insertIfAbsent(StatEntity(p.name, key, t.currencyCode, 0.0, 0.0))
            db.statDao().addDelta(p.name, key, t.currencyCode, inc, exp)
        }
    }
}