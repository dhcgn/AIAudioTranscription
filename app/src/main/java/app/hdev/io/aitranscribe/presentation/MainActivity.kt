package app.hdev.io.aitranscribe

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.hdev.io.aitranscribe.api.ChatRequest
import app.hdev.io.aitranscribe.api.ChatResponse
import app.hdev.io.aitranscribe.api.Message
import app.hdev.io.aitranscribe.api.MODEL_GPT_4O_TRANSCRIBE
import app.hdev.io.aitranscribe.api.MODEL_GPT_4O_MINI_TRANSCRIBE
import app.hdev.io.aitranscribe.api.MODEL_WHISPER
import app.hdev.io.aitranscribe.api.OpenAiApiService
import app.hdev.io.aitranscribe.api.RetrofitClient
import app.hdev.io.aitranscribe.api.WhisperResponse
import app.hdev.io.aitranscribe.data.TranscriptionDbHelper
import app.hdev.io.aitranscribe.data.TranscriptionEntry
import app.hdev.io.aitranscribe.presentation.HistoryActivity
import app.hdev.io.aitranscribe.presentation.SettingsActivity
import app.hdev.io.aitranscribe.sharedPrefsUtils.SharedPrefsUtils
import app.hdev.io.aitranscribe.ui.theme.AIAudioTranscriptionTheme
import app.hdev.io.aitranscribe.utils.FileProcessingException
import app.hdev.io.aitranscribe.utils.FileProcessingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import javax.inject.Inject

private const val MAX_FILE_SIZE_BYTES = 24 * 1024 * 1024 // 24MB in bytes

sealed class ProcessingState {
    data object Idle : ProcessingState()
    data object CopyingMedia : ProcessingState()
    data object RecodingToAAC : ProcessingState()
    data object UploadingToWhisper : ProcessingState()
    data object DownloadingResponse : ProcessingState()
    data object CleaningUpWithAI : ProcessingState()
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var fileProcessingManager: FileProcessingManager
    
