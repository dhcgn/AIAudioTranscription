package com.example.aiaudiotranscription.sharedPrefsUtils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SharedPrefsUtils {
    private const val PREFS_NAME = "secure_prefs"
    private const val API_KEY = "whisper_api_key"
    private const val LANGUAGE_KEY = "language"
    private const val PROMPT_KEY = "prompt"

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

    // Save the language securely
    fun saveLanguage(context: Context, language: String) {
        val sharedPrefs = getEncryptedPrefs(context)
        sharedPrefs.edit().putString(LANGUAGE_KEY, language).apply()
    }

    // Retrieve the language
    fun getLanguage(context: Context): String? {
        val sharedPrefs = getEncryptedPrefs(context)
        return sharedPrefs.getString(LANGUAGE_KEY, null)
    }

    // Save the prompt securely
    fun savePrompt(context: Context, prompt: String) {
        val sharedPrefs = getEncryptedPrefs(context)
        sharedPrefs.edit().putString(PROMPT_KEY, prompt).apply()
    }

    // Retrieve the prompt
    fun getPrompt(context: Context): String? {
        val sharedPrefs = getEncryptedPrefs(context)
        return sharedPrefs.getString(PROMPT_KEY, null)
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
