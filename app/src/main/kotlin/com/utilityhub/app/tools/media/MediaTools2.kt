package com.utilityhub.app.tools.media

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputFile
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.app.tools.image.ImageCodec
import com.utilityhub.app.tools.image.ImageFormat
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.Validation
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.media.MediaMath
import com.utilityhub.logic.spec.FieldSpec
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

private fun durationMs(ctx: ToolRunContext, input: InputFile): Long? {
    val r = MediaMetadataRetriever()
    return try {
        r.setDataSource(ctx.context, input.uri)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } catch (_: RuntimeException) {
        null
    } finally {
        r.release()
    }
}

class VideoThumbnailTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("video/*")
    override val pickerLabel = "Choose a video"
    override val fields = listOf(
        FieldSpec.Text("time", "Time of the frame", "0:01", hint = "seconds or mm:ss"),
        FieldSpec.Choice("format", "Image format", listOf("JPEG", "PNG"), "JPEG"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose a video first.")
        val timeMs = when (val t = MediaMath.parseTimestamp(ctx.values.string("time", "0:01"))) {
            is Validation.Valid -> t.value
            is Validation.Invalid -> return ProcessingResult.ValidationFailure(t.message)
        }
        val duration = durationMs(ctx, input) ?: return ProcessingResult.Unsupported("This file could not be read as a video.")
        if (timeMs > duration) return ProcessingResult.ValidationFailure("That time is beyond the end of the video (${MediaMath.formatDuration(duration)}).")
        val retriever = MediaMetadataRetriever()
        val frame: Bitmap = try {
            retriever.setDataSource(ctx.context, input.uri)
            retriever.getFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: RuntimeException) {
            null
        } finally {
            retriever.release()
        } ?: return ProcessingResult.Unsupported("A frame could not be decoded at that time. The video codec may not be supported on this device.")
        val format = if (ctx.values.string("format") == "PNG") ImageFormat.PNG else ImageFormat.JPEG
        val file = ctx.newOutputFile(FileNames.outputName(input.name, "frame", format.extension))
        try { ImageCodec.save(frame, file, format, 92) } finally { frame.recycle() }
        val size = ImageCodec.validateOutput(file) ?: run { file.delete(); return ProcessingResult.Failure("The image could not be verified, so it was discarded.") }
        return ProcessingResult.Success(ToolOutput("Saved the frame at ${MediaMath.formatDuration(timeMs)}.", listOf("Size" to "$size"), listOf(OutputFile(file, file.name, format.mime))))
    }
}

/**
 * Lossless trim: samples are copied, never re-encoded.
 * Video cuts start at the keyframe at or before the chosen start (so the clip may begin slightly early); audio-only cuts are exact.
 */
abstract class TrimTool(private val videoMode: Boolean) : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = if (videoMode) arrayOf("video/*") else arrayOf("audio/*", "video/*")
    override val pickerLabel = if (videoMode) "Choose a video" else "Choose an audio file"
    override val fields = listOf(
        FieldSpec.Text("start", "Start", "0:00", hint = "seconds or mm:ss"),
        FieldSpec.Text("end", "End", hint = "Leave blank for the end of the file"),
    )

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose a file first.")
        if (input.sizeBytes > Limits.VIDEO_INPUT_BYTES) return ProcessingResult.ValidationFailure("This file is larger than ${FileNames.humanSize(Limits.VIDEO_INPUT_BYTES)}.")
        val duration = durationMs(ctx, input) ?: return ProcessingResult.Unsupported("This file could not be read as media.")
        val startMs = when (val s = MediaMath.parseTimestamp(ctx.values.string("start", "0:00"))) { is Validation.Valid -> s.value; is Validation.Invalid -> return ProcessingResult.ValidationFailure("Start: ${s.message}") }
        val endRaw = ctx.values.string("end").trim()
        val endMs = if (endRaw.isEmpty()) duration else when (val e = MediaMath.parseTimestamp(endRaw)) { is Validation.Valid -> e.value; is Validation.Invalid -> return ProcessingResult.ValidationFailure("End: ${e.message}") }
        val length = when (val v = MediaMath.validateTrim(startMs, endMs, duration)) { is Validation.Valid -> v.value; is Validation.Invalid -> return ProcessingResult.ValidationFailure(v.message) }

        val out = ctx.newOutputFile(FileNames.outputName(input.name, "trimmed", if (videoMode) "mp4" else "m4a"))
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            try { extractor.setDataSource(ctx.context, input.uri, null) } catch (e: IOException) { return ProcessingResult.Unsupported("This file could not be opened.") }
            val mapping = HashMap<Int, Int>()          // extractor track -> muxer track
            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                val wanted = if (videoMode) mime.startsWith("video/") || mime.startsWith("audio/") else mime.startsWith("audio/")
                if (!wanted) continue
                if (!videoMode && mime != MediaFormat.MIMETYPE_AUDIO_AAC) return ProcessingResult.Unsupported("This audio is \"$mime\". This version trims AAC audio (.m4a / most phone recordings) without re-encoding.")
                try { mapping[i] = m.addTrack(f) } catch (e: IllegalArgumentException) { return ProcessingResult.Unsupported("The codec \"$mime\" cannot be trimmed without re-encoding on this version.") }
                extractor.selectTrack(i)
                if (!videoMode) break
            }
            if (mapping.isEmpty()) return ProcessingResult.Unsupported(if (videoMode) "No video or audio track was found." else "This file has no audio track.")
            m.start(); started = true
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val endUs = endMs * 1000
            val buffer = ByteBuffer.allocate(2 shl 20)
            val info = MediaCodec.BufferInfo()
            var base = -1L
            var written = 0
            while (true) {
                ctx.checkCancelled()
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val time = extractor.sampleTime
                if (time > endUs) break
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (base < 0) base = time
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, (time - base).coerceAtLeast(0), flags)
                m.writeSampleData(mapping.getValue(track), buffer, info)
                written++
                ctx.reportProgress(((time - startMs * 1000).toFloat() / (endUs - startMs * 1000)).coerceIn(0f, 1f))
                extractor.advance()
            }
            if (written == 0) return ProcessingResult.Failure("Nothing could be read in that time range.")
            m.stop(); started = false
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            try { if (started) muxer?.stop() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
            extractor.release()
        }
        if (!out.exists() || out.length() <= 0) { out.delete(); return ProcessingResult.Failure("The trimmed file could not be verified, so it was discarded.") }
        val details = buildList {
            add("Selected length" to MediaMath.formatDuration(length))
            add("File size" to FileNames.humanSize(out.length()))
            if (videoMode) add("Accuracy" to "Cuts begin at the nearest earlier keyframe, so the start can be a little early.")
        }
        return ProcessingResult.Success(ToolOutput("Trimmed without re-encoding.", details, listOf(OutputFile(out, out.name, if (videoMode) "video/mp4" else "audio/mp4"))))
    }
}

class VideoTrimTool : TrimTool(true)
class AudioTrimTool : TrimTool(false)
