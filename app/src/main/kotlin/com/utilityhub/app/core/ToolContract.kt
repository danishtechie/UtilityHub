package com.utilityhub.app.core

import android.content.Context
import android.net.Uri
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.spec.FieldSpec
import com.utilityhub.logic.spec.FieldValues
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

data class InputFile(val uri: Uri, val name: String, val sizeBytes: Long, val mimeType: String?)

data class OutputFile(
    val file: File,
    val displayName: String,
    val mimeType: String,
    /** Path inside a chosen destination folder (sub-folders for extracted archives). */
    val relativePath: String = displayName,
)

data class ToolOutput(
    val summary: String,
    val details: List<Pair<String, String>> = emptyList(),
    val files: List<OutputFile> = emptyList(),
    val text: String? = null,
)

enum class InputRequirement { NONE, SINGLE, MULTIPLE }

class OperationCancelledException : RuntimeException("Operation cancelled")
class UnsupportedContentException(message: String) : IOException(message)
class FileTooLargeException(val limitBytes: Long) : IOException("The file is larger than the supported limit.")

/** Contract implemented by every file-processing tool. Runs on a background thread, never on the main thread. */
interface FileTool {
    val inputRequirement: InputRequirement
    val acceptedMimeTypes: Array<String>
    val pickerLabel: String
    val maxInputs: Int get() = 1
    val reorderable: Boolean get() = false
    /** The system Photo Picker redacts GPS/EXIF location; tools that must see it use the document picker instead. */
    val preferDocumentPicker: Boolean get() = false
    val fields: List<FieldSpec> get() = emptyList()
    fun run(ctx: ToolRunContext): ProcessingResult<ToolOutput>
}

class ToolRunContext(
    val context: Context,
    val io: FileAccess,
    val inputs: List<InputFile>,
    val values: FieldValues,
    val sessionDir: File,
    private val cancelled: AtomicBoolean,
    private val progressSink: (Float?) -> Unit,
) {
    fun checkCancelled() {
        if (cancelled.get()) throw OperationCancelledException()
    }

    /** Pass null when real progress is unknown - never invent a percentage. */
    fun reportProgress(fraction: Float?) = progressSink(fraction)

    fun newOutputFile(safeName: String): File = File(sessionDir, safeName)
}
