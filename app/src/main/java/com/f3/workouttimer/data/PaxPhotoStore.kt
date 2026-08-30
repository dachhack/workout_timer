package com.f3.workouttimer.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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

    /** Copies a picked image in, re-encoded down to a sane size for a splash. */
    fun add(context: Context, uri: Uri): Boolean = runCatching {
        val open = { context.contentResolver.openInputStream(uri) as InputStream }
        val bitmap = decodeSampled(open, MAX_EDGE) ?: return false
        val target = File(dir(context), "pax_${System.currentTimeMillis()}_${uri.hashCode()}.jpg")
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        true
    }.getOrDefault(false)

    fun clear(context: Context) {
        runCatching { photos(context).forEach { it.delete() } }
    }

    /** A random photo, preferring the user's own over anything bundled in assets. */
    fun randomBitmap(context: Context): Bitmap? {
        photos(context).randomOrNull()?.let { file ->
            decodeSampled({ file.inputStream() }, MAX_EDGE)?.let { return it }
        }
        val bundled = runCatching {
            context.assets.list(DIR).orEmpty()
                .filter { name -> EXTENSIONS.any { name.lowercase().endsWith(it) } }
        }.getOrDefault(emptyList())
        val chosen = bundled.randomOrNull() ?: return null
        return decodeSampled({ context.assets.open("$DIR/$chosen") }, MAX_EDGE)
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
