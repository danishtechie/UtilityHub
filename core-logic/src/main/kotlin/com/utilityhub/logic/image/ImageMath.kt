package com.utilityhub.logic.image

import com.utilityhub.logic.Validation
import kotlin.math.roundToInt

data class PixelSize(val width: Int, val height: Int) {
    val pixels: Long get() = width.toLong() * height.toLong()
    override fun toString() = "$width × $height px"
}

object ImageMath {
    const val MAX_DIMENSION = 16_384
    const val MAX_OUTPUT_PIXELS = 40_000_000L

    /** Resolves the requested output size, validating ranges and the memory budget. */
    fun resolveTarget(original: PixelSize, width: Int?, height: Int?, keepAspect: Boolean): Validation<PixelSize> {
        if (original.width <= 0 || original.height <= 0) return Validation.Invalid("The source image has no valid dimensions.")
        if (width == null && height == null) return Validation.Invalid("Enter a width, a height, or both.")
        if (width != null && (width < 1 || width > MAX_DIMENSION)) {
            return Validation.Invalid("Width must be between 1 and $MAX_DIMENSION pixels.")
        }
        if (height != null && (height < 1 || height > MAX_DIMENSION)) {
            return Validation.Invalid("Height must be between 1 and $MAX_DIMENSION pixels.")
        }
        val result = if (keepAspect) {
            when {
                width != null && height != null -> {
                    val scale = minOf(width.toDouble() / original.width, height.toDouble() / original.height)
                    PixelSize(
                        (original.width * scale).roundToInt().coerceAtLeast(1),
                        (original.height * scale).roundToInt().coerceAtLeast(1),
                    )
                }
                width != null -> PixelSize(width, (width.toDouble() * original.height / original.width).roundToInt().coerceAtLeast(1))
                else -> PixelSize((height!!.toDouble() * original.width / original.height).roundToInt().coerceAtLeast(1), height)
            }
        } else {
            PixelSize(width ?: original.width, height ?: original.height)
        }
        if (result.width > MAX_DIMENSION || result.height > MAX_DIMENSION) {
            return Validation.Invalid("The resulting size ($result) exceeds the $MAX_DIMENSION px limit.")
        }
        if (result.pixels > MAX_OUTPUT_PIXELS) {
            return Validation.Invalid("The resulting size ($result) would need too much memory on this device. Choose a smaller size.")
        }
        return Validation.Valid(result)
    }

    /** Largest power-of-two sample size that still leaves the decoded image at least the target size. */
    fun inSampleSize(original: PixelSize, targetWidth: Int, targetHeight: Int): Int {
        var sample = 1
        while (original.width / (sample * 2) >= targetWidth && original.height / (sample * 2) >= targetHeight) sample *= 2
        return sample
    }

    /** Sample size so that the longest edge of the decoded image is not much larger than [maxEdge]. */
    fun previewSampleSize(original: PixelSize, maxEdge: Int): Int {
        var sample = 1
        while (maxOf(original.width, original.height) / (sample * 2) >= maxEdge) sample *= 2
        return sample
    }

    /** Positive = smaller output. Negative = the output grew. */
    fun reductionPercent(originalBytes: Long, outputBytes: Long): Double =
        if (originalBytes <= 0L) 0.0 else (1.0 - outputBytes.toDouble() / originalBytes.toDouble()) * 100.0
}
