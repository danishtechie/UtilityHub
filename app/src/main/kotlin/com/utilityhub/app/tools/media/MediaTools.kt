package com.utilityhub.app.tools.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.media.MediaMath
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Copies the AAC audio track of a video into an .m4a container WITHOUT re-encoding (lossless, fast, low memory).
 * Samples are streamed through a 1 MiB buffer, so large videos are never loaded into memory.
 * Other audio codecs (Opus, Vorbis, AC-3, ...) are reported as unsupported rather than faked.
 */
class VideoToAudioTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("video/*")
    override val pickerLabel = "Choose a video"

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose a video first.")
        if (input.sizeBytes > Limits.VIDEO_INPUT_BYTES) return ProcessingResult.ValidationFailure("This video is larger than ${FileNames.humanSize(Limits.VIDEO_INPUT_BYTES)}.")
        val out = ctx.newOutputFile(FileNames.outputName(input.name, "audio", "m4a"))
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            try {
                extractor.setDataSource(ctx.context, input.uri, null)
            } catch (e: IOException) {
                return ProcessingResult.Unsupported("This file could not be opened as a video. It may be damaged or use an unsupported container.")
            }
            var audioIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { audioIndex = i; audioFormat = f; break }
            }
            if (audioIndex < 0 || audioFormat == null) return ProcessingResult.Unsupported("This video has no audio track.")
            val mime = audioFormat.getString(MediaFormat.KEY_MIME)
            if (mime != MediaFormat.MIMETYPE_AUDIO_AAC) {
                return ProcessingResult.Unsupported("The audio in this video is \"$mime\". This version extracts AAC audio (used by most phone videos) without re-encoding; other codecs are not supported yet.")
            }
            extractor.selectTrack(audioIndex)
            val durationUs = if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) audioFormat.getLong(MediaFormat.KEY_DURATION) else 0L

            val m = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = m
            val track = m.addTrack(audioFormat)
            m.start()
            muxerStarted = true

            val buffer = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            var samples = 0
            while (true) {
                ctx.checkCancelled()
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                info.set(0, size, time, flags)
                m.writeSampleData(track, buffer, info)
                samples++
                ctx.reportProgress(MediaMath.progress(time, durationUs))   // null when the duration is unknown
                extractor.advance()
            }
            if (samples == 0) return ProcessingResult.Failure("No audio data could be read from this video.")
            m.stop()
            muxerStarted = false
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            try { if (muxerStarted) muxer?.stop() } catch (_: Exception) { }
            try { muxer?.release() } catch (_: Exception) { }
            extractor.release()
        }
        if (!isPlayableAudio(out)) {
            out.delete()
            return ProcessingResult.Failure("The audio file could not be verified, so it was discarded.")
        }
        return ProcessingResult.Success(
            ToolOutput(
                "Audio extracted without quality loss.",
                listOf("Format" to "AAC in .m4a", "File size" to FileNames.humanSize(out.length())),
                listOf(OutputFile(out, out.name, "audio/mp4")),
            ),
        )
    }

    private fun isPlayableAudio(file: java.io.File): Boolean {
        if (!file.exists() || file.length() <= 0) return false
        val check = MediaExtractor()
        return try {
            check.setDataSource(file.absolutePath)
            check.trackCount >= 1
        } catch (_: Exception) {
            false
        } finally {
            check.release()
        }
    }
}

/** Read-only inspector for video and audio files. */
class MediaInfoTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("video/*", "audio/*")
    override val pickerLabel = "Choose a video or audio file"

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose a file first.")
        val rows = ArrayList<Pair<String, String>>()
        if (input.sizeBytes >= 0) rows += "File size" to FileNames.humanSize(input.sizeBytes)
        val retriever = MediaMetadataRetriever()
        try {
            try {
                retriever.setDataSource(ctx.context, input.uri)
            } catch (e: RuntimeException) {
                return ProcessingResult.Unsupported("This file could not be read as media. It may be damaged or use an unsupported format.")
            }
            fun meta(key: Int) = retriever.extractMetadata(key)
            val durationMs = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durationMs != null) rows += "Duration" to MediaMath.formatDuration(durationMs)
            meta(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)?.let { rows += "Container" to it }
            val w = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val h = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            if (w != null && h != null) rows += "Resolution" to "$w × $h"
            meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let { rows += "Rotation" to "$it°" }
            meta(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { rows += "Overall bitrate" to "${it / 1000} kbps" }
            if (Build.VERSION.SDK_INT >= 28 && durationMs != null && durationMs > 0) {
                meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()?.let {
                    rows += "Average frame rate" to String.format(java.util.Locale.US, "%.2f fps", it * 1000.0 / durationMs)
                }
            }
        } finally {
            retriever.release()
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(ctx.context, input.uri, null)
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: "unknown"
                val detail = when {
                    mime.startsWith("video/") -> buildString {
                        if (f.containsKey(MediaFormat.KEY_WIDTH)) append("${f.getInteger(MediaFormat.KEY_WIDTH)} × ${f.getInteger(MediaFormat.KEY_HEIGHT)}")
                        if (f.containsKey(MediaFormat.KEY_FRAME_RATE)) append(", ${runCatching { f.getInteger(MediaFormat.KEY_FRAME_RATE) }.getOrNull() ?: "?"} fps")
                    }
                    mime.startsWith("audio/") -> buildString {
                        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) append("${f.getInteger(MediaFormat.KEY_SAMPLE_RATE)} Hz")
                        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) append(", ${f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)} ch")
                    }
                    else -> ""
                }
                rows += "Track ${i + 1}" to (mime + if (detail.isNotEmpty()) " ($detail)" else "")
            }
            if (extractor.trackCount == 0) rows += "Tracks" to "none found"
        } catch (_: Exception) {
            rows += "Tracks" to "could not be listed"
        } finally {
            extractor.release()
        }
        return ProcessingResult.Success(ToolOutput("Media details", rows))
    }
}
