package com.utilityhub.app.ui.screens

import android.media.RingtoneManager
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.utilityhub.app.ui.components.FieldEditor
import com.utilityhub.app.ui.components.PrimaryButton
import com.utilityhub.app.ui.components.SecondaryButton
import com.utilityhub.logic.Validation
import com.utilityhub.logic.catalog.ToolMeta
import com.utilityhub.logic.spec.FieldSpec
import com.utilityhub.logic.text.MarkdownLite
import com.utilityhub.logic.text.MdBlock
import com.utilityhub.logic.text.MdSpan
import com.utilityhub.logic.text.MdStyle
import com.utilityhub.logic.util.ExpressionEvaluator
import com.utilityhub.logic.util.UnitConverter
import kotlinx.coroutines.delay
import java.util.Locale

// ------------------------------------------------------------------------------------------------ Calculator
@Composable
fun CalculatorScreen(meta: ToolMeta, onBack: () -> Unit) {
    var expr by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val history = remember { mutableStateListOf<String>() }
    val live = remember(expr) { if (expr.isBlank()) null else ExpressionEvaluator.evaluate(expr) }

    fun press(key: String) {
        error = null
        when (key) {
            "C" -> expr = ""
            "⌫" -> expr = expr.dropLast(1)
            "=" -> when (val r = ExpressionEvaluator.evaluate(expr)) {
                is Validation.Valid -> { val text = UnitConverter.format(r.value); history.add(0, "$expr = $text"); if (history.size > 10) history.removeAt(history.lastIndex); expr = text }
                is Validation.Invalid -> error = r.message
            }
            else -> if (expr.length < ExpressionEvaluator.MAX_LENGTH) expr += key
        }
    }

    ToolScaffold(meta.title, onBack) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                OutlinedTextField(value = expr, onValueChange = { expr = it.take(ExpressionEvaluator.MAX_LENGTH); error = null }, placeholder = { Text("0") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                val preview = (live as? Validation.Valid)?.let { "= ${UnitConverter.format(it.value)}" }
                Text(error ?: preview ?: " ", style = MaterialTheme.typography.titleLarge, color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
        listOf(listOf("C", "(", ")", "÷"), listOf("7", "8", "9", "×"), listOf("4", "5", "6", "−"), listOf("1", "2", "3", "+"), listOf("0", ".", "⌫", "=")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { key ->
                    val operator = key in setOf("÷", "×", "−", "+", "=")
                    val modifier = Modifier.weight(1f).height(58.dp)
                    if (operator) Button(onClick = { press(key) }, modifier = modifier, shape = RoundedCornerShape(16.dp)) { Text(key, fontSize = 22.sp) }
                    else FilledTonalButton(onClick = { press(key) }, modifier = modifier, shape = RoundedCornerShape(16.dp)) { Text(key, fontSize = 22.sp) }
                }
            }
        }
        Text("Also understands ^ % sqrt() sin() cos() tan() ln() log() abs() pi e", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (history.isNotEmpty()) {
            Text("Recent", style = MaterialTheme.typography.titleSmall)
            history.forEach { h -> TextButton(onClick = { expr = h.substringAfter("= ") }) { Text(h) } }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Stopwatch & timer
private fun clock(ms: Long): String {
    val total = ms.coerceAtLeast(0)
    val m = total / 60_000; val s = (total / 1000) % 60; val cs = (total / 10) % 100
    return String.format(Locale.US, "%02d:%02d.%02d", m, s, cs)
}

@Composable
fun StopwatchScreen(meta: ToolMeta, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf("Stopwatch") }
    ToolScaffold(meta.title, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Stopwatch", "Timer").forEach { FilterChip(selected = tab == it, onClick = { tab = it }, label = { Text(it) }) }
        }
        if (tab == "Stopwatch") StopwatchPanel() else TimerPanel()
        Text("Timers run while this screen is open. The app has no background permission or notifications, so close-the-app alarms are not supported.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun KeepScreenOn(active: Boolean) {
    val view = LocalView.current
    DisposableEffect(active) { view.keepScreenOn = active; onDispose { view.keepScreenOn = false } }
}

@Composable
private fun StopwatchPanel() {
    var running by rememberSaveable { mutableStateOf(false) }
    var accumulated by rememberSaveable { mutableStateOf(0L) }
    var startedAt by rememberSaveable { mutableStateOf(0L) }
    var laps by rememberSaveable { mutableStateOf("") }          // comma separated elapsed times
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) { while (running) { now = SystemClock.elapsedRealtime(); delay(30) } }
    KeepScreenOn(running)
    val elapsed = accumulated + if (running) now - startedAt else 0L

    Text(clock(elapsed), style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (!running) PrimaryButton(if (elapsed == 0L) "Start" else "Resume", onClick = { startedAt = SystemClock.elapsedRealtime(); now = startedAt; running = true }, modifier = Modifier.weight(1f))
        else PrimaryButton("Stop", onClick = { accumulated += SystemClock.elapsedRealtime() - startedAt; running = false }, modifier = Modifier.weight(1f))
        SecondaryButton(if (running) "Lap" else "Reset", modifier = Modifier.weight(1f), onClick = {
            if (running) laps = (listOf(elapsed.toString()) + laps.split(',').filter { it.isNotBlank() }).take(50).joinToString(",")
            else { accumulated = 0L; laps = "" }
        })
    }
    val lapList = laps.split(',').mapNotNull { it.toLongOrNull() }
    lapList.forEachIndexed { i, t ->
        val previous = lapList.getOrNull(i + 1) ?: 0L
        Text("Lap ${lapList.size - i}   ${clock(t)}   (+${clock(t - previous)})", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun TimerPanel() {
    val context = LocalContext.current
    var minutes by rememberSaveable { mutableStateOf("5") }
    var seconds by rememberSaveable { mutableStateOf("0") }
    var running by rememberSaveable { mutableStateOf(false) }
    var endAt by rememberSaveable { mutableStateOf(0L) }
    var pausedRemaining by rememberSaveable { mutableStateOf(0L) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        while (running) {
            now = SystemClock.elapsedRealtime()
            if (now >= endAt) {
                running = false; finished = true; pausedRemaining = 0L
                runCatching { RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play() }
            }
            delay(50)
        }
    }
    KeepScreenOn(running)
    val remaining = if (running) (endAt - now).coerceAtLeast(0) else pausedRemaining

    if (!running && pausedRemaining == 0L) {
        FieldEditor(FieldSpec.IntValue("min", "Minutes", 5, 0, 99), minutes) { minutes = it }
        FieldEditor(FieldSpec.IntValue("sec", "Seconds", 0, 0, 59), seconds) { seconds = it }
    } else {
        Text(clock(remaining).dropLast(3), style = MaterialTheme.typography.displayMedium, fontFamily = FontFamily.Monospace)
    }
    if (finished) Text("Time's up!", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (!running) {
            val total = (minutes.toIntOrNull() ?: 0) * 60_000L + (seconds.toIntOrNull() ?: 0) * 1000L
            PrimaryButton(if (pausedRemaining > 0) "Resume" else "Start", enabled = pausedRemaining > 0 || total > 0, modifier = Modifier.weight(1f), onClick = {
                finished = false
                val duration = if (pausedRemaining > 0) pausedRemaining else total
                now = SystemClock.elapsedRealtime(); endAt = now + duration; running = true
            })
        } else PrimaryButton("Pause", modifier = Modifier.weight(1f), onClick = { pausedRemaining = (endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0); running = false })
        SecondaryButton("Reset", modifier = Modifier.weight(1f), onClick = { running = false; pausedRemaining = 0L; finished = false })
    }
}

// ------------------------------------------------------------------------------------------------ Markdown preview
private fun styled(spans: List<MdSpan>, link: androidx.compose.ui.graphics.Color, codeBg: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    spans.forEach { s ->
        when (s.style) {
            MdStyle.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.text) }
            MdStyle.ITALIC -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(s.text) }
            MdStyle.CODE -> withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) { append(s.text) }
            MdStyle.LINK -> withStyle(SpanStyle(color = link, textDecoration = TextDecoration.Underline)) { append(s.text) }   // shown, not clickable
            MdStyle.PLAIN -> append(s.text)
        }
    }
}

@Composable
fun MarkdownScreen(meta: ToolMeta, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("# Hello\n\nWrite **Markdown** here.\n\n- one\n- two") }
    val blocks = remember(text) { MarkdownLite.parse(text) }
    val link = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceVariant
    ToolScaffold(meta.title, onBack) {
        OutlinedTextField(value = text, onValueChange = { text = it.take(MarkdownLite.MAX_CHARS) }, label = { Text("Markdown") }, minLines = 5, maxLines = 12, modifier = Modifier.fillMaxWidth())
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
            Column(Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                blocks.forEach { b ->
                    when (b) {
                        is MdBlock.Heading -> Text(styled(b.spans, link, codeBg), style = when (b.level) { 1 -> MaterialTheme.typography.headlineMedium; 2 -> MaterialTheme.typography.headlineSmall; 3 -> MaterialTheme.typography.titleLarge; else -> MaterialTheme.typography.titleMedium })
                        is MdBlock.Paragraph -> Text(styled(b.spans, link, codeBg), style = MaterialTheme.typography.bodyMedium)
                        is MdBlock.ListItem -> Text(AnnotatedString(if (b.ordered) "${b.number}. " else "•  ") + styled(b.spans, link, codeBg), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp))
                        is MdBlock.Quote -> Text(styled(b.spans, link, codeBg), style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, modifier = Modifier.padding(start = 8.dp).background(codeBg).padding(8.dp))
                        is MdBlock.CodeBlock -> Text(b.code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().background(codeBg).padding(10.dp))
                        MdBlock.Rule -> androidx.compose.material3.HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Text to speech
@Composable
fun SpeechScreen(meta: ToolMeta, onBack: () -> Unit) {
    val context = LocalContext.current
    var text by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("10") }          // tenths: 5..20 -> 0.5x..2.0x
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val tts = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(Unit) {
        tts[0] = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) ready = true else failed = true }
        onDispose { tts[0]?.stop(); tts[0]?.shutdown() }
    }
    ToolScaffold(meta.title, onBack) {
        OutlinedTextField(value = text, onValueChange = { text = it.take(TextToSpeech.getMaxSpeechInputLength()) }, label = { Text("Text to read aloud") }, minLines = 4, maxLines = 10, modifier = Modifier.fillMaxWidth())
        FieldEditor(FieldSpec.IntValue("rate", "Speed (×0.1)", 10, 5, 20), rate) { rate = it }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PrimaryButton("Speak", enabled = ready && text.isNotBlank(), modifier = Modifier.weight(1f), onClick = {
                tts[0]?.let { engine -> engine.setSpeechRate((rate.toIntOrNull() ?: 10) / 10f); engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utilityhub") }
            })
            SecondaryButton("Stop", modifier = Modifier.weight(1f), onClick = { tts[0]?.stop() })
        }
        if (failed) Text("No text-to-speech engine is available on this device.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Text("Uses your device's speech engine and voices. Some engines may download voice data or use the network; this app itself never sends your text anywhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
