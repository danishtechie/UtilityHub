package com.utilityhub.logic.files

enum class DetectedType(val label: String) {
    JPEG("JPEG image"), PNG("PNG image"), GIF("GIF image"), WEBP("WebP image"), BMP("BMP image"),
    PDF("PDF document"), ZIP("ZIP archive"), MP4("MP4 / MOV / M4A media"), MATROSKA("Matroska / WebM media"),
    OGG("Ogg media"), MP3("MP3 audio"), WAV("WAV audio"), UNKNOWN("Unknown type"),
}

/** Detects a file type from its first bytes. Extensions and MIME types supplied by apps are not trusted. */
object ContentSniffer {
    const val HEADER_BYTES = 1024

    private fun starts(h: ByteArray, vararg b: Int, at: Int = 0): Boolean {
        if (h.size < at + b.size) return false
        for (i in b.indices) if ((h[at + i].toInt() and 0xFF) != b[i]) return false
        return true
    }

    private fun ascii(h: ByteArray, text: String, at: Int): Boolean {
        if (h.size < at + text.length) return false
        for (i in text.indices) if (h[at + i].toInt().toChar() != text[i]) return false
        return true
    }

    fun detect(h: ByteArray): DetectedType = when {
        starts(h, 0xFF, 0xD8, 0xFF) -> DetectedType.JPEG
        starts(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) -> DetectedType.PNG
        ascii(h, "GIF87a", 0) || ascii(h, "GIF89a", 0) -> DetectedType.GIF
        ascii(h, "RIFF", 0) && ascii(h, "WEBP", 8) -> DetectedType.WEBP
        ascii(h, "RIFF", 0) && ascii(h, "WAVE", 8) -> DetectedType.WAV
        ascii(h, "BM", 0) && h.size > 14 -> DetectedType.BMP
        pdfMarker(h) -> DetectedType.PDF
        starts(h, 0x50, 0x4B, 0x03, 0x04) || starts(h, 0x50, 0x4B, 0x05, 0x06) -> DetectedType.ZIP
        ascii(h, "ftyp", 4) -> DetectedType.MP4
        starts(h, 0x1A, 0x45, 0xDF, 0xA3) -> DetectedType.MATROSKA
        ascii(h, "OggS", 0) -> DetectedType.OGG
        ascii(h, "ID3", 0) || (starts(h, 0xFF) && h.size > 1 && (h[1].toInt() and 0xE0) == 0xE0) -> DetectedType.MP3
        else -> DetectedType.UNKNOWN
    }

    /** PDF readers accept the "%PDF-" marker anywhere in the first 1024 bytes. */
    private fun pdfMarker(h: ByteArray): Boolean {
        val limit = minOf(h.size, HEADER_BYTES) - 5
        for (i in 0..limit) if (ascii(h, "%PDF-", i)) return true
        return false
    }

    fun isImage(t: DetectedType) = t == DetectedType.JPEG || t == DetectedType.PNG || t == DetectedType.GIF ||
        t == DetectedType.WEBP || t == DetectedType.BMP
}
