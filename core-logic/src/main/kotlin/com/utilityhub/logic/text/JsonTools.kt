package com.utilityhub.logic.text

import com.utilityhub.logic.Validation

sealed interface JsonNode {
    data class Obj(val members: LinkedHashMap<String, JsonNode>) : JsonNode
    data class Arr(val items: List<JsonNode>) : JsonNode
    data class Str(val value: String) : JsonNode
    data class Num(val raw: String) : JsonNode
    data class Bool(val value: Boolean) : JsonNode
    data object Null : JsonNode
}

/** Strict RFC 8259 JSON validator / formatter with input-size and depth limits. */
object JsonTools {
    const val MAX_INPUT_CHARS = 2_000_000
    const val MAX_DEPTH = 200

    fun parse(text: String): Validation<JsonNode> {
        if (text.isBlank()) return Validation.Invalid("Enter some JSON.")
        if (text.length > MAX_INPUT_CHARS) return Validation.Invalid("Input is larger than $MAX_INPUT_CHARS characters.")
        return try {
            Validation.Valid(JsonParser(text).parseDocument())
        } catch (e: JsonParser.JsonError) {
            Validation.Invalid(e.message ?: "Invalid JSON.")
        }
    }

    fun validate(text: String): Validation<Unit> = when (val r = parse(text)) {
        is Validation.Valid -> Validation.Valid(Unit)
        is Validation.Invalid -> r
    }

    fun format(text: String, indent: Int = 2): Validation<String> = when (val r = parse(text)) {
        is Validation.Valid -> Validation.Valid(StringBuilder().also { write(r.value, it, indent.coerceIn(1, 8), 0) }.toString())
        is Validation.Invalid -> r
    }

    fun minify(text: String): Validation<String> = when (val r = parse(text)) {
        is Validation.Valid -> Validation.Valid(StringBuilder().also { write(r.value, it, null, 0) }.toString())
        is Validation.Invalid -> r
    }

    private fun write(node: JsonNode, sb: StringBuilder, indent: Int?, level: Int) {
        fun pad(l: Int) { if (indent != null) { sb.append('\n'); repeat(l * indent) { sb.append(' ') } } }
        when (node) {
            is JsonNode.Obj -> {
                if (node.members.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, v) in node.members) {
                    if (!first) sb.append(',')
                    first = false
                    pad(level + 1)
                    writeString(k, sb)
                    sb.append(if (indent != null) ": " else ":")
                    write(v, sb, indent, level + 1)
                }
                pad(level)
                sb.append('}')
            }
            is JsonNode.Arr -> {
                if (node.items.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                node.items.forEachIndexed { index, v ->
                    if (index > 0) sb.append(',')
                    pad(level + 1)
                    write(v, sb, indent, level + 1)
                }
                pad(level)
                sb.append(']')
            }
            is JsonNode.Str -> writeString(node.value, sb)
            is JsonNode.Num -> sb.append(node.raw)
            is JsonNode.Bool -> sb.append(node.value)
            JsonNode.Null -> sb.append("null")
        }
    }

    private fun writeString(s: String, sb: StringBuilder) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c == '\b' -> sb.append("\\b")
            c == '\u000C' -> sb.append("\\f")
            c.code < 0x20 -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }
}

private class JsonParser(private val s: String) {
    class JsonError(message: String) : Exception(message)

    private var i = 0
    private val numberPattern = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")

    fun parseDocument(): JsonNode {
        skipWs()
        val v = value(0)
        skipWs()
        if (i < s.length) fail("Unexpected data after the JSON value")
        return v
    }

    private fun fail(message: String): Nothing {
        var line = 1
        var col = 1
        for (k in 0 until minOf(i, s.length)) { if (s[k] == '\n') { line++; col = 1 } else col++ }
        throw JsonError("$message at line $line, column $col")
    }

    private fun skipWs() { while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++ }

    private fun value(depth: Int): JsonNode {
        if (depth > JsonTools.MAX_DEPTH) fail("Nesting is too deep")
        if (i >= s.length) fail("Unexpected end of input")
        return when (val c = s[i]) {
            '{' -> obj(depth)
            '[' -> arr(depth)
            '"' -> JsonNode.Str(string())
            't' -> literal("true", JsonNode.Bool(true))
            'f' -> literal("false", JsonNode.Bool(false))
            'n' -> literal("null", JsonNode.Null)
            else -> if (c == '-' || c in '0'..'9') number() else fail("Unexpected character '$c'")
        }
    }

    private fun literal(word: String, node: JsonNode): JsonNode {
        if (!s.startsWith(word, i)) fail("Invalid literal")
        i += word.length
        return node
    }

    private fun number(): JsonNode {
        val m = numberPattern.matchAt(s, i) ?: fail("Invalid number")
        i += m.value.length
        return JsonNode.Num(m.value)
    }

    private fun string(): String {
        i++ // opening quote
        val sb = StringBuilder()
        while (true) {
            if (i >= s.length) fail("Unterminated string")
            val c = s[i++]
            when {
                c == '"' -> return sb.toString()
                c.code < 0x20 -> { i--; fail("Unescaped control character in string") }
                c == '\\' -> {
                    if (i >= s.length) fail("Unterminated escape")
                    when (val e = s[i++]) {
                        '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                        'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) fail("Incomplete unicode escape")
                            val code = s.substring(i, i + 4).toIntOrNull(16) ?: fail("Invalid unicode escape")
                            sb.append(code.toChar())
                            i += 4
                        }
                        else -> { i--; fail("Invalid escape '\\$e'") }
                    }
                }
                else -> sb.append(c)
            }
        }
    }

    private fun arr(depth: Int): JsonNode {
        i++
        val items = ArrayList<JsonNode>()
        skipWs()
        if (i < s.length && s[i] == ']') { i++; return JsonNode.Arr(items) }
        while (true) {
            skipWs()
            items += value(depth + 1)
            skipWs()
            if (i >= s.length) fail("Unterminated array")
            when (s[i++]) {
                ',' -> continue
                ']' -> return JsonNode.Arr(items)
                else -> { i--; fail("Expected ',' or ']'") }
            }
        }
    }

    private fun obj(depth: Int): JsonNode {
        i++
        val members = LinkedHashMap<String, JsonNode>()
        skipWs()
        if (i < s.length && s[i] == '}') { i++; return JsonNode.Obj(members) }
        while (true) {
            skipWs()
            if (i >= s.length || s[i] != '"') fail("Expected a string key")
            val key = string()
            skipWs()
            if (i >= s.length || s[i] != ':') fail("Expected ':'")
            i++
            skipWs()
            members[key] = value(depth + 1)
            skipWs()
            if (i >= s.length) fail("Unterminated object")
            when (s[i++]) {
                ',' -> continue
                '}' -> return JsonNode.Obj(members)
                else -> { i--; fail("Expected ',' or '}'") }
            }
        }
    }
}
