package com.utilityhub.app.tools.pdf

import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputFile
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.Validation
import com.utilityhub.logic.files.DetectedType
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.pdf.PageSelection
import com.utilityhub.logic.spec.FieldSpec
import java.io.File
import java.io.IOException

/*
 * PDF tools use PDFBox-Android (Apache-2.0). They do PAGE MANIPULATION only: merge, split, extract, delete,
 * inspect, text extraction. They do not edit text inside pages, fill forms or run OCR.
 * Originals are never modified: every result is a new file.
 * PDFBox keeps documents in memory, hence the conservative size limits in Limits.
 */

private const val PDF_MIME = "application/pdf"

private class PdfRejected(val result: ProcessingResult<Nothing>) : Exception()

/** Copies the selected PDF into app-private storage (size-limited), verifies it is a PDF and opens it. */
private fun openPdf(ctx: ToolRunContext, input: InputFile, tempDir: File, copyName: String = "src_${System.nanoTime()}.pdf"): PDDocument {
    if (input.sizeBytes > Limits.PDF_INPUT_BYTES) {
        throw PdfRejected(ProcessingResult.ValidationFailure("\"${input.name}\" is larger than ${FileNames.humanSize(Limits.PDF_INPUT_BYTES)}."))
    }
    val type = ctx.io.detect(input.uri)
    if (type != DetectedType.PDF) throw PdfRejected(ProcessingResult.Unsupported("\"${input.name}\" is not a PDF (detected: ${type.label})."))
    tempDir.mkdirs()
    val copy = File(tempDir, copyName)
    ctx.io.copyToFile(input.uri, copy, Limits.PDF_INPUT_BYTES, ctx)
    val doc = try {
        PDDocument.load(copy)
    } catch (e: InvalidPasswordException) {
        throw PdfRejected(ProcessingResult.Unsupported("\"${input.name}\" is password-protected. Password-protected PDFs are not supported in this version."))
    } catch (e: IOException) {
        throw PdfRejected(ProcessingResult.ValidationFailure("\"${input.name}\" is damaged or is not a valid PDF."))
    }
    if (doc.isEncrypted) {
        doc.close()
        throw PdfRejected(ProcessingResult.Unsupported("\"${input.name}\" is encrypted. Encrypted PDFs are not supported in this version."))
    }
    return doc
}

private inline fun guarded(ctx: ToolRunContext, block: (tempDir: File) -> ProcessingResult<ToolOutput>): ProcessingResult<ToolOutput> {
    val temp = File(ctx.sessionDir, "tmp")
    return try {
        block(temp)
    } catch (e: PdfRejected) {
        e.result
    } finally {
        temp.deleteRecursively()
    }
}

private fun savePages(source: PDDocument, pages: List<Int>, target: File) {
    val doc = PDDocument()
    try {
        for (p in pages) doc.importPage(source.getPage(p))
        doc.save(target)
    } finally {
        doc.close()
    }
}

private fun verifyPageCount(file: File, expected: Int): Boolean {
    if (!file.exists() || file.length() <= 0) return false
    return try { PDDocument.load(file).use { it.numberOfPages == expected } } catch (_: Exception) { false }
}

private fun pdfOutput(file: File) = OutputFile(file, file.name, PDF_MIME)

abstract class SinglePdfTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf(PDF_MIME)
    override val pickerLabel = "Choose a PDF"
}

