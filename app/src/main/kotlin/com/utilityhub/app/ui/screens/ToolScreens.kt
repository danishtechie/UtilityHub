package com.utilityhub.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.utilityhub.app.UtilityHubApp
import com.utilityhub.app.core.FileTool
import com.utilityhub.app.core.InputRequirement
import com.utilityhub.app.core.OutputFile
import com.utilityhub.app.tools.ToolHost
import com.utilityhub.app.tools.ToolRegistry
import com.utilityhub.app.tools.form.FormTool
import com.utilityhub.app.ui.components.EmptyState
import com.utilityhub.app.ui.components.FieldEditor
import com.utilityhub.app.ui.components.KeyValueRow
import com.utilityhub.app.ui.components.PrimaryButton
import com.utilityhub.app.ui.components.SecondaryButton
import com.utilityhub.app.ui.viewmodel.FileToolViewModel
import com.utilityhub.app.ui.viewmodel.FormViewModel
import com.utilityhub.app.ui.viewmodel.MainViewModel
import com.utilityhub.app.ui.viewmodel.Phase
import com.utilityhub.logic.catalog.ToolMeta
import com.utilityhub.logic.catalog.ToolStatus
import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.image.ImageMath
import com.utilityhub.logic.image.PixelSize
import com.utilityhub.logic.spec.FieldSpec
import com.utilityhub.logic.util.UnitConverter
import com.utilityhub.logic.Validation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun appContainer() = (LocalContext.current.applicationContext as UtilityHubApp).container

