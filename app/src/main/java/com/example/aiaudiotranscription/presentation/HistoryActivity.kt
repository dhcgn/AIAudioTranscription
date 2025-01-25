package com.example.aiaudiotranscription.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import com.example.aiaudiotranscription.data.TranscriptionDbHelper
import com.example.aiaudiotranscription.data.TranscriptionEntry
import com.example.aiaudiotranscription.ui.theme.AIAudioTranscriptionTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

class HistoryActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAudioTranscriptionTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Transcription History") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                var showDeleteAllDialog by remember { mutableStateOf(false) }
                                IconButton(onClick = { showDeleteAllDialog = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete All"
                                    )
                                }

                                if (showDeleteAllDialog) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteAllDialog = false },
                                        title = { Text("Delete All Transcriptions") },
                                        text = { Text("Are you sure you want to delete all transcriptions? This cannot be undone.") },
                                        confirmButton = {
                                            Button(
                                                onClick = {
                                                    val dbHelper = TranscriptionDbHelper(this@HistoryActivity)
                                                    dbHelper.deleteAllTranscriptions()
                                                    showDeleteAllDialog = false
                                                    recreate() // Reload activity
                                                }
                                            ) {
                                                Text("Delete All")
                                            }
                                        },
                                        dismissButton = {
                                            Button(onClick = { showDeleteAllDialog = false }) {
                                                Text("Cancel")
                                            }
                                        }
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    HistoryScreen(modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dbHelper = remember { TranscriptionDbHelper(context) }
    val transcriptions = remember { mutableStateOf(listOf<TranscriptionEntry>()) }

    LaunchedEffect(Unit) {
        transcriptions.value = dbHelper.getAllTranscriptions()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(transcriptions.value) { entry ->
            TranscriptionHistoryItem(entry = entry)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionHistoryItem(entry: TranscriptionEntry) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Language: ${entry.language}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Prompt: ${entry.prompt}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Source: ${entry.sourceHint}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Date: ${dateFormat.format(entry.timestamp)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options"
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy") },
                            onClick = {
                                showMenu = false
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Transcription", entry.text)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Copy"
                                )
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete"
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Transcription") },
            text = { Text("Are you sure you want to delete this transcription?") },
            confirmButton = {
                Button(
                    onClick = {
                        val dbHelper = TranscriptionDbHelper(context)
                        dbHelper.deleteTranscription(entry.id)
                        showDeleteDialog = false
                        // Refresh activity
                        (context as? ComponentActivity)?.recreate()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HistoryScreenPreview() {
    AIAudioTranscriptionTheme {
        HistoryScreen()
    }
}
