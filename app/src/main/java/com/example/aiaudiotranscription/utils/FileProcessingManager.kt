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

@Singleton
class FileProcessingManager @Inject constructor(
    private val context: Context
) {
    suspend fun processAudioFile(uri: Uri): File = withContext(Dispatchers.IO) {
        try {
            // 1. Copy input file
            val inputFile = copyUriToFile(uri)
            
            // 2. Prepare output file
            val outputFile = File(context.filesDir, "converted_${System.currentTimeMillis()}.ogg")
            
            // 3. Convert file
            convertAudioFile(inputFile.absolutePath, outputFile.absolutePath)
            
            // 4. Clean up input file
            inputFile.delete()
            
            outputFile
        } catch (e: Exception) {
            throw FileProcessingException("Failed to process audio file", e)
        }
    }

    private suspend fun convertAudioFile(
        inputPath: String,
        outputPath: String
    ) = suspendCancellableCoroutine { continuation ->
        val command = "-i $inputPath -vn -map_metadata -1 -ac 1 -c:a libopus -b:a 12k -application voip $outputPath"
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
