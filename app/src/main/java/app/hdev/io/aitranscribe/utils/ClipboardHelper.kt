package app.hdev.io.aitranscribe.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher

/**
 * Helper object for handling clipboard operations with size limitations.
 * 
 * Android has a clipboard size limitation due to the Binder transaction limit (~1MB).
 * This helper proactively checks text size and offers file save as an alternative
 * for large text content.
 */
object ClipboardHelper {
    
    /**
     * Maximum character count for clipboard operations.
     * Beyond this threshold, text will be saved to a file instead.
     */
    const val MAX_CLIPBOARD_CHARS = 20_000
    
    /**
     * Handles copying text to clipboard or saving to file based on size.
     * 
     * @param context The application context
     * @param text The text to copy or save
     * @param label Label for the clipboard data
     * @param fileName Default filename for file save (without extension)
     * @param fileSaveLauncher ActivityResultLauncher for file save intent
     * @param onFileSaveInitiated Optional callback when file save dialog is shown
     */
    fun handleTextCopy(
        context: Context,
        text: String,
        label: String = "Text",
        fileName: String = "transcription",
        fileSaveLauncher: ActivityResultLauncher<Intent>?,
        onFileSaveInitiated: ((String) -> Unit)? = null
    ) {
        if (text.length <= MAX_CLIPBOARD_CHARS) {
            // Text is small enough for clipboard
            copyToClipboard(context, text, label)
        } else {
            // Text is too large, initiate file save
            if (fileSaveLauncher != null) {
                initiateFileSave(context, text, fileName, fileSaveLauncher, onFileSaveInitiated)
            } else {
                // Fallback: show error if no file save launcher provided
                Toast.makeText(
                    context,
                    "Text is too large (${formatCharCount(text.length)}) for clipboard. File save not configured.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Copies text to clipboard (for text within size limits).
     */
    private fun copyToClipboard(context: Context, text: String, label: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * Initiates file save dialog for large text.
     */
    private fun initiateFileSave(
        context: Context,
        text: String,
        fileName: String,
        fileSaveLauncher: ActivityResultLauncher<Intent>,
        onFileSaveInitiated: ((String) -> Unit)?
    ) {
        // Show info toast
        Toast.makeText(
            context,
            "Text is too large (${formatCharCount(text.length)}) for clipboard. Opening file save dialog...",
            Toast.LENGTH_LONG
        ).show()
        
        // Store text temporarily for retrieval in result callback
        onFileSaveInitiated?.invoke(text)
        
        // Create file save intent
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "$fileName.txt")
        }
        
        fileSaveLauncher.launch(intent)
    }
    
    /**
     * Writes text to a URI (typically called in ActivityResult callback).
     * 
     * @param context The application context
     * @param uri The URI to write to
     * @param text The text to write
     * @return true if successful, false otherwise
     */
    fun writeTextToUri(context: Context, uri: Uri, text: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(text.toByteArray())
            }
            Toast.makeText(context, "Text saved to file successfully", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to save file: ${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }
    
    /**
     * Formats character count for display.
     */
    private fun formatCharCount(count: Int): String {
        return when {
            count < 1_000 -> "$count chars"
            count < 1_000_000 -> String.format("%.1fK chars", count / 1_000.0)
            else -> String.format("%.1fM chars", count / 1_000_000.0)
        }
    }
}
