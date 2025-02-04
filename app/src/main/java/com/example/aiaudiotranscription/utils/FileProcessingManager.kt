package com.example.aiaudiotranscription.utils

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.example.aiaudiotranscription.api.MODEL_WHISPER
import com.example.aiaudiotranscription.presentation.getTranscriptionModel
import com.example.aiaudiotranscription.sharedPrefsUtils.SharedPrefsUtils

@Singleton
class FileProcessingManager @Inject constructor(
    private val context: Context
) {
    // Change to use both opus and mp3 files
    private val opusOutputFile = File(context.filesDir, "transcription_audio.ogg")
    private val mp3OutputFile = File(context.filesDir, "transcription_audio.mp3")

    suspend fun processAudioFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val selectedModel = SharedPrefsUtils.getTranscriptionModel(context) ?: MODEL_WHISPER
        val useOpus = selectedModel == MODEL_WHISPER
        val outputFile = if (useOpus) opusOutputFile else mp3OutputFile

        try {
            // 1. Copy input file
            val inputFile = copyUriToFile(uri)
            
            // 2. Ensure output file is clean
            if (outputFile.exists()) {
                outputFile.delete()
            }
            
            // 3. Convert file
            convertAudioFile(inputFile.absolutePath, outputFile.absolutePath, useOpus)
            
            // 4. Clean up input file
            inputFile.delete()
            
            outputFile
        } catch (e: Exception) {
            // Clean up output file on error
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw FileProcessingException("Failed to process audio file", e)
        }
    }

    private suspend fun convertAudioFile(
        inputPath: String,
        outputPath: String,
        useOpus: Boolean
    ) = suspendCancellableCoroutine { continuation ->
        val command = if (useOpus) {
            // Original Opus command for Whisper
            "-i $inputPath -vn -map_metadata -1 -ac 1 -c:a libopus -b:a 12k -application voip $outputPath"
        } else {
            // MP3 command for GPT-4 Audio
            "-i $inputPath -vn -map_metadata -1 -ac 1 -c:a libmp3lame -b:a 32k $outputPath"
        }
        
        FFmpegKit.executeAsync(command) { session ->
            if (ReturnCode.isSuccess(session.returnCode)) {
                continuation.resume(Unit)
            } else {
                continuation.resumeWithException(
                    FileProcessingException("FFmpeg error: ${session.failStackTrace}")
                )
            }
        }
    }

    private suspend fun copyUriToFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw FileProcessingException("Could not open input stream for URI")
        tempFile
    }
}

class FileProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)
