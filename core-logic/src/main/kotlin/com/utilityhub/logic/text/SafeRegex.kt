package com.utilityhub.logic.text

import com.utilityhub.logic.Validation
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class RegexMatch(val start: Int, val end: Int, val text: String, val groups: List<String?>)

/** Regex tester with length limits and a hard time budget (catastrophic backtracking protection). */
object SafeRegex {
    const val MAX_PATTERN = 500
    const val MAX_INPUT = 100_000
    const val MAX_MATCHES = 200

    private class Timeout : RuntimeException()

    private class DeadlineText(private val inner: CharSequence, private val deadlineNanos: Long) : CharSequence {
        private var calls = 0
        override val length: Int get() = inner.length
        override fun get(index: Int): Char {
            if ((++calls and 0x3FF) == 0 && System.nanoTime() > deadlineNanos) throw Timeout()
            return inner[index]
        }
        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = inner.subSequence(startIndex, endIndex)
        override fun toString(): String = inner.toString()
    }

    fun findAll(
        pattern: String,
        input: String,
        ignoreCase: Boolean = false,
        multiline: Boolean = false,
        timeoutMs: Long = 1_500,
    ): Validation<List<RegexMatch>> {
        if (pattern.isEmpty()) return Validation.Invalid("Enter a pattern.")
        if (pattern.length > MAX_PATTERN) return Validation.Invalid("Pattern is longer than $MAX_PATTERN characters.")
        if (input.length > MAX_INPUT) return Validation.Invalid("Test text is longer than $MAX_INPUT characters.")
        var flags = 0
        if (ignoreCase) flags = flags or Pattern.CASE_INSENSITIVE
        if (multiline) flags = flags or Pattern.MULTILINE
        val compiled = try { Pattern.compile(pattern, flags) } catch (e: PatternSyntaxException) {
            return Validation.Invalid("Invalid pattern: ${e.description}")
        }
        val matcher = compiled.matcher(DeadlineText(input, System.nanoTime() + timeoutMs * 1_000_000))
        val results = ArrayList<RegexMatch>()
        try {
            while (results.size < MAX_MATCHES && matcher.find()) {
                results += RegexMatch(
                    matcher.start(), matcher.end(), matcher.group(),
                    (1..matcher.groupCount()).map { matcher.group(it) },
                )
            }
        } catch (e: Timeout) {
            return Validation.Invalid("The pattern took too long to evaluate and was stopped. Try a simpler pattern.")
        }
        return Validation.Valid(results)
    }
}
