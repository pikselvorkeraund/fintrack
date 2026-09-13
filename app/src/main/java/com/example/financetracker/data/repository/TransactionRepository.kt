package com.example.financetracker.data.repository
import com.example.financetracker.data.local.TransactionDao
import com.example.financetracker.data.model.TransactionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class TransactionRepository @Inject constructor(private val dao: TransactionDao) {
    fun getAll(): Flow<List<TransactionEntity>> = dao.getAll()
    suspend fun income(cur: String) = dao.totalIncome(cur) ?: 0.0
    suspend fun expense(cur: String) = dao.totalExpense(cur) ?: 0.0
    suspend fun add(t: TransactionEntity) = dao.insert(t)
    suspend fun remove(t: TransactionEntity) = dao.delete(t)
    suspend fun wipe() = dao.deleteAll()
}