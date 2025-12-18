package com.example.aiaudiotranscription.api

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

// Add constants
const val MODEL_WHISPER = "whisper-1"
const val MODEL_GPT = "gpt-4o-mini"
const val MODEL_GPT_AUDIO = "gpt-4o-audio-preview"
const val MODEL_GPT_4O_TRANSCRIBE = "gpt-4o-transcribe"

const val MODEL_GPT_4O_MINI_TRANSCRIBE = "gpt-4o-mini-transcribe-2025-12-15"

interface OpenAiApiService {
    @Headers("OpenAI-Beta: assistants=v1")
    @GET("models")
    fun testApiKey(): Call<WhisperModelsResponse>

    @POST("audio/transcriptions")
    fun transcribeAudio(@Body requestBody: RequestBody): Call<WhisperResponse>

    @Headers("OpenAI-Beta: assistants=v1")
    @POST("chat/completions")
    fun cleanupText(@Body request: ChatRequest): Call<ChatResponse>

    @POST("chat/completions")
    fun transcribeAudioWithGPT(@Body request: AudioChatRequest): Call<ChatResponse>

    @POST("audio/transcriptions")
    fun transcribeAudioWithGPT4O(@Body requestBody: RequestBody): Call<WhisperResponse>
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

data class AudioChatRequest(
    val model: String = MODEL_GPT_AUDIO,
    val modalities: List<String> = listOf("text"),
    val messages: List<AudioMessage>
)

data class AudioMessage(
    val role: String = "user",
    val content: List<AudioContent>
)

sealed class AudioContent {
    data class Text(
        val type: String = "text",
        val text: String
    ) : AudioContent()

    data class Audio(
        val type: String = "input_audio",
        val input_audio: AudioData
    ) : AudioContent()
}

data class AudioData(
    val data: String,
    val format: String
)
