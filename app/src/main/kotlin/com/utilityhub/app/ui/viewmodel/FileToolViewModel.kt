package com.utilityhub.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utilityhub.app.core.ErrorMessages
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputFile
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.OperationCancelledException
import com.utilityhub.app.core.ToolOutput
import com.utilityhub.app.core.ToolRunContext
import com.utilityhub.app.core.TreeWriter
import com.utilityhub.app.data.HistoryEntry
import com.utilityhub.app.di.AppContainer
import com.utilityhub.logic.ProcessingResult
import com.utilityhub.logic.catalog.ToolMeta
import com.utilityhub.logic.entitlement.Reservation
import com.utilityhub.logic.entitlement.ReserveResult
import com.utilityhub.logic.spec.FieldValues
import com.utilityhub.logic.spec.defaultValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class Phase { Idle, Running, Done, Failed, Cancelled }

data class FileToolState(
    val inputs: List<InputFile> = emptyList(),
    val values: Map<String, String> = emptyMap(),
    val phase: Phase = Phase.Idle,
    val progress: Float? = null,
    val cancelling: Boolean = false,
    val output: ToolOutput? = null,
    val message: String? = null,        // error / cancel text
    val notice: String? = null,         // "Saved", "Could not save", ...
    val showPaywall: Boolean = false,
)

/**
 * Runs one file tool. Entitlement flow (metered tools only):
 *   reserve()  -> run on IO -> validate output -> finalizeSuccess()   (counts exactly once)
 *   anything else (denied / validation error / failure / cancel / process death) -> nothing is consumed.
 * A second tap while running is ignored, so repeated submissions cannot start parallel work.
 */
class FileToolViewModel(private val c: AppContainer, private val meta: ToolMeta, val tool: FileTool) : ViewModel() {
    private val _state = MutableStateFlow(FileToolState(values = tool.fields.defaultValues()))
    val state: StateFlow<FileToolState> = _state.asStateFlow()

    private var job: Job? = null
    private var cancelFlag = AtomicBoolean(false)
    private var sessionDir: File? = null

    fun addInputs(uris: List<Uri>) {
        viewModelScope.launch {
            val described = withContext(Dispatchers.IO) { uris.map { c.fileAccess.describe(it) } }
            _state.update { s ->
                val merged = if (tool.inputRequirement == InputRequirement.SINGLE) described.take(1)
                else (s.inputs + described).distinctBy { it.uri }.take(tool.maxInputs)
                s.copy(inputs = merged, phase = Phase.Idle, output = null, message = null, notice = null)
            }
        }
    }

    fun removeInput(index: Int) = _state.update { s -> s.copy(inputs = s.inputs.filterIndexed { i, _ -> i != index }) }

    fun moveInput(index: Int, delta: Int) = _state.update { s ->
        val target = index + delta
        if (target !in s.inputs.indices) s else s.copy(inputs = s.inputs.toMutableList().also { val x = it.removeAt(index); it.add(target, x) })
    }

    fun setValue(key: String, value: String) = _state.update { it.copy(values = it.values + (key to value)) }
    fun consumePaywall() = _state.update { it.copy(showPaywall = false) }
    fun clearNotice() = _state.update { it.copy(notice = null) }

    fun reset() {
        if (job?.isActive == true) return
        c.outputStore.deleteSession(sessionDir)
        sessionDir = null
        _state.update { it.copy(phase = Phase.Idle, output = null, message = null, notice = null, inputs = if (tool.inputRequirement == InputRequirement.NONE) it.inputs else emptyList()) }
    }

    fun cancel() {
        if (job?.isActive != true) return
        cancelFlag.set(true)
        _state.update { it.copy(cancelling = true) }
    }

