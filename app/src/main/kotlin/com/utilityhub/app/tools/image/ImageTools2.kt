package com.utilityhub.app.tools.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputFile
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.Validation
import com.utilityhub.logic.archive.UniqueNames
import com.utilityhub.logic.files.ContentSniffer
import com.utilityhub.logic.files.DetectedType
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.image.ImageGeometry
import com.utilityhub.logic.image.ImageMath
import com.utilityhub.logic.image.PixelSize
import com.utilityhub.logic.spec.FieldSpec
import java.io.File
import java.util.Base64

private fun colorOf(name: String): Int = when (name) {
    "Black" -> 0xFF000000.toInt()
    "Gray" -> 0xFF808080.toInt()
    "Violet" -> 0xFF5444D8.toInt()
    else -> 0xFFFFFFFF.toInt()
}

private fun mutableCopy(b: Bitmap): Bitmap = b.copy(Bitmap.Config.ARGB_8888, true).also { if (it !== b) b.recycle() }

/** Replaces background pixels with a target color while preserving human face, hair, and clothing. */
fun replaceBackgroundWithColor(source: Bitmap, targetColor: Int, tolerancePercent: Int = 35, feather: Boolean = true): Bitmap {
    val width = source.width
    val height = source.height
    val pixels = IntArray(width * height)
    source.getPixels(pixels, 0, width, 0, 0, width, height)

    val seedSamples = mutableListOf<Int>()
    for (x in 0 until width step maxOf(1, width / 20)) {
        seedSamples.add(pixels[x])
        seedSamples.add(pixels[(height - 1) * width + x])
    }
    for (y in 0 until height step maxOf(1, height / 20)) {
        seedSamples.add(pixels[y * width])
        seedSamples.add(pixels[y * width + (width - 1)])
    }

    var bgR = 0; var bgG = 0; var bgB = 0
    seedSamples.forEach { c ->
        bgR += Color.red(c)
        bgG += Color.green(c)
        bgB += Color.blue(c)
    }
    bgR /= seedSamples.size.coerceAtLeast(1)
    bgG /= seedSamples.size.coerceAtLeast(1)
    bgB /= seedSamples.size.coerceAtLeast(1)

    val maxDistSq = (tolerancePercent / 100f * 255f) * (tolerancePercent / 100f * 255f) * 3f
    val outPixels = IntArray(width * height)

    val newR = Color.red(targetColor)
    val newG = Color.green(targetColor)
    val newB = Color.blue(targetColor)
    val newA = Color.alpha(targetColor)

    for (i in pixels.indices) {
        val c = pixels[i]
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        val a = Color.alpha(c)

        val dr = r - bgR
        val dg = g - bgG
        val db = b - bgB
        val distSq = (dr * dr + dg * dg + db * db).toFloat()

        if (distSq <= maxDistSq) {
            outPixels[i] = Color.argb(newA, newR, newG, newB)
        } else if (feather && distSq <= maxDistSq * 1.5f) {
            val factor = ((distSq - maxDistSq) / (maxDistSq * 0.5f)).coerceIn(0f, 1f)
            val blendedR = (r * factor + newR * (1f - factor)).toInt().coerceIn(0, 255)
            val blendedG = (g * factor + newG * (1f - factor)).toInt().coerceIn(0, 255)
            val blendedB = (b * factor + newB * (1f - factor)).toInt().coerceIn(0, 255)
            outPixels[i] = Color.argb(a, blendedR, blendedG, blendedB)
        } else {
            outPixels[i] = c
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outPixels, 0, width, 0, 0, width, height)
    return result
}

/** Visual crop with preset ratios and a position slider. */
class CropTool : SingleImageTool() {
    private val ratios = mapOf("1:1" to (1 to 1), "4:3" to (4 to 3), "3:2" to (3 to 2), "16:9" to (16 to 9), "3:4" to (3 to 4), "2:3" to (2 to 3), "9:16" to (9 to 16))
    override val fields = listOf(
        FieldSpec.Choice("ratio", "Aspect ratio", ratios.keys.toList(), "1:1"),
        FieldSpec.IntValue("posX", "Horizontal position", 50, 0, 100, "%"),
        FieldSpec.IntValue("posY", "Vertical position", 50, 0, 100, "%"),
        formatField, qualityField,
    )

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val (aw, ah) = ratios[ctx.values.string("ratio")] ?: (1 to 1)
        val bitmap = ImageCodec.decode(ctx.io, input.uri)
        val hadAlpha = bitmap.hasAlpha()
        val rect = when (val r = ImageGeometry.aspectCrop(PixelSize(bitmap.width, bitmap.height), aw, ah, ctx.values.int("posX") ?: 50, ctx.values.int("posY") ?: 50)) {
            is Validation.Valid -> r.value
            is Validation.Invalid -> return ProcessingResult.ValidationFailure(r.message)
        }
        val cropped = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width, rect.height)
        if (cropped !== bitmap) bitmap.recycle()
        val format = ImageFormat.resolve(ctx.values.string("format"), detected)
        return finish(ctx, input, cropped, "crop", format, ctx.values.int("quality") ?: 90, "Cropped to ${ctx.values.string("ratio")}.", metadataNote(format, hadAlpha))
    }
}