    // Add this property to store the last used URI
    private var lastUsedUri: Uri? = null

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handleFileUri(it) }
        }

    private val transcriptionState = mutableStateOf("")
    private val processingState = mutableStateOf<ProcessingState>(ProcessingState.Idle)
    private val languageState = mutableStateOf("")
    private val promptState = mutableStateOf("")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved language and prompt - now without defaults
        languageState.value = SharedPrefsUtils.getLanguage(this)
        promptState.value = SharedPrefsUtils.getWhisperPrompt(this)

        // Handle shared media from external apps
        intent?.let { handleSharedIntent(it) }

        setContent {
            AIAudioTranscriptionTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { AppTopBar() }
                ) { innerPadding ->
                    MainContent(
                        onPickFile = { filePicker.launch("audio/*") },
                        onRetry = { retryTranscription() },
                        transcription = transcriptionState.value,
                        processingState = processingState.value,
                        modifier = Modifier.padding(innerPadding),
                        onCleanupRequest = { text -> cleanupWithAI(text) },
                        onProcessingStateChanged = { state -> processingState.value = state },
                        onTranscriptionUpdate = { newText -> transcriptionState.value = newText },
                        language = languageState.value,
                        onLanguageChange = { lang -> languageState.value = lang },
                        prompt = promptState.value,
                        onPromptChange = { newPrompt -> promptState.value = newPrompt }
                    )
                }
            }
        }
    }

    private fun handleSharedIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("audio/") == true || intent.type?.startsWith("video/") == true) {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    uri?.let { handleFileUri(it) }
                }
            }
            Intent.ACTION_VIEW -> {
                if (intent.type?.startsWith("audio/") == true || intent.type?.startsWith("video/") == true) {
                    intent.data?.let { handleFileUri(it) }
                }
            }
        }
    }

    private fun handleFileUri(uri: Uri) {
        lastUsedUri = uri  // Store the URI when handling a file
        // Check if API key is set
        if (SharedPrefsUtils.getApiKey(this).isNullOrEmpty()) {
            Toast.makeText(this, "Please set your OpenAI API key in Settings first", Toast.LENGTH_LONG).show()
            return
        }

        processingState.value = ProcessingState.CopyingMedia
        transcriptionState.value = "Starting transcription process..."

        lifecycleScope.launch {
            try {
                processingState.value = ProcessingState.RecodingToAAC
                val processedFile = fileProcessingManager.processAudioFile(uri)
                
                // Add file size check here
                if (processedFile.length() > MAX_FILE_SIZE_BYTES) {
                    processedFile.delete() // Clean up the file
                    throw FileProcessingException("Audio file is too large. Maximum size is 24MB after processing.")
                }

                transcribeAudio(this@MainActivity, processedFile.absolutePath) { transcription ->
                    transcriptionState.value = transcription
                    
                    // Check if auto-format is enabled
                    val autoFormatEnabled = SharedPrefsUtils.getAutoFormat(this@MainActivity)
                    if (autoFormatEnabled && !transcription.startsWith("Error")) {
                        // Apply automatic cleanup
                        processingState.value = ProcessingState.CleaningUpWithAI
                        lifecycleScope.launch {
                            try {
                                val cleanedText = cleanupWithAI(transcription)
                                if (!cleanedText.startsWith("Error")) {
                                    transcriptionState.value = cleanedText
                                    // Save cleaned version to history
                                    val dbHelper = TranscriptionDbHelper(this@MainActivity)
                                    dbHelper.addTranscription(
                                        TranscriptionEntry(
                                            text = cleanedText,
                                            language = languageState.value,
                                            prompt = "Auto-formatted version",
                                            sourceHint = processedFile.absolutePath,
                                            model = SharedPrefsUtils.getTranscriptionModel(this@MainActivity, MODEL_WHISPER)
                                        )
                                    )
                                } else {
                                    runOnUiThread {
                                        Toast.makeText(this@MainActivity, "Auto-format failed: $cleanedText", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread {
                                    Toast.makeText(this@MainActivity, "Auto-format error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            } finally {
                                processingState.value = ProcessingState.Idle
                            }
                        }
                    } else {
                        processingState.value = ProcessingState.Idle
                    }
                    
                    processedFile.delete() // Clean up processed file after use
                }
            } catch (e: FileProcessingException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                    processingState.value = ProcessingState.Idle
                }
            }
        }
    }

    // Add this function to retry transcription
    private fun retryTranscription() {
        lastUsedUri?.let { handleFileUri(it) }
    }

    private fun transcribeAudio(context: Context, filePath: String, onComplete: (String) -> Unit) {
        val currentLanguage = languageState.value
        val selectedModel = SharedPrefsUtils.getTranscriptionModel(context, MODEL_WHISPER)

        val retrofit = RetrofitClient.create(context)
        val openAiApiService = retrofit.create(OpenAiApiService::class.java)

        val file = File(filePath)
        
        if (selectedModel == MODEL_WHISPER) {
            // Existing Whisper implementation
            // Updated to audio/mp4 as we always output M4A/AAC now
            val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, requestFile)
                .addFormDataPart("model", MODEL_WHISPER)
            
            if (currentLanguage.length in 2..3) {
                requestBodyBuilder.addFormDataPart("language", currentLanguage)
            }

            val whisperPrompt = SharedPrefsUtils.getWhisperPrompt(context)
            if (whisperPrompt.isNotEmpty()) {
                requestBodyBuilder.addFormDataPart("prompt", whisperPrompt)
            }

            openAiApiService.transcribeAudio(requestBodyBuilder.build())
                .enqueue(object : Callback<WhisperResponse> {
                    // ...existing callback implementation...
                    override fun onResponse(
                        call: Call<WhisperResponse>,
                        response: Response<WhisperResponse>
                    ) {
                        processingState.value = ProcessingState.DownloadingResponse
                        if (response.isSuccessful) {
                            val transcriptionText = response.body()?.text ?: "No transcription found."
                            // Save to history
                            val dbHelper = TranscriptionDbHelper(context)
                            dbHelper.addTranscription(
                                TranscriptionEntry(
                                    text = transcriptionText,
                                    language = languageState.value,
                                    prompt = promptState.value,
                                    sourceHint = filePath,
                                    model = selectedModel // Include model information
                                )
                            )
                            onComplete(transcriptionText)
                        } else {
                            val errorBody = response.errorBody()?.string() ?: "Unknown error"
                            onComplete("Error: $errorBody")
                        }
                        processingState.value = ProcessingState.Idle
                    }

                    override fun onFailure(call: Call<WhisperResponse>, t: Throwable) {
                        onComplete("Error: ${t.message}")
                        processingState.value = ProcessingState.Idle
                    }
                })
        } else if (selectedModel == MODEL_GPT_4O_TRANSCRIBE || selectedModel == MODEL_GPT_4O_MINI_TRANSCRIBE) {
            // GPT-4o Transcribe implementation
            // Updated to audio/mp4 as we always output M4A/AAC now
            val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
            val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, requestFile)
                .addFormDataPart("model", selectedModel ?: MODEL_GPT_4O_TRANSCRIBE)
            
            if (currentLanguage.length in 2..3) {
                requestBodyBuilder.addFormDataPart("language", currentLanguage)
            }

            val gpt4oPrompt = SharedPrefsUtils.getGptPrompt(context)
            if (gpt4oPrompt.isNotEmpty()) {
                requestBodyBuilder.addFormDataPart("prompt", gpt4oPrompt)
            }

            openAiApiService.transcribeAudioWithGPT4O(requestBodyBuilder.build())
                .enqueue(object : Callback<WhisperResponse> {
                    override fun onResponse(
                        call: Call<WhisperResponse>,
                        response: Response<WhisperResponse>
                    ) {
                        processingState.value = ProcessingState.DownloadingResponse
                        if (response.isSuccessful) {
                            val transcriptionText = response.body()?.text ?: "No transcription found."
                            // Save to history
                            val dbHelper = TranscriptionDbHelper(context)
                            dbHelper.addTranscription(
                                TranscriptionEntry(
                                    text = transcriptionText,
                                    language = languageState.value,
                                    prompt = promptState.value,
                                    sourceHint = filePath,
                                    model = selectedModel // Include model information
                                )
                            )
                            onComplete(transcriptionText)
                        } else {
                            val errorBody = response.errorBody()?.string() ?: "Unknown error"
                            onComplete("Error: $errorBody")
                        }
                        processingState.value = ProcessingState.Idle
                    }

                    override fun onFailure(call: Call<WhisperResponse>, t: Throwable) {
                        onComplete("Error: ${t.message}")
                        processingState.value = ProcessingState.Idle
                    }
                })
        } else {
            // Unsupported model
            onComplete("Error: Unsupported transcription model: $selectedModel")
            processingState.value = ProcessingState.Idle
        }
    }

    private suspend fun cleanupWithAI(text: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val retrofit = RetrofitClient.create(this@MainActivity)
                val apiService = retrofit.create(OpenAiApiService::class.java)
                
                val cleanupPrompt = SharedPrefsUtils.getCleanupPrompt(this@MainActivity)
                if (!cleanupPrompt.contains("{{message}}")) {
                    throw Exception("Cleanup prompt must contain {{message}} placeholder")
                }
                val prompt = cleanupPrompt.replace("{{message}}", text)

                val request = ChatRequest(
                    messages = listOf(
                        Message(
                            role = "user",
                            content = prompt
                        )
                    )
                )

                val response = apiService.cleanupText(request).execute()
                if (response.isSuccessful) {
                    response.body()?.choices?.firstOrNull()?.message?.content
                        ?: throw Exception("No content in response")
                } else {
                    val errorBody = response.errorBody()?.string()
                    throw Exception("API Error: ${response.code()} - $errorBody")
                }
            } catch (e: Exception) {
                "Error during cleanup: ${e.message ?: "Unknown error"}"
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar() {
    TopAppBar(
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "AI Universal Transcriber",
                    )
                    if (BuildConfig.DEBUG) {
                        Text(
                            " (Debug)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Text(
                    "Transcribe media files with OpenAI's Whisper and GPT-4o Transcribe models",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.padding(end = 50.dp)
                )
            }
        },
        modifier = Modifier.padding(top = 16.dp)
    )
}

@Preview(showBackground = true)
@Composable
fun AppTopBarPreview() {
    AIAudioTranscriptionTheme {
        AppTopBar()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    onPickFile: () -> Unit,
    // Add onRetry parameter
    onRetry: () -> Unit,
    transcription: String,
    processingState: ProcessingState,
    modifier: Modifier = Modifier,
    onCleanupRequest: suspend (String) -> String,
    onProcessingStateChanged: (ProcessingState) -> Unit,
    onTranscriptionUpdate: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit
) {
    var isApiKeySet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        isApiKeySet = SharedPrefsUtils.getApiKey(context)?.isNotEmpty() == true
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Transcription Text Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary))
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    SelectionContainer {
                        Text(
                            text = transcription.ifEmpty { "Transcription will appear here..." },
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp), // Reduced vertical padding
                    horizontalArrangement = Arrangement.spacedBy(4.dp) // Reduced spacing between buttons
                ) {
                    IconButton(
                        onClick = {
                            val clipboard =
                                context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Transcription", transcription)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT)
                                .show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp) // Add small vertical padding
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Copy",
                                modifier = Modifier.scale(0.7f)
                            )
                            Text(
                                text = "Copy to Clipboard",
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (transcription.isNotEmpty()) {
                                onProcessingStateChanged(ProcessingState.CleaningUpWithAI)
                                scope.launch {
                                    try {
                                        val cleanedText = onCleanupRequest(transcription)
                                        if (!cleanedText.startsWith("Error")) {
                                            onTranscriptionUpdate(cleanedText)
                                            // Save cleaned version to history
                                            val dbHelper = TranscriptionDbHelper(context)
                                            dbHelper.addTranscription(
                                                TranscriptionEntry(
                                                    text = cleanedText,
                                                    language = language,
                                                    prompt = "Cleaned version of previous transcription",
                                                    sourceHint = "AI Cleanup",
                                                    model = SharedPrefsUtils.getTranscriptionModel(context, MODEL_WHISPER) // Include model information
                                                )
                                            )
                                        } else {
                                            Toast.makeText(context, cleanedText, Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        onProcessingStateChanged(ProcessingState.Idle)
                                    }
                                }
                            }
                        },
                        enabled = processingState == ProcessingState.Idle && transcription.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp) // Add small vertical padding
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Cleanup",
                                modifier = Modifier.scale(0.7f)
                            )
                            Text(
                                text ="Cleanup with AI",
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // First row - Main action button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPickFile,
                enabled = processingState == ProcessingState.Idle,
                modifier = Modifier.weight(1f)
            ) {
                val selectedModel = SharedPrefsUtils.getTranscriptionModel(context, MODEL_WHISPER)
                val buttonText = when (processingState) {
                    ProcessingState.Idle -> "Select File and Transcribe"
                    ProcessingState.CopyingMedia -> "Copying Media File..."
                    ProcessingState.RecodingToAAC -> "Re-encode to M4A..."
                    ProcessingState.UploadingToWhisper -> "Uploading to Whisper..."
                    ProcessingState.DownloadingResponse -> "Downloading Response..."
                    ProcessingState.CleaningUpWithAI -> "Cleaning Up Text with AI..."
                    else -> {"Unknown State"}
                }
                Text(
                    buttonText,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Button(
                onClick = onRetry,
                enabled = processingState == ProcessingState.Idle,
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry transcription"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Second row - Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    context.startActivity(Intent(context, HistoryActivity::class.java))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("History")
            }
            Button(
                onClick = {
                    context.startActivity(Intent(context, SettingsActivity::class.java))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Settings")
            }
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://github.com/dhcgn/AIAudioTranscription")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(0.75f)
            ) {
                Text("Help")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    AIAudioTranscriptionTheme {
        MainContent(
            onPickFile = {},
            onRetry = {},
            transcription = "This is a sample transcription displayed in the preview.",
            processingState = ProcessingState.Idle,
            onCleanupRequest = { "" },
            onProcessingStateChanged = {},
            onTranscriptionUpdate = {},
            language = "en",
            onLanguageChange = {},
            prompt = "voice message of one person",
            onPromptChange = {}
        )
    }
}
