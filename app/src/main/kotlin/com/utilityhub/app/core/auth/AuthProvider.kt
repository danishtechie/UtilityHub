package com.utilityhub.app.core.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed interface AuthState {
    data object SignedOut : AuthState
    data class SignedIn(val uid: String, val email: String, val emailVerified: Boolean) : AuthState
}

sealed interface AuthResult {
    data object Success : AuthResult
    data class Error(val message: String) : AuthResult
}

/**
 * Account boundary. Every feature requires sign in / registration.
 */
interface AuthProvider {
    val isAvailable: Boolean
    val state: StateFlow<AuthState>
    suspend fun signUp(email: String, password: String): AuthResult
    suspend fun signIn(email: String, password: String): AuthResult
    suspend fun sendPasswordReset(email: String): AuthResult
    suspend fun resendVerification(): AuthResult
    suspend fun refresh(): AuthResult
    fun signOut()
    suspend fun deleteAccount(): AuthResult
}

/** Local, offline-capable auth provider used when Firebase is unavailable or for local sessions. */
class LocalAuthProvider(context: Context) : AuthProvider {
    private val prefs = context.getSharedPreferences("local_auth_prefs", Context.MODE_PRIVATE)
    override val isAvailable = true

    private val _state = MutableStateFlow<AuthState>(loadInitialState())
    override val state: StateFlow<AuthState> = _state

    private fun loadInitialState(): AuthState {
        val savedEmail = prefs.getString("current_user_email", null)
        return if (!savedEmail.isNullOrBlank()) {
            AuthState.SignedIn(
                uid = "local_" + savedEmail.lowercase().hashCode(),
                email = savedEmail,
                emailVerified = true,
            )
        } else {
            AuthState.SignedOut
        }
    }

    override suspend fun signUp(email: String, password: String): AuthResult {
        val cleanEmail = email.trim().lowercase()
        val existingPass = prefs.getString("user_pass_$cleanEmail", null)
        if (existingPass != null) {
            return AuthResult.Error("An account with this email already exists. Please sign in.")
        }
        prefs.edit()
            .putString("user_pass_$cleanEmail", password)
            .putString("current_user_email", cleanEmail)
            .apply()
        _state.value = AuthState.SignedIn(
            uid = "local_" + cleanEmail.hashCode(),
            email = cleanEmail,
            emailVerified = true,
        )
        return AuthResult.Success
    }

    override suspend fun signIn(email: String, password: String): AuthResult {
        val cleanEmail = email.trim().lowercase()
        val savedPass = prefs.getString("user_pass_$cleanEmail", null)
        if (savedPass != null && savedPass != password) {
            return AuthResult.Error("Incorrect email or password.")
        }
        prefs.edit()
            .putString("user_pass_$cleanEmail", password)
            .putString("current_user_email", cleanEmail)
            .apply()
        _state.value = AuthState.SignedIn(
            uid = "local_" + cleanEmail.hashCode(),
            email = cleanEmail,
            emailVerified = true,
        )
        return AuthResult.Success
    }

    override suspend fun sendPasswordReset(email: String): AuthResult = AuthResult.Success

    override suspend fun resendVerification(): AuthResult = AuthResult.Success

    override suspend fun refresh(): AuthResult = AuthResult.Success

    override fun signOut() {
        prefs.edit().remove("current_user_email").apply()
        _state.value = AuthState.SignedOut
    }

    override suspend fun deleteAccount(): AuthResult {
        val currentEmail = (state.value as? AuthState.SignedIn)?.email?.lowercase()
        if (currentEmail != null) {
            prefs.edit().remove("user_pass_$currentEmail").remove("current_user_email").apply()
        }
        _state.value = AuthState.SignedOut
        return AuthResult.Success
    }
}

class NoAuthProvider : AuthProvider {
    override val isAvailable = false
    override val state: StateFlow<AuthState> = MutableStateFlow(AuthState.SignedOut)
    private val off = AuthResult.Error("Accounts are not set up in this build.")
    override suspend fun signUp(email: String, password: String) = off
    override suspend fun signIn(email: String, password: String) = off
    override suspend fun sendPasswordReset(email: String) = off
    override suspend fun resendVerification() = off
    override suspend fun refresh() = off
    override fun signOut() {}
    override suspend fun deleteAccount() = off
}