@Composable
fun ToolRoute(toolId: String, main: MainViewModel, onBack: () -> Unit, onPaywall: () -> Unit) {
    val meta = com.utilityhub.logic.catalog.ToolCatalog.byId(toolId)
    val host = ToolRegistry.host(toolId)
    if (meta == null || host == null || meta.status != ToolStatus.AVAILABLE) {
        ToolScaffold(meta?.title ?: "Tool", onBack) {
            EmptyState("Planned", "This tool is not available in this version of the app.")
        }
        return
    }
    when (host) {
        is ToolHost.Form -> FormToolScreen(meta, host.tool, onBack)
        is ToolHost.Files -> FileToolScreen(meta, host.tool, main, onBack, onPaywall)
        ToolHost.UnitConverter -> UnitConverterScreen(meta, onBack)
        is ToolHost.Custom -> when (host.kind) {
            com.utilityhub.app.tools.CustomKind.CALCULATOR -> CalculatorScreen(meta, onBack)
            com.utilityhub.app.tools.CustomKind.STOPWATCH -> StopwatchScreen(meta, onBack)
            com.utilityhub.app.tools.CustomKind.NOTES -> NotesScreen(meta, onBack)
            com.utilityhub.app.tools.CustomKind.MARKDOWN -> MarkdownScreen(meta, onBack)
            com.utilityhub.app.tools.CustomKind.SPEECH -> SpeechScreen(meta, onBack)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolScaffold(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
private fun MessageCard(text: String, error: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)) {
        Text(
            text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp),
            color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TextResult(text: String) {
    val clipboard = LocalClipboardManager.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp)) {
            SelectionContainer { Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) }
            Spacer(Modifier.height(8.dp))
            // Clipboard is only written on this explicit tap.
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Copy") }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Form tools (instant, free)
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun FormToolScreen(meta: ToolMeta, tool: FormTool, onBack: () -> Unit) {
    val vm: FormViewModel = viewModel(key = meta.id, factory = viewModelFactory { initializer { FormViewModel(tool) } })
    val s by vm.state.collectAsStateWithLifecycle()
    ToolScaffold(meta.title, onBack) {
        Text(meta.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        tool.fields.forEach { spec -> FieldEditor(spec, s.values[spec.key] ?: "") { vm.setValue(spec.key, it) } }
        PrimaryButton(tool.actionLabel, onClick = vm::run)
        s.error?.let { MessageCard(it, error = true) }
        s.output?.let { out ->
            out.note?.let { MessageCard(it, error = false) }
            TextResult(out.text)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Unit converter (needs dependent dropdowns, so it has its own small screen)
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun UnitConverterScreen(meta: ToolMeta, onBack: () -> Unit) {
    val categories = UnitConverter.categories.keys.toList()
    var category by rememberSaveable { mutableStateOf(categories.first()) }
    var from by rememberSaveable { mutableStateOf(UnitConverter.unitNames(category).first()) }
    var to by rememberSaveable { mutableStateOf(UnitConverter.unitNames(category)[1]) }
    var value by rememberSaveable { mutableStateOf("1") }
    val units = UnitConverter.unitNames(category)
    val parsed = value.trim().replace(',', '.').toDoubleOrNull()
    ToolScaffold(meta.title, onBack) {
        FieldEditor(FieldSpec.Choice("category", "Category", categories, category), category) { picked ->
            category = picked
            from = UnitConverter.unitNames(picked).first()
            to = UnitConverter.unitNames(picked)[1]
        }
        FieldEditor(FieldSpec.Text("value", "Value"), value) { value = it }
        FieldEditor(FieldSpec.Choice("from", "From", units, from), from) { from = it }
        FieldEditor(FieldSpec.Choice("to", "To", units, to), to) { to = it }
        when {
            parsed == null -> MessageCard("Enter a valid number.", error = true)
            else -> when (val r = UnitConverter.convert(category, from, to, parsed)) {
                is Validation.Valid -> TextResult("${UnitConverter.format(parsed)} $from = ${UnitConverter.format(r.value)} $to")
                is Validation.Invalid -> MessageCard(r.message, error = true)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// File tools
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun FileToolScreen(meta: ToolMeta, tool: FileTool, main: MainViewModel, onBack: () -> Unit, onPaywall: () -> Unit) {
    val container = appContainer()
    val vm: FileToolViewModel = viewModel(key = meta.id, factory = viewModelFactory { initializer { FileToolViewModel(container, meta, tool) } })
    val s by vm.state.collectAsStateWithLifecycle()
    val snapshot by main.entitlement.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val multiple = tool.inputRequirement == InputRequirement.MULTIPLE

    LaunchedEffect(s.showPaywall) { if (s.showPaywall) { vm.consumePaywall(); onPaywall() } }

    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.addInputs(listOf(uri)) }
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> if (uris.isNotEmpty()) vm.addInputs(uris) }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.addInputs(listOf(uri)) }
    val pickMedias = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris -> if (uris.isNotEmpty()) vm.addInputs(uris.take(tool.maxInputs)) }

    fun choose() {
        val mimes = tool.acceptedMimeTypes
        val imageOnly = !tool.preferDocumentPicker && mimes.isNotEmpty() && mimes.all { it.startsWith("image/") }
        val videoOnly = !tool.preferDocumentPicker && mimes.isNotEmpty() && mimes.all { it == "video/*" }
        when {
            imageOnly && multiple -> pickMedias.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            imageOnly -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            videoOnly -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            multiple -> pickMany.launch(mimes)
            else -> pickOne.launch(mimes)
        }
    }

    val firstFile = s.output?.files?.firstOrNull()
    val createDoc = rememberLauncherForActivityResult(
        remember(firstFile?.mimeType) { ActivityResultContracts.CreateDocument(firstFile?.mimeType ?: "*/*") },
    ) { uri -> if (uri != null) vm.saveSingle(uri) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) vm.saveToFolder(uri) }

    ToolScaffold(meta.title, onBack) {
        Text(meta.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        val snap = snapshot
        if (meta.metered && snap != null && !snap.hasPaidAccess) {
            MessageCard("Uses 1 of your ${snap.freeRemaining} remaining free operations when it finishes successfully. Cancelled or failed runs are not counted.", error = false)
        } else if (!meta.metered) {
            MessageCard("This tool is free and does not use your allowance.", error = false)
        }

        val running = s.phase == Phase.Running

        if (tool.inputRequirement != InputRequirement.NONE) {
            if (s.inputs.isEmpty()) {
                SecondaryButton(tool.pickerLabel, onClick = ::choose, enabled = !running, modifier = Modifier.fillMaxWidth())
            } else {
                s.inputs.forEachIndexed { index, file ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                if (file.sizeBytes >= 0) Text(FileNames.humanSize(file.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (tool.reorderable && s.inputs.size > 1 && !running) {
                                IconButton(onClick = { vm.moveInput(index, -1) }, enabled = index > 0) { Icon(Icons.Rounded.ArrowUpward, "Move ${file.name} up") }
                                IconButton(onClick = { vm.moveInput(index, 1) }, enabled = index < s.inputs.lastIndex) { Icon(Icons.Rounded.ArrowDownward, "Move ${file.name} down") }
                            }
                            if (!running) IconButton(onClick = { vm.removeInput(index) }) { Icon(Icons.Rounded.Close, "Remove ${file.name}") }
                        }
                    }
                }
                if (!running) {
                    if (multiple && s.inputs.size < tool.maxInputs) SecondaryButton("Add more (up to ${tool.maxInputs})", onClick = ::choose)
                    if (!multiple) SecondaryButton("Choose a different file", onClick = ::choose)
                }
            }
        }

        if (tool.fields.isNotEmpty()) {
            tool.fields.forEach { spec -> FieldEditor(spec, s.values[spec.key] ?: "") { vm.setValue(spec.key, it) } }
        }

        if (running) {
            if (s.progress != null) LinearProgressIndicator(progress = { s.progress ?: 0f }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(if (s.cancelling) "Cancelling…" else "Working… you can cancel at any time.", style = MaterialTheme.typography.bodySmall)
            SecondaryButton("Cancel", onClick = vm::cancel, enabled = !s.cancelling, modifier = Modifier.fillMaxWidth())
        } else {
            val ready = tool.inputRequirement == InputRequirement.NONE || s.inputs.isNotEmpty()
            PrimaryButton(if (s.phase == Phase.Done) "Run again" else "Start", onClick = vm::start, enabled = ready)
        }

        if ((s.phase == Phase.Failed || s.phase == Phase.Cancelled) && s.message != null) MessageCard(s.message!!, error = s.phase == Phase.Failed)

        val out = s.output
        if (s.phase == Phase.Done && out != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(out.summary, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    out.details.forEach { (k, v) -> KeyValueRow(k, v) }
                    val preview = out.files.firstOrNull { it.mimeType.startsWith("image/") }
                    if (preview != null) {
                        Spacer(Modifier.height(12.dp))
                        ImagePreview(preview.file)
                    }
                    if (out.files.size > 1 || (out.files.size == 1 && out.files[0].relativePath != out.files[0].displayName)) {
                        Spacer(Modifier.height(8.dp))
                        Text("${out.files.size} files", style = MaterialTheme.typography.labelLarge)
                        out.files.take(8).forEach { Text(it.relativePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (out.files.size > 8) Text("… and ${out.files.size - 8} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            out.text?.let { TextResult(it) }
            if (out.files.isNotEmpty()) {
                if (out.files.size == 1 && out.files[0].relativePath == out.files[0].displayName) {
                    PrimaryButton("Save as…", onClick = { createDoc.launch(out.files[0].displayName) })
                } else {
                    PrimaryButton("Save to folder…", onClick = { pickFolder.launch(null) })
                }
                SecondaryButton("Share", onClick = { share(context, vm.shareUris()) }, modifier = Modifier.fillMaxWidth())
                Text("Your original file is never changed. Results are new files; saving never overwrites an existing file.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SecondaryButton("Start over", onClick = vm::reset, modifier = Modifier.fillMaxWidth())
        }
        s.notice?.let { MessageCard(it, error = false) }
    }
}

private fun share(context: Context, uris: List<Pair<android.net.Uri, String>>) {
    if (uris.isEmpty()) return
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply { type = uris[0].second; putExtra(Intent.EXTRA_STREAM, uris[0].first) }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply { type = "*/*"; putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris.map { it.first })) }
    }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun ImagePreview(file: File) {
    val bitmap: ImageBitmap? by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0) null else {
                val opts = BitmapFactory.Options().apply { inSampleSize = ImageMath.previewSampleSize(PixelSize(bounds.outWidth, bounds.outHeight), 1024) }
                BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
            }
        }
    }
    bitmap?.let {
        Image(it, contentDescription = "Preview of the result", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
    }
}
