package com.example.aiaudiotranscription.presentation

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.aiaudiotranscription.api.MODEL_GPT
import com.example.aiaudiotranscription.api.MODEL_WHISPER
import com.example.aiaudiotranscription.api.RetrofitClient
import com.example.aiaudiotranscription.api.OpenAiApiService
import com.example.aiaudiotranscription.api.WhisperModelsResponse
import com.example.aiaudiotranscription.sharedPrefsUtils.SharedPrefsUtils
import com.example.aiaudiotranscription.ui.theme.AIAudioTranscriptionTheme
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class SettingsActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAudioTranscriptionTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    SettingsScreen(
                        modifier = Modifier.padding(padding),
                        onClose = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onClose: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var storedApiKey by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }
    var cleanupPrompt by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        storedApiKey = SharedPrefsUtils.getApiKey(context) ?: ""
        cleanupPrompt = SharedPrefsUtils.getCleanupPrompt(context)
        selectedModel = SharedPrefsUtils.getTranscriptionModel(context) ?: MODEL_WHISPER
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("OpenAI API Key") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (apiKeyInput.isNotEmpty()) {
                        SharedPrefsUtils.saveApiKey(context, apiKeyInput)
                        storedApiKey = apiKeyInput
                        Toast.makeText(context, "API Key Saved!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save Key")
            }

            Button(
                onClick = {
                    if (storedApiKey.isBlank()) {
                        Toast.makeText(context, "Please set an API key first", Toast.LENGTH_SHORT).show()
                    } else {
                        testApiKey(context, storedApiKey) { result ->
                            testResult = result
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Test Key")
            }
        }

        if (testResult.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(testResult)
        }

        if (storedApiKey.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            val preview = if (storedApiKey.length > 12) {
                "${storedApiKey.take(6)}...${storedApiKey.takeLast(6)}"
            } else {
                storedApiKey
            }
            Text("Current API Key: $preview")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Transcription Model", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = selectedModel == MODEL_WHISPER,
                    onClick = { 
                        selectedModel = MODEL_WHISPER
                        SharedPrefsUtils.saveTranscriptionModel(context, MODEL_WHISPER)
                    }
                )
                Text("Whisper-1")
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                RadioButton(
                    selected = selectedModel == MODEL_GPT,
                    onClick = { 
                        selectedModel = MODEL_GPT
                        SharedPrefsUtils.saveTranscriptionModel(context, MODEL_GPT)
                    }
                )
                Text("GPT-4 Audio")
            }
        }
        Text(
            text = when (selectedModel) {
                MODEL_WHISPER -> "Traditional audio transcription model"
                MODEL_GPT -> "New GPT-4 based model with better understanding"
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        // Cleanup prompt section with reset button underneath
        OutlinedTextField(
            value = cleanupPrompt,
            onValueChange = { 
                cleanupPrompt = it
                SharedPrefsUtils.saveCleanupPrompt(context, it)
            },
            label = { Text("AI Cleanup Prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Button(
            onClick = {
                cleanupPrompt = SharedPrefsUtils.DEFAULT_CLEANUP_PROMPT
                SharedPrefsUtils.saveCleanupPrompt(context, cleanupPrompt)
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Reset to Default")
        }


    }
}

@Preview(showBackground = true)
@Composable
fun SettingsScreenPreview() {
    AIAudioTranscriptionTheme {
        SettingsScreen(
            modifier = Modifier,
            onClose = {}
        )
    }
}

private fun testApiKey(context: Context, apiKey: String, onResult: (String) -> Unit) {
    val retrofit = RetrofitClient.create(context)
    val openAiApiService = retrofit.create(OpenAiApiService::class.java)

    openAiApiService.testApiKey()
        .enqueue(object : Callback<WhisperModelsResponse> {
            override fun onResponse(
                call: Call<WhisperModelsResponse>,
                response: Response<WhisperModelsResponse>
            ) {
                if (response.isSuccessful) {
                    val models = response.body()?.data ?: emptyList()
                    val whisperModel = models.find { it.id == MODEL_WHISPER }
                    val gptModel = models.find { it.id == MODEL_GPT }
                    when {
                        whisperModel != null && gptModel != null -> 
                            onResult("API Key is valid and has access to all required models.")
                        whisperModel == null && gptModel == null ->
                            onResult("API Key is valid but does not have access to required models ($MODEL_WHISPER and $MODEL_GPT).")
                        whisperModel == null ->
                            onResult("API Key is valid but does not have access to the model $MODEL_WHISPER.")
                        gptModel == null ->
                            onResult("API Key is valid but does not have access to the model $MODEL_GPT.")
                    }
                } else {
                    onResult("Error: ${response.code()} - ${response.errorBody()?.string() ?: "No error body"}")
                }
            }

            override fun onFailure(call: Call<WhisperModelsResponse>, t: Throwable) {
                onResult("Error: ${t.message}")
            }
        })
}

// Add to SharedPrefsUtils object:
fun SharedPrefsUtils.saveTranscriptionModel(context: Context, model: String) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString("transcription_model", model).apply()
}

fun SharedPrefsUtils.getTranscriptionModel(context: Context): String? {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return prefs.getString("transcription_model", MODEL_WHISPER)
}
