package com.example.financetracker.di

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.example.financetracker.data.local.AppDatabase
import com.example.financetracker.data.local.TransactionDao
import com.example.financetracker.data.security.DbKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DbKeyManager
    ): AppDatabase {
        val passphrase = keyManager.getOrCreateKey()

        // Room.databaseBuilder().build() НЕ открывает БД — реальное
        // открытие (и проверка пароля SQLCipher) происходит лениво при
        // первом запросе, вне try/catch, что приводит к падению процесса.
        // Поэтому принудительно открываем БД здесь и при ошибке
        // пересоздаём её с нуля. Закрываем именно helper (не сам
        // connection), иначе Room вернёт закрытое кэшированное соединение.
        return try {
            val db = buildDatabase(context, passphrase)
            db.openHelper.readableDatabase
            db.openHelper.close()
            db
        } catch (e: Exception) {
            Log.e("AppModule", "DB open failed, recreating: ${e.message}")
            context.deleteDatabase(AppDatabase.DB_NAME)
            // Ключ мог разойтись с содержимым БД — получаем новый
            val freshPassphrase = keyManager.getOrCreateKey()
            try {
                val db = buildDatabase(context, freshPassphrase)
                db.openHelper.readableDatabase
                db.openHelper.close()
                db
            } catch (e2: Exception) {
                Log.e("AppModule", "DB recreate failed: ${e2.message}")
                throw e2
            }
        }
    }

    private fun buildDatabase(context: Context, passphrase: ByteArray): AppDatabase {
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DB_NAME
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: AppDatabase): TransactionDao {
        return database.dao()
    }
}
