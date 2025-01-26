package com.example.aiaudiotranscription.api

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

// Add constants
const val MODEL_WHISPER = "whisper-1"
const val MODEL_GPT = "gpt-4o-mini"

interface OpenAiApiService {
    @Headers("OpenAI-Beta: assistants=v1")
    @GET("models")
    fun testApiKey(): Call<WhisperModelsResponse>

    @POST("audio/transcriptions")
    fun transcribeAudio(@Body requestBody: RequestBody): Call<WhisperResponse>

    @Headers("OpenAI-Beta: assistants=v1")
    @POST("chat/completions")
    fun cleanupText(@Body request: ChatRequest): Call<ChatResponse>
}

data class WhisperModelsResponse(
    val data: List<ModelData>
)

data class ModelData(
    val id: String,
    val created: Long
)

data class WhisperResponse(
    val text: String,
    val language: String? = null,
    val duration: Double? = null,
)
data class ChatResponse(
    val choices: List<Choice>,
)

data class Choice(
    val message: Message,
)

data class Message(
    val role: String,
    val content: String
)

data class ChatRequest(
    val model: String = MODEL_GPT,
    val messages: List<Message>,
    val temperature: Double = 0.3,
)
