package app.hdev.io.aitranscribe.sharedPrefsUtils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.hdev.io.aitranscribe.api.MODEL_WHISPER

object SharedPrefsUtils {
    // Preference Keys
    private const val ENCRYPTED_PREFS_NAME = "secure_prefs"
    private const val PREFS_NAME = "app_prefs"
    private const val API_KEY = "whisper_api_key"
    private const val LANGUAGE_KEY = "language"
    private const val TRANSCRIPTION_MODEL_KEY = "transcription_model"
    private const val WHISPER_PROMPT_KEY = "whisper_prompt"
    private const val GPT_PROMPT_KEY = "gpt_prompt"
    private const val REFORMAT_PROMPT_KEY = "cleanup_prompt"  // Keep old key for backward compatibility
    private const val AUTO_FORMAT_KEY = "auto_format"

    // Default Values
    const val DEFAULT_WHISPER_PROMPT = "voice message of one person"
    const val DEFAULT_GPT_PROMPT =
        """Transcribe the audio exactly as spoken, capturing every word with precision.
When words are unclear, make well-educated guesses based on context and language.
If an ISO 639 language code is provided, ensure the transcription respects the specified language. If not, keep the language of the transcribe the same as of the audio.
Return only the transcription — no additional commentary, explanations, or extraneous text."""

    const val DEFAULT_REFORMAT_PROMPT = """This is a transcript of a voice message, help to enhance the readability.

- enhance the readability by adding punctuation, capitalization and paragraphs.
- be very careful not to alter information.
- Keep the original language of the transcript I give you. 
- The language of your enhanced version must also be the same as the transcript. 
- Respond ONLY with the enhanced transcript, without any other text.

Transcript:

{{message}}"""

    private fun getEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun getNormalPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // API Key (using encrypted storage)
    fun saveApiKey(context: Context, apiKey: String) {
        getEncryptedPrefs(context).edit().putString(API_KEY, apiKey).apply()
    }

    fun getApiKey(context: Context): String? {
        return getEncryptedPrefs(context).getString(API_KEY, null)
    }

    // Language
    fun saveLanguage(context: Context, language: String) {
        getNormalPrefs(context).edit().putString(LANGUAGE_KEY, language).apply()
    }

    fun getLanguage(context: Context): String {
        return getNormalPrefs(context).getString(LANGUAGE_KEY, "") ?: ""
    }

    // Transcription Model
    fun saveTranscriptionModel(context: Context, model: String) {
        getNormalPrefs(context).edit().putString(TRANSCRIPTION_MODEL_KEY, model).apply()
    }

    fun getTranscriptionModel(context: Context): String {
        return getNormalPrefs(context).getString(TRANSCRIPTION_MODEL_KEY, MODEL_WHISPER) ?: MODEL_WHISPER
    }

    // Whisper Prompt
    fun saveWhisperPrompt(context: Context, prompt: String) {
        getNormalPrefs(context).edit().putString(WHISPER_PROMPT_KEY, prompt).apply()
    }

    fun getWhisperPrompt(context: Context): String {
        return getNormalPrefs(context).getString(WHISPER_PROMPT_KEY, DEFAULT_WHISPER_PROMPT) 
            ?: DEFAULT_WHISPER_PROMPT
    }

    // GPT Prompt
    fun saveGptPrompt(context: Context, prompt: String) {
        getNormalPrefs(context).edit().putString(GPT_PROMPT_KEY, prompt).apply()
    }

    fun getGptPrompt(context: Context): String {
        return getNormalPrefs(context).getString(GPT_PROMPT_KEY, DEFAULT_GPT_PROMPT) 
            ?: DEFAULT_GPT_PROMPT
    }

    // Reformat Prompt (previously Cleanup)
    fun saveReformatPrompt(context: Context, prompt: String) {
        getNormalPrefs(context).edit().putString(REFORMAT_PROMPT_KEY, prompt).apply()
    }

    fun getReformatPrompt(context: Context): String {
        return getNormalPrefs(context).getString(REFORMAT_PROMPT_KEY, DEFAULT_REFORMAT_PROMPT) 
            ?: DEFAULT_REFORMAT_PROMPT
    }

    // Auto Format
    fun saveAutoFormat(context: Context, enabled: Boolean) {
        getNormalPrefs(context).edit().putBoolean(AUTO_FORMAT_KEY, enabled).apply()
    }

    fun getAutoFormat(context: Context): Boolean {
        return getNormalPrefs(context).getBoolean(AUTO_FORMAT_KEY, false)
    }
}
