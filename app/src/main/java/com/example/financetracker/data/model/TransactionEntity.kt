package com.example.financetracker.data.model
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["accountId", "currencyCode", "id"]),
        Index(value = ["categoryId"]),
        // Экран «Периоды»: keyset-страница транзакций за выбранный период
        // (фильтр по timestamp + продолжение по паре (timestamp, id)).
        // Без этого индекса запрос периода сканировал бы всю историю счёта.
        Index(value = ["accountId", "currencyCode", "timestamp", "id"]),
        // Фильтр «Расходы/Доходы» на вкладке «Операции»: индекс покрывает
        // равенство accountId+currencyCode+isIncome и range/ORDER BY по
        // (timestamp, id) — страница читается без скана периода.
        Index(value = ["accountId", "currencyCode", "isIncome", "timestamp", "id"]),
        // Фильтр по категории на вкладке «Операции»: аналогично, но по
        // categoryId. Комбинация типа+категории обслуживается одним из этих
        // индексов + пост-фильтром (SQLite выбирает более полезный).
        Index(value = ["accountId", "currencyCode", "categoryId", "timestamp", "id"])
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