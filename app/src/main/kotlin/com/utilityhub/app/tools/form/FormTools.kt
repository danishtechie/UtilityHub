package com.utilityhub.app.tools.form

import com.utilityhub.logic.Validation
import com.utilityhub.logic.security.PasswordGenerator
import com.utilityhub.logic.security.PasswordOptions
import com.utilityhub.logic.spec.FieldSpec
import com.utilityhub.logic.spec.FieldValues
import com.utilityhub.logic.text.CaseMode
import com.utilityhub.logic.text.EncodingTools
import com.utilityhub.logic.text.JsonTools
import com.utilityhub.logic.text.SafeRegex
import com.utilityhub.logic.text.TextTools
import com.utilityhub.logic.util.DateTools
import com.utilityhub.logic.util.PercentTools
import com.utilityhub.logic.util.UnitConverter
import java.security.SecureRandom
import java.time.LocalDate
import java.util.UUID

data class FormOutput(val text: String, val note: String? = null)

/** A lightweight tool: declarative fields in, text out. Pure functions from core-logic do the work. */
class FormTool(
    val fields: List<FieldSpec>,
    val actionLabel: String,
    val run: (FieldValues) -> Validation<FormOutput>,
)

object FormTools {
    private val secureRandom = SecureRandom()

    private fun text(key: String = "text", label: String = "Text", hint: String = "") = FieldSpec.Text(key, label, multiline = true, hint = hint)

    private fun ok(s: String, note: String? = null) = Validation.Valid(FormOutput(s, note))

    private fun num(raw: String, label: String): Validation<Double> {
        val v = raw.trim().replace(',', '.').toDoubleOrNull()
        return if (v == null || v.isNaN() || v.isInfinite()) Validation.Invalid("Enter a valid number for $label.") else Validation.Valid(v)
    }

    private inline fun <T> Validation<T>.then(next: (T) -> Validation<FormOutput>): Validation<FormOutput> = when (this) {
        is Validation.Valid -> next(value)
        is Validation.Invalid -> this
    }

    private fun fmt(d: Double) = UnitConverter.format(d)

