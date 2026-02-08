package app.hdev.io.aitranscribe.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import app.hdev.io.aitranscribe.data.TranscriptionDbHelper
import app.hdev.io.aitranscribe.data.TranscriptionEntry
import app.hdev.io.aitranscribe.ui.theme.AIAudioTranscriptionTheme
import app.hdev.io.aitranscribe.utils.ClipboardHelper
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

/**
 * Format file size in bytes to human-readable format
 */
fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "N/A"
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = bytes.toDouble()
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    return String.format("%.2f %s", size, units[unitIndex])
}

/**
 * Format duration in seconds to human-readable format
 */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "N/A"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        minutes > 0 -> String.format("%d:%02d", minutes, secs)
        else -> String.format("%ds", secs)
    }
}

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
    // File save launcher for large clipboard text
    private var pendingTextForSave: String? = null
    private val fileSaveLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    pendingTextForSave?.let { text ->
                        ClipboardHelper.writeTextToUri(this, uri, text)
                        pendingTextForSave = null
                    }
                }
            } else {
                pendingTextForSave = null
            }
        }

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
                    HistoryScreen(
                        modifier = Modifier.padding(padding),
                        onCopyToClipboard = { text, fileName ->
                            handleCopyToClipboard(text, fileName)
                        }
                    )
                }
            }
        }
    }

    private fun handleCopyToClipboard(text: String, fileName: String = "transcription") {
        ClipboardHelper.handleTextCopy(
            context = this,
            text = text,
            label = "Transcription",
            fileName = fileName,
            fileSaveLauncher = fileSaveLauncher,
            onFileSaveInitiated = { textToSave ->
                pendingTextForSave = textToSave
            }
        )
    }
}

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onCopyToClipboard: (String, String) -> Unit = { _, _ -> }
) {
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
            TranscriptionHistoryItem(
                entry = entry,
                onCopyToClipboard = onCopyToClipboard
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionHistoryItem(
    entry: TranscriptionEntry,
    onCopyToClipboard: (String, String) -> Unit = { _, _ -> }
) {
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
                    
                    // Always show key statistics
                    Text(
                        text = "Length: ${entry.transcriptLength} chars • Model: ${entry.model}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Show file sizes if available
                    if (entry.uploadedFileSizeBytes > 0 || entry.originalFileSizeBytes > 0) {
                        Text(
                            text = "File: ${formatFileSize(entry.originalFileSizeBytes)} → ${formatFileSize(entry.uploadedFileSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Make the text clickable
                        Text(
                            text = if (expanded) "Less details" else "More details",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
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
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Transcript Statistics
                        Text(
                            text = "Transcript Statistics:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Length: ${entry.transcriptLength} characters",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (entry.audioDurationSeconds > 0) {
                            Text(
                                text = "• Duration: ${formatDuration(entry.audioDurationSeconds)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        // File Statistics (only if available)
                        if (entry.originalFileSizeBytes > 0 || entry.uploadedFileSizeBytes > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "File Statistics:",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (entry.originalFileSizeBytes > 0) {
                                Text(
                                    text = "• Original file size: ${formatFileSize(entry.originalFileSizeBytes)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            if (entry.uploadedFileSizeBytes > 0) {
                                Text(
                                    text = "• Uploaded file size: ${formatFileSize(entry.uploadedFileSizeBytes)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        
                        // Processing Settings (only show non-empty values)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Processing Settings:",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Model: ${entry.model}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (entry.language.isNotEmpty()) {
                            Text(
                                text = "• Language: ${entry.language}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (entry.prompt.isNotEmpty()) {
                            Text(
                                text = "• Prompt: ${entry.prompt}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        if (entry.sourceHint.isNotEmpty()) {
                            Text(
                                text = "• Source: ${entry.sourceHint}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Date: ${dateFormat.format(entry.timestamp)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                onCopyToClipboard(entry.text, "transcription_${entry.timestamp}")
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
                                    
                                    appendLine("Transcript Statistics:")
                                    appendLine("• Length: ${entry.transcriptLength} characters")
                                    if (entry.audioDurationSeconds > 0) {
                                        appendLine("• Duration: ${formatDuration(entry.audioDurationSeconds)}")
                                    }
                                    
                                    if (entry.originalFileSizeBytes > 0 || entry.uploadedFileSizeBytes > 0) {
                                        appendLine()
                                        appendLine("File Statistics:")
                                        if (entry.originalFileSizeBytes > 0) {
                                            appendLine("• Original file size: ${formatFileSize(entry.originalFileSizeBytes)}")
                                        }
                                        if (entry.uploadedFileSizeBytes > 0) {
                                            appendLine("• Uploaded file size: ${formatFileSize(entry.uploadedFileSizeBytes)}")
                                        }
                                    }
                                    
                                    appendLine()
                                    appendLine("Processing Settings:")
                                    appendLine("• Model: ${entry.model}")
                                    if (entry.language.isNotEmpty()) {
                                        appendLine("• Language: ${entry.language}")
                                    }
                                    if (entry.prompt.isNotEmpty()) {
                                        appendLine("• Prompt: ${entry.prompt}")
                                    }
                                    if (entry.sourceHint.isNotEmpty()) {
                                        appendLine("• Source: ${entry.sourceHint}")
                                    }
                                    appendLine("• Date: ${dateFormat.format(entry.timestamp)}")
                                }
                                onCopyToClipboard(detailedText, "transcription_details_${entry.timestamp}")
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
            language = "en",
            prompt = "Interview transcription",
            sourceHint = "interview.mp3",
            model = "whisper-1",
            timestamp = Date(),
            originalFileSizeBytes = 5_242_880, // 5 MB
            uploadedFileSizeBytes = 2_621_440, // 2.5 MB
            transcriptLength = 103,
            audioDurationSeconds = 180 // 3 minutes
        ),
        TranscriptionEntry(
            id = 2,
            text = "Dies ist eine Beispieltranskription auf Deutsch. Die Audioqualität war ausgezeichnet.",
            language = "de",
            prompt = "Meeting notes",
            sourceHint = "meeting_2024.m4a",
            model = "gpt-4o-audio-preview",
            timestamp = Date(),
            originalFileSizeBytes = 12_582_912, // 12 MB
            uploadedFileSizeBytes = 8_388_608, // 8 MB
            transcriptLength = 85,
            audioDurationSeconds = 600 // 10 minutes
        ),
        TranscriptionEntry(
            id = 3,
            text = "This is a longer transcription that contains multiple sentences. It demonstrates how the card handles longer text content. The text should be truncated after three lines to keep the UI clean and consistent.",
            language = "en",
            prompt = "Lecture transcription",
            sourceHint = "lecture_recording.wav",
            model = "gpt-4o-audio-preview-2024-12-17",
            timestamp = Date(),
            originalFileSizeBytes = 20_971_520, // 20 MB
            uploadedFileSizeBytes = 15_728_640, // 15 MB
            transcriptLength = 196,
            audioDurationSeconds = 1800 // 30 minutes
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
