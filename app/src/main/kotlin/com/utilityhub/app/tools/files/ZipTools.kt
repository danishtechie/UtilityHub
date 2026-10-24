package com.utilityhub.app.tools.files

import android.webkit.MimeTypeMap
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.Limits
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.archive.ArchiveLimits
import com.utilityhub.logic.archive.EntryNameCheck
import com.utilityhub.logic.archive.ExtractionGuard
import com.utilityhub.logic.archive.UniqueNames
import com.utilityhub.logic.archive.ZipEntryNames
import com.utilityhub.logic.files.DetectedType
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.spec.FieldSpec
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipCreateTool(private val maxFiles: Int) : FileTool {
    override val inputRequirement = InputRequirement.MULTIPLE
    override val acceptedMimeTypes = arrayOf("*/*")
    override val pickerLabel = "Choose files to zip"
    override val maxInputs get() = maxFiles
    override val fields = listOf(FieldSpec.Text("name", "Archive name", "archive"))

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        if (ctx.inputs.isEmpty()) return ProcessingResult.ValidationFailure("Choose at least one file.")
        if (ctx.inputs.size > maxFiles) return ProcessingResult.ValidationFailure("At most $maxFiles files per archive.")
        val knownTotal = ctx.inputs.all { it.sizeBytes >= 0 }
        val total = ctx.inputs.sumOf { it.sizeBytes.coerceAtLeast(0) }
        if (total > Limits.ZIP_INPUT_TOTAL_BYTES) {
            return ProcessingResult.ValidationFailure("The selected files are larger than ${FileNames.humanSize(Limits.ZIP_INPUT_TOTAL_BYTES)} in total.")
        }
        val baseName = FileNames.sanitize(ctx.values.string("name", "archive").removeSuffix(".zip"), "archive")
        val out = ctx.newOutputFile("$baseName.zip")
        val names = UniqueNames()
        var written = 0L
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
                for (input in ctx.inputs) {
                    ctx.checkCancelled()
                    zip.putNextEntry(ZipEntry(names.claim(FileNames.sanitize(input.name))))
                    ctx.io.open(input.uri).use { stream ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            ctx.checkCancelled()
                            val n = stream.read(buf)
                            if (n < 0) break
                            written += n
                            if (written > Limits.ZIP_INPUT_TOTAL_BYTES) {
                                return ProcessingResult.ValidationFailure("The files are larger than the supported total size.").also { out.delete() }
                            }
                            zip.write(buf, 0, n)
                            if (knownTotal && total > 0) ctx.reportProgress((written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    zip.closeEntry()
                }
            }
            val entries = ZipFile(out).use { it.size() }
            if (entries != ctx.inputs.size) {
                out.delete()
                return ProcessingResult.Failure("The archive could not be verified, so it was discarded.")
            }
        } catch (t: Throwable) {
            out.delete()
            throw t
        }
        return ProcessingResult.Success(
            ToolOutput(
                "Created a ZIP archive with ${ctx.inputs.size} file(s).",
                listOf("Files" to ctx.inputs.size.toString(), "Original size" to FileNames.humanSize(written), "Archive size" to FileNames.humanSize(out.length())),
                listOf(OutputFile(out, out.name, "application/zip")),
            ),
        )
    }
}

/**
 * Safe extractor. Entry names and sizes in an archive are attacker-controlled, so:
 *  - any unsafe path (.., absolute, drive letters, backslashes) rejects the WHOLE archive;
 *  - real decompressed bytes are counted (entries, per-file, total, compression ratio);
 *  - only regular files are ever written (symlink entries become ordinary files, nothing is executed);
 *  - output stays inside app-private storage until the user chooses where to save it.
 */
class ZipExtractTool : FileTool {
    override val inputRequirement = InputRequirement.SINGLE
    override val acceptedMimeTypes = arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
    override val pickerLabel = "Choose a ZIP file"

    override fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput> {
        val input = ctx.inputs.firstOrNull() ?: return ProcessingResult.ValidationFailure("Choose a ZIP file first.")
        val type = ctx.io.detect(input.uri)
        if (type != DetectedType.ZIP) return ProcessingResult.Unsupported("This is not a ZIP archive (detected: ${type.label}).")
        val limits = ArchiveLimits()
        val guard = ExtractionGuard(limits, input.sizeBytes)
        guard.checkArchiveSize()?.let { return ProcessingResult.ValidationFailure(it) }

        val root = File(ctx.sessionDir, "extracted").also { it.mkdirs() }
        val rootPath = root.canonicalPath + File.separator
        val names = UniqueNames()
        val outputs = ArrayList<OutputFile>()

        fun reject(message: String): ProcessingResult<ToolOutput> {
            root.deleteRecursively()
            return ProcessingResult.ValidationFailure(message)
        }

        try {
            ZipInputStream(BufferedInputStream(ctx.io.open(input.uri))).use { zin ->
                while (true) {
                    ctx.checkCancelled()
                    val entry = zin.nextEntry ?: break
                    guard.startEntry()?.let { return reject(it) }
                    val safe = when (val check = ZipEntryNames.check(entry.name, limits)) {
                        is EntryNameCheck.Safe -> check
                        is EntryNameCheck.Unsafe -> return reject("The archive contains an unsafe entry (${check.reason}), so nothing was extracted.")
                    }
                    if (entry.isDirectory) continue
                    val relative = names.claim(safe.path)
                    val target = File(root, relative)
                    val parent = target.parentFile
                    if (parent == null || (!parent.isDirectory && !parent.mkdirs())) {
                        return reject("The archive has conflicting file and folder names.")
                    }
                    if (!target.canonicalPath.startsWith(rootPath)) return reject("The archive tries to write outside the destination.")
                    var entryBytes = 0L
                    FileOutputStream(target).use { out ->
                        val buf = ByteArray(32 * 1024)
                        while (true) {
                            ctx.checkCancelled()
                            val n = zin.read(buf)
                            if (n < 0) break
                            entryBytes += n
                            guard.addBytes(entryBytes, n.toLong())?.let { return reject(it) }
                            out.write(buf, 0, n)
                        }
                    }
                    outputs += OutputFile(target, target.name, mimeFor(target.name), relative)
                }
            }
        } catch (e: ZipException) {
            root.deleteRecursively()
            return ProcessingResult.ValidationFailure("This ZIP archive is damaged or uses features that are not supported (for example password protection).")
        } catch (t: Throwable) {
            root.deleteRecursively()
            throw t
        }
        if (outputs.isEmpty()) return reject("The archive does not contain any files.")
        return ProcessingResult.Success(
            ToolOutput(
                "Extracted ${outputs.size} file(s) safely.",
                listOf("Files" to outputs.size.toString(), "Total size" to FileNames.humanSize(guard.totalBytes)),
                outputs,
            ),
        )
    }

    private fun mimeFor(name: String): String {
        val ext = FileNames.extension(name)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }
}
