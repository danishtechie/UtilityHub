package com.utilityhub.logic.files

import com.utilityhub.logic.Validation

data class CsvTable(val rows: List<List<String>>, val delimiter: Char, val truncated: Boolean)

object CsvParser {
    const val MAX_CHARS = 2_000_000
    const val MAX_ROWS = 1_000
    const val MAX_COLUMNS = 200

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        return listOf(',', ';', '\t', '|').maxByOrNull { d -> firstLine.count { it == d } }?.takeIf { d -> firstLine.contains(d) } ?: ','
    }

    fun parse(text: String): Validation<CsvTable> {
        if (text.isBlank()) return Validation.Invalid("The file is empty.")
        if (text.length > MAX_CHARS) return Validation.Invalid("The file is larger than the supported preview size.")
        val delimiter = detectDelimiter(text)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var truncated = false
        var i = 0
        fun endCell() { row.add(cell.toString()); cell.setLength(0) }
        fun endRow(): Boolean {
            endCell()
            if (row.size > MAX_COLUMNS) return false
            if (!(row.size == 1 && row[0].isEmpty())) rows.add(row)
            row = ArrayList()
            return true
        }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted -> if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
                } else cell.append(c)
                c == '"' && cell.isEmpty() -> quoted = true
                c == delimiter -> endCell()
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    if (!endRow()) return Validation.Invalid("The file has more than $MAX_COLUMNS columns.")
                    if (rows.size >= MAX_ROWS) { truncated = true; break }
                }
                else -> cell.append(c)
            }
            i++
        }
        if (!truncated && (cell.isNotEmpty() || row.isNotEmpty())) if (!endRow()) return Validation.Invalid("The file has more than $MAX_COLUMNS columns.")
        if (quoted) return Validation.Invalid("A quoted value is never closed - the file may be damaged.")
        return if (rows.isEmpty()) Validation.Invalid("No rows were found.") else Validation.Valid(CsvTable(rows, delimiter, truncated))
    }
}

/** Batch rename patterns: {name} {ext} {n} {n:3} (zero padded counter). */
object RenamePattern {
    private val counter = Regex("\\{n(?::(\\d{1,2}))?\\}")

    fun validate(pattern: String): Validation<String> {
        if (pattern.isBlank()) return Validation.Invalid("Enter a name pattern, for example holiday_{n:3}.")
        if (pattern.length > 100) return Validation.Invalid("The pattern is too long.")
        return Validation.Valid(pattern)
    }

    fun apply(pattern: String, originalName: String, index: Int): String {
        val base = FileNames.baseName(originalName)
        val ext = FileNames.extension(originalName)
        var out = counter.replace(pattern) { m -> index.toString().padStart(m.groupValues[1].toIntOrNull() ?: 1, '0') }
        out = out.replace("{name}", base).replace("{ext}", ext)
        if (!pattern.contains("{ext}") && ext.isNotEmpty()) out += ".$ext"
        return FileNames.sanitize(out, "file")
    }
}
