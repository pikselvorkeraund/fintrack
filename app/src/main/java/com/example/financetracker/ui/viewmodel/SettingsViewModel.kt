package com.example.financetracker.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.financetracker.data.settings.SettingsRepository
import com.example.financetracker.ui.locale.Language
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository
) : ViewModel() {
    val lang: StateFlow<Language> = settings.lang
    fun setLanguage(l: Language) = settings.setLanguage(l)
}
