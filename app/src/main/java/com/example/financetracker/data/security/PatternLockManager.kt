package com.example.financetracker.data.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PatternLockManager @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    companion object {
        private const val PREFS_NAME = "pattern_prefs"
        private const val HASH_KEY = "hash"
        private const val SALT_KEY = "salt"
        private const val ATTEMPTS_KEY = "att"
        private const val MAX_ATTEMPTS = 3
        private const val PBKDF2_ITERATIONS = 120_000
        private const val KEY_BITS = 256
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
        val e = prefs.edit()
        if (prefs.getString(SALT_KEY, null) == null) {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            e.putString(SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
        }
        e.putString(HASH_KEY, hash(pattern))
            .putInt(ATTEMPTS_KEY, 0)
            .apply()
    }

    /**
     * Итоговый ключ шифрования БД = PBKDF2(hash(графического узора), соль).
     * Без правильного узора ключ получить невозможно — БД не откроется.
     */
    fun derivePassphrase(pattern: List<Int>): ByteArray {
        val saltB64 = prefs.getString(SALT_KEY, null) ?: run {
            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)
            val b = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs.edit().putString(SALT_KEY, b).apply()
            b
        }
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val spec = PBEKeySpec(hash(pattern).toByteArray(Charsets.UTF_8), salt, PBKDF2_ITERATIONS, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
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
        // Полная очистка: удаляем паттерн, соль и БД
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
