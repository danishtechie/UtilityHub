package com.utilityhub.logic.util

import com.utilityhub.logic.Validation
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class DateDifference(val period: Period, val totalDays: Long, val earlier: LocalDate, val later: LocalDate) {
    fun describe(): String = "${plural(period.years, "year")}, ${plural(period.months, "month")}, ${plural(period.days, "day")} ($totalDays days in total)"
}

private fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"

object DateTools {
    fun parseDate(text: String): Validation<LocalDate> = try {
        Validation.Valid(LocalDate.parse(text.trim()))
    } catch (e: DateTimeParseException) {
        Validation.Invalid("Use the format YYYY-MM-DD, for example 2001-04-23.")
    }

    fun difference(a: LocalDate, b: LocalDate): DateDifference {
        val earlier = if (a <= b) a else b
        val later = if (a <= b) b else a
        return DateDifference(Period.between(earlier, later), ChronoUnit.DAYS.between(earlier, later), earlier, later)
    }

    fun age(birth: LocalDate, today: LocalDate): Validation<DateDifference> =
        if (birth.isAfter(today)) Validation.Invalid("The birth date is in the future.") else Validation.Valid(difference(birth, today))

    /** Epoch seconds or milliseconds (auto-detected) -> ISO-8601 UTC. */
    fun fromEpoch(text: String): Validation<String> {
        val n = text.trim().toLongOrNull() ?: return Validation.Invalid("Enter a whole number of seconds or milliseconds.")
        return try {
            val instant = if (abs(n) >= 100_000_000_000L) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n)
            Validation.Valid("$instant UTC (${if (abs(n) >= 100_000_000_000L) "milliseconds" else "seconds"} detected)")
        } catch (e: DateTimeException) {
            Validation.Invalid("That timestamp is out of range.")
        }
    }

    /** ISO-8601 (with offset, or without = UTC) -> epoch seconds and milliseconds. */
    fun toEpoch(text: String): Validation<String> {
        val t = text.trim()
        val instant = try { OffsetDateTime.parse(t).toInstant() } catch (e: DateTimeParseException) {
            try { LocalDateTime.parse(t).toInstant(ZoneOffset.UTC) } catch (e2: DateTimeParseException) {
                try { LocalDate.parse(t).atStartOfDay().toInstant(ZoneOffset.UTC) } catch (e3: DateTimeParseException) {
                    return Validation.Invalid("Use ISO-8601, e.g. 2026-10-20T14:30:00Z or 2026-10-20.")
                }
            }
        }
        return Validation.Valid("${instant.epochSecond} seconds\n${instant.toEpochMilli()} milliseconds")
    }
}

object PercentTools {
    fun percentOf(percent: Double, of: Double): Double = percent / 100.0 * of

    fun whatPercent(part: Double, whole: Double): Validation<Double> =
        if (whole == 0.0) Validation.Invalid("The total cannot be zero.") else Validation.Valid(part / whole * 100.0)

    fun change(from: Double, to: Double): Validation<Double> =
        if (from == 0.0) Validation.Invalid("The starting value cannot be zero.") else Validation.Valid((to - from) / abs(from) * 100.0)
}
