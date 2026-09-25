package com.example.financetracker.data.model
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["accountId", "currencyCode", "id"]),
        Index(value = ["categoryId"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Int,
    val amount: Double,
    val currencyCode: String = Currency.RUB.code,
    /** Ссылка на справочник categories.id (FK без жёсткого ограничения Room). */
    val categoryId: Int,
    val note: String = "",
    val isIncome: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)