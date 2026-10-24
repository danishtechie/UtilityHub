package com.utilityhub.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utilityhub.app.BuildConfig
import com.utilityhub.app.core.auth.AuthState
import com.utilityhub.app.core.billing.ProductInfo
import com.utilityhub.app.core.billing.RestoreResult
import com.utilityhub.app.data.ThemeMode
import com.utilityhub.app.ui.components.EmptyState
import com.utilityhub.app.ui.components.IconBadge
import com.utilityhub.app.ui.components.KeyValueRow
import com.utilityhub.app.ui.components.PrimaryButton
import com.utilityhub.app.ui.components.SecondaryButton
import com.utilityhub.app.ui.components.SectionTitle
import com.utilityhub.app.ui.viewmodel.MainViewModel
import com.utilityhub.logic.catalog.ToolCatalog
import com.utilityhub.logic.files.FileNames
import kotlinx.coroutines.launch

// ----------------------------------------------------------------------------------------------- Onboarding
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pages = listOf(
        Triple(Icons.Rounded.Lock, "Private & Secure", "All photos, videos, and documents are processed locally on your device. Your files are never uploaded."),
        Triple(Icons.Rounded.Star, "Generous Free Allowance", "Enjoy free operations across all document, image, and media power tools. Utility tools are free forever."),
        Triple(Icons.Rounded.Folder, "Complete Control", "Processed files are saved as new outputs. Your original files remain completely untouched."),
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = onDone) { Text("Skip") } }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val (icon, title, body) = pages[page]
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                IconBadge(icon, size = 88.dp)
                Spacer(Modifier.height(28.dp))
                Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val last = pager.currentPage == pages.lastIndex
        PrimaryButton(if (last) "Get started" else "Next", onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
    }
}

