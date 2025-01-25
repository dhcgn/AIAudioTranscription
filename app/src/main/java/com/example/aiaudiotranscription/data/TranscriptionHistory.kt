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
    val timestamp: Date = Date()
)

class TranscriptionDbHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_VERSION = 1
        const val DATABASE_NAME = "TranscriptionHistory.db"

        private const val SQL_CREATE_ENTRIES = """
            CREATE TABLE transcriptions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                text TEXT,
                language TEXT,
                prompt TEXT,
                source_hint TEXT,
                timestamp INTEGER
            )
        """
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
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
