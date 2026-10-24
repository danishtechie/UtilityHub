package com.utilityhub.logic.security

import com.utilityhub.logic.Validation

/** Client-side checks that give fast feedback. The identity provider remains the authority on what is accepted. */
object AuthRules {
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
    private val common = setOf("password", "password1", "password12", "password123", "1234567890", "12345678", "123456789", "qwertyuiop", "qwerty123", "iloveyou1", "letmein123", "welcome123", "admin12345", "abc1234567")

    fun validateEmail(text: String): Validation<String> {
        val email = text.trim()
        if (email.isEmpty()) return Validation.Invalid("Enter your email address.")
        if (email.length > 254 || !emailPattern.matches(email)) return Validation.Invalid("Enter a valid email address.")
        return Validation.Valid(email)
    }

    fun validateNewPassword(password: String, email: String = ""): Validation<String> {
        if (password.length < 10) return Validation.Invalid("Use at least 10 characters.")
        if (password.length > 128) return Validation.Invalid("Use at most 128 characters.")
        if (password.none { it.isLetter() } || password.none { it.isDigit() }) return Validation.Invalid("Include at least one letter and one number.")
        if (password.lowercase() in common || password.toSet().size < 4) return Validation.Invalid("That password is too easy to guess.")
        val local = email.substringBefore('@').lowercase()
        if (local.length >= 4 && password.lowercase().contains(local)) return Validation.Invalid("Do not use your email name inside the password.")
        return Validation.Valid(password)
    }
}

/** Slows down repeated failed attempts on this device. This is a courtesy; real rate limiting is enforced server-side by the provider. */
class AttemptLimiter(
    private val maxFailures: Int = 5,
    private val lockMillis: Long = 60_000,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private var failures = 0
    private var lockedUntil = 0L

    fun remainingLockMillis(): Long = (lockedUntil - now()).coerceAtLeast(0)

    fun recordFailure() {
        if (remainingLockMillis() > 0) return
        failures++
        if (failures >= maxFailures) { lockedUntil = now() + lockMillis; failures = 0 }
    }

    fun recordSuccess() { failures = 0; lockedUntil = 0 }
}

/** Minimum gap between actions that send email (password reset, verification). */
class Cooldown(private val gapMillis: Long, private val now: () -> Long = { System.currentTimeMillis() }) {
    private var last = Long.MIN_VALUE / 2
    fun remainingMillis(): Long = (last + gapMillis - now()).coerceAtLeast(0)
    fun mark() { last = now() }
}
