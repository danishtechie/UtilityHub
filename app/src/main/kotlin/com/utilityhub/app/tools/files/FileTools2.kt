package com.utilityhub.app.tools.files

import android.webkit.MimeTypeMap
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.Validation
import com.utilityhub.logic.archive.UniqueNames
import com.utilityhub.logic.files.CsvParser
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.files.RenamePattern
import com.utilityhub.logic.spec.FieldSpec
import java.security.MessageDigest

class CsvViewerTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "application/octet-stream")
    override val pickerLabel = "Choose a CSV file"

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose a file first.")
        val bytes = ctx.io.open(input.uri).use { s ->
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(32 * 1024)
            while (buf.size() <= CsvParser.MAX_CHARS * 2) { val n = s.read(chunk); if (n < 0) break; buf.write(chunk, 0, n) }
            buf.toByteArray()
        }
        val text = String(bytes, Charsets.UTF_8).removePrefix("\uFEFF").take(CsvParser.MAX_CHARS)
        if (text.contains('\u0000')) return ProcessingResult.Unsupported("This looks like a binary file, not a CSV text file.")
        val table = when (val r = CsvParser.parse(text)) { is Validation.Valid -> r.value; is Validation.Invalid -> return ProcessingResult.ValidationFailure(r.message) }
        val columns = table.rows.maxOf { it.size }
        val shown = table.rows.take(200).joinToString("\n") { row -> row.joinToString("  |  ") { it.take(60) } }
        val delimiterName = when (table.delimiter) { '\t' -> "tab"; ',' -> "comma"; ';' -> "semicolon"; else -> "pipe" }
        return ProcessingResult.Success(ToolOutput(
            "Showing ${minOf(200, table.rows.size)} of ${table.rows.size}${if (table.truncated) "+" else ""} rows",
            listOf("Columns" to columns.toString(), "Delimiter" to delimiterName),
            text = shown,
        ))
    }
}

class DuplicateFinderTool(private val maxFiles: Int) : FileTool {
    override val inputRequirement = InputRequirement.MULTIPLE
    override val acceptedMimeTypes = arrayOf("*/*")
    override val pickerLabel = "Choose files to compare"
    override val maxInputs get() = maxFiles

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        if (ctx.inputs.size < 2) return ProcessingResult.ValidationFailure("Choose at least two files.")
        val byHash = LinkedHashMap<String, MutableList<Pair<String, Long>>>()
        ctx.inputs.forEachIndexed { i, f ->
            ctx.checkCancelled()
            ctx.reportProgress(i.toFloat() / ctx.inputs.size)
            val md = MessageDigest.getInstance("SHA-256")
            var size = 0L
            ctx.io.open(f.uri).use { s -> val b = ByteArray(128 * 1024); while (true) { ctx.checkCancelled(); val n = s.read(b); if (n < 0) break; md.update(b, 0, n); size += n } }
            byHash.getOrPut(md.digest().joinToString("") { "%02x".format(it) } + ":$size") { ArrayList() }.add(f.name to size)
        }
        val groups = byHash.values.filter { it.size > 1 }
        if (groups.isEmpty()) return ProcessingResult.Success(ToolOutput("No duplicates found among ${ctx.inputs.size} files. Files are compared by their full content (SHA-256), not by name."))
        val reclaimable = groups.sumOf { g -> g[0].second * (g.size - 1) }
        val report = groups.mapIndexed { i, g -> "Group ${i + 1} (${FileNames.humanSize(g[0].second)} each):\n" + g.joinToString("\n") { "  • ${it.first}" } }.joinToString("\n\n")
        return ProcessingResult.Success(ToolOutput("${groups.size} group(s) of identical files", listOf("Space that could be freed" to FileNames.humanSize(reclaimable), "Note" to "Nothing was deleted. Remove duplicates yourself in your file manager."), text = report))
    }
}

/** Android's picker does not give safe rename access to originals, so this creates renamed COPIES; originals are untouched. */
class BatchRenameTool(private val maxFiles: Int) : FileTool {
    override val inputRequirement = InputRequirement.MULTIPLE
    override val acceptedMimeTypes = arrayOf("*/*")
    override val pickerLabel = "Choose files to rename"
    override val maxInputs get() = maxFiles
    override val reorderable = true
    override val fields = listOf(
        FieldSpec.Text("pattern", "New name pattern", "file_{n:3}", hint = "{name} {ext} {n} {n:3}"),
        FieldSpec.Text("start", "First number", "1"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        if (ctx.inputs.isEmpty()) return ProcessingResult.ValidationFailure("Choose at least one file.")
        val pattern = when (val p = RenamePattern.validate(ctx.values.string("pattern"))) { is Validation.Valid -> p.value; is Validation.Invalid -> return ProcessingResult.ValidationFailure(p.message) }
        val start = ctx.values.string("start", "1").trim().toIntOrNull()?.takeIf { it in 0..99_999 } ?: return ProcessingResult.ValidationFailure("The first number must be between 0 and 99999.")
        if (ctx.inputs.sumOf { it.sizeBytes.coerceAtLeast(0) } > Limits.ZIP_INPUT_TOTAL_BYTES) return ProcessingResult.ValidationFailure("The files are larger than ${FileNames.humanSize(Limits.ZIP_INPUT_TOTAL_BYTES)} in total.")
        val names = UniqueNames()
        val outputs = ArrayList<OutputFile>()
        try {
            ctx.inputs.forEachIndexed { i, f ->
                ctx.checkCancelled()
                ctx.reportProgress(i.toFloat() / ctx.inputs.size)
                val newName = names.claim(RenamePattern.apply(pattern, f.name, start + i))
                val target = ctx.newOutputFile(newName)
                ctx.io.copyToFile(f.uri, target, Limits.ZIP_INPUT_TOTAL_BYTES, ctx)
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(FileNames.extension(newName)) ?: "application/octet-stream"
                outputs += OutputFile(target, newName, mime)
            }
        } catch (t: Throwable) {
            outputs.forEach { it.file.delete() }
            throw t
        }
        val preview = ctx.inputs.zip(outputs).take(5).joinToString("\n") { (a, b) -> "${a.name}  →  ${b.displayName}" }
        return ProcessingResult.Success(ToolOutput("Created ${outputs.size} renamed copies. Your originals are unchanged.", listOf("Preview" to preview), outputs))
    }
}
