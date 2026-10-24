package com.utilityhub.logic.util

import com.utilityhub.logic.Validation
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** Safe arithmetic evaluator (no eval, no reflection). Supports + - * / % ^ ( ), unary minus, sqrt sin cos tan ln log abs, pi, e. */
object ExpressionEvaluator {
    const val MAX_LENGTH = 200
    private const val MAX_DEPTH = 40

    private class Fail(message: String) : Exception(message)

    fun evaluate(expression: String): Validation<Double> {
        val text = expression.trim()
        if (text.isEmpty()) return Validation.Invalid("Enter an expression.")
        if (text.length > MAX_LENGTH) return Validation.Invalid("The expression is too long.")
        return try {
            val p = Parser(text.replace('×', '*').replace('÷', '/').replace('−', '-'))
            val v = p.parse()
            if (v.isNaN() || v.isInfinite()) Validation.Invalid("The result is not a finite number.") else Validation.Valid(v)
        } catch (e: Fail) {
            Validation.Invalid(e.message ?: "Invalid expression.")
        }
    }

    private class Parser(private val s: String) {
        private var i = 0

        fun parse(): Double {
            val v = expr(0)
            skip()
            if (i < s.length) throw Fail("Unexpected '${s[i]}'.")
            return v
        }

        private fun skip() { while (i < s.length && s[i] == ' ') i++ }
        private fun peek(): Char? { skip(); return if (i < s.length) s[i] else null }

        private fun expr(depth: Int): Double {
            if (depth > MAX_DEPTH) throw Fail("Too many nested brackets.")
            var v = term(depth)
            while (true) {
                when (peek()) {
                    '+' -> { i++; v += term(depth) }
                    '-' -> { i++; v -= term(depth) }
                    else -> return v
                }
            }
        }

        private fun term(depth: Int): Double {
            var v = unary(depth)
            while (true) {
                when (peek()) {
                    '*' -> { i++; v *= unary(depth) }
                    '/' -> { i++; val d = unary(depth); if (d == 0.0) throw Fail("Cannot divide by zero."); v /= d }
                    '%' -> { i++; val d = unary(depth); if (d == 0.0) throw Fail("Cannot divide by zero."); v %= d }
                    else -> return v
                }
            }
        }

        private fun unary(depth: Int): Double = when (peek()) {
            '-' -> { i++; -unary(depth) }
            '+' -> { i++; unary(depth) }
            else -> power(depth)
        }

        private fun power(depth: Int): Double {
            val base = atom(depth)
            return if (peek() == '^') { i++; base.pow(unary(depth)) } else base   // right associative
        }

        private fun atom(depth: Int): Double {
            val c = peek() ?: throw Fail("The expression ends unexpectedly.")
            if (c == '(') {
                i++
                val v = expr(depth + 1)
                if (peek() != ')') throw Fail("Missing closing bracket.")
                i++
                return v
            }
            if (c.isDigit() || c == '.') return number()
            if (c.isLetter()) {
                val start = i
                while (i < s.length && s[i].isLetter()) i++
                val name = s.substring(start, i).lowercase()
                if (name == "pi") return Math.PI
                if (name == "e") return Math.E
                if (peek() != '(') throw Fail("Unknown name \"$name\".")
                i++
                val arg = expr(depth + 1)
                if (peek() != ')') throw Fail("Missing closing bracket.")
                i++
                return when (name) {
                    "sqrt" -> if (arg < 0) throw Fail("Square root of a negative number.") else sqrt(arg)
                    "sin" -> sin(arg); "cos" -> cos(arg); "tan" -> tan(arg)
                    "ln" -> if (arg <= 0) throw Fail("ln needs a positive number.") else ln(arg)
                    "log" -> if (arg <= 0) throw Fail("log needs a positive number.") else log10(arg)
                    "abs" -> abs(arg)
                    else -> throw Fail("Unknown function \"$name\".")
                }
            }
            throw Fail("Unexpected '$c'.")
        }

        private fun number(): Double {
            val start = i
            var dot = false
            while (i < s.length && (s[i].isDigit() || (s[i] == '.' && !dot))) { if (s[i] == '.') dot = true; i++ }
            return s.substring(start, i).toDoubleOrNull() ?: throw Fail("Invalid number.")
        }
    }
}
