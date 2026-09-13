package com.example.financetracker.data.repository

import com.example.financetracker.data.local.DbHolder
import com.example.financetracker.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(private val db: DbHolder) {
    fun getAll(): Flow<List<TransactionEntity>> = db.dao().getAll()
    suspend fun income(cur: String) = db.dao().totalIncome(cur) ?: 0.0
    suspend fun expense(cur: String) = db.dao().totalExpense(cur) ?: 0.0
    suspend fun add(t: TransactionEntity) = db.dao().insert(t)
    suspend fun remove(t: TransactionEntity) = db.dao().deleteById(t.id)
    suspend fun wipe() = db.dao().deleteAll()
}
