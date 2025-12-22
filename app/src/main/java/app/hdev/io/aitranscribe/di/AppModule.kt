package app.hdev.io.aitranscribe.di

import android.content.Context
import app.hdev.io.aitranscribe.api.OpenAiApiService
import app.hdev.io.aitranscribe.api.RetrofitClient
import app.hdev.io.aitranscribe.data.TranscriptionDbHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideTranscriptionDbHelper(@ApplicationContext context: Context): TranscriptionDbHelper {
        return TranscriptionDbHelper(context)
    }

    @Provides
    @Singleton
    fun provideWhisperApiService(@ApplicationContext context: Context): OpenAiApiService {
        return RetrofitClient.create(context).create(OpenAiApiService::class.java)
    }
}
