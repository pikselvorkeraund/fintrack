package com.example.financetracker.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val color: Long
) {
    companion object {
        /**
         * Фиксированная палитра из 12 цветов.
         * При создании нового счёта выбирается первый цвет,
         * которого ещё нет среди существующих.
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
        )

        /** Берёт первый свободный цвет из палитры, или первый, если все заняты. */
        fun nextColor(existing: List<Long>): Long {
            val used = existing.toSet()
            return PALETTE.firstOrNull { it !in used } ?: PALETTE.first()
        }
    }
}