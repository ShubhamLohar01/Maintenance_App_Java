package com.candorfoods.factoryops.util.pdf

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Download" for a generated PDF — NOT the share sheet. Saves a copy to the device's public
 * Downloads collection (Android 10+ via MediaStore, no storage permission needed) and opens
 * it in a PDF viewer for review. On older devices (pre-Android 10) it can't write public
 * Downloads without a permission flow, so it falls back to opening the file for review.
 */
object PdfDownloader {

    /** Save to Downloads + open for review. Returns a short user-facing status message. */
    suspend fun downloadAndOpen(context: Context, file: File): String {
        val savedUri = withContext(Dispatchers.IO) { saveToDownloads(context, file, file.name) }
        val viewUri = savedUri ?: fileUriOrNull(context, file)
        val opened = viewUri != null && open(context, viewUri)
        return when {
            savedUri != null && opened -> "Saved to Downloads · opened for review"
            savedUri != null           -> "Saved to Downloads"
            opened                     -> "Opened the PDF (couldn't save to Downloads on this device)"
            else                       -> "Couldn't open the PDF — no PDF viewer installed"
        }
    }

    /** Copy [source] into the public Downloads collection. Returns the saved content Uri, or
     *  null when it can't be saved there (pre-Android 10, or on failure). */
    private fun saveToDownloads(context: Context, source: File, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: error("openOutputStream returned null")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** Open a PDF (content or file Uri) in a viewer. Returns false if no viewer app exists. */
    private fun open(context: Context, uri: Uri): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(view)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun fileUriOrNull(context: Context, file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file.canonicalFile)
    }.getOrNull()
}
