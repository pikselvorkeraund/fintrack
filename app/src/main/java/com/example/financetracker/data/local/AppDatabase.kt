package com.example.financetracker.data.local
import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.financetracker.data.model.TransactionEntity
@Database(entities = [TransactionEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TransactionDao
    companion object { const val DB_NAME = "finance.db" }
}