package com.utilityhub.app.tools.image

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.files.ContentSniffer
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.spec.FieldSpec
import java.io.FileOutputStream
import kotlin.math.min

/** One image per page, generated entirely on-device with the framework PdfDocument. */
class ImageToPdfTool(private val maxImages: Int) : FileTool {
    override val inputRequirement = InputRequirement.MULTIPLE
    override val acceptedMimeTypes = arrayOf("image/*")
    override val pickerLabel = "Choose images"
    override val maxInputs get() = maxImages
    override val reorderable = true
    override val fields = listOf(
        FieldSpec.Choice("pageSize", "Page size", listOf("A4", "Letter", "Fit to image"), "A4"),
        FieldSpec.IntValue("margin", "Margin", 10, 0, 40, "mm"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        if (ctx.inputs.isEmpty()) return ProcessingResult.ValidationFailure("Choose at least one image.")
        if (ctx.inputs.size > maxImages) return ProcessingResult.ValidationFailure("At most $maxImages images per PDF.")
        val pageChoice = ctx.values.string("pageSize", "A4")
        val marginPt = (ctx.values.int("margin") ?: 10).coerceIn(0, 40) * 72f / 25.4f
        val outFile = ctx.newOutputFile("images.pdf")
        val document = PdfDocument()
        try {
            ctx.inputs.forEachIndexed { index, input ->
                ctx.checkCancelled()
                ctx.reportProgress(index.toFloat() / ctx.inputs.size)
                if (input.sizeBytes > Limits.IMAGE_INPUT_BYTES) {
                    return ProcessingResult.ValidationFailure("\"${input.name}\" is larger than ${FileNames.humanSize(Limits.IMAGE_INPUT_BYTES)}.")
                }
                val type = ctx.io.detect(input.uri)
                if (!ContentSniffer.isImage(type)) return ProcessingResult.Unsupported("\"${input.name}\" is not a supported image (detected: ${type.label}).")
                // 2400 px on the long edge is ~300 dpi on A4 and keeps memory bounded.
                val bitmap = ImageCodec.decode(ctx.io, input.uri, 2400, 2400)
                try {
                    val landscape = bitmap.width > bitmap.height
                    val (pw, ph) = when (pageChoice) {
                        "Letter" -> 612 to 792
                        "Fit to image" -> maxOf(1, (bitmap.width * 0.48f).toInt()) to maxOf(1, (bitmap.height * 0.48f).toInt())
                        else -> 595 to 842
                    }.let { (w, h) -> if (pageChoice != "Fit to image" && landscape) h to w else w to h }
                    val margin = if (pageChoice == "Fit to image") 0f else marginPt
                    val page = document.startPage(PdfDocument.PageInfo.Builder(pw, ph, index + 1).create())
                    val availW = pw - 2 * margin
                    val availH = ph - 2 * margin
                    if (availW <= 0 || availH <= 0) return ProcessingResult.ValidationFailure("The margin is too large for this page size.")
                    val scale = min(availW / bitmap.width, availH / bitmap.height)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    val left = (pw - w) / 2f
                    val top = (ph - h) / 2f
                    page.canvas.drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            ctx.checkCancelled()
            FileOutputStream(outFile).use { document.writeTo(it) }
        } catch (t: Throwable) {
            outFile.delete()
            throw t
        } finally {
            document.close()
        }
        if (!outFile.exists() || outFile.length() <= 0) return ProcessingResult.Failure("The PDF could not be created.")
        return ProcessingResult.Success(
            ToolOutput(
                "Created a ${ctx.inputs.size}-page PDF.",
                listOf("Pages" to ctx.inputs.size.toString(), "File size" to FileNames.humanSize(outFile.length()), "Page size" to pageChoice),
                listOf(OutputFile(outFile, outFile.name, "application/pdf")),
            ),
        )
    }
}
