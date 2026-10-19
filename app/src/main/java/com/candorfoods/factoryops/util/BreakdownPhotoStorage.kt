package com.candorfoods.factoryops.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

object BreakdownPhotoStorage {

    private const val DIR_BEFORE = "breakdown_before"
    private const val DIR_AFTER = "breakdown_after"
    private const val DIR_QC    = "breakdown_qc"

    /**
     * The operator's photo of the fault, captured while raising the flag. Keyed on
     * the machine rather than a ticket id because at this point the ticket does not
     * exist yet — the photo is taken before the flag is submitted.
     */
    fun createBeforePhotoFile(context: Context, machineId: String): File =
        createPhotoFile(context, DIR_BEFORE, machineId)

    fun createAfterPhotoFile(context: Context, ticketId: String): File =
        createPhotoFile(context, DIR_AFTER, ticketId)

    fun createQcPhotoFile(context: Context, ticketId: String): File =
        createPhotoFile(context, DIR_QC, ticketId)

    private fun createPhotoFile(context: Context, subdir: String, ticketId: String): File {
        val dir = File(context.filesDir, subdir).apply { if (!exists()) mkdirs() }
        val safeId = ticketId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "$safeId-${System.currentTimeMillis()}.jpg")
    }

    fun fileToShareUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        // canonicalFile resolves /data/data → /data/user/0 so FileProvider's
        // literal prefix match against <files-path> succeeds for paths stored
        // by older seed data.
        return FileProvider.getUriForFile(context, authority, file.canonicalFile)
    }
}

/**
 * Phase 2C-2 — photo storage for PM execution. Mirrors BreakdownPhotoStorage but
 * scopes paths under "pm_<woId>_<itemId>_<ts>.jpg" so the file system can be cleaned
 * by WO once the WO is closed (Phase 3 sync will upload + purge).
 */
object PmPhotoStorage {
    private const val DIR = "pm_photos"

    fun createItemPhotoFile(context: Context, woId: String, itemId: String): File {
        val dir = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }
        val safeWo = woId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeItem = itemId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(dir, "${safeWo}_${safeItem}_${System.currentTimeMillis()}.jpg")
    }

    fun fileToShareUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file.canonicalFile)
    }
}

/** Photo storage for the Machine Transfer proof image (live camera capture). */
object TransferPhotoStorage {
    private const val DIR = "transfer_photos"

    fun createPhotoFile(context: Context): File {
        val dir = File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }
        return File(dir, "transfer-${System.currentTimeMillis()}.jpg")
    }

    fun fileToShareUri(context: Context, file: File): Uri {
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file.canonicalFile)
    }
}
