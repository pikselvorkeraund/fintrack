package com.example.financetracker.di
import android.content.Context
import androidx.room.Room
import com.example.financetracker.data.local.AppDatabase
import com.example.financetracker.data.local.TransactionDao
import com.example.financetracker.data.security.DbKeyManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context, km: DbKeyManager): AppDatabase {
        val factory = SupportFactory(km.getOrCreateKey())
        return Room.databaseBuilder(ctx, AppDatabase::class.java, AppDatabase.DB_NAME)
            .openHelperFactory(factory).fallbackToDestructiveMigration().build()
    }
    @Provides @Singleton
    fun dao(db: AppDatabase): TransactionDao = db.dao()
}