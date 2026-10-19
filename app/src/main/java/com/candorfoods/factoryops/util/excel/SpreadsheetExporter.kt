package com.candorfoods.factoryops.util.excel

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a [Sheet] to an .xlsx and puts it in the device's public Downloads, then offers to
 * open it. Mirrors PdfDownloader's behaviour so "Download" means the same thing everywhere
 * in the app: the file lands in Downloads (Android 10+ via MediaStore, no permission), and
 * opening it is a convenience rather than the point.
 */
@Singleton
class SpreadsheetExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Build the workbook and save it. Returns a short user-facing status line.
     *
     * An empty sheet is refused rather than written — a spreadsheet with only headers
     * looks like a failed export and invites a bug report.
     */
    suspend fun export(sheet: Sheet, title: String, stamp: String = LocalDate.now().toString()): String {
        if (sheet.rows.isEmpty()) return "Nothing to download yet"
        val name = Xlsx.fileName(title, stamp)
        return withContext(Dispatchers.IO) {
            val file = runCatching { writeToCache(sheet, name) }.getOrElse {
                return@withContext "Couldn't create the file — ${it.message ?: "unknown error"}"
            }
            val savedUri = saveToDownloads(file, name)
            val viewUri = savedUri ?: fileUriOrNull(file)
            val opened = viewUri != null && open(viewUri)
            when {
                savedUri != null && opened -> "Saved to Downloads · opened"
                savedUri != null -> "Saved to Downloads"
                opened -> "Opened (couldn't save to Downloads on this device)"
                else -> "Saved, but no app on this device can open .xlsx"
            }
        }
    }

    private fun writeToCache(sheet: Sheet, name: String): File {
        val dir = File(context.cacheDir, CACHE_DIR)
        if (!dir.exists() && !dir.mkdirs()) error("Failed to create export dir: $dir")
        val file = File(dir, name)
        file.outputStream().use { Xlsx.write(sheet, it) }
        return file
    }

    private fun saveToDownloads(source: File, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, displayName)
            put(MediaStore.Downloads.MIME_TYPE, Xlsx.MIME)
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

    private fun open(uri: Uri): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, Xlsx.MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(view)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    private fun fileUriOrNull(file: File): Uri? = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file.canonicalFile)
    }.getOrNull()

    private companion object {
        const val CACHE_DIR = "reading_exports"
    }
}
