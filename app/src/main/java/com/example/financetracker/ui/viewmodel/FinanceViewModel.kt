package com.example.financetracker.ui.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.model.Currency
import com.example.financetracker.data.model.TransactionEntity
import com.example.financetracker.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
data class UiState(
    val income: Double = 0.0, val expense: Double = 0.0,
    val balance: Double = 0.0, val currency: Currency = Currency.RUB,
    val items: List<TransactionEntity> = emptyList()
)
@HiltViewModel
class FinanceViewModel @Inject constructor(private val repo: TransactionRepository) : ViewModel() {
    private val _cur = MutableStateFlow(Currency.RUB)
    val currency: StateFlow<Currency> = _cur
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui
    init { viewModelScope.launch { repo.getAll().collect { refresh(it) } } }
    fun setCurrency(c: Currency) { _cur.value = c; viewModelScope.launch { repo.getAll().first().let { refresh(it) } } }
    private suspend fun refresh(list: List<TransactionEntity>) {
        val c = _cur.value
        val i = repo.income(c.code); val e = repo.expense(c.code)
        _ui.value = UiState(i, e, i - e, c, list)
    }
    fun add(amount: Double, cat: String, note: String, income: Boolean) {
        viewModelScope.launch { repo.add(TransactionEntity(amount = amount, currencyCode = _cur.value.code, category = cat, note = note, isIncome = income)) }
    }
    fun remove(t: TransactionEntity) { viewModelScope.launch { repo.remove(t) } }
}