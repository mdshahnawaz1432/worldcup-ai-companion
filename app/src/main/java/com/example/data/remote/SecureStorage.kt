package com.example.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureStorage {
    private const val TAG = "SecureStorage"
    private const val KEY_ALIAS = "TxLineSecureKey"
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "txline_secure_prefs"
    private const val TOKEN_KEY = "encrypted_api_token"
    private const val IV_KEY = "encryption_iv"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
            entry.secretKey
        } else {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            )
            keyGenerator.generateKey()
        }
    }

    private fun encrypt(context: Context, key: String, value: String) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            
            val encryptedBase64 = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(key, encryptedBase64)
                .putString(key + "_iv", ivBase64)
                .apply()
            Log.d(TAG, "Key $key encrypted and saved successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to encrypt $key, falling back to plain storage.", e)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(key + "_plain", value).apply()
        }
    }

    private fun decrypt(context: Context, key: String): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encryptedBase64 = prefs.getString(key, null)
        val ivBase64 = prefs.getString(key + "_iv", null)
        if (encryptedBase64 == null || ivBase64 == null) {
            val plain = prefs.getString(key + "_plain", null)
            if (plain != null) Log.d(TAG, "Plain key $key retrieved.")
            return plain
        }
        return try {
            val encryptedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            Log.d(TAG, "Key $key decrypted and retrieved successfully.")
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt $key, returning plain fallback.", e)
            prefs.getString(key + "_plain", null)
        }
    }

    fun saveCredentials(context: Context, guestJwt: String, apiToken: String) {
        encrypt(context, "guest_jwt", guestJwt)
        encrypt(context, "api_token", apiToken)
    }

    fun getGuestJwt(context: Context): String? {
        return decrypt(context, "guest_jwt")
    }

    fun getApiToken(context: Context): String? {
        return decrypt(context, "api_token")
    }

    fun clearCredentials(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        Log.d(TAG, "Credentials cleared from secure storage.")
    }

    fun saveToken(context: Context, token: String) {
        encrypt(context, "api_token", token)
    }

    fun getToken(context: Context): String? {
        return decrypt(context, "api_token")
    }

    fun clearToken(context: Context) {
        clearCredentials(context)
    }
}
