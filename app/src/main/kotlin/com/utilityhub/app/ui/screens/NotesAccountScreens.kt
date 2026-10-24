package com.utilityhub.app.ui.screens

import android.content.Intent
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.utilityhub.app.core.auth.AuthState
import com.utilityhub.app.ui.components.EmptyState
import com.utilityhub.app.ui.components.IconBadge
import com.utilityhub.app.ui.components.PrimaryButton
import com.utilityhub.app.ui.components.SecondaryButton
import com.utilityhub.app.ui.viewmodel.AccountViewModel
import com.utilityhub.app.ui.viewmodel.NotesViewModel
import com.utilityhub.logic.catalog.ToolMeta

// ------------------------------------------------------------------------------------------------ Notes
@Composable
fun NotesScreen(meta: ToolMeta, onBack: () -> Unit) {
    val container = appContainer()
    val vm: NotesViewModel = viewModel(factory = viewModelFactory { initializer { NotesViewModel(container.db.notes()) } })
    val notes by vm.notes.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editing by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableStateOf(0L) }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }
    var pinned by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    BackHandler(enabled = editing) { editing = false; error = null }

    ToolScaffold(meta.title, onBack = { if (editing) { editing = false; error = null } else onBack() }) {
        if (editing) {
            OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = body, onValueChange = { body = it.take(100_000) }, label = { Text("Note") }, minLines = 8, maxLines = 20, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            PrimaryButton("Save", onClick = { error = vm.save(editId, title, body, pinned); if (error == null) editing = false })
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(if (pinned) "Unpin" else "Pin", modifier = Modifier.weight(1f), onClick = { pinned = !pinned })
                SecondaryButton("Share", modifier = Modifier.weight(1f), onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body); if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title) }
                    context.startActivity(Intent.createChooser(send, null))
                })
                if (editId != 0L) SecondaryButton("Delete", modifier = Modifier.weight(1f), onClick = { confirmDelete = true })
            }
        } else {
            PrimaryButton("New note", onClick = { editId = 0L; title = ""; body = ""; pinned = false; error = null; editing = true })
            if (notes.isNotEmpty()) OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search notes") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val shown = notes.filter { query.isBlank() || it.title.contains(query, true) || it.body.contains(query, true) }
            if (notes.isEmpty()) EmptyState("No notes yet", "Notes are saved locally on your device.")
            shown.forEach { n ->
                Card(onClick = { editId = n.id; title = n.title; body = n.body; pinned = n.pinned; error = null; editing = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(n.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(n.body, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(DateUtils.getRelativeTimeSpanString(n.updatedAt).toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.togglePin(n) }) { Icon(if (n.pinned) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (n.pinned) "Unpin ${n.title}" else "Pin ${n.title}") }
                    }
                }
            }
            if (notes.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Delete all notes") }
            Text("Notes are stored in secure local application storage on your device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this note?") }, text = { Text("This cannot be undone.") },
        confirmButton = { TextButton(onClick = { vm.delete(editId); confirmDelete = false; editing = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Delete all notes?") }, text = { Text("Every note on this device will be removed. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmClear = false }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

// ------------------------------------------------------------------------------------------------ Auth / Login
@Composable
fun AuthScreen(onAuthSuccess: () -> Unit) {
    val container = appContainer()
    val vm: AccountViewModel = viewModel(factory = viewModelFactory { initializer { AccountViewModel(container.auth) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf("Sign in") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state) {
        if (state is AuthState.SignedIn) {
            onAuthSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (mode == "Sign in") "Welcome Back" else "Create Account",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (mode == "Sign in") "Sign in to access your tools" else "Register to start using all tools",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Switcher
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(Modifier.padding(4.dp)) {
                    val selectedColor = MaterialTheme.colorScheme.surface
                    val unselectedColor = Color.Transparent

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mode == "Sign in") selectedColor else unselectedColor)
                            .clickable { mode = "Sign in"; localError = null }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Sign In",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (mode == "Sign in") FontWeight.Bold else FontWeight.Medium,
                            color = if (mode == "Sign in") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (mode == "Create account") selectedColor else unselectedColor)
                            .clickable { mode = "Create account"; localError = null }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Register",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (mode == "Create account") FontWeight.Bold else FontWeight.Medium,
                            color = if (mode == "Create account") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Form
            Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.take(254); localError = null },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128); localError = null },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, contentDescription = "Toggle password visibility")
                        }
                    },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (mode == "Create account") {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it.take(128); localError = null },
                        label = { Text("Confirm Password") },
                        leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Must be at least 10 characters with numbers & letters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            // Error or status messages
            val errorToShow = localError ?: (if (ui.isError) ui.message else null)
            if (errorToShow != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorToShow,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else if (!ui.isError && ui.message != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ui.message!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            PrimaryButton(
                text = if (mode == "Sign in") "Sign In" else "Create Account",
                enabled = !ui.busy,
                onClick = {
                    if (mode == "Create account" && password != confirmPassword) {
                        localError = "Passwords do not match."
                        return@PrimaryButton
                    }
                    if (mode == "Sign in") vm.signIn(email, password) else vm.signUp(email, password)
                }
            )

            if (mode == "Sign in") {
                TextButton(
                    onClick = { vm.forgotPassword(email) },
                    enabled = !ui.busy
                ) {
                    Text("Forgot password?", color = MaterialTheme.colorScheme.primary)
                }
            }

            if (ui.busy) {
                CircularProgressIndicator()
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Email Verification Screen
@Composable
fun EmailVerificationScreen(onVerified: () -> Unit, onSignOut: () -> Unit) {
    val container = appContainer()
    val vm: AccountViewModel = viewModel(factory = viewModelFactory { initializer { AccountViewModel(container.auth) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()

    val email = (state as? AuthState.SignedIn)?.email ?: ""

    LaunchedEffect(state) {
        if (state is AuthState.SignedIn && (state as AuthState.SignedIn).emailVerified) {
            onVerified()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IconBadge(icon = Icons.Rounded.MarkEmailUnread, size = 80.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Verify Your Email",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "A verification link was sent to:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Please check your inbox and click the link to verify your email address. Verification is required to activate your account and unlock UtilityHub tools.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }

            if (ui.message != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (ui.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = ui.message!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (ui.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                PrimaryButton(
                    text = "I've Verified - Continue",
                    enabled = !ui.busy,
                    onClick = { vm.refresh() }
                )

                SecondaryButton(
                    text = "Resend Verification Email",
                    enabled = !ui.busy,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { vm.resendVerification() }
                )

                TextButton(
                    onClick = { vm.signOut(); onSignOut() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign Out / Use Different Email", color = MaterialTheme.colorScheme.error)
                }
            }

            if (ui.busy) {
                CircularProgressIndicator()
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------ Account
@Composable
fun AccountScreen(onBack: () -> Unit) {
    val container = appContainer()
    val vm: AccountViewModel = viewModel(factory = viewModelFactory { initializer { AccountViewModel(container.auth) } })
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    ToolScaffold("Account", onBack) {
        when (val s = state) {
            is AuthState.SignedIn -> {
                val isAdmin = s.email.trim().lowercase() == "danishannu1111@gmail.com"
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Account Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Text(s.email, style = MaterialTheme.typography.bodyLarge)

                        if (isAdmin) {
                            Spacer(Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Text(
                                        "⭐ Admin Account (Unlimited Access)",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                SecondaryButton("Sign out", onClick = vm::signOut, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { confirmDelete = true }) { Text("Delete my account", color = MaterialTheme.colorScheme.error) }
            }
            AuthState.SignedOut -> {
                Text("Not signed in.")
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete your account?") },
        text = { Text("Your account will be permanently deleted. Files and notes on this device are not affected.") },
        confirmButton = { TextButton(onClick = { vm.deleteAccount(); confirmDelete = false }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