class PdfMergeTool(private val maxFiles: Int) : FileTool {
    override val inputRequirement = InputRequirement.MULTIPLE
    override val acceptedMimeTypes = arrayOf(PDF_MIME)
    override val pickerLabel = "Choose PDFs to merge"
    override val maxInputs get() = maxFiles
    override val reorderable = true

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> = guarded(ctx) { temp ->
        if (ctx.inputs.size < 2) return@guarded ProcessingResult.ValidationFailure("Choose at least two PDFs to merge.")
        if (ctx.inputs.size > maxFiles) return@guarded ProcessingResult.ValidationFailure("At most $maxFiles PDFs can be merged at once.")
        if (ctx.inputs.sumOf { it.sizeBytes.coerceAtLeast(0) } > Limits.PDF_TOTAL_MERGE_BYTES) {
            return@guarded ProcessingResult.ValidationFailure("The PDFs are larger than ${FileNames.humanSize(Limits.PDF_TOTAL_MERGE_BYTES)} in total.")
        }
        var expectedPages = 0
        val ordered = ArrayList<File>()
        for ((i, input) in ctx.inputs.withIndex()) {      // copy in the user's order, validate and count pages
            ctx.checkCancelled()
            val name = "ordered_$i.pdf"
            openPdf(ctx, input, temp, name).use { expectedPages += it.numberOfPages }
            ordered += File(temp, name)
        }
        val out = ctx.newOutputFile("merged.pdf")
        try {
            val merger = PDFMergerUtility()
            ordered.forEach { merger.addSource(it) }
            merger.destinationFileName = out.absolutePath
            merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        } catch (t: Throwable) {
            out.delete()
            throw t
        }
        if (!verifyPageCount(out, expectedPages)) {
            out.delete()
            return@guarded ProcessingResult.Failure("The merged PDF could not be verified, so it was discarded.")
        }
        ProcessingResult.Success(
            ToolOutput("Merged ${ctx.inputs.size} PDFs into one.", listOf("Pages" to expectedPages.toString(), "File size" to FileNames.humanSize(out.length())), listOf(pdfOutput(out))),
        )
    }
}

class PdfSplitTool : SinglePdfTool() {
    override val fields = listOf(FieldSpec.IntValue("every", "Pages per file", 1, 1, 500))

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> = guarded(ctx) { temp ->
        val input = ctx.inputs.firstOrNull() ?: return@guarded ProcessingResult.ValidationFailure("Choose a PDF first.")
        openPdf(ctx, input, temp).use { doc ->
            val chunks = when (val r = PageSelection.chunks(doc.numberOfPages, ctx.values.int("every") ?: 1)) {
                is Validation.Valid -> r.value
                is Validation.Invalid -> return@guarded ProcessingResult.ValidationFailure(r.message)
            }
            if (chunks.size > Limits.PDF_MAX_OUTPUT_FILES) {
                return@guarded ProcessingResult.ValidationFailure("That would create ${chunks.size} files (limit ${Limits.PDF_MAX_OUTPUT_FILES}). Use more pages per file.")
            }
            if (chunks.size == 1) return@guarded ProcessingResult.ValidationFailure("The document has only ${doc.numberOfPages} page(s); choose fewer pages per file to split it.")
            val base = FileNames.sanitize(FileNames.baseName(input.name), "document")
            val outputs = ArrayList<OutputFile>()
            try {
                chunks.forEachIndexed { i, range ->
                    ctx.checkCancelled()
                    ctx.reportProgress(i.toFloat() / chunks.size)
                    val f = ctx.newOutputFile("${base}_part${i + 1}.pdf")
                    savePages(doc, range.toList(), f)
                    if (!verifyPageCount(f, range.count())) return@guarded ProcessingResult.Failure("A split part could not be verified, so the result was discarded.")
                    outputs += pdfOutput(f)
                }
            } catch (t: Throwable) {
                outputs.forEach { it.file.delete() }
                throw t
            }
            ProcessingResult.Success(ToolOutput("Split into ${outputs.size} files.", listOf("Source pages" to doc.numberOfPages.toString()), outputs))
        }
    }
}

class PdfExtractTool : SinglePdfTool() {
    override val fields = listOf(FieldSpec.Text("pages", "Pages to keep", hint = "e.g. 1-3, 5, 8  (order is kept: \"3,1\")"))

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> = guarded(ctx) { temp ->
        val input = ctx.inputs.firstOrNull() ?: return@guarded ProcessingResult.ValidationFailure("Choose a PDF first.")
        openPdf(ctx, input, temp).use { doc ->
            val pages = when (val r = PageSelection.parse(ctx.values.string("pages"), doc.numberOfPages)) {
                is Validation.Valid -> r.value
                is Validation.Invalid -> return@guarded ProcessingResult.ValidationFailure(r.message)
            }
            val out = ctx.newOutputFile("${FileNames.sanitize(FileNames.baseName(input.name), "document")}_pages.pdf")
            try { savePages(doc, pages, out) } catch (t: Throwable) { out.delete(); throw t }
            if (!verifyPageCount(out, pages.size)) { out.delete(); return@guarded ProcessingResult.Failure("The result could not be verified, so it was discarded.") }
            ProcessingResult.Success(ToolOutput("Created a PDF with ${pages.size} page(s).", listOf("Source pages" to doc.numberOfPages.toString()), listOf(pdfOutput(out))))
        }
    }
}