    fun start() {
        if (job?.isActive == true) return                       // ignore duplicate taps
        val s = _state.value
        if (tool.inputRequirement != InputRequirement.NONE && s.inputs.isEmpty()) {
            _state.update { it.copy(phase = Phase.Failed, message = "Choose ${if (tool.inputRequirement == InputRequirement.SINGLE) "a file" else "at least one file"} first.") }
            return
        }
        if (!c.outputStore.hasFreeSpace()) {
            _state.update { it.copy(phase = Phase.Failed, message = "There is not enough free storage. Clear temporary files in Settings or free up space.") }
            return
        }
        c.outputStore.deleteSession(sessionDir)
        cancelFlag = AtomicBoolean(false)
        val flag = cancelFlag
        job = viewModelScope.launch {
            var reservation: Reservation? = null
            try {
                if (meta.metered) {
                    when (val r = c.entitlement.reserve()) {
                        is ReserveResult.Denied -> { _state.update { it.copy(showPaywall = true) }; return@launch }
                        is ReserveResult.Granted -> reservation = r.reservation
                    }
                }
                val dir = c.outputStore.newSession()
                sessionDir = dir
                _state.update { it.copy(phase = Phase.Running, progress = null, cancelling = false, output = null, message = null, notice = null) }
                val ctx = ToolRunContext(c.app, c.fileAccess, s.inputs, FieldValues(s.values), dir, flag) { p -> _state.update { it.copy(progress = p) } }
                val result = withContext(Dispatchers.IO) { runSafely(ctx) }
                when (result) {
                    is ProcessingResult.Success -> {
                        val files = result.value.files
                        if (files.any { !it.file.exists() || it.file.length() <= 0L }) {
                            fail(dir, "The result could not be verified, so it was discarded.")
                        } else {
                            reservation?.let { c.entitlement.finalizeSuccess(it) }    // consumes exactly once
                            c.history.add(HistoryEntry(meta.id, meta.title, files.size, System.currentTimeMillis()))
                            _state.update { it.copy(phase = Phase.Done, output = result.value, progress = null) }
                        }
                    }
                    ProcessingResult.Cancelled -> { c.outputStore.deleteSession(dir); _state.update { it.copy(phase = Phase.Cancelled, progress = null, cancelling = false, message = "Cancelled. Nothing was used from your free allowance.") } }
                    is ProcessingResult.ValidationFailure -> fail(dir, result.message)
                    is ProcessingResult.Unsupported -> fail(dir, result.message)
                    is ProcessingResult.Failure -> fail(dir, result.message)
                }
            } finally {
                reservation?.let { c.entitlement.release(it) }          // harmless after finalize; frees the slot otherwise
            }
        }
    }

    private fun fail(dir: File, message: String) {
        c.outputStore.deleteSession(dir)                                  // never leave partial output behind
        _state.update { it.copy(phase = Phase.Failed, message = message, progress = null, cancelling = false) }
    }

    private fun runSafely(ctx: ToolRunContext): ProcessingResult<ToolOutput> = try {
        tool.run(ctx)
    } catch (e: OperationCancelledException) {
        ProcessingResult.Cancelled
    } catch (e: OutOfMemoryError) {
        ProcessingResult.Failure(ErrorMessages.from(e))
    } catch (e: Exception) {
        ProcessingResult.Failure(ErrorMessages.from(e))                    // message is path-free; nothing is logged
    }

    fun saveSingle(destination: Uri) {
        val file = _state.value.output?.files?.firstOrNull()?.file ?: return
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { TreeWriter.copyFile(c.app, file, destination) }.isSuccess }
            _state.update { it.copy(notice = if (ok) "Saved." else "Could not save the file to that location.") }
        }
    }

    fun saveToFolder(tree: Uri) {
        val files = _state.value.output?.files ?: return
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { runCatching { TreeWriter.copyAll(c.app, tree, files) }.getOrNull() }
            _state.update { it.copy(notice = if (count != null) "Saved $count file(s)." else "Could not save to that folder.") }
        }
    }

    fun shareUris(): List<Pair<Uri, String>> = _state.value.output?.files.orEmpty().map { c.outputStore.uriFor(it.file) to it.mimeType }

    override fun onCleared() {
        // Results live in the cache until the user clears them or they go stale; nothing to release here.
        cancelFlag.set(true)
    }
}
