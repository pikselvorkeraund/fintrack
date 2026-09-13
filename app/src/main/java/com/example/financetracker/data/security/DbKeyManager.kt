package com.example.financetracker.data.security
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class DbKeyManager @Inject constructor(private val ctx: Context) {
    private val masterKey by lazy {
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }
    private val prefs by lazy {
        EncryptedSharedPreferences.create(ctx, "db_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }
    fun getOrCreateKey(): ByteArray {
        val k = prefs.getString("key", null)
        val iv = prefs.getString("iv", null)
        if (k != null && iv != null) return unwrap(
            android.util.Base64.decode(k, 0), android.util.Base64.decode(iv, 0))
        val newKey = ByteArray(32)
        java.security.SecureRandom().nextBytes(newKey)
        val (enc, i) = wrap(newKey)
        prefs.edit().putString("key", android.util.Base64.encodeToString(enc, 0))
            .putString("iv", android.util.Base64.encodeToString(i, 0)).apply()
        return newKey
    }
    private fun ksKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore"); ks.load(null)
        (ks.getEntry("ft_master", null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder("ft_master",
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return kg.generateKey()
    }
    private fun wrap(key: ByteArray): Pair<ByteArray, ByteArray> {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, ksKey())
        return Pair(c.doFinal(key), c.iv)
    }
    private fun unwrap(enc: ByteArray, iv: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, ksKey(), GCMParameterSpec(128, iv))
        return c.doFinal(enc)
    }
}