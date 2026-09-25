package com.example.financetracker.data.local
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.financetracker.data.model.AccountEntity
import com.example.financetracker.data.model.CategoryEntity
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.StatEntity
import com.example.financetracker.data.model.TransactionEntity

/** Полные PK-ключи строк stats для агрегации в миграции v3->v4.
 *  Обычные Array/ключи не подходят: HashMap сравнивает их по ссылке,
 *  а data class даёт структурные equals/hashCode. */
private data class AggKey(val acc: Int, val pt: String, val pk: String, val cur: String)
private data class CatKey(val acc: Int, val pt: String, val pk: String, val cur: String, val cat: Int)

@Database(
    entities = [
        TransactionEntity::class,
        StatEntity::class,
        AccountEntity::class,
        CategoryEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TransactionDao
    abstract fun statDao(): StatDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao

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
                // 1. Таблица счетов. PK обязан быть NOT NULL — Room при
                // валидации сравнивает column def строго и падает, если в БД
                // PK-колонка объявлена NULLABLE.
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `accounts` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` INTEGER NOT NULL)"
                )
                // Дефолтный счёт — первый цвет палитры
                db.execSQL(
                    "INSERT INTO accounts (name, color) VALUES ('Мои финансы', ${AccountEntity.PALETTE[0]})"
                )

                // 2. Добавляем accountId в transactions
                db.execSQL("ALTER TABLE transactions ADD COLUMN accountId INTEGER NOT NULL DEFAULT 1")
                // Room сравнивает набор индексов строго (в обе стороны): любой
                // лишний индекс из v1/v2, не объявленный в @Entity v3, роняет
                // валидацию. Не зная точных имён прежних индексов, удаляем все
                // индексы transactions и создаём ровно ожидаемый.
                // ВАЖНО: сначала собираем имена в список и ЗАКРЫВАЕМ курсор,
                // и только потом делаем DROP. Выполнение DDL при открытом
                // курсоре этого же соединения — undefined behavior SQLite
                // (кандидат на нативный SIGSEGV, который не ловится ни одним
                // catch в JVM и роняет процесс молча).
                val oldIndexes = ArrayList<String>()
                db.query("PRAGMA index_list(`transactions`)").use { c ->
                    val iName = c.getColumnIndexOrThrow("name")
                    while (c.moveToNext()) {
                        c.getString(iName)?.let { oldIndexes.add(it) }
                    }
                }
                for (idx in oldIndexes) {
                    db.execSQL("DROP INDEX IF EXISTS `$idx`")
                }
                db.execSQL(
                    "CREATE INDEX `index_transactions_accountId_currencyCode_id` " +
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

        /**
         * v3 -> v4: справочник категорий + статистика по категориям.
         * 1. Создаёт `categories` и засевает дефолтный набор
         *    (расходы первыми, затем доходы — те же ключи, что переводятся
         *    через карту Strings в UI). DDL таблицы — ровно таким, каким Room
         *    создал бы её из @Entity (без DEFAULT): иначе валидация схемы
         *    после миграции падает и fallbackToDestructiveMigration стирает
         *    данные.
         * 2. `transactions` ПЕРЕСТРАИВАЕТСЯ (create new + INSERT..SELECT +
         *    drop + rename): старая колонка `category TEXT NOT NULL` в v4
         *    сущности отсутствует, и просто `ALTER ADD categoryId` оставила
         *    бы NOT NULL-колонку без значения по умолчанию — INSERT Room'а
         *    падал бы с `NOT NULL constraint failed`. categoryId заполняется
         *    маппингом по (name, isIncome) справочника, фолбэк — «Other»
         *    того же типа (посеян всегда).
         * 3. Индексы создаются заново на переименованной таблице (ровно как
         *    объявлены в v4).
         * 4. Пересборка stats: DROP + CREATE с categoryId в составном PK,
         *    затем из уцелевших транзакций агрегируются строки
         *    categoryId = 0 (итог по валюте, читается дашбордом точечно)
         *    и categoryId > 0 (разбивка по категориям для экрана статистики).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Таблица категорий (PK NOT NULL; определения — ровно как
                // создаёт Room по @Entity: без DEFAULT, иначе валидация
                // схемы после миграции не совпадёт и destructive-fallback
                // сотрёт данные)
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `categories` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`isIncome` INTEGER NOT NULL)"
                )
                for (n in CategoryEntity.DEFAULT_EXPENSE) {
                    db.execSQL(
                        "INSERT INTO categories (name, isIncome) VALUES (?, 0)",
                        arrayOf(n)
                    )
                }
                for (n in CategoryEntity.DEFAULT_INCOME) {
                    db.execSQL(
                        "INSERT INTO categories (name, isIncome) VALUES (?, 1)",
                        arrayOf(n)
                    )
                }

                // 2. Перестроение transactions: без старой `category`
                // (её больше нет в сущности v4), с новым `categoryId`.
                // Имена колонок и их определения — ровно как Room создаёт
                // по @Entity v4.
                db.execSQL(
                    "CREATE TABLE `transactions_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`accountId` INTEGER NOT NULL, " +
                        "`amount` REAL NOT NULL, " +
                        "`currencyCode` TEXT NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`isIncome` INTEGER NOT NULL, " +
                        "`timestamp` INTEGER NOT NULL)"
                )
                // Маппинг строковых категорий в id. Имена в данных v3 — ключи
                // карты catsEn/catsRu ("Food", "Salary"…). Фолбэк — «Other»
                // того же типа; он посеян для обоих типов, NULL здесь
                // невозможен. Старые id сохраняются (keyset-пагинация цела).
                db.execSQL(
                    "INSERT INTO `transactions_new` " +
                        "(id, accountId, amount, currencyCode, categoryId, note, isIncome, timestamp) " +
                        "SELECT t.id, t.accountId, t.amount, t.currencyCode, " +
                        "IFNULL(" +
                        "(SELECT c.id FROM categories c WHERE c.name = t.category AND c.isIncome = t.isIncome LIMIT 1), " +
                        "(SELECT c.id FROM categories c WHERE c.name = 'Other' AND c.isIncome = t.isIncome LIMIT 1)), " +
                        "t.note, t.isIncome, t.timestamp FROM transactions t"
                )
                db.execSQL("DROP TABLE `transactions`")
                db.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions`")

                // 3. Индексы: пересоздаём ровно как в @Entity v4 (DDL при
                // открытом курсоре прежней таблицы уже не выполняется —
                // таблица переименована; старые индексы удалены вместе с ней).
                db.execSQL(
                    "CREATE INDEX `index_transactions_accountId_currencyCode_id` " +
                        "ON `transactions` (`accountId`, `currencyCode`, `id`)"
                )
                db.execSQL(
                    "CREATE INDEX `index_transactions_categoryId` " +
                        "ON `transactions` (`categoryId`)"
                )

                // 4. Пересоздаём stats с categoryId в составном PK
                db.execSQL("DROP TABLE IF EXISTS `stats`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `stats` (" +
                        "`accountId` INTEGER NOT NULL, " +
                        "`periodType` TEXT NOT NULL, " +
                        "`periodKey` TEXT NOT NULL, " +
                        "`currencyCode` TEXT NOT NULL, " +
                        "`categoryId` INTEGER NOT NULL, " +
                        "`income` REAL NOT NULL, " +
                        "`expense` REAL NOT NULL, " +
                        "PRIMARY KEY(`accountId`, `periodType`, `periodKey`, `currencyCode`, `categoryId`))"
                )
                // Пересборка из транзакций (данные уцелели): ключ — полный PK
                // строки, value — [income, expense]. Две карты: агрегат по
                // валюте и разбивка по категории. Ключи — data class'ы, иначе
                // getOrPut не найдёт существующую запись (Array = сравнение
                // по ссылке, каждая транзакция создала бы новую строку).
                val agg = HashMap<AggKey, DoubleArray>()
                val byCat = HashMap<CatKey, DoubleArray>()
                db.query(
                    "SELECT accountId, categoryId, currencyCode, timestamp, isIncome, amount FROM transactions"
                ).use { c ->
                    val iAcc = c.getColumnIndexOrThrow("accountId")
                    val iCat = c.getColumnIndexOrThrow("categoryId")
                    val iCur = c.getColumnIndexOrThrow("currencyCode")
                    val iTs = c.getColumnIndexOrThrow("timestamp")
                    val iInc = c.getColumnIndexOrThrow("isIncome")
                    val iAmt = c.getColumnIndexOrThrow("amount")
                    while (c.moveToNext()) {
                        val acc = c.getInt(iAcc)
                        val cat = c.getInt(iCat)
                        val cur = c.getString(iCur)
                        val ts = c.getLong(iTs)
                        val amount = c.getDouble(iAmt)
                        val isIncome = c.getInt(iInc) == 1
                        for (p in PeriodType.entries) {
                            val pk = p.keyOf(ts)
                            agg.getOrPut(AggKey(acc, p.name, pk, cur)) { doubleArrayOf(0.0, 0.0) }
                                .let { if (isIncome) it[0] += amount else it[1] += amount }
                            byCat.getOrPut(CatKey(acc, p.name, pk, cur, cat)) { doubleArrayOf(0.0, 0.0) }
                                .let { if (isIncome) it[0] += amount else it[1] += amount }
                        }
                    }
                }
                agg.forEach { (k, v) ->
                    db.execSQL(
                        "INSERT INTO stats (accountId, periodType, periodKey, currencyCode, categoryId, income, expense) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(k.acc, k.pt, k.pk, k.cur, StatEntity.AGGREGATE_ID, v[0], v[1])
                    )
                }
                byCat.forEach { (k, v) ->
                    db.execSQL(
                        "INSERT INTO stats (accountId, periodType, periodKey, currencyCode, categoryId, income, expense) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                        arrayOf(k.acc, k.pt, k.pk, k.cur, k.cat, v[0], v[1])
                    )
                }
            }
        }
    }
}