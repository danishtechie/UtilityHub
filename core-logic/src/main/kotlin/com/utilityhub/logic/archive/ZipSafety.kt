package com.utilityhub.logic.archive

data class ArchiveLimits(
    val maxEntries: Int = 2_000,
    val maxEntryBytes: Long = 256L * 1024 * 1024,
    val maxTotalBytes: Long = 1024L * 1024 * 1024,
    val maxCompressionRatio: Long = 200,
    val ratioCheckFloorBytes: Long = 16L * 1024 * 1024,
    val maxDepth: Int = 16,
    val maxSegmentLength: Int = 200,
    val maxArchiveBytes: Long = 512L * 1024 * 1024,
)

sealed interface EntryNameCheck {
    data class Safe(val segments: List<String>) : EntryNameCheck {
        val path: String get() = segments.joinToString("/")
    }

    data class Unsafe(val reason: String) : EntryNameCheck
}

/** Zip Slip / path traversal defence. Entry names are untrusted input. */
object ZipEntryNames {
    private val driveLetter = Regex("^[A-Za-z]:")

    fun check(rawName: String, limits: ArchiveLimits = ArchiveLimits()): EntryNameCheck {
        if (rawName.isBlank()) return EntryNameCheck.Unsafe("empty entry name")
        if (rawName.any { it == '\u0000' || it.code < 0x20 }) return EntryNameCheck.Unsafe("control characters in entry name")
        if (rawName.contains('\\')) return EntryNameCheck.Unsafe("backslash in entry name")
        if (rawName.startsWith("/")) return EntryNameCheck.Unsafe("absolute path")
        if (driveLetter.containsMatchIn(rawName)) return EntryNameCheck.Unsafe("drive-letter path")
        val segments = rawName.split('/').filter { it.isNotEmpty() && it != "." }
        if (segments.isEmpty()) return EntryNameCheck.Unsafe("empty entry name")
        if (segments.any { it == ".." }) return EntryNameCheck.Unsafe("path traversal")
        if (segments.size > limits.maxDepth) return EntryNameCheck.Unsafe("path nested too deeply")
        if (segments.any { it.length > limits.maxSegmentLength }) return EntryNameCheck.Unsafe("name too long")
        return EntryNameCheck.Safe(segments)
    }
}

/**
 * Tracks entries and *actual* decompressed bytes while streaming. Declared sizes in a ZIP are
 * attacker-controlled, so only bytes really produced are trusted.
 * Methods return a violation message, or null when everything is within limits.
 */
class ExtractionGuard(private val limits: ArchiveLimits, private val archiveSizeBytes: Long) {
    private var entries = 0
    private var total = 0L

    val totalBytes: Long get() = total
    val entryCount: Int get() = entries

    fun checkArchiveSize(): String? =
        if (archiveSizeBytes > limits.maxArchiveBytes) "Archive is larger than the supported limit." else null

    fun startEntry(): String? {
        entries++
        return if (entries > limits.maxEntries) "Archive contains more than ${limits.maxEntries} entries." else null
    }

    fun addBytes(entryBytesSoFar: Long, delta: Long): String? {
        total += delta
        if (entryBytesSoFar > limits.maxEntryBytes) return "A file in the archive expands beyond the per-file limit."
        if (total > limits.maxTotalBytes) return "Archive expands beyond the total size limit."
        if (archiveSizeBytes > 0 && total > limits.ratioCheckFloorBytes &&
            total > archiveSizeBytes * limits.maxCompressionRatio
        ) return "Compression ratio is suspiciously high (possible archive bomb)."
        return null
    }
}

/** Case-insensitive unique naming: "a.txt" -> "a (1).txt". Works on paths with '/'. */
class UniqueNames {
    private val used = HashSet<String>()

    fun claim(path: String): String {
        if (used.add(path.lowercase())) return path
        val slash = path.lastIndexOf('/')
        val dir = if (slash >= 0) path.substring(0, slash + 1) else ""
        val file = path.substring(slash + 1)
        val dot = file.lastIndexOf('.')
        val base = if (dot > 0) file.substring(0, dot) else file
        val ext = if (dot > 0) file.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = "$dir$base ($n)$ext"
            if (used.add(candidate.lowercase())) return candidate
            n++
        }
    }
}
