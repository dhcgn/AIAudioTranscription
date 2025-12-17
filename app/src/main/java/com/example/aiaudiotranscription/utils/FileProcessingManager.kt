package com.example.aiaudiotranscription.utils

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.aiaudiotranscription.api.MODEL_WHISPER
import com.example.aiaudiotranscription.sharedPrefsUtils.SharedPrefsUtils
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @ApplicationContext private val context: Context
) {
    // Change to use both opus and mp4 (aac) files
    private val opusOutputFile = File(context.filesDir, "transcription_audio.ogg")
    private val mp4OutputFile = File(context.filesDir, "transcription_audio.m4a")

    suspend fun processAudioFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val selectedModel = SharedPrefsUtils.getTranscriptionModel(context, MODEL_WHISPER)
        val useOpus = selectedModel == MODEL_WHISPER
        val outputFile = if (useOpus) opusOutputFile else mp4OutputFile

        try {
            // 1. Copy input file
            val inputFile = copyUriToFile(uri)

            // 2. Ensure output file is clean
            if (outputFile.exists()) {
                outputFile.delete()
            }

            // 3. Convert file
            var bitrate = if (useOpus) 12000 else 32000
            val minBitrate = if (useOpus) 6000 else 16000
            val maxSizeBytes = 25 * 1024 * 1024L
            var attempts = 0
            val maxAttempts = 10

            while (attempts < maxAttempts) {
                convertAudioFile(inputFile.absolutePath, outputFile.absolutePath, useOpus, bitrate)
                attempts++

                val currentSize = outputFile.length()
                if (currentSize <= maxSizeBytes || bitrate <= minBitrate) {
                    break
                }

                // Delete output file before retry
                if (outputFile.exists()) {
                    outputFile.delete()
                }

                // Reduce bitrate if file is too large
                // Calculate proportional reduction
                val targetBitrate = (bitrate * maxSizeBytes.toDouble() / currentSize).toInt()
                // Ensure we drop at least 1kbps
                val nextBitrate = minOf(targetBitrate, bitrate - 1000)

                // Clamp to min
                bitrate = maxOf(minBitrate, nextBitrate)
            }

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

    @OptIn(UnstableApi::class)
    private suspend fun convertAudioFile(
        inputPath: String,
        outputPath: String,
        useOpus: Boolean,
        bitrate: Int
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val mimeType = if (useOpus) MimeTypes.AUDIO_OPUS else MimeTypes.AUDIO_AAC
            
            val audioEncoderSettings = AudioEncoderSettings.Builder()
                .setBitrate(bitrate)
                .build()
                
            val encoderFactory = DefaultEncoderFactory.Builder(context)
                .setRequestedAudioEncoderSettings(audioEncoderSettings)
                .build()
            
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(mimeType)
                .setEncoderFactory(encoderFactory)
                .build()
                
            val mediaItem = MediaItem.fromUri(Uri.fromFile(File(inputPath)))
            val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
            // Use Builder to avoid private constructor issue.
            val editedMediaItemSequence = EditedMediaItemSequence.Builder(editedMediaItem).build()

            val composition = Composition.Builder(editedMediaItemSequence).build()

            transformer.addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    continuation.resumeWithException(
                        FileProcessingException("Transformer error: ${exportException.message}", exportException)
                    )
                }
            })

            transformer.start(composition, outputPath)
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
