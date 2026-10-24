package com.utilityhub.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.utilityhub.app.ui.categoryIcon
import com.utilityhub.app.ui.toolIcon
import com.utilityhub.logic.catalog.ToolCategory
import com.utilityhub.logic.catalog.ToolMeta
import com.utilityhub.logic.entitlement.EntitlementSnapshot
import com.utilityhub.logic.spec.FieldSpec
import kotlin.math.roundToInt

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val border = BorderStroke(
        1.dp,
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
    if (onClick != null) {
        Card(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            border = border,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = modifier,
            content = content
        )
    } else {
        Card(
            shape = shape,
            border = border,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = modifier,
            content = content
        )
    }
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 44.dp, muted: Boolean = false) {
    val container = if (muted) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
    val content = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(size * 0.52f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.semantics { }
    )
}

@Composable
fun ToolListItem(meta: ToolMeta, available: Boolean, favorite: Boolean, onClick: () -> Unit, onToggleFavorite: () -> Unit) {
    GlassCard(
        onClick = onClick,
        enabled = available,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(toolIcon(meta.id, meta.category), muted = !available)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    meta.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (available) meta.description else "Coming soon",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (available) {
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (favorite) "Remove ${meta.title} from favorites" else "Add ${meta.title} to favorites",
                        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryCard(category: ToolCategory, availableCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(
        onClick = onClick,
        modifier = modifier,
    ) {
        Column(Modifier.padding(18.dp).fillMaxWidth()) {
            IconBadge(categoryIcon(category))
            Spacer(Modifier.height(12.dp))
            Text(category.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text("$availableCount tools available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AllowanceCard(snapshot: EntitlementSnapshot?, onDetails: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "View access details", onClick = onDetails)
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconBadge(Icons.Rounded.Star, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    "100% Free & Unlimited Access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "All tools (Images, Video, PDF, ZIP & Text) are completely free with zero limits.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.55f))
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

/** Renders any [FieldSpec]; used by every file tool and every form tool so interaction stays consistent. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldEditor(spec: FieldSpec, value: String, onValueChange: (String) -> Unit) {
    val fieldShape = RoundedCornerShape(16.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )

    when (spec) {
        is FieldSpec.Text -> OutlinedTextField(
            value = value, onValueChange = onValueChange, label = { Text(spec.label) },
            placeholder = if (spec.hint.isNotEmpty()) ({ Text(spec.hint) }) else null,
            minLines = if (spec.multiline) 4 else 1, maxLines = if (spec.multiline) 12 else 1,
            shape = fieldShape, colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )
        is FieldSpec.IntValue -> {
            val current = value.toIntOrNull() ?: spec.default
            if (spec.max - spec.min > 100) {
                OutlinedTextField(
                    value = value, onValueChange = { onValueChange(it.filter(Char::isDigit).take(6)) },
                    label = { Text("${spec.label} (${spec.min}–${spec.max}${if (spec.unit.isNotEmpty()) " ${spec.unit}" else ""})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
                    shape = fieldShape, colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(spec.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("$current${if (spec.unit.isNotEmpty()) " ${spec.unit}" else ""}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = current.coerceIn(spec.min, spec.max).toFloat(), onValueChange = { onValueChange(it.roundToInt().toString()) },
                        valueRange = spec.min.toFloat()..spec.max.toFloat(),
                        modifier = Modifier.semantics { contentDescription = spec.label },
                    )
                }
            }
        }
        is FieldSpec.Choice -> Column(Modifier.fillMaxWidth()) {
            Text(spec.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                spec.choices.forEach { choice ->
                    FilterChip(
                        selected = choice == value,
                        onClick = { onValueChange(choice) },
                        label = { Text(choice) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }
        is FieldSpec.Toggle -> Row(
            Modifier.fillMaxWidth().toggleable(value = value.toBooleanStrictOrNull() ?: spec.default, role = Role.Switch, onValueChange = { onValueChange(it.toString()) }).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(spec.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Switch(checked = value.toBooleanStrictOrNull() ?: spec.default, onCheckedChange = null)
        }
    }
}
