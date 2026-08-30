package com.f3.workouttimer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

/**
 * The PAX photos shown behind the splash message. Photos the user picks from
 * their gallery are copied in here; anything shipped in assets/pax is used as
 * a fallback so a fresh install still has something to show.
 */
object PaxPhotoStore {

    private const val DIR = "pax"
    private const val MAX_EDGE = 1600
    private val EXTENSIONS = listOf(".jpg", ".jpeg", ".png", ".webp")

    private fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun photos(context: Context): List<File> =
        runCatching {
            dir(context).listFiles()
                ?.filter { file -> EXTENSIONS.any { file.name.lowercase().endsWith(it) } }
                ?.sortedBy { it.name }
                .orEmpty()
        }.getOrDefault(emptyList())

    fun count(context: Context): Int = photos(context).size

    /** Copies a picked image in, upright and re-encoded down to a sane size. */
    fun add(context: Context, uri: Uri): Boolean = runCatching {
        val open = { context.contentResolver.openInputStream(uri) as InputStream }
        val decoded = decodeSampled(open, MAX_EDGE) ?: return false
        // BitmapFactory ignores EXIF, so phone photos arrive sideways without this.
        val bitmap = applyExifRotation(decoded, open)
        val target = File(dir(context), "pax_${System.currentTimeMillis()}_${uri.hashCode()}.jpg")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        true
    }.getOrDefault(false)

    private fun applyExifRotation(bitmap: Bitmap, open: () -> InputStream): Bitmap {
        val degrees = runCatching {
            val orientation = open().use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it != bitmap) bitmap.recycle() }
        }.getOrDefault(bitmap)
    }

    fun clear(context: Context) {
        runCatching { photos(context).forEach { it.delete() } }
    }

    private fun bundled(context: Context): List<String> = runCatching {
        context.assets.list(DIR).orEmpty()
            .filter { name -> EXTENSIONS.any { name.lowercase().endsWith(it) } }
    }.getOrDefault(emptyList())

    /** A random photo from everything available — the app's own and the user's. */
    fun randomBitmap(context: Context): Bitmap? {
        val sources: List<() -> InputStream> =
            photos(context).map { file -> { file.inputStream() } } +
                bundled(context).map { name -> { context.assets.open("$DIR/$name") } }
        return sources.randomOrNull()?.let { decodeSampled(it, MAX_EDGE) }
    }

    /** Decodes at roughly the size the screen needs, so big camera shots don't blow up memory. */
    private fun decodeSampled(open: () -> InputStream, maxEdge: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest > 0 && longest / sample > maxEdge) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        open().use { BitmapFactory.decodeStream(it, null, options) }
    }.getOrNull()
}
