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
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.example.aiaudiotranscription.api.RetrofitClient
import com.example.aiaudiotranscription.api.WhisperApiService
import com.example.aiaudiotranscription.api.WhisperModelsResponse
import com.example.aiaudiotranscription.api.WhisperResponse
import com.example.aiaudiotranscription.sharedPrefsUtils.SharedPrefsUtils
import com.example.aiaudiotranscription.ui.theme.AIAudioTranscriptionTheme
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : ComponentActivity() {
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
        languageState.value = SharedPrefsUtils.getLanguage(this) ?: "en"
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
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun handleSharedIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            uri?.let { handleFileUri(it) }
        }
    }

    private fun handleFileUri(uri: Uri) {
        val inputFilePath = FileUtils.getPath(this, uri) ?: return
        val outputFilePath = "${filesDir.absolutePath}/output.ogg"

        // Show busy state during processing
        isBusy.value = true
        transcriptionState.value = "Transcription in progress..."

        // Convert file to MP3
        convertToMp3(inputFilePath, outputFilePath) { success, message ->
            if (success) {
                transcribeAudio(this, outputFilePath) { transcription ->
                    runOnUiThread {
                        transcriptionState.value = transcription
                        isBusy.value = false
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    isBusy.value = false
                }
            }
        }
    }

    private fun convertToMp3(
        inputFilePath: String,
        outputFilePath: String,
        onComplete: (Boolean, String) -> Unit
    ) {
        val outputFile = File(outputFilePath)
        if (outputFile.exists()) {
            outputFile.delete()
        }
        // ffmpeg -i audio.mp3 -vn -map_metadata -1 -ac 1 -c:a libopus -b:a 12k -application voip audio.ogg
        val command =
            "-i $inputFilePath -vn -map_metadata -1 -ac 1 -c:a libopus -b:a 12k -application voip  $outputFilePath"
        FFmpegKit.executeAsync(command) { session: FFmpegSession ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                onComplete(true, "File converted successfully: $outputFilePath")
            } else {
                onComplete(false, "Error during conversion: ${session.failStackTrace}")
            }
        }
    }

    private fun transcribeAudio(context: Context, filePath: String, onComplete: (String) -> Unit) {
        val retrofit = RetrofitClient.create(context)
        val whisperApiService = retrofit.create(WhisperApiService::class.java)

        val file = File(filePath)
        val requestFile = file.asRequestBody("audio/mpeg".toMediaTypeOrNull())

        val language = languageState.value
        val prompt = promptState.value

        val requestBodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, requestFile)
            .addFormDataPart("model", "whisper-1")

        if (language.length in 2..3) {
            requestBodyBuilder.addFormDataPart("language", language)
        }

        if (prompt.isNotEmpty()) {
            requestBodyBuilder.addFormDataPart("prompt", prompt)
        }

        val requestBody = requestBodyBuilder.build()

        whisperApiService.transcribeAudio(requestBody)
            .enqueue(object : Callback<WhisperResponse> {
                override fun onResponse(
                    call: Call<WhisperResponse>,
                    response: Response<WhisperResponse>
                ) {
                    if (response.isSuccessful) {
                        onComplete(response.body()?.text ?: "No transcription found.")
                    } else {
                        onComplete("Error: ${response.message()}")
                    }
                }

                override fun onFailure(call: Call<WhisperResponse>, t: Throwable) {
                    onComplete("Error: ${t.message}")
                }
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar() {
    TopAppBar(
        title = {
            Column {
                Text(
                    "AI Transcription for voice messages",
                )
                Text(
                    "Transcribe voice message or other media files to text with the help of OpenAI's Whisper API",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
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

@Composable
fun MainContent(
    onPickFile: () -> Unit,
    transcription: String,
    isBusy: Boolean,
    modifier: Modifier = Modifier
) {
    var language by remember { mutableStateOf("en") }
    var prompt by remember { mutableStateOf("voice message of one person") }
    var isApiKeySet by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        isApiKeySet = SharedPrefsUtils.getApiKey(context)?.isNotEmpty() == true
        language = SharedPrefsUtils.getLanguage(context) ?: "en"
        prompt = SharedPrefsUtils.getPrompt(context) ?: "voice message of one person"
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

                IconButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Transcription", transcription)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT)
                            .show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Copy",
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Copy to Clipboard")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Parameters Grid
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = language,
                onValueChange = {
                    language = it
                    SharedPrefsUtils.saveLanguage(context, it)
                },
                label = { Text("Language") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = {
                    prompt = it
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

        Spacer(modifier = Modifier.height(16.dp))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onPickFile, enabled = !isBusy) {
                if (!isBusy){
                    Text("Select File and Transcribe")
                }else{
                    Text("Transcribing...")
                }
            }
            Button(onClick = { showConfig = true }) {
                Text("Open Config")
            }
        }

        if (showConfig) {
            ConfigurationView(onClose = { showConfig = false })
        }
    }
}

@Composable
fun ConfigurationView(onClose: () -> Unit) {
    var apiKeyInput by remember { mutableStateOf("") }
    var storedApiKey by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        storedApiKey = SharedPrefsUtils.getApiKey(context) ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary)),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Configuration", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("Enter OpenAI API Key") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (apiKeyInput.isNotEmpty()) {
                        SharedPrefsUtils.saveApiKey(context, apiKeyInput)
                        storedApiKey = apiKeyInput
                        Toast.makeText(context, "API Key Saved!", Toast.LENGTH_SHORT).show()
                        onClose()
                    } else {
                        Toast.makeText(context, "API Key cannot be empty!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Save API Key")
            }

            Button(
                onClick = {
                    testApiKey(context, storedApiKey) { result ->
                        testResult = result
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Test API Key")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (storedApiKey.isNotEmpty()) {
            val preview = if (storedApiKey.length > 12) {
                "${storedApiKey.take(6)}...${storedApiKey.takeLast(6)}"
            } else {
                storedApiKey
            }
            Text("Stored API Key: $preview", style = MaterialTheme.typography.bodyMedium)
        }

        if (testResult.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(testResult, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onClose) {
            Text("Close")
        }
    }
}

private fun testApiKey(context: Context, apiKey: String, onResult: (String) -> Unit) {
    val retrofit = RetrofitClient.create(context)
    val whisperApiService = retrofit.create(WhisperApiService::class.java)

    whisperApiService.testApiKey()
        .enqueue(object : Callback<WhisperModelsResponse> {
            override fun onResponse(call: Call<WhisperModelsResponse>, response: Response<WhisperModelsResponse>) {
                if (response.isSuccessful) {
                    val models = response.body()?.data ?: emptyList()
                    val whisperModel = models.find { it.id == "whisper-1" }
                    if (whisperModel != null) {
                        onResult("API Key is valid and has access to the model.")
                    } else {
                        onResult("API Key is valid but does not have access to the model whisper-1.")
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

@Preview(showBackground = true)
@Composable
fun ConfigurationViewPreview() {
    AIAudioTranscriptionTheme {
        ConfigurationView(onClose = {})
    }
}

object FileUtils {
    fun getPath(context: Context, uri: Uri): String? {
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val tempFile = File(context.cacheDir, "temp_audio_file")
            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
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
            isBusy = false
        )
    }
}
