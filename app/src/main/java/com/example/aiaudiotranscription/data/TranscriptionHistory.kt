package com.example.aiaudiotranscription.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Date

data class TranscriptionEntry(
    val id: Long = 0,
    val text: String,
    val language: String = "",     // Add default values
    val prompt: String = "",       // Add default values
    val sourceHint: String = "",   // Add default values
    val model: String = "whisper-1", // Add default value
    val timestamp: Date = Date(),
    // Statistics
    val originalFileSizeBytes: Long = 0,  // Size of original file before processing
    val uploadedFileSizeBytes: Long = 0,  // Size of file after processing/encoding
    val transcriptLength: Int = 0,        // Character count of transcript text
    val audioDurationSeconds: Long = 0     // Duration of audio in seconds (if available)
)

class TranscriptionDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 3 // Updated version for statistics
        const val DATABASE_NAME = "TranscriptionHistory.db"

        private const val SQL_CREATE_ENTRIES = """
            CREATE TABLE transcriptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT,
                language TEXT,
                prompt TEXT,
                source_hint TEXT,
                model TEXT,
                timestamp INTEGER,
                original_file_size_bytes INTEGER,
                uploaded_file_size_bytes INTEGER,
                transcript_length INTEGER,
                audio_duration_seconds INTEGER
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE transcriptions ADD COLUMN model TEXT")
        }
        if (oldVersion < 3) {
            // Add statistics columns
            db.execSQL("ALTER TABLE transcriptions ADD COLUMN original_file_size_bytes INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE transcriptions ADD COLUMN uploaded_file_size_bytes INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE transcriptions ADD COLUMN transcript_length INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE transcriptions ADD COLUMN audio_duration_seconds INTEGER DEFAULT 0")
        }
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Handle downgrade by recreating the database
        db.execSQL("DROP TABLE IF EXISTS transcriptions")
        onCreate(db)
    }

    fun addTranscription(entry: TranscriptionEntry): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put("text", entry.text)
            put("language", entry.language)
            put("prompt", entry.prompt)
            put("source_hint", entry.sourceHint)
            put("model", entry.model) // Store model information
            put("timestamp", entry.timestamp.time)
            put("original_file_size_bytes", entry.originalFileSizeBytes)
            put("uploaded_file_size_bytes", entry.uploadedFileSizeBytes)
            put("transcript_length", entry.transcriptLength)
            put("audio_duration_seconds", entry.audioDurationSeconds)
        }
        return db.insert("transcriptions", null, values)
    }

    fun getAllTranscriptions(): List<TranscriptionEntry> {
        val list = mutableListOf<TranscriptionEntry>()
        val db = this.readableDatabase
        val cursor = db.query(
            "transcriptions",
            null,
            null,
            null,
            null,
            null,
            "timestamp DESC"
        )

        with(cursor) {
            while (moveToNext()) {
                list.add(
                    TranscriptionEntry(
                        id = getLong(getColumnIndexOrThrow("id")),
                        text = getString(getColumnIndexOrThrow("text")) ?: "",
                        language = getString(getColumnIndexOrThrow("language")) ?: "",
                        prompt = getString(getColumnIndexOrThrow("prompt")) ?: "",
                        sourceHint = getString(getColumnIndexOrThrow("source_hint")) ?: "",
                        model = getString(getColumnIndexOrThrow("model")) ?: "whisper-1",
                        timestamp = Date(getLong(getColumnIndexOrThrow("timestamp"))),
                        originalFileSizeBytes = getLong(getColumnIndexOrThrow("original_file_size_bytes")),
                        uploadedFileSizeBytes = getLong(getColumnIndexOrThrow("uploaded_file_size_bytes")),
                        transcriptLength = getInt(getColumnIndexOrThrow("transcript_length")),
                        audioDurationSeconds = getLong(getColumnIndexOrThrow("audio_duration_seconds"))
                    )
                )
            }
        }
        cursor.close()
        return list
    }

    fun deleteTranscription(id: Long): Boolean {
        val db = this.writableDatabase
        return db.delete("transcriptions", "id = ?", arrayOf(id.toString())) > 0
    }

    fun deleteAllTranscriptions() {
        val db = this.writableDatabase
        db.delete("transcriptions", null, null)
    }
}
