package com.example.financetracker.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DbKeyManager @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    companion object {
        private const val PREFS_NAME = "secure_prefs"
        private const val KEY_ALIAS = "ft_master_key_v2"
        private const val DB_KEY = "db_key_enc"
        private const val DB_IV = "db_key_iv"
    }

    private val prefs: SharedPreferences by lazy {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getOrCreateKey(): ByteArray {
        val enc = prefs.getString(DB_KEY, null)
        val iv = prefs.getString(DB_IV, null)

        if (enc != null && iv != null) {
            try {
                return decrypt(
                    Base64.decode(enc, Base64.DEFAULT),
                    Base64.decode(iv, Base64.DEFAULT)
                )
            } catch (e: Exception) {
                // Keystore повреждён - очищаем и создаём новый ключ
                ctx.deleteDatabase("finance.db")
                prefs.edit().remove(DB_KEY).remove(DB_IV).apply()
            }
        }

        val newKey = ByteArray(32)
        java.security.SecureRandom().nextBytes(newKey)
        val (encrypted, ivBytes) = encrypt(newKey)
        prefs.edit()
            .putString(DB_KEY, Base64.encodeToString(encrypted, Base64.DEFAULT))
            .putString(DB_IV, Base64.encodeToString(ivBytes, Base64.DEFAULT))
            .apply()
        return newKey
    }

    private fun getMasterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val kg = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        kg.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return kg.generateKey()
    }

    private fun encrypt(data: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getMasterKey())
        return Pair(cipher.doFinal(data), cipher.iv)
    }

    private fun decrypt(data: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getMasterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }
}
