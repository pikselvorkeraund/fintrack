package com.example.financetracker.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.financetracker.data.local.AppDatabase
import com.example.financetracker.data.local.DbHolder
import com.example.financetracker.data.model.AccountEntity
import com.example.financetracker.data.model.CategoryEntity
import com.example.financetracker.data.model.PeriodType
import com.example.financetracker.data.model.StatEntity
import com.example.financetracker.data.model.TransactionEntity
import com.example.financetracker.data.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** Режим импорта: полная замена БД или объединение с текущими данными. */
enum class ImportMode { REPLACE, MERGE }

/** Файл не является (совместимой) резервной копией FinTrack. */
class BackupFormatException : Exception("not a fintrack backup")

/** Неверный пароль / файл повреждён (не прошёл GCM-тег). */
class BackupWrongPasswordException : Exception("wrong password or corrupted file")

/** Копия создана более новой версией схемы приложения. */
class BackupNewerVersionException : Exception("backup from newer app version")

/**
 * Экспорт/импорт всей БД в защищённый контейнер.
 *
 * Формат файла: `FINTX1` + salt(16) + IV(12) + AES-256-GCM(gzip(JSON)).
 * Ключ шифрования = PBKDF2WithHmacSHA256(пароль юзера, salt, 310 000 итераций)
 * — независим от графического узора, поэтому копию можно восстановить
 * на любом устройстве. GCM-тег одновременно проверяет целостность и
 * правильность пароля (неверный пароль → AEADBadTagException).
 *
 * Статистика в файл НЕ пишется: при импорте `stats` пересобирается дельтами
 * из транзакций (гарантия консистентности, см. AGENTS.md).
 */
