package com.utilityhub.app.tools.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.app.tools.image.ImageCodec
import com.utilityhub.app.tools.image.ImageFormat
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.Validation
import com.utilityhub.logic.files.DetectedType
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.pdf.PageSelection
import com.utilityhub.logic.spec.FieldSpec
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

private val pageSizes = mapOf("A4" to (595 to 842), "Letter" to (612 to 792), "A5" to (420 to 595))

/** Renders pages with the framework PdfRenderer (no third-party library). Password-protected PDFs are rejected. */
class PdfToImageTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("application/pdf")
    override val pickerLabel = "Choose a PDF"
    override val fields = listOf(
        FieldSpec.Text("pages", "Pages", hint = "Leave blank for all (max 50), or e.g. 1-3, 5"),
        FieldSpec.Choice("quality", "Resolution", listOf("Standard (150 dpi)", "High (220 dpi)"), "Standard (150 dpi)"),
        FieldSpec.Choice("format", "Image format", listOf("PNG", "JPEG"), "PNG"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose a PDF first.")
        if (input.sizeBytes > Limits.PDF_INPUT_BYTES) return ProcessingResult.ValidationFailure("This PDF is larger than ${FileNames.humanSize(Limits.PDF_INPUT_BYTES)}.")
        val type = ctx.io.detect(input.uri)
        if (type != DetectedType.PDF) return ProcessingResult.Unsupported("This is not a PDF (detected: ${type.label}).")
        val temp = File(ctx.sessionDir, "tmp").also { it.mkdirs() }
        val copy = File(temp, "src.pdf")
        val outputs = ArrayList<OutputFile>()
        var renderer: PdfRenderer? = null
        var pfd: ParcelFileDescriptor? = null
        try {
            ctx.io.copyToFile(input.uri, copy, Limits.PDF_INPUT_BYTES, ctx)
            try {
                pfd = ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
            } catch (e: SecurityException) {
                return ProcessingResult.Unsupported("This PDF is password-protected, which is not supported in this version.")
            } catch (e: IOException) {
                return ProcessingResult.ValidationFailure("This PDF is damaged or could not be opened.")
            }
            val count = renderer.pageCount
            val raw = ctx.values.string("pages").trim()
            val pages = if (raw.isEmpty()) (0 until minOf(count, MAX_PAGES)).toList() else when (val r = PageSelection.parse(raw, count)) {
                is Validation.Valid -> r.value
                is Validation.Invalid -> return ProcessingResult.ValidationFailure(r.message)
            }
            if (pages.size > MAX_PAGES) return ProcessingResult.ValidationFailure("Convert at most $MAX_PAGES pages at a time.")
            val dpi = if (ctx.values.string("quality").startsWith("High")) 220 else 150
            val format = if (ctx.values.string("format") == "JPEG") ImageFormat.JPEG else ImageFormat.PNG
            val base = FileNames.sanitize(FileNames.baseName(input.name), "page")
            pages.forEachIndexed { n, index ->
                ctx.checkCancelled()
                ctx.reportProgress(n.toFloat() / pages.size)
                val page = renderer.openPage(index)
                try {
                    var scale = dpi / 72f
                    while (page.width * scale * page.height * scale > 20_000_000f) scale *= 0.8f     // memory budget
                    val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val file = ctx.newOutputFile("${base}_page${index + 1}.${format.extension}")
                    try { ImageCodec.save(bitmap, file, format, 92) } finally { bitmap.recycle() }
                    if (ImageCodec.validateOutput(file) == null) { outputs.forEach { it.file.delete() }; file.delete(); return ProcessingResult.Failure("A page could not be verified, so the result was discarded.") }
                    outputs += OutputFile(file, file.name, format.mime)
                } finally {
                    page.close()
                }
            }
        } catch (t: Throwable) {
            outputs.forEach { it.file.delete() }
            throw t
        } finally {
            try { renderer?.close() } catch (_: Exception) { }
            try { pfd?.close() } catch (_: Exception) { }
            temp.deleteRecursively()
        }
        return ProcessingResult.Success(ToolOutput("Converted ${outputs.size} page(s) to images.", listOf("Pages" to outputs.size.toString()), outputs))
    }

    private companion object { const val MAX_PAGES = 50 }
}

