package com.example.financetracker.data.settings

import android.content.Context
import com.example.financetracker.ui.locale.Language
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    companion object {
        private const val PREFS = "settings"
        private const val LANG_KEY = "lang"
        private const val ACCOUNT_KEY = "currentAccountId"
        const val DEFAULT_ACCOUNT_ID = 1
    }

    private val prefs by lazy { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    private val _lang = MutableStateFlow(
        runCatching { Language.valueOf(prefs.getString(LANG_KEY, null) ?: "EN") }
            .getOrDefault(Language.EN)
    )
    val lang: StateFlow<Language> = _lang

    private val _currentAccount = MutableStateFlow(
        prefs.getInt(ACCOUNT_KEY, DEFAULT_ACCOUNT_ID)
    )
    val currentAccount: StateFlow<Int> = _currentAccount

    fun setLanguage(l: Language) {
        _lang.value = l
        prefs.edit().putString(LANG_KEY, l.name).apply()
    }

    fun currentAccountId(): Int = _currentAccount.value

    fun setCurrentAccount(id: Int) {
        _currentAccount.value = id
        prefs.edit().putInt(ACCOUNT_KEY, id).apply()
    }
}