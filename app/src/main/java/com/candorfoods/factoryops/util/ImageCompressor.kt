package com.candorfoods.factoryops.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * Downscales + re-encodes a picked/captured image to a small JPEG so uploads are fast.
 * A full-res phone photo is several MB; a proof/after photo only needs ~1600px, which
 * lands around 200-500 KB — a 10x+ smaller upload (device -> backend -> S3).
 *
 * BitmapFactory does NOT honour EXIF rotation, so we bake the camera orientation into
 * the pixels; otherwise the re-encoded photo would show up sideways.
 */
object ImageCompressor {

    private const val MAX_DIM = 1600
    private const val QUALITY = 80

    /** Compressed JPEG bytes for [uri], or null if it couldn't be read/decoded (caller
     *  should then fall back to the original bytes). */
    fun compressToJpeg(
        context: Context,
        uri: Uri,
        maxDim: Int = MAX_DIM,
        quality: Int = QUALITY,
    ): ByteArray? {
        val source = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (source.isEmpty()) return null

        // 1) read bounds only
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 2) power-of-two downsample while decoding (keeps memory low on big photos)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxDim) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = BitmapFactory.decodeByteArray(source, 0, source.size, decodeOpts) ?: return null

        // 3) fine-scale so the longest side is <= maxDim
        val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height)
        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true,
            )
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }

        // 4) apply EXIF orientation
        bmp = applyExifRotation(source, bmp)

        // 5) encode to JPEG
        return ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            bmp.recycle()
            out.toByteArray()
        }
    }

    private fun applyExifRotation(source: ByteArray, bmp: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.inputStream())
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bmp
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (rotated !== bmp) bmp.recycle()
            rotated
        }.getOrDefault(bmp)
    }
}
