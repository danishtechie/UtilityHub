package com.utilityhub.app.tools.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.WriterException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.app.tools.image.ImageCodec
import com.utilityhub.app.tools.image.ImageFormat
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.files.ContentSniffer
import com.utilityhub.logic.spec.FieldSpec

/** Entered text is never logged or sent anywhere; the QR image is rendered locally. */
class QrGenerateTool : FileTool {
    override val inputRequirement = InputRequirement.NONE
    override val acceptedMimeTypes = emptyArray<String>()
    override val pickerLabel = ""
    private val levels = mapOf(
        "L (7% recovery)" to ErrorCorrectionLevel.L, "M (15% recovery)" to ErrorCorrectionLevel.M,
        "Q (25% recovery)" to ErrorCorrectionLevel.Q, "H (30% recovery)" to ErrorCorrectionLevel.H,
    )
    override val fields = listOf(
        FieldSpec.Text("content", "Text or link", multiline = true, hint = "https://example.com"),
        FieldSpec.IntValue("size", "Image size", 768, 256, 2048, "px"),
        FieldSpec.Choice("ecc", "Error correction", levels.keys.toList(), "M (15% recovery)"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val content = ctx.values.string("content").trim()
        if (content.isEmpty()) return ProcessingResult.ValidationFailure("Enter the text or link to encode.")
        if (content.length > Limits.QR_MAX_CHARS) return ProcessingResult.ValidationFailure("Please keep the content under ${Limits.QR_MAX_CHARS} characters.")
        val size = (ctx.values.int("size") ?: 768).coerceIn(256, 2048)
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to (levels[ctx.values.string("ecc")] ?: ErrorCorrectionLevel.M),
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 2,
        )
        val matrix = try {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        } catch (e: WriterException) {
            return ProcessingResult.ValidationFailure("There is too much data for a QR code at this error-correction level. Shorten the text or lower the correction level.")
        }
        ctx.checkCancelled()
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) pixels[y * matrix.width + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        val file = ctx.newOutputFile("qr_code.png")
        try { ImageCodec.save(bitmap, file, ImageFormat.PNG, 100) } finally { bitmap.recycle() }
        if (ImageCodec.validateOutput(file) == null) {
            file.delete()
            return ProcessingResult.Failure("The QR code image could not be verified, so it was discarded.")
        }
        return ProcessingResult.Success(
            ToolOutput("QR code created.", listOf("Size" to "${matrix.width} × ${matrix.height} px", "Characters encoded" to content.length.toString()), listOf(OutputFile(file, file.name, "image/png"))),
        )
    }
}

/** Reads a QR code / barcode from a saved image or screenshot. No camera permission is needed. */
class QrScanTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("image/*")
    override val pickerLabel = "Choose an image with a code"

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose an image first.")
        if (input.sizeBytes > Limits.IMAGE_INPUT_BYTES) return ProcessingResult.ValidationFailure("This image is too large.")
        val type = ctx.io.detect(input.uri)
        if (!ContentSniffer.isImage(type)) return ProcessingResult.Unsupported("This file is not a supported image (detected: ${type.label}).")
        val bitmap = ImageCodec.decode(ctx.io, input.uri, 1600, 1600)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val w = bitmap.width
        val h = bitmap.height
        bitmap.recycle()
        ctx.checkCancelled()
        val reader = MultiFormatReader().apply { setHints(mapOf(DecodeHintType.TRY_HARDER to true)) }
        val source = RGBLuminanceSource(w, h, pixels)
        val result = try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)))
        } catch (e: NotFoundException) {
            reader.reset()
            try { reader.decode(BinaryBitmap(HybridBinarizer(source.invert()))) } catch (e2: NotFoundException) { null }
        } finally {
            reader.reset()
        }
            ?: return ProcessingResult.Unsupported("No QR code or barcode was found. Try a sharper, closer image with the whole code visible.")
        val text = result.text.take(Limits.TEXT_OUTPUT_MAX_CHARS)
        val isLink = text.startsWith("http://", true) || text.startsWith("https://", true)
        return ProcessingResult.Success(
            ToolOutput(
                if (isLink) "Code found. It contains a link - only open links you trust." else "Code found.",
                listOf("Code type" to result.barcodeFormat.name),
                text = text,
            ),
        )
    }
}

/** 1D barcodes. ZXing validates the content for each symbology (e.g. EAN-13 needs 12-13 digits) and reports errors. */
class BarcodeGenerateTool : FileTool {
    override val inputRequirement = InputRequirement.NONE
    override val acceptedMimeTypes = emptyArray<String>()
    override val pickerLabel = ""
    private val formats = linkedMapOf("Code 128 (any text)" to BarcodeFormat.CODE_128, "EAN-13 (12-13 digits)" to BarcodeFormat.EAN_13, "UPC-A (11-12 digits)" to BarcodeFormat.UPC_A, "Code 39 (A-Z 0-9 - . space)" to BarcodeFormat.CODE_39)
    override val fields = listOf(
        FieldSpec.Choice("format", "Type", formats.keys.toList(), "Code 128 (any text)"),
        FieldSpec.Text("content", "Content", hint = "e.g. 4006381333931"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val content = ctx.values.string("content").trim()
        if (content.isEmpty()) return ProcessingResult.ValidationFailure("Enter the content to encode.")
        if (content.length > 80) return ProcessingResult.ValidationFailure("Barcodes hold at most about 80 characters.")
        val matrix = try {
            MultiFormatWriter().encode(content, formats[ctx.values.string("format")] ?: BarcodeFormat.CODE_128, 900, 300)
        } catch (e: IllegalArgumentException) {
            return ProcessingResult.ValidationFailure("That content is not valid for this barcode type: ${e.message ?: "check the length and characters"}.")
        } catch (e: WriterException) {
            return ProcessingResult.ValidationFailure("That content cannot be encoded in this barcode type.")
        }
        val captionHeight = 60
        val pixels = IntArray(matrix.width * matrix.height) { i -> if (matrix.get(i % matrix.width, i / matrix.width)) Color.BLACK else Color.WHITE }
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height + captionHeight, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        android.graphics.Canvas(bitmap).drawText(content, 20f, (matrix.height + 42).toFloat(), android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; color = Color.BLACK })
        val file = ctx.newOutputFile("barcode.png")
        try { ImageCodec.save(bitmap, file, ImageFormat.PNG, 100) } finally { bitmap.recycle() }
        if (ImageCodec.validateOutput(file) == null) { file.delete(); return ProcessingResult.Failure("The barcode image could not be verified, so it was discarded.") }
        return ProcessingResult.Success(ToolOutput("Barcode created.", listOf("Type" to ctx.values.string("format").substringBefore(" (")), listOf(OutputFile(file, file.name, "image/png"))))
    }
}