class ImageBgChangerTool : SingleImageTool() {
    private val colorMap = mapOf(
        "White" to 0xFFFFFFFF.toInt(),
        "Light Blue" to 0xFFDCEBFC.toInt(),
        "Passport Blue" to 0xFF0066CC.toInt(),
        "Off-White" to 0xFFF5F5F0.toInt(),
        "Red" to 0xFFDC3232.toInt(),
        "Light Grey" to 0xFFE6E6E6.toInt(),
        "Transparent" to 0x00000000,
    )

    override val fields = listOf(
        FieldSpec.Choice("bgColor", "New Background Color", colorMap.keys.toList(), "White"),
        FieldSpec.IntValue("tolerance", "Edge Sensitivity", 35, 10, 80, "%"),
        FieldSpec.Toggle("feather", "Smooth Edges", true),
        formatField, qualityField,
    )

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val targetColor = colorMap[ctx.values.string("bgColor")] ?: 0xFFFFFFFF.toInt()
        val tolerance = ctx.values.int("tolerance") ?: 35
        val feather = ctx.values.bool("feather", true)

        val source = ImageCodec.decode(ctx.io, input.uri)
        val modified = replaceBackgroundWithColor(source, targetColor, tolerance, feather)
        source.recycle()

        val format = ImageFormat.resolve(ctx.values.string("format"), detected)
        return finish(
            ctx, input, modified, "bg_changed", format, ctx.values.int("quality") ?: 90,
            "Background replaced with ${ctx.values.string("bgColor")}.",
            listOf("Background" to ctx.values.string("bgColor"), "Sensitivity" to "$tolerance%")
        )
    }
}

class WatermarkTool : SingleImageTool() {
    private val positions = listOf("Top left", "Top right", "Center", "Bottom left", "Bottom right")
    override val fields = listOf(
        FieldSpec.Text("text", "Watermark text", "© My photo"),
        FieldSpec.Choice("position", "Position", positions, "Bottom right"),
        FieldSpec.IntValue("size", "Text size", 8, 2, 25, "% of width"),
        FieldSpec.IntValue("opacity", "Opacity", 60, 10, 100, "%"),
        FieldSpec.Choice("color", "Colour", listOf("White", "Black"), "White"),
        formatField, qualityField,
    )

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val text = ctx.values.string("text").trim()
        if (text.isEmpty()) return ProcessingResult.ValidationFailure("Enter the watermark text.")
        if (text.length > 100) return ProcessingResult.ValidationFailure("Keep the watermark under 100 characters.")
        val source = ImageCodec.decode(ctx.io, input.uri)
        val hadAlpha = source.hasAlpha()
        val bitmap = mutableCopy(source)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorOf(ctx.values.string("color", "White"))
            alpha = ((ctx.values.int("opacity") ?: 60).coerceIn(10, 100) * 255 / 100)
            textSize = bitmap.width * (ctx.values.int("size") ?: 8).coerceIn(2, 25) / 100f
            setShadowLayer(textSize / 14f, 0f, 0f, Color.argb(160, 0, 0, 0))
        }
        val margin = bitmap.width * 0.03f
        val textWidth = paint.measureText(text)
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val (x, y) = when (ctx.values.string("position")) {
            "Top left" -> margin to margin - metrics.ascent
            "Top right" -> bitmap.width - margin - textWidth to margin - metrics.ascent
            "Center" -> (bitmap.width - textWidth) / 2f to (bitmap.height - textHeight) / 2f - metrics.ascent
            "Bottom left" -> margin to bitmap.height - margin - metrics.descent
            else -> bitmap.width - margin - textWidth to bitmap.height - margin - metrics.descent
        }
        Canvas(bitmap).drawText(text, x.coerceAtLeast(0f), y, paint)
        val format = ImageFormat.resolve(ctx.values.string("format"), detected)
        return finish(ctx, input, bitmap, "watermarked", format, ctx.values.int("quality") ?: 90, "Watermark added.", metadataNote(format, hadAlpha))
    }
}

