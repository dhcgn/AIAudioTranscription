package com.example.aiaudiotranscription

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.example.aiaudiotranscription.ui.theme.AIAudioTranscriptionTheme
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import com.example.aiaudiotranscription.api.RetrofitClient
import com.example.aiaudiotranscription.api.WhisperApiService
import com.example.aiaudiotranscription.api.WhisperResponse
import com.example.aiaudiotranscription.sharedPrefsUtils.SharedPrefsUtils
import java.io.FileOutputStream
import java.io.InputStream
import java.io.File
import com.example.aiaudiotranscription.sharedPrefsUtils.SharedPrefsUtils.saveApiKey
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response


class MainActivity : ComponentActivity() {
    private val filePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // Get file path from URI and convert it
            val inputFilePath = FileUtils.getPath(this, uri) ?: return@let
            val outputFilePath = "${filesDir.absolutePath}/output.mp3"

            convertToMp3(inputFilePath, outputFilePath) { success, message ->
                runOnUiThread {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAudioTranscriptionTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(title = { Text("Audio Transcription") })
                    }
                ) { innerPadding ->
                    MainContent(
                        onPickFile = { filePicker.launch("audio/*") },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun convertToMp3(inputFilePath: String, outputFilePath: String, onComplete: (Boolean, String) -> Unit) {
        val command = "-i $inputFilePath -vn -ar 44100 -ac 2 -b:a 192k $outputFilePath"
        FFmpegKit.executeAsync(command) { session: FFmpegSession ->
            val returnCode = session.returnCode
            if (ReturnCode.isSuccess(returnCode)) {
                onComplete(true, "File converted successfully: $outputFilePath")
            } else {
                onComplete(false, "Error during conversion: ${session.failStackTrace}")
            }
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
            override fun onResponse(call: Call<WhisperResponse>, response: Response<WhisperResponse>) {
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


@Composable
fun MainContent(onPickFile: () -> Unit, modifier: Modifier = Modifier) {
    var apiKeyInput by remember { mutableStateOf("") } // Input field state
    var storedApiKey by remember { mutableStateOf("") } // Stored API key state
    val context = LocalContext.current

    // Retrieve the stored API key on app launch
    LaunchedEffect(Unit) {
        storedApiKey = SharedPrefsUtils.getApiKey(context) ?: ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TextField for user to input API Key
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { apiKeyInput = it },
            label = { Text("Enter Whisper API Key") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Save API Key Button
        Button(onClick = {
            if (apiKeyInput.isNotEmpty()) {
                SharedPrefsUtils.saveApiKey(context, apiKeyInput)
                storedApiKey = apiKeyInput // Update the stored key state
                Toast.makeText(context, "API Key Saved!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "API Key cannot be empty!", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text("Save API Key")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Display preview of the Stored API Key
        if (storedApiKey.isNotEmpty()) {
            val preview = if (storedApiKey.length > 10) {
                "${storedApiKey.take(5)}...${storedApiKey.takeLast(5)}"
            } else {
                storedApiKey // Display full key if it's shorter than 10 characters
            }
            Text(
                text = "Stored API Key: $preview",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Button to pick a file for transcription
        Button(onClick = onPickFile) {
            Text("Pick Audio File")
        }
    }
}



object FileUtils {
    fun getPath(context: Context, uri: Uri): String? {
        try {
            // Create a temporary file in the app's private directory
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val tempFile = File(context.cacheDir, "temp_audio_file")
            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            return tempFile.absolutePath // Return the temporary file's path
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
        MainContent(onPickFile = {})
    }
}
