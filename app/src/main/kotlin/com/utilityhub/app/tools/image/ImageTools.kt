package com.utilityhub.app.tools.image

import android.graphics.Bitmap
import android.graphics.Matrix
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputFile
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.Validation
import com.utilityhub.logic.files.ContentSniffer
import com.utilityhub.logic.files.DetectedType
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.image.ImageMath
import com.utilityhub.logic.image.PixelSize
import com.utilityhub.logic.spec.FieldSpec

internal val formatChoices = listOf(ImageFormat.SAME_AS_SOURCE, "JPEG", "PNG", "WebP")
internal val qualityField = FieldSpec.IntValue("quality", "Quality (JPEG / WebP)", 90, 10, 100, "%")
internal val formatField = FieldSpec.Choice("format", "Output format", formatChoices, ImageFormat.SAME_AS_SOURCE)

/** Shared checks for every single-image tool: real content sniffing + size limit. */
abstract class SingleImageTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("image/*")
    override val pickerLabel = "Choose an image"

    protected abstract fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput>

    final override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose an image first.")
        if (input.sizeBytes > Limits.IMAGE_INPUT_BYTES) {
            return ProcessingResult.ValidationFailure("This image is larger than ${FileNames.humanSize(Limits.IMAGE_INPUT_BYTES)}.")
        }
        val detected = ctx.io.detect(input.uri)
        val looksLikeHeic = detected == DetectedType.MP4 && input.mimeType?.startsWith("image/") == true
        if (!ContentSniffer.isImage(detected) && !looksLikeHeic) {
            return ProcessingResult.Unsupported("This file is not a supported image (detected: ${detected.label}).")
        }
        return process(ctx, input, detected)
    }

    /** Writes [bitmap], validates the result and builds the output description. */
    protected fun finish(
        ctx: ToolRunContext,
        input: InputFile,
        bitmap: Bitmap,
        suffix: String,
        format: ImageFormat,
        quality: Int,
        summary: String,
        extraDetails: List<Pair<String, String>> = emptyList(),
    ): ProcessingResult<ToolOutput> {
        ctx.checkCancelled()
        val file = ctx.newOutputFile(FileNames.outputName(input.name, suffix, format.extension))
        try {
            ImageCodec.save(bitmap, file, format, quality)
        } finally {
            bitmap.recycle()
        }
        val size = ImageCodec.validateOutput(file) ?: run {
            file.delete()
            return ProcessingResult.Failure("The result could not be verified, so it was discarded.")
        }
        val details = buildList {
            add("Output size" to "$size")
            if (input.sizeBytes >= 0) {
                add("Original file" to FileNames.humanSize(input.sizeBytes))
                add("Result file" to FileNames.humanSize(file.length()))
                val change = ImageMath.reductionPercent(input.sizeBytes, file.length())
                add(if (change >= 0) "Size reduction" to String.format(java.util.Locale.US, "%.1f%%", change)
                else "Size change" to String.format(java.util.Locale.US, "+%.1f%% (larger)", -change))
            }
            addAll(extraDetails)
        }
        return ProcessingResult.Success(
            ToolOutput(summary, details, listOf(OutputFile(file, file.name, format.mime))),
        )
    }

    protected fun metadataNote(format: ImageFormat, hadAlpha: Boolean): List<Pair<String, String>> = buildList {
        add("Metadata" to "EXIF and location data are not copied to the result.")
        if (format == ImageFormat.JPEG && hadAlpha) add("Transparency" to "JPEG cannot store transparency; it was replaced with white.")
    }
}