class BorderTool : SingleImageTool() {
    override val fields = listOf(
        FieldSpec.IntValue("size", "Border size", 5, 1, 30, "% of shorter side"),
        FieldSpec.Choice("color", "Colour", listOf("White", "Black", "Gray", "Violet"), "White"),
        formatField, qualityField,
    )

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val source = ImageCodec.decode(ctx.io, input.uri)
        val hadAlpha = source.hasAlpha()
        val border = (minOf(source.width, source.height) * (ctx.values.int("size") ?: 5).coerceIn(1, 30) / 100f).toInt().coerceAtLeast(1)
        val target = PixelSize(source.width + 2 * border, source.height + 2 * border)
        if (target.pixels > ImageMath.MAX_OUTPUT_PIXELS) { source.recycle(); return ProcessingResult.ValidationFailure("The result would be too large for this device. Use a smaller border.") }
        val out = Bitmap.createBitmap(target.width, target.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(colorOf(ctx.values.string("color", "White"))); drawBitmap(source, border.toFloat(), border.toFloat(), null) }
        source.recycle()
        val format = ImageFormat.resolve(ctx.values.string("format"), detected)
        return finish(ctx, input, out, "border", format, ctx.values.int("quality") ?: 90, "Added a ${border}px border.", metadataNote(format, hadAlpha))
    }
}

abstract class BatchImageTool(private val maxImages: Int) : FileTool {
    override val inputRequirement = InputRequirement.MULTIPLE
    override val acceptedMimeTypes = arrayOf("image/*")
    override val pickerLabel = "Choose images"
    override val maxInputs get() = maxImages

    protected abstract val suffix: String
    protected abstract fun summaryFor(count: Int): String
    protected abstract fun transform(ctx: ToolRunContext, input: InputFile, detected: DetectedType): Pair<Bitmap, ImageFormat>

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        if (ctx.inputs.isEmpty()) return ProcessingResult.ValidationFailure("Choose at least one image.")
        if (ctx.inputs.size > maxImages) return ProcessingResult.ValidationFailure("At most $maxImages images per batch.")
        val names = UniqueNames()
        val outputs = ArrayList<OutputFile>()
        var before = 0L
        var after = 0L
        try {
            ctx.inputs.forEachIndexed { i, input ->
                ctx.checkCancelled()
                ctx.reportProgress(i.toFloat() / ctx.inputs.size)
                if (input.sizeBytes > Limits.IMAGE_INPUT_BYTES) return fail(outputs, "\"${input.name}\" is larger than ${FileNames.humanSize(Limits.IMAGE_INPUT_BYTES)}.")
                val type = ctx.io.detect(input.uri)
                if (!ContentSniffer.isImage(type)) return fail(outputs, "\"${input.name}\" is not a supported image (detected: ${type.label}). Nothing was created.")
                val (bitmap, format) = transform(ctx, input, type)
                val file = ctx.newOutputFile(names.claim(FileNames.outputName(input.name, suffix, format.extension)))
                try { ImageCodec.save(bitmap, file, format, ctx.values.int("quality") ?: 80) } finally { bitmap.recycle() }
                if (ImageCodec.validateOutput(file) == null) return fail(outputs, "A result could not be verified, so the whole batch was discarded.")
                outputs += OutputFile(file, file.name, format.mime)
                before += input.sizeBytes.coerceAtLeast(0); after += file.length()
            }
        } catch (t: Throwable) {
            outputs.forEach { it.file.delete() }
            throw t
        }
        val details = buildList {
            add("Images" to outputs.size.toString())
            if (before > 0) { add("Before" to FileNames.humanSize(before)); add("After" to FileNames.humanSize(after)); add("Change" to String.format(java.util.Locale.US, "%.1f%%", -ImageMath.reductionPercent(before, after))) }
        }
        return ProcessingResult.Success(ToolOutput(summaryFor(outputs.size), details, outputs))
    }

    private fun fail(outputs: List<OutputFile>, message: String): ProcessingResult<ToolOutput> {
        outputs.forEach { it.file.delete() }
        return ProcessingResult.ValidationFailure(message)
    }
}