class TextToPdfTool : FileTool {
    override val inputRequirement = InputRequirement.NONE
    override val acceptedMimeTypes = emptyArray<String>()
    override val pickerLabel = ""
    override val fields = listOf(
        FieldSpec.Text("text", "Text", multiline = true, hint = "Type or paste your text"),
        FieldSpec.Choice("page", "Page size", listOf("A4", "Letter", "A5"), "A4"),
        FieldSpec.IntValue("fontSize", "Font size", 12, 8, 32, "pt"),
        FieldSpec.IntValue("margin", "Margin", 20, 5, 40, "mm"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val text = ctx.values.string("text").replace("\r\n", "\n").replace("\t", "    ")
        if (text.isBlank()) return ProcessingResult.ValidationFailure("Enter some text first.")
        if (text.length > 200_000) return ProcessingResult.ValidationFailure("The text is longer than 200,000 characters.")
        val (w, h) = pageSizes[ctx.values.string("page")] ?: pageSizes.getValue("A4")
        val fontSize = (ctx.values.int("fontSize") ?: 12).coerceIn(8, 32).toFloat()
        val margin = (ctx.values.int("margin") ?: 20).coerceIn(5, 40) * 72f / 25.4f
        val maxWidth = w - 2 * margin
        if (maxWidth < 50f || h - 2 * margin < 50f) return ProcessingResult.ValidationFailure("The margin is too large for this page.")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = fontSize; color = Color.BLACK }
        val lineHeight = fontSize * 1.35f
        val document = PdfDocument()
        val out = ctx.newOutputFile("text.pdf")
        try {
            var pageNo = 0
            var page: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var y = 0f
            fun newPage() {
                page?.let { document.finishPage(it) }
                pageNo++
                if (pageNo > 200) throw IllegalStateException("too many pages")
                page = document.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
                canvas = page!!.canvas
                y = margin + fontSize
            }
            newPage()
            for (paragraph in text.split('\n')) {
                ctx.checkCancelled()
                var rest = paragraph
                do {
                    val fit = if (rest.isEmpty()) 0 else paint.breakText(rest, true, maxWidth, null).coerceAtLeast(1)
                    var cut = fit
                    if (fit in 1 until rest.length) { val space = rest.lastIndexOf(' ', fit - 1); if (space > 0) cut = space + 1 }   // wrap at a space when possible
                    if (y > h - margin) newPage()
                    canvas!!.drawText(rest.substring(0, cut).trimEnd(), margin, y, paint)
                    y += lineHeight
                    rest = rest.substring(cut)
                } while (rest.isNotEmpty())
            }
            page?.let { document.finishPage(it) }
            FileOutputStream(out).use { document.writeTo(it) }
        } catch (e: IllegalStateException) {
            out.delete()
            return ProcessingResult.ValidationFailure("The text would need more than 200 pages. Please split it.")
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            document.close()
        }
        if (!out.exists() || out.length() <= 0) return ProcessingResult.Failure("The PDF could not be created.")
        return ProcessingResult.Success(ToolOutput("Created a PDF from your text.", listOf("File size" to FileNames.humanSize(out.length()), "Note" to "Characters outside your device's fonts may not render."), listOf(OutputFile(out, out.name, "application/pdf"))))
    }
}

class BlankPdfTool : FileTool {
    override val inputRequirement = InputRequirement.NONE
    override val acceptedMimeTypes = emptyArray<String>()
    override val pickerLabel = ""
    override val fields = listOf(
        FieldSpec.Choice("page", "Page size", listOf("A4", "Letter", "A5"), "A4"),
        FieldSpec.IntValue("pages", "Number of pages", 1, 1, 100),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val (w, h) = pageSizes[ctx.values.string("page")] ?: pageSizes.getValue("A4")
        val count = (ctx.values.int("pages") ?: 1).coerceIn(1, 100)
        val out = ctx.newOutputFile("blank.pdf")
        val document = PdfDocument()
        try {
            for (i in 1..count) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(w, h, i).create())
                page.canvas.drawColor(Color.WHITE)
                document.finishPage(page)
            }
            FileOutputStream(out).use { document.writeTo(it) }
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            document.close()
        }
        return ProcessingResult.Success(ToolOutput("Created a blank $count-page PDF.", listOf("Page size" to ctx.values.string("page")), listOf(OutputFile(out, out.name, "application/pdf"))))
    }
}
