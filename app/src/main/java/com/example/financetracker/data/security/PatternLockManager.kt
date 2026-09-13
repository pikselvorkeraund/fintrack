package com.example.financetracker.data.security
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class PatternLockManager @Inject constructor(private val ctx: Context) {
    private val mk by lazy { MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build() }
    private val prefs by lazy {
        EncryptedSharedPreferences.create(ctx, "pat_prefs", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    val isSet: Boolean get() = prefs.getString("hash", null) != null
    val attempts: Int get() = prefs.getInt("att", 0)
    val remaining: Int get() = (3 - attempts).coerceAtLeast(0)
    fun save(pattern: List<Int>) {
        prefs.edit().putString("hash", hash(pattern)).putInt("att", 0).apply()
    }
    fun verify(pattern: List<Int>): Boolean {
        val stored = prefs.getString("hash", null) ?: return false
        return if (stored == hash(pattern)) { prefs.edit().putInt("att", 0).apply(); true }
        else { prefs.edit().putInt("att", attempts + 1).apply(); false }
    }
    fun shouldWipe(): Boolean = attempts >= 3
    private fun hash(p: List<Int>): String {
        val d = MessageDigest.getInstance("SHA-256")
        return d.digest(p.joinToString("-").toByteArray()).joinToString("") { "%02x".format(it) }
    }
}