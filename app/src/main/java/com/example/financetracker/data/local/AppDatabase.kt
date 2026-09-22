package com.example.financetracker.data.local
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.financetracker.data.model.AccountEntity
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.StatEntity
import com.example.financetracker.data.model.TransactionEntity

@Database(
    entities = [TransactionEntity::class, StatEntity::class, AccountEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TransactionDao
    abstract fun statDao(): StatDao
    abstract fun accountDao(): AccountDao

    companion object {
        const val DB_NAME = "finance.db"

        /**
         * v1 -> v2: создаёт таблицу статистики и заполняет её суммами
         * из существующих транзакций, чтобы инкрементальные обновления
         * были корректными с первого же добавления/удаления.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stats` (" +
                        "`periodType` TEXT NOT NULL, " +
                        "`periodKey` TEXT NOT NULL, " +
                        "`currencyCode` TEXT NOT NULL, " +
                        "`income` REAL NOT NULL, " +
                        "`expense` REAL NOT NULL, " +
                        "PRIMARY KEY(`periodType`, `periodKey`, `currencyCode`))"
                )
                val agg = HashMap<Triple<String, String, String>, DoubleArray>()
                db.query(
                    "SELECT currencyCode, timestamp, isIncome, amount FROM transactions"
                ).use { c ->
                    val iCur = c.getColumnIndexOrThrow("currencyCode")
                    val iTs = c.getColumnIndexOrThrow("timestamp")
                    val iInc = c.getColumnIndexOrThrow("isIncome")
                    val iAmt = c.getColumnIndexOrThrow("amount")
                    while (c.moveToNext()) {
                        val cur = c.getString(iCur)
                        val ts = c.getLong(iTs)
                        val amount = c.getDouble(iAmt)
                        val isIncome = c.getInt(iInc) == 1
                        for (p in PeriodType.entries) {
                            val arr = agg.getOrPut(Triple(p.name, p.keyOf(ts), cur)) {
                                doubleArrayOf(0.0, 0.0)
                            }
                            if (isIncome) arr[0] += amount else arr[1] += amount
                        }
                    }
                }
                agg.forEach { (k, v) ->
                    db.execSQL(
                        "INSERT INTO stats (periodType, periodKey, currencyCode, income, expense) VALUES (?, ?, ?, ?, ?)",
                        arrayOf(k.first, k.second, k.third, v[0], v[1])
                    )
                }
            }
        }

        /**
         * v2 -> v3: вводит многоучётность.
         * 1. Создаёт таблицу `accounts`, вставляет дефолтный счёт.
         * 2. Добавляет `accountId` в `transactions` (default 1).
         * 3. Пересоздаёт `stats` с `accountId` в составном PK,
         *    переносит существующие данные со счётом 1.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Таблица счетов
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL)"
                )
                // Дефолтный счёт — первый цвет палитры
                db.execSQL(
                    "INSERT INTO accounts (name, color) VALUES ('Мои финансы', ${AccountEntity.PALETTE[0]})"
                )

                // 2. Добавляем accountId в transactions
                db.execSQL("ALTER TABLE transactions ADD COLUMN accountId INTEGER NOT NULL DEFAULT 1")
                // Индекс для keyset-пагинации по счёту + валюте
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_accountId_currencyCode_id` " +
                        "ON `transactions` (`accountId`, `currencyCode`, `id`)"
                )

                // 3. Пересоздаём stats с accountId в PK
                db.execSQL("DROP TABLE IF EXISTS `stats`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stats` (" +
                        "`accountId` INTEGER NOT NULL, " +
                        "`periodType` TEXT NOT NULL, " +
                        "`periodKey` TEXT NOT NULL, " +
                        "`currencyCode` TEXT NOT NULL, " +
                        "`income` REAL NOT NULL, " +
                        "`expense` REAL NOT NULL, " +
                        "PRIMARY KEY(`accountId`, `periodType`, `periodKey`, `currencyCode`))"
                )
                // Переносим данные из transactions (все со счётом 1)
                val agg = HashMap<Triple<String, String, String>, DoubleArray>()
                db.query(
                    "SELECT currencyCode, timestamp, isIncome, amount FROM transactions"
                ).use { c ->
                    val iCur = c.getColumnIndexOrThrow("currencyCode")
                    val iTs = c.getColumnIndexOrThrow("timestamp")
                    val iInc = c.getColumnIndexOrThrow("isIncome")
                    val iAmt = c.getColumnIndexOrThrow("amount")
                    while (c.moveToNext()) {
                        val cur = c.getString(iCur)
                        val ts = c.getLong(iTs)
                        val amount = c.getDouble(iAmt)
                        val isIncome = c.getInt(iInc) == 1
                        for (p in PeriodType.entries) {
                            val arr = agg.getOrPut(Triple(p.name, p.keyOf(ts), cur)) {
                                doubleArrayOf(0.0, 0.0)
                            }
                            if (isIncome) arr[0] += amount else arr[1] += amount
                        }
                    }
                }
                agg.forEach { (k, v) ->
                    db.execSQL(
                        "INSERT INTO stats (accountId, periodType, periodKey, currencyCode, income, expense) " +
                            "VALUES (?, ?, ?, ?, ?, ?)",
                        arrayOf(1, k.first, k.second, k.third, v[0], v[1])
                    )
                }
            }
        }
    }
}