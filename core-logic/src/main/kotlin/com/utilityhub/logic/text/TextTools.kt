package com.utilityhub.logic.text

import java.util.Locale

data class TextStats(val characters: Int, val charactersNoSpaces: Int, val words: Int, val lines: Int)

enum class CaseMode { UPPER, LOWER, TITLE, SENTENCE }

data class DedupeResult(val text: String, val removed: Int)

object TextTools {
    const val MAX_CHARS = 2_000_000

    fun stats(text: String): TextStats {
        val words = if (text.isBlank()) 0 else text.trim().split(Regex("\\s+")).size
        val lines = if (text.isEmpty()) 0 else text.replace("\r\n", "\n").split('\n').size
        return TextStats(text.codePointCount(0, text.length), text.count { !it.isWhitespace() }, words, lines)
    }

    fun convertCase(text: String, mode: CaseMode): String = when (mode) {
        CaseMode.UPPER -> text.uppercase(Locale.ROOT)
        CaseMode.LOWER -> text.lowercase(Locale.ROOT)
        CaseMode.TITLE -> {
            val sb = StringBuilder(text.length)
            var wordStart = true
            for (c in text) {
                if (c.isWhitespace()) { sb.append(c); wordStart = true }
                else { sb.append(if (wordStart && c.isLetter()) c.uppercaseChar() else c.lowercaseChar()); wordStart = false }
            }
            sb.toString()
        }
        CaseMode.SENTENCE -> {
            val sb = StringBuilder(text.length)
            var capitalizeNext = true
            for (c in text.lowercase(Locale.ROOT)) {
                if (capitalizeNext && c.isLetter()) { sb.append(c.uppercaseChar()); capitalizeNext = false }
                else {
                    sb.append(c)
                    if (c == '.' || c == '!' || c == '?' || c == '\n') capitalizeNext = true
                    else if (c.isLetterOrDigit()) capitalizeNext = false
                }
            }
            sb.toString()
        }
    }

    private fun lines(text: String) = text.replace("\r\n", "\n").split('\n')

    fun dedupeLines(text: String, ignoreCase: Boolean, trimWhitespace: Boolean): DedupeResult {
        val seen = HashSet<String>()
        val kept = ArrayList<String>()
        for (line in lines(text)) {
            var key = if (trimWhitespace) line.trim() else line
            if (ignoreCase) key = key.lowercase(Locale.ROOT)
            if (seen.add(key)) kept += line
        }
        return DedupeResult(kept.joinToString("\n"), lines(text).size - kept.size)
    }

    fun sortLines(text: String, descending: Boolean, ignoreCase: Boolean, numeric: Boolean): String {
        val base: Comparator<String> = if (numeric) {
            Comparator { a, b ->
                val x = a.trim().toDoubleOrNull()
                val y = b.trim().toDoubleOrNull()
                when {
                    x != null && y != null -> x.compareTo(y)
                    x != null -> -1
                    y != null -> 1
                    else -> a.compareTo(b, ignoreCase)
                }
            }
        } else Comparator { a, b -> a.compareTo(b, ignoreCase) }
        val sorted = lines(text).sortedWith(base)
        return (if (descending) sorted.reversed() else sorted).joinToString("\n")
    }
}
