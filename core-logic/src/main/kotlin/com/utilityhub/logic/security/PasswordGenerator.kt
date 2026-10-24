package com.utilityhub.logic.security

import com.utilityhub.logic.Validation
import java.util.Random

data class PasswordOptions(
    val length: Int = 16,
    val lower: Boolean = true,
    val upper: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val excludeAmbiguous: Boolean = false,
)

/** Callers MUST pass a java.security.SecureRandom. The parameter type only exists to keep tests deterministic. */
object PasswordGenerator {
    const val MIN_LENGTH = 8
    const val MAX_LENGTH = 128
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{};:,.?"
    private const val AMBIGUOUS = "0O1lI|"

    fun generate(options: PasswordOptions, random: Random): Validation<String> {
        if (options.length !in MIN_LENGTH..MAX_LENGTH) {
            return Validation.Invalid("Length must be between $MIN_LENGTH and $MAX_LENGTH.")
        }
        val sets = buildList {
            if (options.lower) add(LOWER)
            if (options.upper) add(UPPER)
            if (options.digits) add(DIGITS)
            if (options.symbols) add(SYMBOLS)
        }.map { set -> if (options.excludeAmbiguous) set.filter { it !in AMBIGUOUS } else set }
        if (sets.isEmpty()) return Validation.Invalid("Choose at least one character type.")
        val all = sets.joinToString("")
        val chars = ArrayList<Char>(options.length)
        sets.forEach { chars += it[random.nextInt(it.length)] }   // guarantee one of each chosen type
        while (chars.size < options.length) chars += all[random.nextInt(all.length)]
        for (i in chars.indices.reversed()) {                        // Fisher–Yates shuffle
            val j = random.nextInt(i + 1)
            val tmp = chars[i]; chars[i] = chars[j]; chars[j] = tmp
        }
        return Validation.Valid(chars.joinToString(""))
    }

    fun randomString(length: Int, alphabet: String, random: Random): Validation<String> {
        if (length !in 1..4096) return Validation.Invalid("Length must be between 1 and 4096.")
        if (alphabet.isEmpty()) return Validation.Invalid("The alphabet is empty.")
        return Validation.Valid(String(CharArray(length) { alphabet[random.nextInt(alphabet.length)] }))
    }
}
