package com.utilityhub.app.tools.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.utilityhub.app.core.FileAccess
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.UnsupportedContentException
import com.utilityhub.logic.files.DetectedType
import com.utilityhub.logic.image.ImageMath
import com.utilityhub.logic.image.PixelSize
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

enum class ImageFormat(val label: String, val mime: String, val extension: String) {
    JPEG("JPEG", "image/jpeg", "jpg"),
    PNG("PNG", "image/png", "png"),
    WEBP("WebP", "image/webp", "webp");

    @Suppress("DEPRECATION")
    fun compressFormat(): Bitmap.CompressFormat = when (this) {
        JPEG -> Bitmap.CompressFormat.JPEG
        PNG -> Bitmap.CompressFormat.PNG
        WEBP -> if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    }

    companion object {
        const val SAME_AS_SOURCE = "Same as source"

        /** "Same as source" keeps JPEG/PNG/WebP and falls back to PNG (lossless) for other inputs. */
        fun resolve(choice: String, detected: DetectedType): ImageFormat = when (choice) {
            "JPEG" -> JPEG
            "PNG" -> PNG
            "WebP" -> WEBP
            else -> when (detected) {
                DetectedType.JPEG -> JPEG
                DetectedType.WEBP -> WEBP
                else -> PNG
            }
        }
    }
}

object ImageCodec {
    fun readBounds(io: FileAccess, uri: Uri): PixelSize? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        io.open(uri).use { BitmapFactory.decodeStream(it, null, options) }
        return if (options.outWidth > 0 && options.outHeight > 0) PixelSize(options.outWidth, options.outHeight) else null
    }

    fun readOrientation(io: FileAccess, uri: Uri): Int = try {
        io.open(uri).use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    private fun swapsAxes(orientation: Int) = orientation in 5..8

    /** Size as the user sees it (after EXIF rotation). */
    fun displayedSize(io: FileAccess, uri: Uri, orientation: Int): PixelSize? =
        readBounds(io, uri)?.let { if (swapsAxes(orientation)) PixelSize(it.height, it.width) else it }

    /**
     * Decodes with sub-sampling so that huge photos never blow the memory budget, then applies EXIF rotation.
     * If [minWidth]/[minHeight] are given (displayed orientation) the decoded bitmap is never smaller than that.
     */
    fun decode(io: FileAccess, uri: Uri, minWidth: Int = 0, minHeight: Int = 0): Bitmap {
        val bounds = readBounds(io, uri) ?: throw UnsupportedContentException("This image could not be decoded.")
        val orientation = readOrientation(io, uri)
        val (mw, mh) = if (swapsAxes(orientation)) minHeight to minWidth else minWidth to minHeight
        var sample = if (mw > 0 && mh > 0) ImageMath.inSampleSize(bounds, mw, mh) else 1
        while (bounds.pixels / (sample.toLong() * sample) > Limits.DECODE_MAX_PIXELS) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = try {
            io.open(uri).use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: OutOfMemoryError) {
            throw IOException("This image is too large to process on this device.")
        } ?: throw UnsupportedContentException("This image could not be decoded.")
        return applyOrientation(bitmap, orientation)
    }

    fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(-90f)
            else -> return bitmap
        }
        return transform(bitmap, m)
    }

    fun transform(bitmap: Bitmap, matrix: Matrix): Bitmap {
        val result = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (result !== bitmap) bitmap.recycle()
        return result
    }

    fun toGrayscale(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
        Canvas(out).drawBitmap(bitmap, 0f, 0f, paint)
        bitmap.recycle()
        return out
    }

    private fun flattenOnWhite(bitmap: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(bitmap, 0f, 0f, null) }
        return out
    }

    /** Encodes to [file]. EXIF/location metadata is never written, so outputs are privacy-clean. */
    fun save(bitmap: Bitmap, file: File, format: ImageFormat, quality: Int) {
        val toSave = if (format == ImageFormat.JPEG && bitmap.hasAlpha()) flattenOnWhite(bitmap) else bitmap
        try {
            FileOutputStream(file).use { out ->
                if (!toSave.compress(format.compressFormat(), quality.coerceIn(1, 100), out)) {
                    throw IOException("The image could not be encoded.")
                }
            }
        } finally {
            if (toSave !== bitmap) toSave.recycle()
        }
    }

    /** Output validation: the file exists, is non-empty and decodes (bounds only - cheap). */
    fun validateOutput(file: File): PixelSize? {
        if (!file.exists() || file.length() <= 0) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        return if (o.outWidth > 0 && o.outHeight > 0) PixelSize(o.outWidth, o.outHeight) else null
    }
}
