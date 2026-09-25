package com.example.financetracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financetracker.data.model.CategoryEntity

@Dao
interface CategoryDao {
    /** Весь справочник по возрастанию id = по времени создания («в конец списка»). */
    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun listAll(): List<CategoryEntity>

    /** Поиск категории того же типа по имени (регистронезависимо) — против дублей. */
    @Query("SELECT * FROM categories WHERE isIncome = :inc AND name = :name COLLATE NOCASE LIMIT 1")
    suspend fun byName(name: String, inc: Boolean): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(c: CategoryEntity): Long

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}