class BatchResizeTool(maxImages: Int) : BatchImageTool(maxImages) {
    override val suffix = "resized"
    override val fields = listOf(
        FieldSpec.Choice("mode", "Resize by", listOf("Longest side", "Width", "Height"), "Longest side"),
        FieldSpec.IntValue("size", "Size (px)", 1280, 16, 8000, "px"),
        formatField, FieldSpec.IntValue("quality", "Quality (JPEG / WebP)", 85, 10, 100, "%"),
    )
    override fun summaryFor(count: Int) = "Resized $count image(s) in one operation."

    override fun transform(ctx: ToolRunContext, input: InputFile, detected: DetectedType): Pair<Bitmap, ImageFormat> {
        val size = (ctx.values.int("size") ?: 1280).coerceIn(16, ImageMath.MAX_DIMENSION)
        val orientation = ImageCodec.readOrientation(ctx.io, input.uri)
        val original = ImageCodec.displayedSize(ctx.io, input.uri, orientation) ?: throw com.utilityhub.app.core.UnsupportedContentException("\"${input.name}\" could not be decoded.")
        val byWidth = when (ctx.values.string("mode")) { "Width" -> true; "Height" -> false; else -> original.width >= original.height }
        val target = (if (byWidth) ImageMath.resolveTarget(original, size, null, true) else ImageMath.resolveTarget(original, null, size, true))
            .let { if (it is Validation.Valid) it.value else throw com.utilityhub.app.core.UnsupportedContentException((it as Validation.Invalid).message) }
        val decoded = ImageCodec.decode(ctx.io, input.uri, target.width, target.height)
        val scaled = if (decoded.width == target.width && decoded.height == target.height) decoded
        else Bitmap.createScaledBitmap(decoded, target.width, target.height, true).also { if (it !== decoded) decoded.recycle() }
        return scaled to ImageFormat.resolve(ctx.values.string("format"), detected)
    }
}

class BatchCompressTool(maxImages: Int) : BatchImageTool(maxImages) {
    override val suffix = "compressed"
    override val fields = listOf(
        FieldSpec.IntValue("quality", "Quality", 70, 10, 95, "%"),
        FieldSpec.Choice("format", "Output format", listOf("JPEG", "WebP"), "JPEG"),
    )
    override fun summaryFor(count: Int) = "Compressed $count image(s) in one operation."
    override fun transform(ctx: ToolRunContext, input: InputFile, detected: DetectedType): Pair<Bitmap, ImageFormat> =
        ImageCodec.decode(ctx.io, input.uri) to ImageFormat.resolve(ctx.values.string("format"), detected)
}

/** Image -> Base64 text (strict 3 MB input limit). */
class ImageToBase64Tool : SingleImageTool() {
    override val fields = listOf(FieldSpec.Toggle("dataUri", "Include data: URI prefix", false))
    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        if (input.sizeBytes > MAX_BYTES) return ProcessingResult.ValidationFailure("Base64 conversion is limited to images of ${FileNames.humanSize(MAX_BYTES)}. Compress the image first.")
        val bytes = ctx.io.open(input.uri).use { it.readBytes() }
        if (bytes.size > MAX_BYTES) return ProcessingResult.ValidationFailure("The image is larger than ${FileNames.humanSize(MAX_BYTES)}.")
        val mime = when (detected) { DetectedType.PNG -> "image/png"; DetectedType.WEBP -> "image/webp"; DetectedType.GIF -> "image/gif"; else -> "image/jpeg" }
        val encoded = Base64.getEncoder().encodeToString(bytes).let { if (ctx.values.bool("dataUri")) "data:$mime;base64,$it" else it }
        val file = ctx.newOutputFile(FileNames.outputName(input.name, "base64", "txt"))
        file.writeText(encoded)
        val shown = if (encoded.length > Limits.TEXT_OUTPUT_MAX_CHARS) encoded.take(Limits.TEXT_OUTPUT_MAX_CHARS) + "…" else encoded
        return ProcessingResult.Success(ToolOutput("Encoded ${FileNames.humanSize(bytes.size.toLong())} as ${encoded.length} characters.", emptyList(), listOf(OutputFile(file, file.name, "text/plain")), shown))
    }
    private companion object { const val MAX_BYTES = 3L * 1024 * 1024 }
}

