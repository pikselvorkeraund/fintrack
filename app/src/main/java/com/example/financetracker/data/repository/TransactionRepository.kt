package com.example.financetracker.data.repository

import androidx.room.withTransaction
import com.example.financetracker.data.local.DbHolder
import com.example.financetracker.data.model.AccountEntity
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.StatEntity
import com.example.financetracker.data.model.TransactionEntity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(private val db: DbHolder) {

    /** Страница из `limit` записей активного счёта и валюты (keyset-пагинация по id). */
    suspend fun page(acc: Int, cur: String, lastId: Long, limit: Int): List<TransactionEntity> =
        db.dao().getPage(acc, cur, lastId, limit)

    suspend fun countFor(acc: Int, cur: String): Int = db.dao().countFor(acc, cur)

    /**
     * Суммы берутся из инкрементальной таблицы статистики: период TOTAL —
     * одна накопленная строка на счёт + валюту, обновляемая дельтой при
     * каждом добавлении/удалении. Чтение — точечный запрос по PK, без скана.
     */
    suspend fun income(acc: Int, cur: String): Double =
        db.statDao().periodIncome(acc, cur, PeriodType.TOTAL.name, "")

    suspend fun expense(acc: Int, cur: String): Double =
        db.statDao().periodExpense(acc, cur, PeriodType.TOTAL.name, "")

    suspend fun periodIncome(acc: Int, cur: String, pt: PeriodType, key: String): Double =
        db.statDao().periodIncome(acc, cur, pt.name, key)

    suspend fun periodExpense(acc: Int, cur: String, pt: PeriodType, key: String): Double =
        db.statDao().periodExpense(acc, cur, pt.name, key)

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
     * Добавление нового счёта: вставка в `accounts` + создание нулевых строк
     * статистики для каждого периода и каждой валюты (необязательно, но
     * упрощает чтение без NULL-чекеров). Цвет выбирается из палитры.
     */
    suspend fun addAccount(name: String): Long {
        val existing = db.accountDao().listAll()
        val color = AccountEntity.nextColor(existing.map { it.color })
        val id = db.accountDao().insert(AccountEntity(name = name, color = color))
        return id
    }

    /** Переименование счёта. */
    suspend fun renameAccount(id: Int, newName: String) {
        val acc = db.accountDao().byId(id) ?: return
        db.accountDao().deleteById(id)
        db.accountDao().insert(acc.copy(id = id, name = newName))
    }

    /**
     * Каскадное удаление счёта: удаляет все транзакции и статистики
     * в одной транзакции, затем сам счёт.
     */
    suspend fun deleteAccount(id: Int) =
        db.db().withTransaction {
            db.dao().deleteByAccount(id)
            db.statDao().deleteByAccount(id)
            db.accountDao().deleteById(id)
        }

    suspend fun listAccounts(): List<AccountEntity> = db.accountDao().listAll()

    suspend fun accountCount(): Int = db.accountDao().count()

    /**
     * Гарантирует существование хотя бы одного счёта.
     * Вызывается при инициализации ViewModel — если таблица `accounts`
     * пуста (новый файл БД после recreateWith или первый запуск),
     * создаёт дефолтный счёт «Мои финансы».
     */
    suspend fun ensureDefaultAccount() {
        if (db.accountDao().count() == 0) {
            db.accountDao().insert(
                AccountEntity(id = 1, name = "Мои финансы", color = AccountEntity.PALETTE[0])
            )
        }
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
            db.statDao().insertIfAbsent(
                StatEntity(t.accountId, p.name, key, t.currencyCode, 0.0, 0.0)
            )
            db.statDao().addDelta(t.accountId, p.name, key, t.currencyCode, inc, exp)
        }
    }
}