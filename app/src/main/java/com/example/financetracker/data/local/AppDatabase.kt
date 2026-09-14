package com.example.financetracker.data.local
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.StatEntity
import com.example.financetracker.data.model.TransactionEntity

@Database(
    entities = [TransactionEntity::class, StatEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TransactionDao
    abstract fun statDao(): StatDao

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
    }
}