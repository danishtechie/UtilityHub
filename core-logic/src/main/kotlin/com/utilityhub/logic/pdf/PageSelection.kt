package com.utilityhub.logic.pdf

import com.utilityhub.logic.Validation

object PageSelection {
    /**
     * Parses "1-3, 5, 7-9" (1-based) into zero-based page indices.
     * Order is preserved (so "3,1" extracts page 3 then page 1); duplicates are dropped.
     */
    fun parse(spec: String, pageCount: Int): Validation<List<Int>> {
        if (pageCount <= 0) return Validation.Invalid("The document has no pages.")
        if (spec.isBlank()) return Validation.Invalid("Enter the pages to use, for example 1-3, 5.")
        val result = LinkedHashSet<Int>()
        for (rawToken in spec.split(',')) {
            val token = rawToken.trim()
            if (token.isEmpty()) return Validation.Invalid("There is an empty entry in the page list.")
            val dash = token.indexOf('-')
            if (dash < 0) {
                val page = token.toIntOrNull() ?: return Validation.Invalid("\"$token\" is not a page number.")
                if (page !in 1..pageCount) return Validation.Invalid("Page $page is outside 1–$pageCount.")
                result += page - 1
            } else {
                val from = token.substring(0, dash).trim().toIntOrNull()
                val to = token.substring(dash + 1).trim().toIntOrNull()
                if (from == null || to == null) return Validation.Invalid("\"$token\" is not a valid page range.")
                if (from > to) return Validation.Invalid("Range \"$token\" runs backwards.")
                if (from < 1 || to > pageCount) return Validation.Invalid("Range \"$token\" is outside 1–$pageCount.")
                for (p in from..to) result += p - 1
            }
        }
        return Validation.Valid(result.toList())
    }

    /** Pages that remain after deleting [deleted] (zero-based), in document order. */
    fun remaining(pageCount: Int, deleted: Collection<Int>): List<Int> {
        val drop = deleted.toSet()
        return (0 until pageCount).filter { it !in drop }
    }

    /** Splits [pageCount] pages into consecutive chunks of [every] pages. */
    fun chunks(pageCount: Int, every: Int): Validation<List<IntRange>> {
        if (pageCount <= 0) return Validation.Invalid("The document has no pages.")
        if (every < 1) return Validation.Invalid("Pages per file must be at least 1.")
        return Validation.Valid((0 until pageCount step every).map { it until minOf(it + every, pageCount) })
    }
}