class ResizeTool : SingleImageTool() {
    override val fields = listOf(
        FieldSpec.Text("width", "Width (px)", hint = "Leave blank to calculate"),
        FieldSpec.Text("height", "Height (px)", hint = "Leave blank to calculate"),
        FieldSpec.Toggle("keepAspect", "Keep aspect ratio", true),
        formatField, qualityField,
    )

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val width = ctx.values.string("width").trim().takeIf { it.isNotEmpty() }?.let {
            it.toIntOrNull() ?: return ProcessingResult.ValidationFailure("Width must be a whole number.")
        }
        val height = ctx.values.string("height").trim().takeIf { it.isNotEmpty() }?.let {
            it.toIntOrNull() ?: return ProcessingResult.ValidationFailure("Height must be a whole number.")
        }
        val orientation = ImageCodec.readOrientation(ctx.io, input.uri)
        val original = ImageCodec.displayedSize(ctx.io, input.uri, orientation)
            ?: return ProcessingResult.Unsupported("This image could not be decoded.")
        val target = when (val r = ImageMath.resolveTarget(original, width, height, ctx.values.bool("keepAspect", true))) {
            is Validation.Valid -> r.value
            is Validation.Invalid -> return ProcessingResult.ValidationFailure(r.message)
        }
        ctx.checkCancelled()
        val decoded = ImageCodec.decode(ctx.io, input.uri, target.width, target.height)
        val hadAlpha = decoded.hasAlpha()
        val scaled = if (decoded.width == target.width && decoded.height == target.height) decoded
        else Bitmap.createScaledBitmap(decoded, target.width, target.height, true).also { if (it !== decoded) decoded.recycle() }
        val format = ImageFormat.resolve(ctx.values.string("format"), detected)
        return finish(
            ctx, input, scaled, "${target.width}x${target.height}", format, ctx.values.int("quality") ?: 90,
            "Resized from $original to $target.", listOf("Original size" to "$original") + metadataNote(format, hadAlpha),
        )
    }
}

class CompressTool : SingleImageTool() {
    override val fields = listOf(
        FieldSpec.Choice("mode", "Compression Method", listOf("Target File Size (KB/MB)", "Quality Percentage (%)"), "Target File Size (KB/MB)"),
        FieldSpec.IntValue("targetKb", "Target Size (KB)", 200, 10, 5000, "KB"),
        FieldSpec.IntValue("quality", "Quality Percentage", 70, 10, 95, "%"),
        FieldSpec.Choice("format", "Output format", listOf("JPEG", "WebP"), "JPEG"),
    )

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val orientation = ImageCodec.readOrientation(ctx.io, input.uri)
        val original = ImageCodec.displayedSize(ctx.io, input.uri, orientation)
        var bitmap = ImageCodec.decode(ctx.io, input.uri)
        val hadAlpha = bitmap.hasAlpha()
        val format = ImageFormat.resolve(ctx.values.string("format"), detected)
        val mode = ctx.values.string("mode", "Target File Size (KB/MB)")

