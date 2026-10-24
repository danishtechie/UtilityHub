package com.utilityhub.app.tools.image

import android.media.ExifInterface
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.files.ContentSniffer
import com.utilityhub.logic.files.DetectedType
import com.utilityhub.logic.files.FileNames
import com.utilityhub.app.core.FileTool

/** Read-only inspector: nothing is written and nothing leaves the device. Values are never logged. */
class ImageMetadataTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("image/*")
    override val pickerLabel = "Choose an image"
    override val preferDocumentPicker = true

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose an image first.")
        val type = ctx.io.detect(input.uri)
        if (!ContentSniffer.isImage(type) && type != DetectedType.MP4) {
            return ProcessingResult.Unsupported("This file is not a supported image (detected: ${type.label}).")
        }
        val rows = ArrayList<Pair<String, String>>()
        rows += "Detected format" to type.label
        if (input.sizeBytes >= 0) rows += "File size" to "${FileNames.humanSize(input.sizeBytes)} (${input.sizeBytes} bytes)"
        ImageCodec.readBounds(ctx.io, input.uri)?.let { rows += "Pixel dimensions" to "$it" }

        var hasLocation = false
        try {
            ctx.io.open(input.uri).use { stream ->
                val exif = ExifInterface(stream)
                fun add(label: String, tag: String) { exif.getAttribute(tag)?.takeIf { it.isNotBlank() }?.let { rows += label to it } }
                add("Camera make", ExifInterface.TAG_MAKE)
                add("Camera model", ExifInterface.TAG_MODEL)
                add("Software", ExifInterface.TAG_SOFTWARE)
                add("Date taken", ExifInterface.TAG_DATETIME)
                add("Exposure time (s)", ExifInterface.TAG_EXPOSURE_TIME)
                add("Aperture (f-number)", ExifInterface.TAG_F_NUMBER)
                @Suppress("DEPRECATION") add("ISO", ExifInterface.TAG_ISO_SPEED_RATINGS)
                add("Focal length (mm)", ExifInterface.TAG_FOCAL_LENGTH)
                add("Orientation code", ExifInterface.TAG_ORIENTATION)
                val latLong = FloatArray(2)
                if (exif.getLatLong(latLong)) {
                    hasLocation = true
                    rows += "GPS location" to String.format(java.util.Locale.US, "%.5f, %.5f", latLong[0], latLong[1])
                }
            }
        } catch (_: Exception) {
            rows += "EXIF" to "Could not be read (this format may not carry EXIF data)."
        }
        val summary = if (hasLocation)
            "This photo contains GPS location data. Anyone you send the original to can see where it was taken. UtilityHub's resize, compress and convert tools strip it."
        else "No GPS location data found."
        return ProcessingResult.Success(ToolOutput(summary, rows))
    }
}
