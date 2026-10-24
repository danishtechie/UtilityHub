package com.utilityhub.app.core

/** Central, easy-to-tune safety limits (bytes unless noted). */
object Limits {
    const val IMAGE_INPUT_BYTES = 100L * 1024 * 1024
    const val DECODE_MAX_PIXELS = 40_000_000L
    const val PDF_INPUT_BYTES = 80L * 1024 * 1024
    const val PDF_TOTAL_MERGE_BYTES = 120L * 1024 * 1024
    const val PDF_MAX_OUTPUT_FILES = 200
    const val ZIP_INPUT_TOTAL_BYTES = 1024L * 1024 * 1024
    const val VIDEO_INPUT_BYTES = 8L * 1024 * 1024 * 1024
    const val TEXT_OUTPUT_MAX_CHARS = 200_000
    const val QR_MAX_CHARS = 2_000
    const val MIN_FREE_CACHE_BYTES = 32L * 1024 * 1024
    const val TEMP_MAX_AGE_MS = 24L * 60 * 60 * 1000
}