@Singleton
class BackupRepository @Inject constructor(
    private val db: DbHolder,
    private val repo: TransactionRepository,
    private val settings: SettingsRepository,
    @ApplicationContext private val ctx: Context
) {
    companion object {
        private const val MAGIC = "FINTX1"
        private const val FORMAT = 1
        private const val SALT_BYTES = 16
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val ITERATIONS = 310_000
        private const val KEY_BITS = 256
        private const val TRANSFORM = "AES/GCM/NoPadding"
        private val HEADER_LEN = MAGIC.length + SALT_BYTES + IV_BYTES
    }

    /**
     * Экспорт всей БД в документ по [uri]. Возвращает число выгруженных записей.
     * Тяжёлые шаги (чтение, gzip, PBKDF2, запись) — на IO-диспетчере.
     */
    suspend fun export(uri: Uri, password: String): Int = withContext(Dispatchers.IO) {
        val accounts = db.accountDao().listAll()
        val categories = db.categoryDao().listAll()
        val transactions = db.dao().all()

        val root = JSONObject()
            .put("format", FORMAT)
            .put("dbVersion", AppDatabase.VERSION)
            .put("createdAt", System.currentTimeMillis())
            .put("accounts", JSONArray().apply {
                accounts.forEach {
                    put(JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("color", it.color))
                }
            })
            .put("categories", JSONArray().apply {
                categories.forEach {
                    put(JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("isIncome", it.isIncome))
                }
            })
            .put("transactions", JSONArray().apply {
                transactions.forEach {
                    put(JSONObject()
                        .put("id", it.id)
                        .put("accountId", it.accountId)
                        .put("amount", it.amount)
                        .put("currencyCode", it.currencyCode)
                        .put("categoryId", it.categoryId)
                        .put("note", it.note)
                        .put("isIncome", it.isIncome)
                        .put("timestamp", it.timestamp))
                }
            })

        val plain = gzip(root.toString().toByteArray(Charsets.UTF_8))
        val rnd = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { rnd.nextBytes(it) }
        val key = deriveKey(password, salt)
        try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv))
            val enc = cipher.doFinal(plain)
            val out = ByteArrayOutputStream(HEADER_LEN + enc.size)
            out.write(MAGIC.toByteArray(Charsets.US_ASCII))
            out.write(salt)
            out.write(iv)
            out.write(enc)
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(out.toByteArray()) }
                ?: throw IOException("Cannot open output stream")
        } finally {
            key.fill(0)
        }
        transactions.size
    }

    /**
     * Импорт из документа [uri] с защитой паролем.
     * REPLACE — полное замещение всех таблиц (id сохраняются);
     * MERGE — добавление недостающих счетов/категорий/транзакций с дедупом
     * по содержимому. Возвращает число импортированных транзакций.
     */
    suspend fun import(uri: Uri, password: String, mode: ImportMode): Int =
        withContext(Dispatchers.IO) {
            val raw = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Cannot open input stream")
            if (raw.size < HEADER_LEN) throw BackupFormatException()
            val magic = String(raw, 0, MAGIC.length, Charsets.US_ASCII)
            if (magic != MAGIC) throw BackupFormatException()
            val salt = raw.copyOfRange(MAGIC.length, MAGIC.length + SALT_BYTES)
            val iv = raw.copyOfRange(MAGIC.length + SALT_BYTES, HEADER_LEN)
            val enc = raw.copyOfRange(HEADER_LEN, raw.size)
            if (enc.isEmpty()) throw BackupFormatException()

            val dec = decrypt(enc, password, salt, iv)
            val parsed = try {
                parseBackup(String(gunzip(dec), Charsets.UTF_8))
            } catch (_: org.json.JSONException) {
                throw BackupFormatException()
            } catch (_: java.io.IOException) {
                // битый gzip-поток (повреждённый файл)
                throw BackupFormatException()
            }

            val imported = db.db().withTransaction {
                when (mode) {
                    ImportMode.REPLACE -> applyReplace(parsed)
                    ImportMode.MERGE -> applyMerge(parsed)
                }
            }
            // Дамп мог не содержать категорий — засеем дефолтный набор
            // (идемпотентно: метод проверяет count() == 0).
            repo.ensureDefaultCategories()
            if (mode == ImportMode.REPLACE) fixCurrentAccount()
            imported
        }

    // ---------- криптография ----------

    private fun deriveKey(password: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun decrypt(enc: ByteArray, password: String, salt: ByteArray, iv: ByteArray): ByteArray {
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(TAG_BITS, iv))
            cipher.doFinal(enc)
        } catch (_: GeneralSecurityException) {
            // AEADBadTagException и любые сбои расшифровки = неверный пароль
            // или повреждённый файл.
            throw BackupWrongPasswordException()
        } finally {
            key.fill(0)
        }
    }

    // ---------- парсинг ----------

    private class Parsed(
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val transactions: List<TransactionEntity>
    )

    private fun parseBackup(jsonStr: String): Parsed {
        val root = try { JSONObject(jsonStr) } catch (_: org.json.JSONException) {
            throw BackupFormatException()
        }
        if (!root.has("format") || root.getInt("format") != FORMAT) throw BackupFormatException()
        val dbVersion = root.optInt("dbVersion", 0)
        if (dbVersion > AppDatabase.VERSION) throw BackupNewerVersionException()

        val accounts = ArrayList<AccountEntity>()
        val accArr = root.optJSONArray("accounts") ?: throw BackupFormatException()
        for (i in 0 until accArr.length()) {
            val o = accArr.getJSONObject(i)
            accounts.add(AccountEntity(
                id = o.getInt("id"), name = o.getString("name"), color = o.getLong("color")))
        }
        if (accounts.isEmpty()) throw BackupFormatException()

        val categories = ArrayList<CategoryEntity>()
        val catArr = root.optJSONArray("categories") ?: JSONArray()
        for (i in 0 until catArr.length()) {
            val o = catArr.getJSONObject(i)
            categories.add(CategoryEntity(
                id = o.getInt("id"), name = o.getString("name"), isIncome = o.getBoolean("isIncome")))
        }

        val transactions = ArrayList<TransactionEntity>()
        val txArr = root.optJSONArray("transactions") ?: JSONArray()
        for (i in 0 until txArr.length()) {
            val o = txArr.getJSONObject(i)
            transactions.add(TransactionEntity(
                id = o.getLong("id"),
                accountId = o.getInt("accountId"),
                amount = o.getDouble("amount"),
                currencyCode = o.getString("currencyCode"),
                categoryId = o.getInt("categoryId"),
                note = o.getString("note"),
                isIncome = o.getBoolean("isIncome"),
                timestamp = o.getLong("timestamp")))
        }
        return Parsed(accounts, categories, transactions)
    }

    // ---------- применение к БД ----------

    /** Полная замена: clear всех таблиц, вставка с исходными id, пересборка stats. */
    private suspend fun applyReplace(p: Parsed): Int {
        db.dao().deleteAll()
        db.statDao().deleteAll()
        db.accountDao().deleteAll()
        db.categoryDao().deleteAll()
        p.accounts.forEach { db.accountDao().insert(it) }
        p.categories.forEach { db.categoryDao().insert(it) }
        p.transactions.forEach { t ->
            // id в сущности ненулевой → Room вставляет с ним (keyset-пагинация цела)
            db.dao().insert(t)
            addStats(t)
        }
        return p.transactions.size
    }

    /**
     * Объединение: счета матчатся по имени (NOCASE), категории — по
     * (имя, тип); транзакции дедуплицируются по полному содержимому
     * (с уже смапленными id). Новые записи получают локальные id, их вклад
     * добавляется в stats дельтами.
     */
    private suspend fun applyMerge(p: Parsed): Int {
        // 1. Счета
        val existingAccounts = db.accountDao().listAll()
        val usedColors = existingAccounts.mapTo(ArrayList()) { it.color }
        val accMap = HashMap<Int, Int>()
        for (b in p.accounts) {
            val match = existingAccounts.firstOrNull { it.name.equals(b.name, true) }
            if (match != null) {
                accMap[b.id] = match.id
            } else {
                val color = AccountEntity.nextColor(usedColors)
                usedColors.add(color)
                val id = db.accountDao().insert(
                    AccountEntity(name = b.name, color = color)).toInt()
                accMap[b.id] = id
            }
        }
        // 2. Категории
        val catMap = HashMap<Int, Int>()
        for (b in p.categories) {
            val match = db.categoryDao().byName(b.name, b.isIncome)
            if (match != null) {
                catMap[b.id] = match.id
            } else {
                val id = db.categoryDao().insert(
                    CategoryEntity(name = b.name, isIncome = b.isIncome)).toInt()
                catMap[b.id] = id
            }
        }
        // 3. Транзакции с дедупом по содержимому
        val sigs = HashSet<String>()
        db.dao().all().forEach { sigs.add(sig(it)) }
        var imported = 0
        for (b in p.transactions) {
            val acc = accMap[b.accountId] ?: continue
            val cat = catMap[b.categoryId] ?: continue
            val e = b.copy(accountId = acc, categoryId = cat, id = 0)
            if (!sigs.add(sig(e))) continue
            val id = db.dao().insert(e)
            addStats(e.copy(id = id))
            imported++
        }
        return imported
    }

    /** Сигнатура транзакции для дедупа (локальные id не участвуют). */
    private fun sig(t: TransactionEntity): String =
        "${t.accountId}|${t.currencyCode}|${t.amount}|${t.categoryId}|${t.isIncome}|${t.timestamp}|${t.note}"

    /**
     * Вклад транзакции в статистику (sign = +1): в обе строки каждого
     * периода — агрегирующую и категорию. Полный скан не выполняется,
     * но при импорте это единственный корректный способ собрать stats.
     */
    private suspend fun addStats(t: TransactionEntity) {
        val inc = if (t.isIncome) t.amount else 0.0
        val exp = if (t.isIncome) 0.0 else t.amount
        for (p in PeriodType.entries) {
            val key = p.keyOf(t.timestamp)
            db.statDao().insertIfAbsent(
                StatEntity(t.accountId, p.name, key, t.currencyCode, StatEntity.AGGREGATE_ID, 0.0, 0.0))
            db.statDao().addDelta(t.accountId, p.name, key, t.currencyCode, StatEntity.AGGREGATE_ID, inc, exp)
            db.statDao().insertIfAbsent(
                StatEntity(t.accountId, p.name, key, t.currencyCode, t.categoryId, 0.0, 0.0))
            db.statDao().addDelta(t.accountId, p.name, key, t.currencyCode, t.categoryId, inc, exp)
        }
    }

    /** После REPLACE сохранённый currentAccountId может ссылаться на несуществующий счёт. */
    private suspend fun fixCurrentAccount() {
        val accounts = db.accountDao().listAll()
        val cur = settings.currentAccountId()
        if (accounts.isNotEmpty() && accounts.none { it.id == cur }) {
            settings.setCurrentAccount(accounts.minByOrNull { it.id }!!.id)
        }
    }

    // ---------- утилиты ----------

    private fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size / 2 + 64)
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    private fun gunzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size * 2 + 64)
        GZIPInputStream(data.inputStream()).use { gz ->
            val buf = ByteArray(8192)
            while (true) {
                val n = gz.read(buf)
                if (n < 0) break
                bos.write(buf, 0, n)
            }
        }
        return bos.toByteArray()
    }
}