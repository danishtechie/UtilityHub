package com.utilityhub.logic.media

import com.utilityhub.logic.Validation
import java.util.Locale

object MediaMath {
    const val MIN_SELECTION_MS = 100L

    /** @return the selected length in milliseconds. */
    fun validateTrim(startMs: Long, endMs: Long, durationMs: Long): Validation<Long> {
        if (durationMs <= 0) return Validation.Invalid("The media duration is unknown.")
        if (startMs < 0) return Validation.Invalid("Start cannot be negative.")
        if (endMs > durationMs) return Validation.Invalid("End is beyond the end of the media (${formatDuration(durationMs)}).")
        if (endMs <= startMs) return Validation.Invalid("End must be after start.")
        if (endMs - startMs < MIN_SELECTION_MS) return Validation.Invalid("The selection is too short.")
        return Validation.Valid(endMs - startMs)
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms.coerceAtLeast(0) + 500) / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%02d:%02d", m, s)
    }

    /** Progress in 0..1 from a sample timestamp, or null when the duration is unknown (never invent progress). */
    fun progress(sampleTimeUs: Long, durationUs: Long): Float? =
        if (durationUs <= 0 || sampleTimeUs < 0) null else (sampleTimeUs.toDouble() / durationUs).coerceIn(0.0, 1.0).toFloat()

    /** Accepts "90", "1:30", "01:02:03", "1:30.5" and returns milliseconds. */
    fun parseTimestamp(text: String): Validation<Long> {
        val parts = text.trim().split(':')
        if (parts.isEmpty() || parts.size > 3 || parts.any { it.isBlank() }) return Validation.Invalid("Use seconds (90) or mm:ss (1:30).")
        var seconds = 0.0
        for (part in parts) {
            val v = part.toDoubleOrNull()
            if (v == null || v < 0 || v.isNaN() || v.isInfinite()) return Validation.Invalid("\"$text\" is not a valid time.")
            seconds = seconds * 60 + v
        }
        return Validation.Valid((seconds * 1000).toLong())
    }
}
