package com.example.aiaudiotranscription.api

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

interface WhisperApiService {
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
    val owned_by: String,
    val created: Long
)

data class WhisperResponse(
    val text: String,
    val task: String? = null,
    val language: String? = null,
    val duration: Double? = null,
    val segments: List<Segment>? = null,
    val words: List<Word>? = null
)

data class Segment(
    val id: Int,
    val seek: Int,
    val start: Double,
    val end: Double,
    val text: String,
    val tokens: List<Int>,
    val temperature: Double,
    val avg_logprob: Double,
    val compression_ratio: Double,
    val no_speech_prob: Double
)

data class Word(
    val word: String,
    val start: Double,
    val end: Double
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

data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

data class ChatRequest(
    val model: String = "gpt-4o-mini",
    val messages: List<Message>,
    val temperature: Double = 0.3,
)
