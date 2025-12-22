package app.hdev.io.aitranscribe.presentation

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.hdev.io.aitranscribe.api.MODEL_GPT_TEXT
import app.hdev.io.aitranscribe.api.MODEL_WHISPER
import app.hdev.io.aitranscribe.api.MODEL_GPT_4O_TRANSCRIBE
import app.hdev.io.aitranscribe.api.MODEL_GPT_4O_MINI_TRANSCRIBE
import app.hdev.io.aitranscribe.api.RetrofitClient
import app.hdev.io.aitranscribe.api.OpenAiApiService
import app.hdev.io.aitranscribe.api.WhisperModelsResponse
import app.hdev.io.aitranscribe.sharedPrefsUtils.SharedPrefsUtils
import app.hdev.io.aitranscribe.ui.theme.AIAudioTranscriptionTheme
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
    var testResult by remember { mutableStateOf<List<ModelStatus>>(emptyList()) }
    var cleanupPrompt by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }
    var whisperPrompt by remember { mutableStateOf("") }
    var gptPrompt by remember { mutableStateOf("") }
    var autoFormat by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        storedApiKey = SharedPrefsUtils.getApiKey(context) ?: ""
        cleanupPrompt = SharedPrefsUtils.getCleanupPrompt(context)
        selectedModel = SharedPrefsUtils.getTranscriptionModel(context, MODEL_GPT_4O_MINI_TRANSCRIBE)
        language = SharedPrefsUtils.getLanguage(context)
        whisperPrompt = SharedPrefsUtils.getWhisperPrompt(context)
        gptPrompt = SharedPrefsUtils.getGptPrompt(context)
        autoFormat = SharedPrefsUtils.getAutoFormat(context)
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
            Text(
                text = "API Key Test Results:",
                style = MaterialTheme.typography.titleSmall
            )
            testResult.forEach { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (status.isAvailable) 
                            Icons.Default.CheckCircle else Icons.Default.Cancel,
                        contentDescription = if (status.isAvailable) "Available" else "Not Available",
                        tint = if (status.isAvailable) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status.modelName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
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

        Text("Used Transcription Model", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Whisper-1 is the Open Source version large-v2 of whisper. " +
                    "GPT-4o Transcribe models are optimized for audio transcription tasks. " +
                    "All files are transcoded to AAC audio format.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { 
                            selectedModel = MODEL_WHISPER
                            SharedPrefsUtils.saveTranscriptionModel(context, MODEL_WHISPER)
                        }
                    )
            ) {
                RadioButton(
                    selected = selectedModel == MODEL_WHISPER,
                    onClick = { 
                        selectedModel = MODEL_WHISPER
                        SharedPrefsUtils.saveTranscriptionModel(context, MODEL_WHISPER)
                    }
                )
                Text(
                    text = "Whisper-1",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = { 
                            selectedModel = MODEL_GPT_4O_TRANSCRIBE
                            SharedPrefsUtils.saveTranscriptionModel(context, MODEL_GPT_4O_TRANSCRIBE)
                        }
                    )
            ) {
                RadioButton(
                    selected = selectedModel == MODEL_GPT_4O_TRANSCRIBE,
                    onClick = { 
                        selectedModel = MODEL_GPT_4O_TRANSCRIBE
                        SharedPrefsUtils.saveTranscriptionModel(context, MODEL_GPT_4O_TRANSCRIBE)
                    }
                )
                Text(
                    text = "GPT-4o Transcribe",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = {
                            selectedModel = MODEL_GPT_4O_MINI_TRANSCRIBE
                            SharedPrefsUtils.saveTranscriptionModel(context, MODEL_GPT_4O_MINI_TRANSCRIBE)
                        }
                    )
            ) {
                RadioButton(
                    selected = selectedModel == MODEL_GPT_4O_MINI_TRANSCRIBE,
                    onClick = {
                        selectedModel = MODEL_GPT_4O_MINI_TRANSCRIBE
                        SharedPrefsUtils.saveTranscriptionModel(context, MODEL_GPT_4O_MINI_TRANSCRIBE)
                    }
                )
                Text(
                    text = "GPT-4o-mini Transcribe 2025-12-15",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        Text(
            text = when (selectedModel) {
                MODEL_WHISPER -> "Traditional audio transcription model"
                MODEL_GPT_4O_TRANSCRIBE -> "GPT-4o Transcribe model"
                MODEL_GPT_4O_MINI_TRANSCRIBE -> "Efficient GPT-4o-mini Transcribe model"
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        // Cleanup prompt section with reset button underneath
        Text("AI Cleanup Prompt", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "This prompt is only for the action to clean up the transcript for better " +
                    "readability and with maintain in content.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
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

        Spacer(modifier = Modifier.height(24.dp))
        
        // Auto-format toggle section
        Text("Auto-format Settings", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Automatically enhance the readability of transcriptions after processing",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Auto format",
                style = MaterialTheme.typography.bodyLarge
            )
            androidx.compose.material3.Switch(
                checked = autoFormat,
                onCheckedChange = { 
                    autoFormat = it
                    SharedPrefsUtils.saveAutoFormat(context, it)
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Language Settings for transcription", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "If not set Whisper's auto-\"language identification\" will try to figure the language out." +
                    "In the most cases this works perfect, but in edge cases it is helpfull to set the language manually.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = language,
            onValueChange = { language = it },
            label = { Text("Pinned Language (ISO 639 like en, de, fr, ...)") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    SharedPrefsUtils.saveLanguage(context, language)
                    Toast.makeText(context, "Language saved", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Save")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    language = ""
                    SharedPrefsUtils.saveLanguage(context, "")
                    Toast.makeText(context, "Language reset", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Reset")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("Whisper-1 Prompt", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "You can use a prompt to improve the quality of the transcripts generated by the Whisper API. " +
                "The model tries to match the style of the prompt, so it's more likely to use capitalization and " +
                "punctuation if the prompt does too. However, the current prompting system is more limited than our " +
                "other language models and provides limited control over the generated audio. This prompt is limited to 224 token, " +
                "the model only considers the final 224 tokens of the prompt and ignores anything earlier.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = whisperPrompt,
            onValueChange = { whisperPrompt = it },
            label = { Text("Prompt for Whisper-1") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    SharedPrefsUtils.saveWhisperPrompt(context, whisperPrompt)
                    Toast.makeText(context, "Whisper prompt saved", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Save")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    whisperPrompt = SharedPrefsUtils.DEFAULT_WHISPER_PROMPT
                    SharedPrefsUtils.saveWhisperPrompt(context, SharedPrefsUtils.DEFAULT_WHISPER_PROMPT)
                    Toast.makeText(context, "Whisper prompt reset", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Reset")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        
        Text("GPT-4 Audio Prompt", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "The used GPT-4o audio model is the same as the realtime models, but not optimized to low latency. " +
                    "The used mode it \"text + audio in -> text out\". Be carefull with the prompt, this model purpose " +
                    "it not transcription, so the prompt must be very clear to transcribe the audio.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = gptPrompt,
            onValueChange = { gptPrompt = it },
            label = { Text("Prompt for GPT-4 Audio") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    SharedPrefsUtils.saveGptPrompt(context, gptPrompt)
                    Toast.makeText(context, "GPT prompt saved", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Save")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    gptPrompt = SharedPrefsUtils.DEFAULT_GPT_PROMPT
                    SharedPrefsUtils.saveGptPrompt(context, SharedPrefsUtils.DEFAULT_GPT_PROMPT)
                    Toast.makeText(context, "GPT prompt reset", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Reset")
            }
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

private data class ModelStatus(
    val modelName: String,
    val isAvailable: Boolean
)

private fun testApiKey(context: Context, apiKey: String, onResult: (List<ModelStatus>) -> Unit) {
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
                    val modelIds = models.map { it.id }
                    
                    val results = listOf(
                        ModelStatus("API Connection", true),
                        ModelStatus(
                            "Whisper Model (${MODEL_WHISPER})", 
                            modelIds.contains(MODEL_WHISPER)
                        ),
                        ModelStatus(
                            "GPT Model (${MODEL_GPT_TEXT})",
                            modelIds.contains(MODEL_GPT_TEXT)
                        ),
                        ModelStatus(
                            "GPT-4o Transcribe Model (${MODEL_GPT_4O_TRANSCRIBE})", 
                            modelIds.contains(MODEL_GPT_4O_TRANSCRIBE)
                        ),
                        ModelStatus(
                            "GPT-4o-mini Transcribe (${MODEL_GPT_4O_MINI_TRANSCRIBE})",
                            modelIds.contains(MODEL_GPT_4O_MINI_TRANSCRIBE)
                        )
                    )
                    onResult(results)
                } else {
                    onResult(listOf(ModelStatus(
                        "API Connection Error: ${response.code()}", 
                        false
                    )))
                }
            }

            override fun onFailure(call: Call<WhisperModelsResponse>, t: Throwable) {
                onResult(listOf(ModelStatus(
                    "Connection Error: ${t.message}", 
                    false
                )))
            }
        })
}