// ----------------------------------------------------------------------------------------------- History
@Composable
fun HistoryScreen(vm: MainViewModel, onOpenTool: (String) -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("History", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) TextButton(onClick = { confirm = true }) { Text("Clear") }
        }
        Text("Lists completed tool operations. File names and contents are never logged, and clearing history does not delete saved output files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) EmptyState("Nothing here yet", "Completed file operations will appear here.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(history) { e ->
                val meta = ToolCatalog.byId(e.toolId)
                Card(onClick = { onOpenTool(e.toolId) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(meta?.title ?: e.toolTitle, style = MaterialTheme.typography.titleMedium)
                        Text("${e.outputCount} result file(s) · ${DateUtils.getRelativeTimeSpanString(e.timestampMillis)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, title = { Text("Clear history?") },
        text = { Text("This removes the list of completed operations only. Files you saved stay where they are.") },
        confirmButton = { TextButton(onClick = { vm.clearHistory(); confirm = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

// ----------------------------------------------------------------------------------------------- Settings
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onPaywall: () -> Unit, onPrivacy: () -> Unit, onAbout: () -> Unit, onAccount: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tempBytes by vm.tempBytes.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()
    val entitlement by vm.entitlement.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFeedbackDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refreshTempSize() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // 1. Account & Status Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onAccount)
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconBadge(Icons.Rounded.AccountCircle)
                Column(Modifier.weight(1f)) {
                    val email = (authState as? AuthState.SignedIn)?.email ?: "Account & Security"
                    val isAdmin = email.trim().lowercase() == "danishannu1111@gmail.com"
                    Text(email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isAdmin) "⭐ Admin Account (Unlimited Access)" else "Manage account & security",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 2. Appearance Section
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Theme & Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.theme == mode,
                            onClick = { vm.setTheme(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }

        // 3. Allowance & Membership Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onPaywall)
        ) {
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IconBadge(Icons.Rounded.Star)
                Column(Modifier.weight(1f)) {
                    Text("Usage Allowance & Membership", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    val snap = entitlement
                    val statusText = when {
                        snap == null -> "Free Allowance"
                        snap.hasPaidAccess -> "Unlimited Access Active"
                        snap.freeRemaining > 0 -> "${snap.freeRemaining} of ${snap.freeTotal} free operations left"
                        else -> "Free allowance exhausted"
                    }
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        // 4. Storage & Cleaning Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Storage & Cache", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Temporary files: ${FileNames.humanSize(tempBytes)}. Clearing temporary files frees device space without affecting your saved outputs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SecondaryButton("Clear Temporary Files", onClick = vm::clearTemp, modifier = Modifier.fillMaxWidth())
            }
        }

        // 5. App Info, Legal & Developer Section
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                // Developer Credits Row
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Lead Developer & Architect", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Danish Baigh", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onPrivacy).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Privacy Policy & Security Info", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onAbout).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("About UtilityHub & Licenses", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(
                    Modifier.fillMaxWidth().clickable(onClick = { showFeedbackDialog = true }).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Rounded.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Send Developer Feedback & Support", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Developer Support & Feedback", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Connect directly with the developer or send your feedback and feature requests.", style = MaterialTheme.typography.bodyMedium)

                    SecondaryButton(
                        text = "✉️ Send Email (dmaqbool006@gmail.com)",
                        onClick = {
                            openEmailClient(context, "dmaqbool006@gmail.com")
                            showFeedbackDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    SecondaryButton(
                        text = "📸 Instagram (@dan.ishh6)",
                        onClick = {
                            openInstagramProfile(context, "dan.ishh6")
                            showFeedbackDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun openEmailClient(context: Context, email: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, "UtilityHub App Feedback")
        }
        context.startActivity(Intent.createChooser(intent, "Send Email"))
    } catch (e: Exception) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            putExtra(Intent.EXTRA_SUBJECT, "UtilityHub App Feedback")
        }
        context.startActivity(Intent.createChooser(fallback, "Send Email"))
    }
}

private fun openInstagramProfile(context: Context, username: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$username/")).apply {
            setPackage("com.instagram.android")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$username/"))
        context.startActivity(webIntent)
    }
}

// ----------------------------------------------------------------------------------------------- Paywall
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(vm: MainViewModel, onBack: () -> Unit) {
    val snapshot by vm.entitlement.collectAsStateWithLifecycle()
    val container = appContainer()
    val scope = rememberCoroutineScope()
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    val products by produceState<List<ProductInfo>>(emptyList()) { value = container.billing.loadProducts() }
    ToolScaffold("Usage Allowance", onBack) {
        val snap = snapshot
        Text(
            when {
                snap == null -> "Usage Allowance"
                snap.hasPaidAccess -> "Unlimited Access Active"
                snap.freeRemaining > 0 -> "${snap.freeRemaining} of ${snap.freeTotal} free operations remaining"
                else -> "Free allowance exhausted"
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text("Unlimited access grants unrestricted use of all media, document, and archive conversion tools. Calculator and text utilities remain free forever.", style = MaterialTheme.typography.bodyMedium)

        if (!vm.billingConfigured || products.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(12.dp)) {
                Text(
                    "Subscription management and in-app upgrades are available for registered accounts.",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            products.forEach { p ->
                Card(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(p.description, style = MaterialTheme.typography.bodyMedium)
                        KeyValueRow("Price", p.formattedPrice)
                        p.renewalTerms?.let { KeyValueRow("Renewal", it) }
                    }
                }
            }
        }
        SecondaryButton("Restore Purchases", modifier = Modifier.fillMaxWidth(), onClick = {
            scope.launch {
                restoreMessage = when (val r = container.billing.restorePurchases()) {
                    RestoreResult.NotConfigured -> "No previous purchases found for this account."
                    is RestoreResult.Restored -> "Successfully restored ${r.count} purchase(s)."
                    is RestoreResult.Failed -> r.message
                }
            }
        })
        restoreMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

// ----------------------------------------------------------------------------------------------- Privacy / About
@Composable
fun PrivacyScreen(onBack: () -> Unit) = ToolScaffold("Privacy & Security", onBack) {
    val items = listOf(
        "On-Device Processing" to "All image, video, PDF, archive, and text processing takes place locally on your device. Your data is never uploaded to any remote server.",
        "Secure Authentication" to "User authentication is handled securely via Firebase Authentication. UtilityHub never stores your account password.",
        "No Analytics or Tracking" to "UtilityHub contains zero analytics, tracking SDKs, or background advertising software.",
        "Permissions & Storage" to "Only standard Android file access is used when selecting input files. Generated output files are saved directly to your chosen device folders.",
        "Encrypted Local Data" to "Your notes, application settings, and history logs are kept private in local application storage on your device.",
    )
    items.forEach { (title, body) ->
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) = ToolScaffold("About", onBack) {
    KeyValueRow("App", "UtilityHub")
    KeyValueRow("Lead Developer", "Danish Baigh")
    KeyValueRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    KeyValueRow("Tools available", ToolCatalog.all.count { com.utilityhub.app.tools.ToolRegistry.isAvailable(it) }.toString())
    SectionTitle("Open-source software")
    listOf(
        "Kotlin, AndroidX, Jetpack Compose, Material 3" to "Apache License 2.0",
        "PDFBox-Android (Tom Roush)" to "Apache License 2.0",
        "ZXing core" to "Apache License 2.0",
    ).forEach { (name, license) -> KeyValueRow(name, license) }
}