class Base64ToImageTool : FileTool {
    override val inputRequirement = InputRequirement.NONE
    override val acceptedMimeTypes = emptyArray<String>()
    override val pickerLabel = ""
    override val fields = listOf(FieldSpec.Text("data", "Base64 text", multiline = true, hint = "Paste Base64 or a data: URI"))

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        var text = ctx.values.string("data").trim()
        if (text.isEmpty()) return ProcessingResult.ValidationFailure("Paste the Base64 text first.")
        if (text.length > 4_200_000) return ProcessingResult.ValidationFailure("The text is too large (limit about 3 MB of image data).")
        if (text.startsWith("data:")) text = text.substringAfter("base64,", text)
        val bytes = try { Base64.getMimeDecoder().decode(text) } catch (e: IllegalArgumentException) { return ProcessingResult.ValidationFailure("This is not valid Base64.") }
        val type = ContentSniffer.detect(bytes.copyOf(minOf(bytes.size, ContentSniffer.HEADER_BYTES)))
        val ext = when (type) { DetectedType.PNG -> "png"; DetectedType.JPEG -> "jpg"; DetectedType.WEBP -> "webp"; DetectedType.GIF -> "gif"; DetectedType.BMP -> "bmp"; else -> return ProcessingResult.Unsupported("The decoded data is not an image (detected: ${type.label}).") }
        val file = ctx.newOutputFile("decoded.$ext")
        file.writeBytes(bytes)
        val size = ImageCodec.validateOutput(file) ?: run { file.delete(); return ProcessingResult.Unsupported("The decoded data is damaged and cannot be shown as an image.") }
        return ProcessingResult.Success(ToolOutput("Decoded a ${type.label}.", listOf("Size" to "$size", "File size" to FileNames.humanSize(file.length())), listOf(OutputFile(file, file.name, if (ext == "jpg") "image/jpeg" else "image/$ext"))))
    }
}

/** Read-only: extracts the most common colours of an image. */
class ColorPickerTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("image/*")
    override val pickerLabel = "Choose an image"
    override val fields = listOf(FieldSpec.IntValue("count", "Colours", 6, 2, 10))

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose an image first.")
        val type = ctx.io.detect(input.uri)
        if (!ContentSniffer.isImage(type)) return ProcessingResult.Unsupported("This file is not a supported image (detected: ${type.label}).")
        val full = ImageCodec.decode(ctx.io, input.uri, 256, 256)
        val scale = 256f / maxOf(full.width, full.height)
        val small = if (scale < 1f) Bitmap.createScaledBitmap(full, (full.width * scale).toInt().coerceAtLeast(1), (full.height * scale).toInt().coerceAtLeast(1), true) else full
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        if (small !== full) small.recycle()
        full.recycle()
        val colors = ImageGeometry.dominantColors(pixels, ctx.values.int("count") ?: 6)
        if (colors.isEmpty()) return ProcessingResult.Unsupported("The image has no visible colours.")
        return ProcessingResult.Success(ToolOutput("Most common colours", colors.mapIndexed { i, c -> "${i + 1}" to "${c.color}  ${String.format(java.util.Locale.US, "%.0f%%", c.percent)}" }))
    }
}