class PdfDeletePagesTool : SinglePdfTool() {
    override val fields = listOf(FieldSpec.Text("pages", "Pages to delete", hint = "e.g. 2, 4-6"))

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> = guarded(ctx) { temp ->
        val input = ctx.inputs.firstOrNull() ?: return@guarded ProcessingResult.ValidationFailure("Choose a PDF first.")
        openPdf(ctx, input, temp).use { doc ->
            val deleted = when (val r = PageSelection.parse(ctx.values.string("pages"), doc.numberOfPages)) {
                is Validation.Valid -> r.value
                is Validation.Invalid -> return@guarded ProcessingResult.ValidationFailure(r.message)
            }
            val keep = PageSelection.remaining(doc.numberOfPages, deleted)
            if (keep.isEmpty()) return@guarded ProcessingResult.ValidationFailure("You cannot delete every page.")
            val out = ctx.newOutputFile("${FileNames.sanitize(FileNames.baseName(input.name), "document")}_edited.pdf")
            try { savePages(doc, keep, out) } catch (t: Throwable) { out.delete(); throw t }
            if (!verifyPageCount(out, keep.size)) { out.delete(); return@guarded ProcessingResult.Failure("The result could not be verified, so it was discarded.") }
            ProcessingResult.Success(
                ToolOutput("Removed ${deleted.size} page(s); saved a copy. Your original file is unchanged.", listOf("Pages remaining" to keep.size.toString()), listOf(pdfOutput(out))),
            )
        }
    }
}

class PdfInfoTool : SinglePdfTool() {
    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> = guarded(ctx) { temp ->
        val input = ctx.inputs.firstOrNull() ?: return@guarded ProcessingResult.ValidationFailure("Choose a PDF first.")
        openPdf(ctx, input, temp).use { doc ->
            val info = doc.documentInformation
            val rows = ArrayList<Pair<String, String>>()
            rows += "Pages" to doc.numberOfPages.toString()
            if (input.sizeBytes >= 0) rows += "File size" to FileNames.humanSize(input.sizeBytes)
            rows += "PDF version" to doc.version.toString()
            info?.title?.takeIf { it.isNotBlank() }?.let { rows += "Title" to it }
            info?.author?.takeIf { it.isNotBlank() }?.let { rows += "Author" to it }
            info?.creator?.takeIf { it.isNotBlank() }?.let { rows += "Created with" to it }
            info?.producer?.takeIf { it.isNotBlank() }?.let { rows += "Producer" to it }
            ProcessingResult.Success(ToolOutput("PDF details", rows))
        }
    }
}

class PdfTextTool : SinglePdfTool() {
    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> = guarded(ctx) { temp ->
        val input = ctx.inputs.firstOrNull() ?: return@guarded ProcessingResult.ValidationFailure("Choose a PDF first.")
        openPdf(ctx, input, temp).use { doc ->
            ctx.checkCancelled()
            val text = PDFTextStripper().getText(doc).trim()
            if (text.isBlank()) {
                return@guarded ProcessingResult.Unsupported("No selectable text was found. This may be a scanned document (images of text); OCR is not available in this version.")
            }
            val file = ctx.newOutputFile("${FileNames.sanitize(FileNames.baseName(input.name), "document")}.txt")
            file.writeText(text)
            val shown = if (text.length > Limits.TEXT_OUTPUT_MAX_CHARS) text.take(Limits.TEXT_OUTPUT_MAX_CHARS) + "\n\n… (preview shortened - save the .txt file for the full text)" else text
            ProcessingResult.Success(
                ToolOutput("Extracted ${text.length} characters from ${doc.numberOfPages} page(s).", emptyList(), listOf(OutputFile(file, file.name, "text/plain")), shown),
            )
        }
    }
}

/** Adds text to every page and saves a NEW file. Uses the built-in Helvetica font, so only basic Latin characters are supported. */
private fun latinOnly(text: String) = text.all { it.code in 32..126 }

