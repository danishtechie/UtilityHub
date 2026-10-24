package com.utilityhub.logic

import com.utilityhub.logic.catalog.*
import com.utilityhub.logic.util.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class UtilAndCatalogTest {
    private fun <T> v(x: Validation<T>): T = (x as Validation.Valid).value

    @Test fun `unit conversion`() {
        assertEquals(1000.0, v(UnitConverter.convert("Length", "km", "m", 1.0)), 1e-9)
        assertEquals(12.0, v(UnitConverter.convert("Length", "ft", "in", 1.0)), 1e-9)
        assertEquals(1.0, v(UnitConverter.convert("Digital storage", "KiB", "B", 1.0)) / 1024.0, 1e-9)
        assertEquals(212.0, v(UnitConverter.convert("Temperature", "°C", "°F", 100.0)), 1e-9)
        assertEquals(0.0, v(UnitConverter.convert("Temperature", "K", "°C", 273.15)), 1e-9)
        assertTrue(UnitConverter.convert("Temperature", "K", "°C", -1.0) is Validation.Invalid)
        assertTrue(UnitConverter.convert("Length", "m", "parsec", 1.0) is Validation.Invalid)
        assertTrue(UnitConverter.convert("Length", "m", "km", Double.NaN) is Validation.Invalid)
        assertEquals("0.001", UnitConverter.format(0.001)); assertEquals("1000", UnitConverter.format(1000.0)); assertEquals("0", UnitConverter.format(0.0))
    }

    @Test fun `age and date difference`() {
        val d = v(DateTools.age(LocalDate.of(2000, 2, 29), LocalDate.of(2026, 10, 20)))
        assertEquals(26, d.period.years); assertEquals(7, d.period.months)
        assertTrue(DateTools.age(LocalDate.of(2030, 1, 1), LocalDate.of(2026, 1, 1)) is Validation.Invalid)
        assertEquals(31L, DateTools.difference(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)).totalDays)
        assertTrue(DateTools.parseDate("20-10-2026") is Validation.Invalid)
    }

    @Test fun `timestamps`() {
        assertTrue(v(DateTools.fromEpoch("0")).startsWith("1970-01-01T00:00:00Z"))
        assertTrue(v(DateTools.fromEpoch("1700000000000")).contains("milliseconds"))
        assertTrue(DateTools.fromEpoch("abc") is Validation.Invalid)
        assertEquals("0 seconds\n0 milliseconds", v(DateTools.toEpoch("1970-01-01T00:00:00Z")))
        assertEquals("86400 seconds\n86400000 milliseconds", v(DateTools.toEpoch("1970-01-02")))
        assertTrue(DateTools.toEpoch("tomorrow") is Validation.Invalid)
    }

    @Test fun `percentages`() {
        assertEquals(25.0, PercentTools.percentOf(10.0, 250.0), 1e-9)
        assertEquals(50.0, v(PercentTools.whatPercent(1.0, 2.0)), 1e-9)
        assertEquals(-50.0, v(PercentTools.change(200.0, 100.0)), 1e-9)
        assertTrue(PercentTools.whatPercent(1.0, 0.0) is Validation.Invalid)
        assertTrue(PercentTools.change(0.0, 5.0) is Validation.Invalid)
    }

    @Test fun `catalog integrity`() {
        val ids = ToolCatalog.all.map { it.id }
        assertEquals("duplicate ids", ids.size, ids.toSet().size)
        assertTrue(ToolCatalog.all.none { it.status == ToolStatus.PLANNED && it.metered })
        assertTrue(ToolCatalog.all.all { it.title.isNotBlank() && it.description.isNotBlank() })
        ToolCategory.entries.forEach { assertTrue(it.title, ToolCatalog.byCategory(it).any { t -> t.status == ToolStatus.AVAILABLE }) }
    }

    @Test fun `search ranks title matches first and requires all words`() {
        val r = ToolSearch.search(ToolCatalog.all, "resize")
        assertEquals("image_resize", r.first().id)
        assertTrue(ToolSearch.search(ToolCatalog.all, "pdf merge").any { it.id == "pdf_merge" })
        assertTrue(ToolSearch.search(ToolCatalog.all, "zzzzqqq").isEmpty())
        assertEquals(ToolCatalog.all.size, ToolSearch.search(ToolCatalog.all, "  ").size)
        assertTrue(ToolSearch.search(ToolCatalog.all, "EXIF").any { it.id == "image_metadata" })
    }
}
