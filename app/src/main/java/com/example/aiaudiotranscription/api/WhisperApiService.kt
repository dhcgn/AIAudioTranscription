package com.example.aiaudiotranscription.api

import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface WhisperApiService {
    @POST("audio/transcriptions")
    fun transcribeAudio(
        @Body requestBody: MultipartBody
    ): Call<WhisperResponse>
}

data class WhisperResponse(
    val text: String // The transcribed text from the Whisper API
)