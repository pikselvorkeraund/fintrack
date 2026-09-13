package com.example.financetracker.data.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatternLockManager @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    companion object {
        private const val PREFS_NAME = "pattern_prefs"
        private const val HASH_KEY = "hash"
        private const val ATTEMPTS_KEY = "att"
        private const val MAX_ATTEMPTS = 3
    }

    private val prefs by lazy {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val isSet: Boolean
        get() = prefs.getString(HASH_KEY, null) != null

    val attempts: Int
        get() = prefs.getInt(ATTEMPTS_KEY, 0)

    val remaining: Int
        get() = (MAX_ATTEMPTS - attempts).coerceAtLeast(0)

    fun save(pattern: List<Int>) {
        prefs.edit()
            .putString(HASH_KEY, hash(pattern))
            .putInt(ATTEMPTS_KEY, 0)
            .apply()
    }

    fun verify(pattern: List<Int>): Boolean {
        val stored = prefs.getString(HASH_KEY, null) ?: return false
        return if (stored == hash(pattern)) {
            prefs.edit().putInt(ATTEMPTS_KEY, 0).apply()
            true
        } else {
            prefs.edit().putInt(ATTEMPTS_KEY, attempts + 1).apply()
            false
        }
    }

    fun shouldWipe(): Boolean = attempts >= MAX_ATTEMPTS

    fun wipeAll() {
        // Полная очистка: удаляем паттерн, БД, зашифрованный ключ
        prefs.edit().clear().apply()
        ctx.deleteDatabase("finance.db")
        ctx.getSharedPreferences("secure_prefs", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    private fun hash(p: List<Int>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(p.joinToString("-").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
