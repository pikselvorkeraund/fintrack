package com.example.financetracker.data.model
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["accountId", "currencyCode", "id"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Int,
    val amount: Double,
    val currencyCode: String = Currency.RUB.code,
    val category: String,
    val note: String = "",
    val isIncome: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)