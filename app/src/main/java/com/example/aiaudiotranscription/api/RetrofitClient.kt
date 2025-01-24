package com.example.aiaudiotranscription.api

import android.content.Context
import com.example.aiaudiotranscription.sharedPrefsUtils.SharedPrefsUtils
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://api.openai.com/v1/" // Base URL for Whisper API

    // Function to initialize Retrofit with required configurations
    fun create(context: Context): Retrofit {
        // Interceptor to add the API key from SharedPrefsUtils to every request
        val apiKeyInterceptor = Interceptor { chain ->
            val apiKey = SharedPrefsUtils.getApiKey(context)
            if (apiKey.isNullOrEmpty()) {
                throw IllegalStateException("API key is missing. Please save the API key first.")
            }
            val request: Request = chain.request()
                .newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
            chain.proceed(request)
        }

        // Logging Interceptor for debugging (optional)
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // OkHttpClient with interceptors
        val client = OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(loggingInterceptor)
            .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS) // Set read timeout to 30 seconds
            .writeTimeout(90, java.util.concurrent.TimeUnit.SECONDS) // Set write timeout to 30 seconds
            .build()

        // Build and return the Retrofit instance
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
