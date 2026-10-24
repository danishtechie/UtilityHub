package com.utilityhub.logic.text

enum class MdStyle { PLAIN, BOLD, ITALIC, CODE, LINK }
data class MdSpan(val text: String, val style: MdStyle = MdStyle.PLAIN)

sealed interface MdBlock {
    data class Heading(val level: Int, val spans: List<MdSpan>) : MdBlock
    data class Paragraph(val spans: List<MdSpan>) : MdBlock
    data class ListItem(val ordered: Boolean, val number: Int, val spans: List<MdSpan>) : MdBlock
    data class Quote(val spans: List<MdSpan>) : MdBlock
    data class CodeBlock(val code: String) : MdBlock
    data object Rule : MdBlock
}

/** A small, safe Markdown subset (headings, bold, italic, code, links shown as text, lists, quotes, rules). No HTML is ever interpreted. */
object MarkdownLite {
    const val MAX_CHARS = 200_000

    fun parse(text: String): List<MdBlock> {
        val blocks = ArrayList<MdBlock>()
        val lines = text.take(MAX_CHARS).replace("\r\n", "\n").split('\n')
        var i = 0
        val paragraph = ArrayList<String>()
        fun flush() { if (paragraph.isNotEmpty()) { blocks += MdBlock.Paragraph(spans(paragraph.joinToString(" "))); paragraph.clear() } }
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.startsWith("```") -> {
                    flush()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) { code.append(lines[i]).append('\n'); i++ }
                    blocks += MdBlock.CodeBlock(code.toString().trimEnd('\n'))
                }
                t.isEmpty() -> flush()
                t.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) -> { flush(); blocks += MdBlock.Rule }
                t.startsWith("#") -> {
                    val level = t.takeWhile { it == '#' }.length
                    if (level in 1..6 && t.length > level && t[level] == ' ') { flush(); blocks += MdBlock.Heading(level, spans(t.substring(level + 1).trim())) } else paragraph += t
                }
                t.startsWith("> ") -> { flush(); blocks += MdBlock.Quote(spans(t.substring(2))) }
                Regex("^[-*+] .+").matches(t) -> { flush(); blocks += MdBlock.ListItem(false, 0, spans(t.substring(2))) }
                Regex("^\\d{1,3}\\. .+").matches(t) -> { flush(); blocks += MdBlock.ListItem(true, t.substringBefore('.').toInt(), spans(t.substringAfter(". "))) }
                else -> paragraph += t
            }
            i++
        }
        flush()
        return blocks
    }

    fun spans(text: String): List<MdSpan> {
        val out = ArrayList<MdSpan>()
        val plain = StringBuilder()
        fun flushPlain() { if (plain.isNotEmpty()) { out += MdSpan(plain.toString()); plain.setLength(0) } }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                text.startsWith("**", i) && text.indexOf("**", i + 2) > i + 2 -> { flushPlain(); val e = text.indexOf("**", i + 2); out += MdSpan(text.substring(i + 2, e), MdStyle.BOLD); i = e + 2; continue }
                c == '`' && text.indexOf('`', i + 1) > i + 1 -> { flushPlain(); val e = text.indexOf('`', i + 1); out += MdSpan(text.substring(i + 1, e), MdStyle.CODE); i = e + 1; continue }
                (c == '*' || c == '_') && text.indexOf(c, i + 1) > i + 1 && !text[i + 1].isWhitespace() -> { flushPlain(); val e = text.indexOf(c, i + 1); out += MdSpan(text.substring(i + 1, e), MdStyle.ITALIC); i = e + 1; continue }
                c == '[' -> {
                    val close = text.indexOf("](", i)
                    val end = if (close > i) text.indexOf(')', close) else -1
                    if (close > i && end > close) { flushPlain(); out += MdSpan(text.substring(i + 1, close), MdStyle.LINK); i = end + 1; continue }
                }
            }
            plain.append(c); i++
        }
        flushPlain()
        return out
    }
}
