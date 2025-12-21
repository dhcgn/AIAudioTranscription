package app.hdev.io.aitranscribe.api

import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.*

// Add constants
const val MODEL_WHISPER = "whisper-1"
const val MODEL_GPT_TEXT = "gpt-5-nano"
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
    val model: String = MODEL_GPT_TEXT,
    val messages: List<Message>,
)
