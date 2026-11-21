package com.wolnerman.calories

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** Photos de repas stockées dans le stockage privé de l'appli (filesDir/photos), sans permission. */
object Photos {
    private const val MAX_SIDE = 1280
    private const val QUALITY = 80

    private fun dir(context: Context) = File(context.filesDir, "photos").apply { mkdirs() }

    fun newFile(context: Context): File = File(dir(context), "repas_${System.currentTimeMillis()}.jpg")

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /** Copie une image choisie dans la galerie vers le stockage privé, puis la compresse. */
    fun importFromUri(context: Context, uri: Uri): File? = try {
        val f = newFile(context)
        context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
        if (f.length() > 0 && compress(f)) f else { f.delete(); null }
    } catch (e: Exception) { null }

    /** Redimensionne (côté max 1280 px), corrige la rotation EXIF et réenregistre en JPEG 80 %. */
    fun compress(file: File): Boolean = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) false else {
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            var bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })!!
            val scale = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
            val rotation = when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (scale < 1f || rotation != 0f) {
                val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rotation != 0f) postRotate(rotation) }
                val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (out != bmp) bmp.recycle()
                bmp = out
            }
            FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            bmp.recycle()
            true
        }
    } catch (e: Exception) { false }

    /** Charge une miniature (ou l'image entière si maxSide est grand) en mémoire réduite. */
    fun load(path: String, maxSide: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) { null }

    fun delete(path: String?) { if (path != null) runCatching { File(path).delete() } }
}
