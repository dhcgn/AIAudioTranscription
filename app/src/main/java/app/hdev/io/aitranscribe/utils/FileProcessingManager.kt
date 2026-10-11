package app.hdev.io.aitranscribe.utils

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

/**
 * Result of audio file processing containing the processed file and metadata
 */
data class ProcessingResult(
    val processedFile: File,
    val originalFileSizeBytes: Long,
    val processedFileSizeBytes: Long,
    val originalFileName: String? = null
)

@Singleton
class FileProcessingManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mp4OutputFile = File(context.filesDir, "transcription_audio.m4a")

    suspend fun processAudioFile(uri: Uri): ProcessingResult = withContext(Dispatchers.IO) {
        val outputFile = mp4OutputFile

        try {
            LogManager.log(LogCategory.FILE_OP, "Starting audio file processing for URI: $uri")
            
            // 1. Copy input file and extract original filename
            val inputFile = copyUriToFile(uri)
            val originalFileSize = inputFile.length()
            val originalFileName = getFileNameFromUri(uri)
            
            LogManager.log(LogCategory.FILE_OP, "Copied input file: ${inputFile.name}, size: $originalFileSize bytes")

            // 2. Ensure output file is clean
            if (outputFile.exists()) {
                outputFile.delete()
            }

            // 3. Convert file
            var bitrate = 32000
            val minBitrate = 16000
            val maxSizeBytes = 25 * 1024 * 1024L
            var attempts = 0
            val maxAttempts = 10
            
            LogManager.log(LogCategory.REENCODE, "Starting audio re-encoding with bitrate: $bitrate bps")

            while (attempts < maxAttempts) {
                convertAudioFile(inputFile.absolutePath, outputFile.absolutePath, bitrate)
                attempts++

                val currentSize = outputFile.length()
                if (currentSize <= maxSizeBytes || bitrate <= minBitrate) {
                    LogManager.log(LogCategory.REENCODE, "Audio re-encoding completed in $attempts attempt(s). Output size: $currentSize bytes, bitrate: $bitrate bps")
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
                
                LogManager.log(LogCategory.REENCODE, "Re-encoding attempt $attempts: file too large ($currentSize bytes), reducing bitrate to $bitrate bps")
            }

            // 4. Clean up input file
            inputFile.delete()
            LogManager.log(LogCategory.FILE_OP, "Cleaned up temporary input file")
            
            val processedFileSize = outputFile.length()

            ProcessingResult(
                processedFile = outputFile,
                originalFileSizeBytes = originalFileSize,
                processedFileSizeBytes = processedFileSize,
                originalFileName = originalFileName
            )
        } catch (e: Exception) {
            LogManager.log(LogCategory.ERROR, "Audio file processing failed: ${e.message}")
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
        bitrate: Int
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val mimeType = MimeTypes.AUDIO_AAC
            
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
    
    private fun getFileNameFromUri(uri: Uri): String? {
        // Try to get the display name from the content resolver
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        
        // Fallback to the last path segment
        return uri.lastPathSegment
    }
}

class FileProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)
