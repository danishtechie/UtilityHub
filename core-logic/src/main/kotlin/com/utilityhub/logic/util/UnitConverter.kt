package com.utilityhub.logic.util

import com.utilityhub.logic.Validation
import java.math.BigDecimal
import java.math.MathContext

data class UnitDef(val name: String, val toBase: Double)

object UnitConverter {
    const val TEMPERATURE = "Temperature"

    val categories: Map<String, List<UnitDef>> = linkedMapOf(
        "Length" to listOf(
            UnitDef("mm", 0.001), UnitDef("cm", 0.01), UnitDef("m", 1.0), UnitDef("km", 1000.0),
            UnitDef("in", 0.0254), UnitDef("ft", 0.3048), UnitDef("yd", 0.9144), UnitDef("mi", 1609.344),
        ),
        "Mass" to listOf(
            UnitDef("mg", 1e-6), UnitDef("g", 0.001), UnitDef("kg", 1.0), UnitDef("t", 1000.0),
            UnitDef("oz", 0.028349523125), UnitDef("lb", 0.45359237),
        ),
        "Digital storage" to listOf(
            UnitDef("B", 1.0), UnitDef("KB", 1e3), UnitDef("MB", 1e6), UnitDef("GB", 1e9), UnitDef("TB", 1e12),
            UnitDef("KiB", 1024.0), UnitDef("MiB", 1024.0 * 1024), UnitDef("GiB", 1024.0 * 1024 * 1024),
            UnitDef("TiB", 1024.0 * 1024 * 1024 * 1024),
        ),
        "Time" to listOf(UnitDef("s", 1.0), UnitDef("min", 60.0), UnitDef("h", 3600.0), UnitDef("day", 86400.0), UnitDef("week", 604800.0)),
        TEMPERATURE to listOf(UnitDef("°C", 0.0), UnitDef("°F", 0.0), UnitDef("K", 0.0)),
    )

    fun unitNames(category: String): List<String> = categories[category]?.map { it.name } ?: emptyList()

    fun convert(category: String, from: String, to: String, value: Double): Validation<Double> {
        if (value.isNaN() || value.isInfinite()) return Validation.Invalid("Enter a valid number.")
        val units = categories[category] ?: return Validation.Invalid("Unknown category.")
        if (units.none { it.name == from } || units.none { it.name == to }) return Validation.Invalid("Unknown unit.")
        if (category == TEMPERATURE) {
            val kelvin = when (from) { "°C" -> value + 273.15; "°F" -> (value - 32) * 5 / 9 + 273.15; else -> value }
            if (kelvin < 0) return Validation.Invalid("That is below absolute zero.")
            return Validation.Valid(when (to) { "°C" -> kelvin - 273.15; "°F" -> (kelvin - 273.15) * 9 / 5 + 32; else -> kelvin })
        }
        val base = value * units.first { it.name == from }.toBase
        return Validation.Valid(base / units.first { it.name == to }.toBase)
    }

    fun format(value: Double): String {
        if (value == 0.0) return "0"
        return BigDecimal(value).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }
}
