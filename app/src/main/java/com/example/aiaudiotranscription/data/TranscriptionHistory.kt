package com.example.aiaudiotranscription.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Date

data class TranscriptionEntry(
    val id: Long = 0,
    val text: String,
    val language: String,
    val prompt: String,
    val sourceHint: String,
    val model: String, // Added model field
    val timestamp: Date = Date()
)

class TranscriptionDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 2 // Updated version
        const val DATABASE_NAME = "TranscriptionHistory.db"

        private const val SQL_CREATE_ENTRIES = """
            CREATE TABLE transcriptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT,
                language TEXT,
                prompt TEXT,
                source_hint TEXT,
                model TEXT, -- Added model column
                timestamp INTEGER
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
                        text = getString(getColumnIndexOrThrow("text")),
                        language = getString(getColumnIndexOrThrow("language")),
                        prompt = getString(getColumnIndexOrThrow("prompt")),
                        sourceHint = getString(getColumnIndexOrThrow("source_hint")),
                        model = getString(getColumnIndexOrThrow("model")), // Retrieve model information
                        timestamp = Date(getLong(getColumnIndexOrThrow("timestamp")))
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