        if (mode == "Target File Size (KB/MB)") {
            val targetKb = (ctx.values.int("targetKb") ?: 200).coerceIn(10, 10000)
            val targetBytes = targetKb * 1024L

            var currentBitmap = bitmap
            var low = 10
            var high = 95
            var foundQuality = 10

            while (low <= high) {
                val mid = (low + high) / 2
                val stream = java.io.ByteArrayOutputStream()
                val compressFormat = if (format == ImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                currentBitmap.compress(compressFormat, mid, stream)
                val size = stream.toByteArray().size
                if (size <= targetBytes) {
                    foundQuality = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }

            var scaleFactor = 0.85f
            while (foundQuality <= 10 && scaleFactor >= 0.2f) {
                val stream = java.io.ByteArrayOutputStream()
                val compressFormat = if (format == ImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                currentBitmap.compress(compressFormat, 10, stream)
                if (stream.toByteArray().size <= targetBytes) break

                val newW = (currentBitmap.width * scaleFactor).toInt().coerceAtLeast(100)
                val newH = (currentBitmap.height * scaleFactor).toInt().coerceAtLeast(100)
                val scaled = Bitmap.createScaledBitmap(currentBitmap, newW, newH, true)
                if (currentBitmap !== bitmap) currentBitmap.recycle()
                currentBitmap = scaled

                low = 10
                high = 90
                foundQuality = 10
                while (low <= high) {
                    val mid = (low + high) / 2
                    val s = java.io.ByteArrayOutputStream()
                    currentBitmap.compress(compressFormat, mid, s)
                    if (s.toByteArray().size <= targetBytes) {
                        foundQuality = mid
                        low = mid + 1
                    } else {
                        high = mid - 1
                    }
                }
                scaleFactor -= 0.15f
            }

            return finish(
                ctx, input, currentBitmap, "compressed", format, foundQuality,
                "Compressed to target size of ${targetKb} KB.",
                listOf("Target Size" to "$targetKb KB", "Quality Level" to "$foundQuality%") + metadataNote(format, hadAlpha)
            )
        } else {
            val quality = ctx.values.int("quality") ?: 70
            val scaledDown = original != null && (bitmap.width != original.width || bitmap.height != original.height)
            val note = if (scaledDown) listOf("Large image" to "Scaled down to fit this device's memory.") else emptyList()
            return finish(ctx, input, bitmap, "compressed", format, quality, "Compressed at $quality% quality.", note + metadataNote(format, hadAlpha))
        }
    }
}

class ConvertTool : SingleImageTool() {
    override val fields = listOf(
        FieldSpec.Choice("format", "Convert to", listOf("PNG", "JPEG", "WebP"), "PNG"),
        qualityField,
    )

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val bitmap = ImageCodec.decode(ctx.io, input.uri)
        val hadAlpha = bitmap.hasAlpha()
        val format = ImageFormat.resolve(ctx.values.string("format"), detected)
        return finish(ctx, input, bitmap, "converted", format, ctx.values.int("quality") ?: 90, "Converted to ${format.label}.", metadataNote(format, hadAlpha))
    }
}

class RotateFlipTool : SingleImageTool() {
    private val rotations = listOf("None", "90° clockwise", "180°", "90° counter-clockwise")
    override val fields = listOf(
        FieldSpec.Choice("rotate", "Rotate", rotations, "90° clockwise"),
        FieldSpec.Toggle("flipH", "Flip horizontally", false),
        FieldSpec.Toggle("flipV", "Flip vertically", false),
        formatField, qualityField,
    )

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val degrees = when (ctx.values.string("rotate")) { "90° clockwise" -> 90f; "180°" -> 180f; "90° counter-clockwise" -> 270f; else -> 0f }
        val flipH = ctx.values.bool("flipH")
        val flipV = ctx.values.bool("flipV")
        if (degrees == 0f && !flipH && !flipV) return ProcessingResult.ValidationFailure("Choose a rotation or a flip.")
        var bitmap = ImageCodec.decode(ctx.io, input.uri)          // already upright (EXIF applied)
        val hadAlpha = bitmap.hasAlpha()
        val m = Matrix()
        if (flipH) m.postScale(-1f, 1f)
        if (flipV) m.postScale(1f, -1f)
        if (degrees != 0f) m.postRotate(degrees)
        bitmap = ImageCodec.transform(bitmap, m)
        val format = ImageFormat.resolve(ctx.values.string("format"), detected)
        return finish(ctx, input, bitmap, "edited", format, ctx.values.int("quality") ?: 90, "Rotation / flip applied.", metadataNote(format, hadAlpha))
    }
}

class GrayscaleTool : SingleImageTool() {
    override val fields = listOf(formatField, qualityField)

    override fun process(ctx: ToolRunContext, input: InputFile, detected: DetectedType): ProcessingResult<ToolOutput> {
        val source = ImageCodec.decode(ctx.io, input.uri)
        val hadAlpha = source.hasAlpha()
        val gray = ImageCodec.toGrayscale(source)
        val format = ImageFormat.resolve(ctx.values.string("format"), detected)
        return finish(ctx, input, gray, "gray", format, ctx.values.int("quality") ?: 90, "Converted to grayscale.", metadataNote(format, hadAlpha))
    }
}

/** Lower-level helper kept public for tests / future tools. */
fun sampledSizeFor(original: PixelSize, maxEdge: Int): Int = ImageMath.previewSampleSize(original, maxEdge)
