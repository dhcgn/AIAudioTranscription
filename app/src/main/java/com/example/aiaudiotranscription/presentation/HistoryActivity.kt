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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import com.example.aiaudiotranscription.data.TranscriptionDbHelper
import com.example.aiaudiotranscription.data.TranscriptionEntry
import com.example.aiaudiotranscription.ui.theme.AIAudioTranscriptionTheme
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTopBar(
    onBackClick: () -> Unit,
    onDeleteAllClick: () -> Unit,
    showDeleteAllDialog: Boolean,
    onDeleteAllConfirm: () -> Unit,
    onDeleteAllDismiss: () -> Unit
) {
    TopAppBar(
        title = { Text("Transcription History") },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onDeleteAllClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete All"
                )
            }

            if (showDeleteAllDialog) {
                AlertDialog(
                    onDismissRequest = onDeleteAllDismiss,
                    title = { Text("Delete All Transcriptions") },
                    text = { Text("Are you sure you want to delete all transcriptions? This cannot be undone.") },
                    confirmButton = {
                        Button(onClick = onDeleteAllConfirm) {
                            Text("Delete All")
                        }
                    },
                    dismissButton = {
                        Button(onClick = onDeleteAllDismiss) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun HistoryTopBarPreview() {
    AIAudioTranscriptionTheme {
        Surface {
            HistoryTopBar(
                onBackClick = { },
                onDeleteAllClick = { },
                showDeleteAllDialog = false,
                onDeleteAllConfirm = { },
                onDeleteAllDismiss = { }
            )
        }
    }
}

class HistoryActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAudioTranscriptionTheme {
                var showDeleteAllDialog by remember { mutableStateOf(false) }
                
                Scaffold(
                    topBar = {
                        HistoryTopBar(
                            onBackClick = { finish() },
                            onDeleteAllClick = { showDeleteAllDialog = true },
                            showDeleteAllDialog = showDeleteAllDialog,
                            onDeleteAllConfirm = {
                                val dbHelper = TranscriptionDbHelper(this)
                                dbHelper.deleteAllTranscriptions()
                                showDeleteAllDialog = false
                                recreate()
                            },
                            onDeleteAllDismiss = { showDeleteAllDialog = false }
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
    var expanded by remember { mutableStateOf(false) }
    
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Make the text clickable
                        Text(
                            text = "Details",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .clickable { expanded = !expanded }
                                .padding(vertical = 8.dp) // Add padding for better touch target
                        )
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = if (expanded) "Show less" else "Show more"
                            )
                        }
                    }
                    
                    if (expanded) {
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
                            text = "Model: ${entry.model}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Date: ${dateFormat.format(entry.timestamp)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
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
                            text = { Text("Copy with Details") },
                            onClick = {
                                showMenu = false
                                val detailedText = buildString {
                                    appendLine("Transcription:")
                                    appendLine(entry.text)
                                    appendLine()
                                    appendLine("Details:")
                                    appendLine("Language: ${entry.language}")
                                    appendLine("Prompt: ${entry.prompt}")
                                    appendLine("Source: ${entry.sourceHint}")
                                    appendLine("Model: ${entry.model}")
                                    appendLine("Date: ${dateFormat.format(entry.timestamp)}")
                                }
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Transcription with Details", detailedText)
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Text with details copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Copy with Details"
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
    val sampleTranscriptions = listOf(
        TranscriptionEntry(
            id = 1,
            text = "This is a sample transcription of an interview. The audio quality was good and the speaker was clear.",
            language = "English",
            prompt = "Interview transcription",
            sourceHint = "interview.mp3",
            model = "whisper-1",
            timestamp = Date()
        ),
        TranscriptionEntry(
            id = 2,
            text = "Dies ist eine Beispieltranskription auf Deutsch. Die Audioqualität war ausgezeichnet.",
            language = "German",
            prompt = "Meeting notes",
            sourceHint = "meeting_2024.m4a",
            model = "whisper-1",
            timestamp = Date()
        ),
        TranscriptionEntry(
            id = 3,
            text = "This is a longer transcription that contains multiple sentences. It demonstrates how the card handles longer text content. The text should be truncated after three lines to keep the UI clean and consistent.",
            language = "English",
            prompt = "Lecture transcription",
            sourceHint = "lecture_recording.wav",
            model = "whisper-1",
            timestamp = Date()
        )
    )

    AIAudioTranscriptionTheme {
        Surface {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sampleTranscriptions) { entry ->
                    TranscriptionHistoryItem(entry = entry)
                }
            }
        }
    }
}
