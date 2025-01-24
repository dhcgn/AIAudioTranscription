package com.example.aiaudiotranscription.sharedPrefsUtils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SharedPrefsUtils {
    private const val PREFS_NAME = "secure_prefs"
    private const val API_KEY = "whisper_api_key"

    // Save the API key securely
    fun saveApiKey(context: Context, apiKey: String) {
        val sharedPrefs = getEncryptedPrefs(context)
        sharedPrefs.edit().putString(API_KEY, apiKey).apply()
    }

    // Retrieve the API key
    fun getApiKey(context: Context): String? {
        val sharedPrefs = getEncryptedPrefs(context)
        return sharedPrefs.getString(API_KEY, null)
    }

    // Initialize EncryptedSharedPreferences
    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
