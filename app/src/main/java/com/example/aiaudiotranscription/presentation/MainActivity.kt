package com.example.aiaudiotranscription

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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.example.aiaudiotranscription.api.ChatRequest
import com.example.aiaudiotranscription.api.Message
import com.example.aiaudiotranscription.api.RetrofitClient
import com.example.aiaudiotranscription.api.OpenAiApiService
import com.example.aiaudiotranscription.api.WhisperResponse
import com.example.aiaudiotranscription.presentation.SettingsActivity
import com.example.aiaudiotranscription.sharedPrefsUtils.SharedPrefsUtils
import com.example.aiaudiotranscription.ui.theme.AIAudioTranscriptionTheme
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import com.example.aiaudiotranscription.data.TranscriptionDbHelper
import com.example.aiaudiotranscription.data.TranscriptionEntry
import com.example.aiaudiotranscription.presentation.HistoryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.aiaudiotranscription.api.MODEL_WHISPER
import com.example.aiaudiotranscription.utils.FileProcessingException
import com.example.aiaudiotranscription.utils.FileProcessingManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var fileProcessingManager: FileProcessingManager
    
    private val filePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { handleFileUri(it) }
        }

    private val transcriptionState = mutableStateOf("")
    private val isBusy = mutableStateOf(false)
    private val languageState = mutableStateOf("")
    private val promptState = mutableStateOf("")

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load saved language and prompt
        languageState.value = SharedPrefsUtils.getLanguage(this) ?: ""
        promptState.value = SharedPrefsUtils.getPrompt(this) ?: "voice message of one person"

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
                        transcription = transcriptionState.value,
                        isBusy = isBusy.value,
                        modifier = Modifier.padding(innerPadding),
                        onCleanupRequest = { text -> cleanupWithAI(text) },
                        onBusyChanged = { busy -> isBusy.value = busy },
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
        // Check if API key is set
        if (SharedPrefsUtils.getApiKey(this).isNullOrEmpty()) {
            Toast.makeText(this, "Please set your OpenAI API key in Settings first", Toast.LENGTH_LONG).show()
            return
        }

        // Show busy state during processing
        isBusy.value = true
        transcriptionState.value = "Transcription in progress..."

        lifecycleScope.launch {
            try {
                val processedFile = fileProcessingManager.processAudioFile(uri)
                transcribeAudio(this@MainActivity, processedFile.absolutePath) { transcription ->
                    transcriptionState.value = transcription
                    isBusy.value = false
                    processedFile.delete() // Clean up processed file after use
                }
            } catch (e: FileProcessingException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                    isBusy.value = false
                }
            }
        }
    }

    private fun transcribeAudio(context: Context, filePath: String, onComplete: (String) -> Unit) {
        // Get current state values directly instead of using instance variables
        val currentLanguage = languageState.value
        val currentPrompt = promptState.value

        val retrofit = RetrofitClient.create(context)
        val openAiApiService = retrofit.create(OpenAiApiService::class.java)

        val file = File(filePath)
        val requestFile = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())

        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, requestFile)
            .addFormDataPart("model", MODEL_WHISPER)

        if (currentLanguage.length in 2..3) {
            requestBodyBuilder.addFormDataPart("language", currentLanguage)
        }

        if (currentPrompt.isNotEmpty()) {
            requestBodyBuilder.addFormDataPart("prompt", currentPrompt)
        }

        val requestBody = requestBodyBuilder.build() // Build the request body

        openAiApiService.transcribeAudio(requestBody)
            .enqueue(object : Callback<WhisperResponse> {
                override fun onResponse(
                    call: Call<WhisperResponse>,
                    response: Response<WhisperResponse>
                ) {
                    if (response.isSuccessful) {
                        val transcriptionText = response.body()?.text ?: "No transcription found."
                        // Save to history
                        val dbHelper = TranscriptionDbHelper(context)
                        dbHelper.addTranscription(
                            TranscriptionEntry(
                                text = transcriptionText,
                                language = languageState.value,
                                prompt = promptState.value,
                                sourceHint = filePath
                            )
                        )
                        onComplete(transcriptionText)
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        onComplete("Error: $errorBody")
                    }
                }

                override fun onFailure(call: Call<WhisperResponse>, t: Throwable) {
                    onComplete("Error: ${t.message}")
                }
            })
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
                Text(
                    "AI Transcription",
                )
                Text(
                    "Transcribe voice message or other media files to text with the help of OpenAI's Whisper API",
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
    transcription: String,
    isBusy: Boolean,
    modifier: Modifier = Modifier,
    onCleanupRequest: suspend (String) -> String,
    onBusyChanged: (Boolean) -> Unit,
    onTranscriptionUpdate: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    previewExpanded: Boolean = false // New parameter for preview
) {
    var isApiKeySet by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(previewExpanded) } // Use preview value as initial state
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
                                onBusyChanged(true)
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
                                                    sourceHint = "AI Cleanup"
                                                )
                                            )
                                        } else {
                                            Toast.makeText(context, cleanedText, Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        onBusyChanged(false)
                                    }
                                }
                            }
                        },
                        enabled = !isBusy && transcription.isNotEmpty(),
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

        // Parameters Grid in Expandable Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = { isExpanded = !isExpanded }
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transcription Parameters",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }

                if (isExpanded) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = language,
                        onValueChange = {
                            onLanguageChange(it)
                            SharedPrefsUtils.saveLanguage(context, it)
                        },
                        label = { Text("Pinned Language (ISO 639 like en, de, fr, ..)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = {
                            onPromptChange(it)
                            SharedPrefsUtils.savePrompt(context, it)
                        },
                        label = { Text("Prompt for transcription") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isApiKeySet,
                            onCheckedChange = null // Read-only checkbox
                        )
                        Text("OpenAI API Key is set")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // First row - Main action button
        Button(
            onClick = onPickFile,
            enabled = !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (!isBusy) {
                Text(
                    "Select File and Transcribe",
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                Text(
                    "Transcribing...",
                    style = MaterialTheme.typography.titleMedium
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

object SharedPrefsUtils {
    fun saveApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("api_key", apiKey).apply()
    }

    fun getApiKey(context: Context): String? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("api_key", null)
    }

    fun saveLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("language", language).apply()
    }

    fun getLanguage(context: Context): String? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("language", null)
    }

    fun savePrompt(context: Context, prompt: String) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("prompt", prompt).apply()
    }

    fun getPrompt(context: Context): String? {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        return prefs.getString("prompt", null)
    }
}

@Preview(showBackground = true)
@Composable
fun MainContentPreview() {
    AIAudioTranscriptionTheme {
        MainContent(
            onPickFile = {},
            transcription = "This is a sample transcription displayed in the preview.",
            isBusy = false,
            onCleanupRequest = { "" },
            onBusyChanged = {},
            onTranscriptionUpdate = {},
            language = "en",
            onLanguageChange = {},
            prompt = "voice message of one person",
            onPromptChange = {},
            previewExpanded = true // Set expanded state for preview
        )
    }
}