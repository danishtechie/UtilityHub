package com.utilityhub.logic.spec

/** Declarative description of a tool option / input field, rendered generically by the UI. */
sealed interface FieldSpec {
    val key: String
    val label: String

    data class Text(
        override val key: String,
        override val label: String,
        val default: String = "",
        val multiline: Boolean = false,
        val hint: String = "",
    ) : FieldSpec

    data class IntValue(
        override val key: String,
        override val label: String,
        val default: Int,
        val min: Int,
        val max: Int,
        val unit: String = "",
    ) : FieldSpec

    data class Choice(
        override val key: String,
        override val label: String,
        val choices: List<String>,
        val default: String,
    ) : FieldSpec

    data class Toggle(
        override val key: String,
        override val label: String,
        val default: Boolean,
    ) : FieldSpec
}

fun List<FieldSpec>.defaultValues(): Map<String, String> = associate { spec ->
    spec.key to when (spec) {
        is FieldSpec.Text -> spec.default
        is FieldSpec.IntValue -> spec.default.toString()
        is FieldSpec.Choice -> spec.default
        is FieldSpec.Toggle -> spec.default.toString()
    }
}

/** Typed read access to the string map produced by the UI. */
class FieldValues(private val raw: Map<String, String>) {
    fun string(key: String, default: String = ""): String = raw[key] ?: default
    fun int(key: String): Int? = raw[key]?.trim()?.toIntOrNull()
    fun bool(key: String, default: Boolean = false): Boolean = raw[key]?.toBooleanStrictOrNull() ?: default
}
