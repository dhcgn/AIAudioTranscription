package app.hdev.io.aitranscribe.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.hdev.io.aitranscribe.ui.theme.AIAudioTranscriptionTheme
import app.hdev.io.aitranscribe.utils.LogCategory
import app.hdev.io.aitranscribe.utils.LogEntry
import app.hdev.io.aitranscribe.utils.LogManager
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class LoggingActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AIAudioTranscriptionTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Application Logs") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                ShareLogsButton()
                                ClearLogsButton()
                            }
                        )
                    }
                ) { padding ->
                    LoggingScreen(
                        modifier = Modifier.padding(padding)
                    )
                }
            }
        }
    }
}

@Composable
fun LoggingScreen(modifier: Modifier = Modifier) {
    var logs by remember { mutableStateOf<List<LogEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // Load logs on composition
    LaunchedEffect(Unit) {
        scope.launch {
            logs = LogManager.getAllLogs()
            isLoading = false
            
            // Auto-scroll to bottom (most recent)
            if (logs.isNotEmpty()) {
                listState.scrollToItem(logs.size - 1)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (logs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No logs available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "Total logs: ${logs.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { logEntry ->
                    LogEntryItem(logEntry)
                }
            }
        }
    }
}

@Composable
fun LogEntryItem(logEntry: LogEntry) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US) }
    val backgroundColor = when (logEntry.category) {
        LogCategory.ERROR -> MaterialTheme.colorScheme.errorContainer
        LogCategory.API_CALL -> MaterialTheme.colorScheme.primaryContainer
        LogCategory.REENCODE -> MaterialTheme.colorScheme.secondaryContainer
        LogCategory.FILE_OP -> MaterialTheme.colorScheme.tertiaryContainer
    }
    
    val textColor = when (logEntry.category) {
        LogCategory.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        LogCategory.API_CALL -> MaterialTheme.colorScheme.onPrimaryContainer
        LogCategory.REENCODE -> MaterialTheme.colorScheme.onSecondaryContainer
        LogCategory.FILE_OP -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dateFormat.format(logEntry.timestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor
                )
                
                Text(
                    text = logEntry.category.name,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = textColor
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = logEntry.message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace
                ),
                color = textColor
            )
        }
    }
}

@Composable
fun ClearLogsButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { showDialog = true }) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Clear Logs"
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Clear All Logs") },
            text = { Text("Are you sure you want to clear all logs? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            LogManager.clearLogs()
                            showDialog = false
                            // Restart activity to refresh the list
                            (context as? ComponentActivity)?.recreate()
                        }
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ShareLogsButton() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = {
            scope.launch {
                try {
                    val logsText = LogManager.getLogsAsText()
                    
                    if (logsText.isEmpty()) {
                        android.widget.Toast.makeText(
                            context,
                            "No logs to share",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }
                    
                    // Create a temporary file for sharing
                    val logsFile = File(context.cacheDir, "app_logs_export.txt")
                    logsFile.writeText(logsText)
                    
                    // Create a content URI using FileProvider
                    val logsUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        logsFile
                    )
                    
                    // Create share intent
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_STREAM, logsUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Application Logs")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    
                    context.startActivity(Intent.createChooser(shareIntent, "Share Logs"))
                } catch (e: Exception) {
                    android.widget.Toast.makeText(
                        context,
                        "Error sharing logs: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    ) {
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Share Logs"
        )
    }
}
