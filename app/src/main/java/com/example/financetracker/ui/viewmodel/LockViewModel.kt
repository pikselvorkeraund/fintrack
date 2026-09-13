package com.example.financetracker.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financetracker.data.repository.TransactionRepository
import com.example.financetracker.data.security.PatternLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class LockState {
    object Setup : LockState()
    object Enter : LockState()
    object Unlocked : LockState()
    data class Error(val msg: String) : LockState()
    object Wiped : LockState()
}

@HiltViewModel
class LockViewModel @Inject constructor(
    private val plm: PatternLockManager,
    private val repo: TransactionRepository,
    @ApplicationContext private val ctx: Context
) : ViewModel() {

    private val _s = MutableStateFlow<LockState>(
        if (plm.isSet) LockState.Enter else LockState.Setup
    )
    val state: StateFlow<LockState> = _s

    fun onPattern(p: List<Int>) {
        viewModelScope.launch {
            try {
                when (_s.value) {
                    is LockState.Setup -> {
                        if (p.size >= 4) {
                            plm.save(p)
                            _s.value = LockState.Unlocked
                        } else {
                            _s.value = LockState.Error("Min 4 dots")
                        }
                    }
                    is LockState.Enter, is LockState.Error -> {
                        if (plm.verify(p)) {
                            _s.value = LockState.Unlocked
                        } else if (plm.shouldWipe()) {
                            plm.wipeAll()
                            try { repo.wipe() } catch (_: Exception) {}
                            _s.value = LockState.Wiped
                        } else {
                            _s.value = LockState.Error("Wrong. Left: ${plm.remaining}")
                        }
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                _s.value = LockState.Error("Error: ${e.message?.take(50) ?: "unknown"}")
            }
        }
    }

    fun reset() {
        _s.value = LockState.Setup
    }
}
