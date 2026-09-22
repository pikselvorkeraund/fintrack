package com.example.financetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.financetracker.data.model.AccountEntity

@Dao
interface AccountDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(a: AccountEntity): Long

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun listAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun byId(id: Int): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(color), 0) FROM accounts")
    suspend fun maxColor(): Long

    @Delete
    suspend fun delete(a: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Int)
}