package com.example.financetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Справочник категорий (глобальный, вне счетов).
 * name — либо ключ дефолтной категории ("Food", "Salary"…, переводятся через
 * карту Strings.categories), либо введённое пользователем имя как есть.
 * isIncome разделяет списки расходов и доходов.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isIncome: Boolean
) {
    companion object {
        /**
         * Палитра для столбиков статистики: цвет категории = id % size,
         * детерминированно и без хранения в БД.
         */
        val PALETTE: List<Long> = listOf(
            0xFF1976D2L, // синий
            0xFF388E3CL, // зелёный
            0xFFD32F2FL, // красный
            0xFF7B1FA2L, // фиолетовый
            0xFFF57C00L, // оранжевый
            0xFF00ACC1L, // бирюзовый
            0xFFC62828L, // тёмно-красный
            0xFF4527A0L, // индиго
            0xFF2E7D32L, // тёмно-зелёный
            0xFFEF6C00L, // янтарный
            0xFF546E7AL, // серо-голубой
            0xFFAD1457L, // розовый
            0xFF00838FL, // тёмно-бирюзовый
            0xFF9E9D24L, // оливковый
            0xFF6D4C41L, // коричневый
            0xFF303F9FL  // тёмно-синий
        )

        /** Цвет категории по её id (для бар-чартов статистики). */
        fun colorFor(id: Int): Long = PALETTE[id.mod(PALETTE.size)]

        /** Дефолтные расходные категории (ключи переводятся в AppLocale). */
        val DEFAULT_EXPENSE = listOf("Food", "Transport", "Housing", "Fun", "Health", "Other")

        /** Дефолтные доходные категории. */
        val DEFAULT_INCOME = listOf("Salary", "Freelance", "Invest", "Gift", "Other")
    }
}