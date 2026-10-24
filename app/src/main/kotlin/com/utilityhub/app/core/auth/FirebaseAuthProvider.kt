package com.utilityhub.app.core.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** Email + password via Firebase Authentication. Credentials and emails are never logged or stored by this app. */
class FirebaseAuthProvider : AuthProvider {
    private val auth = FirebaseAuth.getInstance()
    override val isAvailable = true
    private val _state = MutableStateFlow(current())
    override val state: StateFlow<AuthState> = _state

    init { auth.addAuthStateListener { _state.value = current() } }

    private fun current(): AuthState =
        auth.currentUser?.let { AuthState.SignedIn(it.uid, it.email.orEmpty(), it.isEmailVerified) } ?: AuthState.SignedOut

    override suspend fun signUp(email: String, password: String) = call {
        auth.createUserWithEmailAndPassword(email, password).await()
        auth.currentUser?.sendEmailVerification()?.await()
    }

    override suspend fun signIn(email: String, password: String) = call { auth.signInWithEmailAndPassword(email, password).await() }

    override suspend fun sendPasswordReset(email: String) = call { auth.sendPasswordResetEmail(email).await() }

    override suspend fun resendVerification() = call { auth.currentUser?.sendEmailVerification()?.await() }

    override suspend fun refresh() = call { auth.currentUser?.reload()?.await(); _state.value = current() }

    override fun signOut() { auth.signOut() }

    override suspend fun deleteAccount() = call { auth.currentUser?.delete()?.await() }

    private suspend fun call(block: suspend () -> Unit): AuthResult = try {
        block()
        AuthResult.Success
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseAuthException) {
        AuthResult.Error(friendly(e.errorCode))
    } catch (e: Exception) {
        AuthResult.Error("Something went wrong. Check your connection and try again.")
    }

    /** Sign-in failures are deliberately vague so the app does not reveal which emails have accounts. */
    private fun friendly(code: String): String = when (code) {
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL" -> "Incorrect email or password."
        "ERROR_EMAIL_ALREADY_IN_USE" -> "That email can't be used to create a new account. Try signing in or resetting your password."
        "ERROR_WEAK_PASSWORD" -> "Choose a stronger password."
        "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Please wait a few minutes and try again."
        "ERROR_USER_DISABLED" -> "This account has been disabled."
        "ERROR_NETWORK_REQUEST_FAILED" -> "No internet connection."
        "ERROR_REQUIRES_RECENT_LOGIN", "ERROR_USER_TOKEN_EXPIRED" -> "For security, please sign in again and then retry."
        else -> "Something went wrong. Please try again."
    }
}
