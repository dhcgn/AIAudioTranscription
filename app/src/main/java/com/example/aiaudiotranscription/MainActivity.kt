package com.example.aiaudiotranscription

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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.aiaudiotranscription.api.WhisperResponse
import com.example.aiaudiotranscription.sharedPrefsUtils.SharedPrefsUtils
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer


import com.example.aiaudiotranscription.ui.theme.AIAudioTranscriptionTheme
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        val requestFile = RequestBody.create("audio/mpeg".toMediaTypeOrNull(), file)
        val filePart = MultipartBody.Part.createFormData("file", file.name, requestFile)
        val model = RequestBody.create("text/plain".toMediaTypeOrNull(), "whisper-1")

        whisperApiService.transcribeAudio(filePart, model)
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
                    "AI Transcription of speech",
                )
                Text(
                    "Transcribe audio message to text with the help of OpenAI's Whisper API",
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
                .padding(8.dp)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary))
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

        Spacer(modifier = Modifier.height(16.dp))

        // Parameters Grid
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = language,
                onValueChange = { language = it },
                label = { Text("Language") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
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
                Text("Select File and Transcribe")
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

        Button(onClick = {
            if (apiKeyInput.isNotEmpty()) {
                SharedPrefsUtils.saveApiKey(context, apiKeyInput)
                storedApiKey = apiKeyInput
                Toast.makeText(context, "API Key Saved!", Toast.LENGTH_SHORT).show()
                onClose()
            } else {
                Toast.makeText(context, "API Key cannot be empty!", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Save API Key")
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

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onClose) {
            Text("Close")
        }
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
