package com.example.financetracker.data.local

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext

import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Держит зашифрованную БД в «закрытом» состоянии до ввода графического
 * ключа. Ключ шифрования = PBKDF2(hash(узор)), поэтому без правильного
 * узора БД физически не открыть.
 */
@Singleton
class DbHolder @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    @Volatile
    private var db: AppDatabase? = null

    fun isUnlocked(): Boolean = db != null

    /**
     * Пытается открыть (или создать) БД с данным паролем.
     * Возвращает false, если пароль не подошёл (файл БД не трогается).
     */
    fun unlock(passphrase: ByteArray): Boolean {
        if (db != null) return true
        val candidate = build(passphrase)
        return try {
            // Реальный запрос через Room: неверный пароль SQLCipher
            // бросает исключение. Проверяем до публикации экземпляра.
            runBlocking { candidate.dao().count() }
            db = candidate
            true
        } catch (e: Exception) {
            try { candidate.close() } catch (_: Exception) {}
            false
        }
    }

    /**
     * Аварийное пересоздание БД (например, после обновления приложения
     * со старой схемой шифрования). Удаляет файл и создаёт заново.
     */
    fun recreateWith(passphrase: ByteArray): Boolean {
        lock()
        ctx.deleteDatabase(AppDatabase.DB_NAME)
        return unlock(passphrase)
    }

    fun lock() {
        val d = db
        db = null
        try { d?.close() } catch (_: Exception) {}
    }

    fun db(): AppDatabase =
        db ?: throw IllegalStateException("Database is locked")

    fun dao(): TransactionDao = db().dao()

    fun statDao(): StatDao = db().statDao()

    private fun build(passphrase: ByteArray): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.DB_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
}
