package com.example.aiaudiotranscription.presentation

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import com.example.aiaudiotranscription.api.RetrofitClient
import com.example.aiaudiotranscription.api.WhisperApiService
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
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        storedApiKey = SharedPrefsUtils.getApiKey(context) ?: ""
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
                    testApiKey(context, storedApiKey) { result ->
                        testResult = result
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Test Key")
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

        if (testResult.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(testResult)
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
    val whisperApiService = retrofit.create(WhisperApiService::class.java)

    whisperApiService.testApiKey()
        .enqueue(object : Callback<WhisperModelsResponse> {
            override fun onResponse(
                call: Call<WhisperModelsResponse>,
                response: Response<WhisperModelsResponse>
            ) {
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
