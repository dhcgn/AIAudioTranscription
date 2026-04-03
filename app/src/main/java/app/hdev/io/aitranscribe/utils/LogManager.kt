package app.hdev.io.aitranscribe.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a log entry with timestamp
 */
data class LogEntry(
    val timestamp: Date,
    val message: String,
    val category: LogCategory
)

/**
 * Categories of log events
 */
enum class LogCategory {
    REENCODE,      // Audio re-encoding operations
    API_CALL,      // API communication
    FILE_OP,       // File operations
    ERROR          // Errors
}

/**
 * Singleton manager for application logging
 * Thread-safe logging with file persistence
 */
object LogManager {
    private const val LOG_FILE_NAME = "app_logs.txt"
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val mutex = Mutex()
    
    private lateinit var logFile: File
    private var isInitialized = false
    
    /**
     * Initialize the log manager with application context
     */
    fun initialize(context: Context) {
        if (!isInitialized) {
            logFile = File(context.filesDir, LOG_FILE_NAME)
            if (!logFile.exists()) {
                logFile.createNewFile()
            }
            isInitialized = true
        }
    }
    
    /**
     * Log a message with the specified category
     */
    suspend fun log(category: LogCategory, message: String) {
        if (!isInitialized) {
            return
        }
        
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    val timestamp = Date()
                    val formattedTimestamp = dateFormat.format(timestamp)
                    val logLine = "[$formattedTimestamp] [${category.name}] $message\n"
                    
                    logFile.appendText(logLine)
                } catch (e: Exception) {
                    // Silent failure to avoid infinite logging loops
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Read all logs from the file
     */
    suspend fun getAllLogs(): List<LogEntry> = withContext(Dispatchers.IO) {
        if (!isInitialized || !logFile.exists()) {
            return@withContext emptyList()
        }
        
        mutex.withLock {
            try {
                val logs = mutableListOf<LogEntry>()
                logFile.readLines().forEach { line ->
                    parseLogLine(line)?.let { logs.add(it) }
                }
                logs
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
    
    /**
     * Get logs as plain text (for sharing)
     */
    suspend fun getLogsAsText(): String = withContext(Dispatchers.IO) {
        if (!isInitialized || !logFile.exists()) {
            return@withContext ""
        }
        
        mutex.withLock {
            try {
                logFile.readText()
            } catch (e: Exception) {
                e.printStackTrace()
                ""
            }
        }
    }
    
    /**
     * Clear all logs
     */
    suspend fun clearLogs() {
        if (!isInitialized) {
            return
        }
        
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    logFile.writeText("")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    
    /**
     * Parse a log line into a LogEntry
     */
    private fun parseLogLine(line: String): LogEntry? {
        if (line.isBlank()) return null
        
        try {
            // Format: [yyyy-MM-dd HH:mm:ss.SSS] [CATEGORY] message
            val timestampEnd = line.indexOf(']')
            if (timestampEnd == -1) return null
            
            val timestampStr = line.substring(1, timestampEnd)
            val timestamp = dateFormat.parse(timestampStr) ?: return null
            
            val categoryStart = line.indexOf('[', timestampEnd)
            val categoryEnd = line.indexOf(']', categoryStart)
            if (categoryStart == -1 || categoryEnd == -1) return null
            
            val categoryStr = line.substring(categoryStart + 1, categoryEnd)
            val category = try {
                LogCategory.valueOf(categoryStr)
            } catch (e: IllegalArgumentException) {
                LogCategory.ERROR
            }
            
            val message = line.substring(categoryEnd + 2) // +2 to skip "] "
            
            return LogEntry(timestamp, message, category)
        } catch (e: Exception) {
            return null
        }
    }
}
