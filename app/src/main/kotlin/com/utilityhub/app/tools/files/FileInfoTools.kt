package com.utilityhub.app.tools.files

import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.files.HashTools
import com.utilityhub.logic.spec.FieldSpec
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date

class FileInfoTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("*/*")
    override val pickerLabel = "Choose a file"

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose a file first.")
        val detected = ctx.io.detect(input.uri)
        val rows = ArrayList<Pair<String, String>>()
        rows += "Name" to input.name
        rows += "Extension" to FileNames.extension(input.name).ifEmpty { "(none)" }
        if (input.sizeBytes >= 0) rows += "Size" to "${FileNames.humanSize(input.sizeBytes)} (${input.sizeBytes} bytes)"
        rows += "Type reported by the sharing app" to (input.mimeType ?: "unknown")
        rows += "Type detected from content" to detected.label
        ctx.io.lastModified(input.uri)?.let { rows += "Last modified" to DateFormat.getDateTimeInstance().format(Date(it)) }
        return ProcessingResult.Success(ToolOutput("File details", rows))
    }
}

class FileHashTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("*/*")
    override val pickerLabel = "Choose a file"
    override val fields = listOf(FieldSpec.Text("expected", "Expected checksum (optional)", hint = "Paste an MD5, SHA-1 or SHA-256 to verify"))

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose a file first.")
        val md5 = MessageDigest.getInstance("MD5")
        val sha1 = MessageDigest.getInstance("SHA-1")
        val sha256 = MessageDigest.getInstance("SHA-256")
        var read = 0L
        ctx.io.open(input.uri).use { stream ->
            val buf = ByteArray(128 * 1024)
            while (true) {
                ctx.checkCancelled()
                val n = stream.read(buf)
                if (n < 0) break
                md5.update(buf, 0, n); sha1.update(buf, 0, n); sha256.update(buf, 0, n)
                read += n
                ctx.reportProgress(if (input.sizeBytes > 0) (read.toFloat() / input.sizeBytes).coerceIn(0f, 1f) else null)
            }
        }
        fun hex(d: MessageDigest) = d.digest().joinToString("") { "%02x".format(it) }
        val m = hex(md5); val s1 = hex(sha1); val s256 = hex(sha256)
        val rows = arrayListOf("SHA-256" to s256, "SHA-1" to s1, "MD5" to m, "Bytes hashed" to read.toString())
        val expected = ctx.values.string("expected")
        val summary = if (expected.isBlank()) "Checksums calculated."
        else when (val cmp = HashTools.compare(expected, m, s1, s256)) {
            null -> "Checksums calculated. The expected value is not a valid MD5, SHA-1 or SHA-256 checksum."
            else -> if (cmp.matches) "Match: the file's ${cmp.algorithm} equals the expected checksum." else "MISMATCH: the file's ${cmp.algorithm} does not equal the expected checksum. The file may be corrupted or altered."
        }
        return ProcessingResult.Success(ToolOutput(summary, rows))
    }
}
