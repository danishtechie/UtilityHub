package com.utilityhub.logic.files

import java.util.Locale

object FileNames {
    private val illegal = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")

    fun sanitize(name: String, fallback: String = "file"): String {
        val cleaned = name.replace(illegal, "_").trim().trimStart('.').take(100).trim()
        return cleaned.ifEmpty { fallback }
    }

    fun baseName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1).lowercase(Locale.ROOT) else ""
    }

    /** photo.jpg + ("resized","png") -> photo_resized.png */
    fun outputName(inputName: String, suffix: String, newExtension: String): String {
        val base = sanitize(baseName(inputName), "output")
        return "${base}_$suffix.$newExtension"
    }

    fun humanSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = -1
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }
}
