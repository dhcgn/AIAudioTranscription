package app.hdev.io.aitranscribe

import android.app.Application
import app.hdev.io.aitranscribe.sharedPrefsUtils.SharedPrefsUtils
import app.hdev.io.aitranscribe.utils.LogManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AITranscriptionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize LogManager
        LogManager.initialize(this)
        
        // Initialize API key from BuildConfig if not already set
        // This allows testers to pre-configure the app with their API key
        if (SharedPrefsUtils.getApiKey(this).isNullOrEmpty() && 
            BuildConfig.DEFAULT_OPENAI_API_KEY.isNotEmpty()) {
            SharedPrefsUtils.saveApiKey(this, BuildConfig.DEFAULT_OPENAI_API_KEY)
        }
    }
}
