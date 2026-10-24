package com.utilityhub.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.utilityhub.app.core.auth.AuthProvider
import com.utilityhub.app.core.auth.AuthResult
import com.utilityhub.app.data.db.NoteDao
import com.utilityhub.app.data.db.NoteEntity
import com.utilityhub.logic.Validation
import com.utilityhub.logic.security.AttemptLimiter
import com.utilityhub.logic.security.AuthRules
import com.utilityhub.logic.security.Cooldown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotesViewModel(private val dao: NoteDao) : ViewModel() {
    val notes: StateFlow<List<NoteEntity>> = dao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** @return an error message, or null when saved. Blank notes are not stored. */
    fun save(id: Long, title: String, body: String, pinned: Boolean): String? {
        if (title.isBlank() && body.isBlank()) return "Write something first."
        if (title.length > 100) return "The title is limited to 100 characters."
        if (body.length > 100_000) return "The note is limited to 100,000 characters."
        viewModelScope.launch { dao.upsert(NoteEntity(id, title.trim(), body, System.currentTimeMillis(), pinned)) }
        return null
    }

    fun delete(id: Long) { viewModelScope.launch { dao.delete(id) } }
    fun deleteAll() { viewModelScope.launch { dao.deleteAll() } }
    fun togglePin(n: NoteEntity) { viewModelScope.launch { dao.upsert(n.copy(pinned = !n.pinned)) } }
}

data class AccountUi(val busy: Boolean = false, val message: String? = null, val isError: Boolean = false)

class AccountViewModel(private val auth: AuthProvider) : ViewModel() {
    val available get() = auth.isAvailable
    val state = auth.state
    private val _ui = MutableStateFlow(AccountUi())
    val ui: StateFlow<AccountUi> = _ui.asStateFlow()

    private val signInLimiter = AttemptLimiter()
    private val resetCooldown = Cooldown(60_000)
    private val verifyCooldown = Cooldown(60_000)

    private fun say(message: String, error: Boolean) { _ui.value = AccountUi(false, message, error) }
    private fun seconds(ms: Long) = ms / 1000 + 1

    private fun launchCall(onResult: (AuthResult) -> Unit, call: suspend () -> AuthResult) {
        if (_ui.value.busy) return                                   // ignore repeated taps
        _ui.value = AccountUi(busy = true)
        viewModelScope.launch { onResult(call()) }
    }

    fun signIn(emailRaw: String, password: String) {
        val email = (AuthRules.validateEmail(emailRaw) as? Validation.Valid)?.value ?: return say("Enter a valid email address.", true)
        if (password.isEmpty()) return say("Enter your password.", true)
        val wait = signInLimiter.remainingLockMillis()
        if (wait > 0) return say("Too many failed attempts. Try again in ${seconds(wait)} seconds.", true)
        launchCall({ r ->
            if (r is AuthResult.Error) { signInLimiter.recordFailure(); say(r.message, true) } else { signInLimiter.recordSuccess(); _ui.value = AccountUi() }
        }) { auth.signIn(email, password) }
    }

    fun signUp(emailRaw: String, password: String) {
        val email = when (val v = AuthRules.validateEmail(emailRaw)) { is Validation.Valid -> v.value; is Validation.Invalid -> return say(v.message, true) }
        (AuthRules.validateNewPassword(password, email) as? Validation.Invalid)?.let { return say(it.message, true) }
        launchCall({ r -> if (r is AuthResult.Error) say(r.message, true) else say("Account created. We sent a verification email - open the link, then tap \"I've verified\".", false) }) { auth.signUp(email, password) }
    }

    fun forgotPassword(emailRaw: String) {
        val email = (AuthRules.validateEmail(emailRaw) as? Validation.Valid)?.value ?: return say("Enter your email address above first.", true)
        val wait = resetCooldown.remainingMillis()
        if (wait > 0) return say("Please wait ${seconds(wait)} seconds before requesting another email.", true)
        resetCooldown.mark()
        launchCall({ r ->
            // The same message whether or not an account exists, so this screen cannot be used to discover registered emails.
            val network = r is AuthResult.Error && (r.message.contains("internet") || r.message.contains("Too many"))
            if (network) say((r as AuthResult.Error).message, true) else say("If an account exists for that email, a password reset link has been sent.", false)
        }) { auth.sendPasswordReset(email) }
    }

    fun resendVerification() {
        val wait = verifyCooldown.remainingMillis()
        if (wait > 0) return say("Please wait ${seconds(wait)} seconds before requesting another email.", true)
        verifyCooldown.mark()
        launchCall({ r -> if (r is AuthResult.Error) say(r.message, true) else say("Verification email sent.", false) }) { auth.resendVerification() }
    }

    fun refresh() = launchCall({ r -> if (r is AuthResult.Error) say(r.message, true) else _ui.value = AccountUi() }) { auth.refresh() }
    fun signOut() { auth.signOut(); _ui.value = AccountUi() }
    fun deleteAccount() = launchCall({ r -> if (r is AuthResult.Error) say(r.message, true) else say("Your account was deleted.", false) }) { auth.deleteAccount() }
}