class PdfPageNumbersTool : SinglePdfTool() {
    override val fields = listOf(
        FieldSpec.Choice("position", "Position", listOf("Bottom center", "Bottom right", "Bottom left", "Top center"), "Bottom center"),
        FieldSpec.Choice("style", "Style", listOf("1", "Page 1", "1 / N"), "1 / N"),
        FieldSpec.IntValue("start", "First page number", 1, 1, 99),
        FieldSpec.IntValue("size", "Font size", 10, 6, 18, "pt"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> = guarded(ctx) { temp ->
        val input = ctx.inputs.firstOrNull() ?: return@guarded ProcessingResult.ValidationFailure("Choose a PDF first.")
        openPdf(ctx, input, temp).use { doc ->
            val total = doc.numberOfPages
            val start = ctx.values.int("start") ?: 1
            val size = (ctx.values.int("size") ?: 10).toFloat()
            for (i in 0 until total) {
                ctx.checkCancelled()
                val page = doc.getPage(i)
                val n = start + i
                val label = when (ctx.values.string("style")) { "Page 1" -> "Page $n"; "1 / N" -> "$n / ${start + total - 1}"; else -> "$n" }
                val box = page.mediaBox
                val textWidth = PDType1Font.HELVETICA.getStringWidth(label) / 1000f * size
                val x = when (ctx.values.string("position")) { "Bottom right" -> box.width - 36f - textWidth; "Bottom left" -> 36f; else -> (box.width - textWidth) / 2f }
                val y = if (ctx.values.string("position") == "Top center") box.height - 30f else 24f
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.beginText(); cs.setFont(PDType1Font.HELVETICA, size); cs.newLineAtOffset(x, y); cs.showText(label); cs.endText()
                }
            }
            val out = ctx.newOutputFile("${FileNames.sanitize(FileNames.baseName(input.name), "document")}_numbered.pdf")
            try { doc.save(out) } catch (t: Throwable) { out.delete(); throw t }
            if (!verifyPageCount(out, total)) { out.delete(); return@guarded ProcessingResult.Failure("The result could not be verified, so it was discarded.") }
            ProcessingResult.Success(ToolOutput("Added page numbers to $total page(s). Your original is unchanged.", emptyList(), listOf(pdfOutput(out))))
        }
    }
}

class PdfWatermarkTool : SinglePdfTool() {
    override val fields = listOf(
        FieldSpec.Text("text", "Watermark text", "CONFIDENTIAL", hint = "Basic Latin characters only"),
        FieldSpec.IntValue("size", "Text size", 48, 16, 120, "pt"),
        FieldSpec.IntValue("opacity", "Opacity", 25, 5, 100, "%"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> = guarded(ctx) { temp ->
        val input = ctx.inputs.firstOrNull() ?: return@guarded ProcessingResult.ValidationFailure("Choose a PDF first.")
        val text = ctx.values.string("text").trim()
        if (text.isEmpty()) return@guarded ProcessingResult.ValidationFailure("Enter the watermark text.")
        if (text.length > 40) return@guarded ProcessingResult.ValidationFailure("Keep the watermark under 40 characters.")
        if (!latinOnly(text)) return@guarded ProcessingResult.ValidationFailure("PDF watermarks support basic Latin letters, digits and punctuation only in this version.")
        openPdf(ctx, input, temp).use { doc ->
            val total = doc.numberOfPages
            val size = (ctx.values.int("size") ?: 48).toFloat()
            val alpha = (ctx.values.int("opacity") ?: 25).coerceIn(5, 100) / 100f
            for (i in 0 until total) {
                ctx.checkCancelled()
                val page = doc.getPage(i)
                val box = page.mediaBox
                val textWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(text) / 1000f * size
                val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = alpha }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(0.5f, 0.5f, 0.5f)
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA_BOLD, size)
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45.0), box.width / 2f - textWidth / 3f, box.height / 2f - textWidth / 3f))
                    cs.showText(text)
                    cs.endText()
                }
            }
            val out = ctx.newOutputFile("${FileNames.sanitize(FileNames.baseName(input.name), "document")}_watermarked.pdf")
            try { doc.save(out) } catch (t: Throwable) { out.delete(); throw t }
            if (!verifyPageCount(out, total)) { out.delete(); return@guarded ProcessingResult.Failure("The result could not be verified, so it was discarded.") }
            ProcessingResult.Success(ToolOutput("Watermarked $total page(s). Your original is unchanged.", emptyList(), listOf(pdfOutput(out))))
        }
    }
}