class PassportPhotoTool : SingleImageTool() {
    private val presets = linkedMapOf(
        "35 × 45 mm (Standard Passport / Visa)" to (35.0 to 45.0),
        "51 × 51 mm (2 × 2 in / US & Exam Form)" to (50.8 to 50.8),
        "35 × 45 mm (Indian Exam / Govt Form)" to (35.0 to 45.0),
        "40 × 50 mm" to (40.0 to 50.0),
        "30 × 40 mm" to (30.0 to 40.0),
        "25 × 35 mm" to (25.0 to 35.0),
        "33 × 48 mm" to (33.0 to 48.0),
    )
    private val papers = linkedMapOf("4 × 6 in" to (101.6 to 152.4), "5 × 7 in" to (127.0 to 177.8), "A4" to (210.0 to 297.0))
    override val fields = listOf(
        FieldSpec.Choice("size", "Photo size & Preset", presets.keys.toList(), "35 × 45 mm (Standard Passport / Visa)"),
        FieldSpec.Choice("bgColor", "Background color", listOf("Solid White", "Light Blue", "Off-White", "Keep Original"), "Solid White"),
        FieldSpec.Choice("paper", "Paper sheet layout", papers.keys.toList(), "4 × 6 in"),
        FieldSpec.Choice("dpi", "Print resolution", listOf("200 dpi", "300 dpi"), "300 dpi"),
        FieldSpec.IntValue("posX", "Move crop left/right", 50, 0, 100, "%"),
        FieldSpec.IntValue("posY", "Move crop up/down", 40, 0, 100, "%"),
        FieldSpec.Toggle("guides", "Draw thin cut guides", true),
    )

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val (pw, ph) = presets[ctx.values.string("size")] ?: presets.values.first()
        val (paperW, paperH) = papers[ctx.values.string("paper")] ?: papers.values.first()
        val dpi = if (ctx.values.string("dpi").startsWith("200")) 200 else 300
        val layout = when (val r = ImageGeometry.bestSheet(paperW, paperH, pw, ph, 2.0, 4.0)) {
            is Validation.Valid -> r.value
            is Validation.Invalid -> return ProcessingResult.ValidationFailure(r.message)
        }
        val photoPx = PixelSize(ImageGeometry.mmToPixels(pw, dpi), ImageGeometry.mmToPixels(ph, dpi))
        val sheetPx = PixelSize(
            ImageGeometry.mmToPixels(if (layout.landscape) paperH else paperW, dpi),
            ImageGeometry.mmToPixels(if (layout.landscape) paperW else paperH, dpi),
        )
        if (sheetPx.pixels > ImageMath.MAX_OUTPUT_PIXELS) return ProcessingResult.ValidationFailure("The sheet is too large for this device.")

        var source = ImageCodec.decode(ctx.io, input.uri)
        val bgChoice = ctx.values.string("bgColor", "Solid White")
        if (bgChoice != "Keep Original") {
            val bgTarget = when (bgChoice) {
                "Light Blue" -> 0xFFDCEBFC.toInt()
                "Off-White" -> 0xFFF5F5F0.toInt()
                else -> 0xFFFFFFFF.toInt()
            }
            val cleaned = replaceBackgroundWithColor(source, bgTarget, 35, true)
            source.recycle()
            source = cleaned
        }

        val crop = when (val r = ImageGeometry.aspectCrop(PixelSize(source.width, source.height), (pw * 100).toInt(), (ph * 100).toInt(), ctx.values.int("posX") ?: 50, ctx.values.int("posY") ?: 40)) {
            is Validation.Valid -> r.value
            is Validation.Invalid -> { source.recycle(); return ProcessingResult.ValidationFailure(r.message) }
        }
        val low = crop.width < photoPx.width * 0.8
        val photo = Bitmap.createBitmap(photoPx.width, photoPx.height, Bitmap.Config.ARGB_8888)
        Canvas(photo).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height), Rect(0, 0, photoPx.width, photoPx.height), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        source.recycle()
        ctx.checkCancelled()
        val sheet = Bitmap.createBitmap(sheetPx.width, sheetPx.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet).apply { drawColor(Color.WHITE) }
        val gap = ImageGeometry.mmToPixels(2.0, dpi)
        val gridW = layout.columns * photoPx.width + (layout.columns - 1) * gap
        val gridH = layout.rows * photoPx.height + (layout.rows - 1) * gap
        val startX = (sheetPx.width - gridW) / 2
        val startY = (sheetPx.height - gridH) / 2
        val guide = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 1f }
        for (row in 0 until layout.rows) for (col in 0 until layout.columns) {
            val x = startX + col * (photoPx.width + gap)
            val y = startY + row * (photoPx.height + gap)
            canvas.drawBitmap(photo, x.toFloat(), y.toFloat(), null)
            if (ctx.values.bool("guides", true)) canvas.drawRect(RectF(x.toFloat(), y.toFloat(), (x + photoPx.width).toFloat(), (y + photoPx.height).toFloat()), guide)
        }
        val sheetFile = ctx.newOutputFile("passport_sheet.jpg")
        val singleFile = ctx.newOutputFile("passport_single.jpg")
        try { ImageCodec.save(sheet, sheetFile, ImageFormat.JPEG, 95); ImageCodec.save(photo, singleFile, ImageFormat.JPEG, 95) } finally { sheet.recycle(); photo.recycle() }
        if (ImageCodec.validateOutput(sheetFile) == null || ImageCodec.validateOutput(singleFile) == null) {
            sheetFile.delete(); singleFile.delete()
            return ProcessingResult.Failure("The result could not be verified, so it was discarded.")
        }
        val details = buildList {
            add("Photos on sheet" to "${layout.count} (${layout.columns} × ${layout.rows}${if (layout.landscape) ", landscape" else ""})")
            add("Photo size" to "${ctx.values.string("size")} at $dpi dpi = $photoPx")
            add("Background" to bgChoice)
            if (low) add("Resolution notice" to "The face area in the original is small. Use a higher resolution original for best print clarity.")
        }
        return ProcessingResult.Success(ToolOutput("Passport photo created.", details, listOf(OutputFile(singleFile, singleFile.name, "image/jpeg"), OutputFile(sheetFile, sheetFile.name, "image/jpeg"))))
    }
}

