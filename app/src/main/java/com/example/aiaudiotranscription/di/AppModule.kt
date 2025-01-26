package com.example.aiaudiotranscription.di

import android.content.Context
import com.example.aiaudiotranscription.api.WhisperApiService
import com.example.aiaudiotranscription.api.RetrofitClient
import com.example.aiaudiotranscription.data.TranscriptionDbHelper
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
    fun provideWhisperApiService(@ApplicationContext context: Context): WhisperApiService {
        return RetrofitClient.create(context).create(WhisperApiService::class.java)
    }
}
