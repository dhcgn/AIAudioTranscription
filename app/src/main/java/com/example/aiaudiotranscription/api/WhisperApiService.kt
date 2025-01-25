package com.example.aiaudiotranscription.api

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface WhisperApiService {
    @GET("models")
    fun testApiKey(): Call<WhisperModelsResponse>

    @POST("audio/transcriptions")
    fun transcribeAudio(
        @Body requestBody: MultipartBody
    ): Call<WhisperResponse>
}

data class WhisperResponse(
    val text: String // The transcribed text from the Whisper API
)

data class WhisperModelsResponse(
    val data: List<WhisperModel>
)

data class WhisperModel(
    val id: String,
)