class CollageTool : FileTool {
    override val inputRequirement = InputRequirement.MULTIPLE
    override val acceptedMimeTypes = arrayOf("image/*")
    override val pickerLabel = "Choose 2–4 images"
    override val maxInputs get() = 4
    override val reorderable = true
    override val fields = listOf(
        FieldSpec.Choice("layout", "Layout", listOf("Side by side", "Stacked", "2 × 2 grid"), "2 × 2 grid"),
        FieldSpec.IntValue("gap", "Spacing", 12, 0, 40, "px"),
        FieldSpec.Choice("background", "Background", listOf("White", "Black"), "White"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        if (ctx.inputs.size !in 2..4) return ProcessingResult.ValidationFailure("Choose between 2 and 4 images.")
        val gap = (ctx.values.int("gap") ?: 12).coerceIn(0, 40)
        val n = ctx.inputs.size
        val canvasSize = when (ctx.values.string("layout")) { "Side by side" -> PixelSize(2400, 1200); "Stacked" -> PixelSize(1200, 2400); else -> PixelSize(2000, 2000) }
        val cells: List<RectF> = when (ctx.values.string("layout")) {
            "Side by side" -> (0 until n).map { i -> val w = (canvasSize.width - gap * (n + 1)) / n.toFloat(); RectF(gap + i * (w + gap), gap.toFloat(), gap + i * (w + gap) + w, (canvasSize.height - gap).toFloat()) }
            "Stacked" -> (0 until n).map { i -> val h = (canvasSize.height - gap * (n + 1)) / n.toFloat(); RectF(gap.toFloat(), gap + i * (h + gap), (canvasSize.width - gap).toFloat(), gap + i * (h + gap) + h) }
            else -> {
                val w = (canvasSize.width - gap * 3) / 2f; val h = (canvasSize.height - gap * 3) / 2f
                (0 until n).map { i -> val c = i % 2; val r = i / 2; RectF(gap + c * (w + gap), gap + r * (h + gap), gap + c * (w + gap) + w, gap + r * (h + gap) + h) }
            }
        }
        val out = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out).apply { drawColor(colorOf(ctx.values.string("background", "White"))) }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        try {
            ctx.inputs.forEachIndexed { i, input ->
                ctx.checkCancelled()
                val type = ctx.io.detect(input.uri)
                if (!ContentSniffer.isImage(type)) return ProcessingResult.Unsupported("\"${input.name}\" is not a supported image (detected: ${type.label}).")
                val cell = cells[i]
                val bmp = ImageCodec.decode(ctx.io, input.uri, cell.width().toInt(), cell.height().toInt())
                val crop = (ImageGeometry.aspectCrop(PixelSize(bmp.width, bmp.height), cell.width().toInt().coerceAtLeast(1), cell.height().toInt().coerceAtLeast(1), 50, 50) as Validation.Valid).value
                canvas.drawBitmap(bmp, Rect(crop.left, crop.top, crop.left + crop.width, crop.top + crop.height), cell, paint)
                bmp.recycle()
            }
            val file = ctx.newOutputFile("collage.jpg")
            ImageCodec.save(out, file, ImageFormat.JPEG, 92)
            if (ImageCodec.validateOutput(file) == null) { file.delete(); return ProcessingResult.Failure("The collage could not be verified, so it was discarded.") }
            return ProcessingResult.Success(ToolOutput("Collage of $n images created.", listOf("Size" to "$canvasSize"), listOf(OutputFile(file, file.name, "image/jpeg"))))
        } finally {
            out.recycle()
        }
    }
}
