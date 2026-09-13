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
        val factory = SupportOpenHelperFactory(passphrase)

        return try {
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                AppDatabase.DB_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        } catch (e: Exception) {
            // Если БД не открывается - удаляем и создаём заново
            Log.e("AppModule", "DB open failed, recreating: ${e.message}")
            context.deleteDatabase(AppDatabase.DB_NAME)
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                AppDatabase.DB_NAME
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }
    }

    @Provides
    @Singleton
    fun provideTransactionDao(database: AppDatabase): TransactionDao {
        return database.dao()
    }
}