    val all: Map<String, FormTool> = mapOf(
        "text_stats" to FormTool(listOf(text()), "Count") { v ->
            val s = TextTools.stats(v.string("text"))
            ok("Words: ${s.words}\nCharacters: ${s.characters}\nCharacters without spaces: ${s.charactersNoSpaces}\nLines: ${s.lines}")
        },
        "text_case" to FormTool(
            listOf(text(), FieldSpec.Choice("mode", "Convert to", listOf("UPPERCASE", "lowercase", "Title Case", "Sentence case"), "Title Case")),
            "Convert",
        ) { v ->
            val mode = when (v.string("mode")) { "UPPERCASE" -> CaseMode.UPPER; "lowercase" -> CaseMode.LOWER; "Sentence case" -> CaseMode.SENTENCE; else -> CaseMode.TITLE }
            ok(TextTools.convertCase(v.string("text"), mode))
        },
        "text_dedupe" to FormTool(
            listOf(text(), FieldSpec.Toggle("ignoreCase", "Ignore letter case", false), FieldSpec.Toggle("trim", "Ignore leading/trailing spaces", true)),
            "Remove duplicates",
        ) { v ->
            val r = TextTools.dedupeLines(v.string("text"), v.bool("ignoreCase"), v.bool("trim", true))
            ok(r.text, "Removed ${r.removed} duplicate line(s).")
        },
        "text_sort" to FormTool(
            listOf(text(), FieldSpec.Toggle("desc", "Descending", false), FieldSpec.Toggle("ignoreCase", "Ignore letter case", true), FieldSpec.Toggle("numeric", "Sort numbers by value", false)),
            "Sort",
        ) { v -> ok(TextTools.sortLines(v.string("text"), v.bool("desc"), v.bool("ignoreCase", true), v.bool("numeric"))) },
        "json_format" to FormTool(
            listOf(text(hint = "{\"name\": \"value\"}", label = "JSON"), FieldSpec.Choice("action", "Action", listOf("Format", "Minify", "Validate only"), "Format"), FieldSpec.IntValue("indent", "Indent", 2, 1, 8, "spaces")),
            "Run",
        ) { v ->
            val input = v.string("text")
            when (v.string("action")) {
                "Minify" -> JsonTools.minify(input).then { ok(it) }
                "Validate only" -> JsonTools.validate(input).then { ok("Valid JSON ✓") }
                else -> JsonTools.format(input, v.int("indent") ?: 2).then { ok(it) }
            }
        },
        "base64_text" to FormTool(
            listOf(text(), FieldSpec.Choice("mode", "Mode", listOf("Encode", "Decode"), "Encode"), FieldSpec.Toggle("urlSafe", "URL-safe alphabet (encode)", false)),
            "Run",
        ) { v ->
            (if (v.string("mode") == "Decode") EncodingTools.base64Decode(v.string("text")) else EncodingTools.base64Encode(v.string("text"), v.bool("urlSafe"))).then { ok(it) }
        },
        "url_codec" to FormTool(
            listOf(text(), FieldSpec.Choice("mode", "Mode", listOf("Encode", "Decode"), "Encode")),
            "Run",
        ) { v -> (if (v.string("mode") == "Decode") EncodingTools.urlDecode(v.string("text")) else EncodingTools.urlEncode(v.string("text"))).then { ok(it) } },
        "password_gen" to FormTool(
            listOf(
                FieldSpec.IntValue("length", "Length", 16, PasswordGenerator.MIN_LENGTH, PasswordGenerator.MAX_LENGTH),
                FieldSpec.Toggle("lower", "Lowercase letters", true), FieldSpec.Toggle("upper", "Uppercase letters", true),
                FieldSpec.Toggle("digits", "Digits", true), FieldSpec.Toggle("symbols", "Symbols", true),
                FieldSpec.Toggle("ambiguous", "Exclude look-alike characters (0 O 1 l I |)", false),
            ),
            "Generate",
        ) { v ->
            PasswordGenerator.generate(
                PasswordOptions(v.int("length") ?: 16, v.bool("lower", true), v.bool("upper", true), v.bool("digits", true), v.bool("symbols", true), v.bool("ambiguous")),
                secureRandom,
            ).then { ok(it, "Generated on this device with a cryptographically secure random generator. It is never stored or sent anywhere.") }
        },
        "uuid_gen" to FormTool(listOf(FieldSpec.IntValue("count", "How many", 1, 1, 50)), "Generate") { v ->
            ok((1..(v.int("count") ?: 1).coerceIn(1, 50)).joinToString("\n") { UUID.randomUUID().toString() })
        },
        "random_string" to FormTool(
            listOf(FieldSpec.IntValue("length", "Length", 32, 1, 512), FieldSpec.Choice("charset", "Characters", listOf("Letters and digits", "Hex", "Digits only", "URL-safe"), "Letters and digits")),
            "Generate",
        ) { v ->
            val alphabet = when (v.string("charset")) {
                "Hex" -> "0123456789abcdef"
                "Digits only" -> "0123456789"
                "URL-safe" -> "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                else -> "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            }
            PasswordGenerator.randomString(v.int("length") ?: 32, alphabet, secureRandom).then { ok(it) }
        },
        "regex_tester" to FormTool(
            listOf(FieldSpec.Text("pattern", "Pattern", hint = "(\\d+)-(\\w+)"), text("input", "Test text"), FieldSpec.Toggle("ignoreCase", "Ignore case", false), FieldSpec.Toggle("multiline", "Multiline (^ and $ per line)", false)),
            "Test",
        ) { v ->
            SafeRegex.findAll(v.string("pattern"), v.string("input"), v.bool("ignoreCase"), v.bool("multiline")).then { matches ->
                if (matches.isEmpty()) ok("No matches.") else ok(
                    matches.mapIndexed { i, m ->
                        "Match ${i + 1} [${m.start}–${m.end}]: ${m.text}" + m.groups.mapIndexed { g, t -> "\n    group ${g + 1}: ${t ?: "(no match)"}" }.joinToString("")
                    }.joinToString("\n"),
                    "${matches.size} match(es)" + if (matches.size >= SafeRegex.MAX_MATCHES) " (showing the first ${SafeRegex.MAX_MATCHES})" else "",
                )
            }
        },
        "timestamp" to FormTool(
            listOf(FieldSpec.Choice("mode", "Convert", listOf("Timestamp → date", "Date → timestamp"), "Timestamp → date"), FieldSpec.Text("value", "Value", hint = "Leave blank for now")),
            "Convert",
        ) { v ->
            val raw = v.string("value").trim()
            val toDate = v.string("mode") != "Date → timestamp"
            when {
                raw.isEmpty() -> ok("Now: ${java.time.Instant.now()}\nEpoch: ${java.time.Instant.now().epochSecond} s")
                toDate -> DateTools.fromEpoch(raw).then { ok(it) }
                else -> DateTools.toEpoch(raw).then { ok(it) }
            }
        },
        "percentage" to FormTool(
            listOf(FieldSpec.Choice("mode", "Calculate", listOf("X% of Y", "X is what % of Y", "% change from X to Y"), "X% of Y"), FieldSpec.Text("a", "X"), FieldSpec.Text("b", "Y")),
            "Calculate",
        ) { v ->
            num(v.string("a"), "X").then { a ->
                num(v.string("b"), "Y").then { b ->
                    when (v.string("mode")) {
                        "X is what % of Y" -> PercentTools.whatPercent(a, b).then { ok("${fmt(it)}%") }
                        "% change from X to Y" -> PercentTools.change(a, b).then { ok("${fmt(it)}% ${if (it >= 0) "increase" else "decrease"}") }
                        else -> ok(fmt(PercentTools.percentOf(a, b)))
                    }
                }
            }
        },
        "age_calc" to FormTool(listOf(FieldSpec.Text("birth", "Date of birth", hint = "YYYY-MM-DD")), "Calculate") { v ->
            DateTools.parseDate(v.string("birth")).then { birth -> DateTools.age(birth, LocalDate.now()).then { ok(it.describe()) } }
        },
        "date_diff" to FormTool(listOf(FieldSpec.Text("from", "From", hint = "YYYY-MM-DD"), FieldSpec.Text("to", "To", hint = "YYYY-MM-DD")), "Calculate") { v ->
            DateTools.parseDate(v.string("from")).then { a -> DateTools.parseDate(v.string("to")).then { b -> ok(DateTools.difference(a, b).describe()) } }
        },
    )
}
