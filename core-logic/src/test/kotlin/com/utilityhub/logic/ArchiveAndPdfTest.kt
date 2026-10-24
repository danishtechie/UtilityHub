package com.utilityhub.logic

import com.utilityhub.logic.archive.*
import com.utilityhub.logic.media.MediaMath
import com.utilityhub.logic.pdf.PageSelection
import org.junit.Assert.*
import org.junit.Test

class ArchiveAndPdfTest {
    private fun unsafe(name: String) = assertTrue("should reject: $name", ZipEntryNames.check(name) is EntryNameCheck.Unsafe)

    @Test fun `zip slip and traversal names are rejected`() {
        listOf("../evil.txt", "a/../../evil", "/etc/passwd", "C:/Windows/x", "c:evil", "a\\b.txt", "a/b\u0000.txt", "", "   ", "./", "..", "a/b/../..").forEach(::unsafe)
    }

    @Test fun `ordinary names are accepted and normalised`() {
        assertEquals("a/b/c.txt", (ZipEntryNames.check("a/b/c.txt") as EntryNameCheck.Safe).path)
        assertEquals("a/c.txt", (ZipEntryNames.check("./a//c.txt") as EntryNameCheck.Safe).path)
        assertEquals("dir", (ZipEntryNames.check("dir/") as EntryNameCheck.Safe).path)
    }

    @Test fun `depth and length limits`() {
        unsafe((1..40).joinToString("/") { "d" })
        unsafe("x".repeat(500))
    }

    @Test fun `entry count limit`() {
        val guard = ExtractionGuard(ArchiveLimits(maxEntries = 3), 1000)
        repeat(3) { assertNull(guard.startEntry()) }
        assertNotNull(guard.startEntry())
    }

    @Test fun `per-file and total expansion limits use real bytes`() {
        val g = ExtractionGuard(ArchiveLimits(maxEntryBytes = 100, maxTotalBytes = 150), 1_000_000)
        assertNull(g.addBytes(60, 60))
        assertNotNull(g.addBytes(101, 41))                                  // single entry too large
        val g2 = ExtractionGuard(ArchiveLimits(maxEntryBytes = 100, maxTotalBytes = 150), 1_000_000)
        assertNull(g2.addBytes(100, 100)); assertNotNull(g2.addBytes(60, 60)) // total too large
    }

    @Test fun `zip bomb ratio is detected`() {
        val limits = ArchiveLimits(maxCompressionRatio = 100, ratioCheckFloorBytes = 1000)
        val g = ExtractionGuard(limits, 100)                                // 100-byte archive
        assertNull(g.addBytes(5_000, 5_000))
        assertNotNull(g.addBytes(20_000, 15_000))                           // > 100 * 100 bytes
    }

    @Test fun `oversized archive is refused`() {
        assertNotNull(ExtractionGuard(ArchiveLimits(maxArchiveBytes = 10), 11).checkArchiveSize())
        assertNull(ExtractionGuard(ArchiveLimits(maxArchiveBytes = 10), 10).checkArchiveSize())
    }

    @Test fun `output name collisions are resolved case-insensitively`() {
        val names = UniqueNames()
        assertEquals("docs/a.txt", names.claim("docs/a.txt"))
        assertEquals("docs/A (1).TXT", names.claim("docs/A.TXT"))   // keeps the caller's casing
        assertEquals("docs/a (2).txt", names.claim("docs/a.txt"))
        assertEquals("noext", names.claim("noext"))
        assertEquals("noext (1)", names.claim("noext"))
    }

    private fun pages(spec: String, count: Int = 10) = (PageSelection.parse(spec, count) as Validation.Valid).value

    @Test fun `page selection parsing`() {
        assertEquals(listOf(0, 1, 2, 4, 6, 7, 8), pages("1-3, 5, 7-9"))
        assertEquals(listOf(2, 0), pages("3,1"))
        assertEquals(listOf(0, 1), pages("1,1,2,2"))
        assertEquals(listOf(9), pages(" 10 "))
    }

    @Test fun `page selection errors`() {
        listOf("", "0", "11", "a", "3-1", "1,,2", "1-11", "0-2", "2-").forEach {
            assertTrue("should reject \"$it\"", PageSelection.parse(it, 10) is Validation.Invalid)
        }
        assertTrue(PageSelection.parse("1", 0) is Validation.Invalid)
    }

    @Test fun `remaining and chunks`() {
        assertEquals(listOf(1, 3), PageSelection.remaining(4, listOf(0, 2)))
        assertEquals(listOf(0..2, 3..5, 6..6), (PageSelection.chunks(7, 3) as Validation.Valid).value.map { it.first..it.last })
        assertTrue(PageSelection.chunks(7, 0) is Validation.Invalid)
    }

    @Test fun `media duration validation`() {
        assertEquals(4000L, (MediaMath.validateTrim(1000, 5000, 10_000) as Validation.Valid).value)
        assertTrue(MediaMath.validateTrim(-1, 5000, 10_000) is Validation.Invalid)
        assertTrue(MediaMath.validateTrim(5000, 5000, 10_000) is Validation.Invalid)
        assertTrue(MediaMath.validateTrim(0, 10_001, 10_000) is Validation.Invalid)
        assertTrue(MediaMath.validateTrim(0, 50, 10_000) is Validation.Invalid)
        assertTrue(MediaMath.validateTrim(0, 10, 0) is Validation.Invalid)
        assertEquals("01:05", MediaMath.formatDuration(65_000))
        assertEquals("1:01:05", MediaMath.formatDuration(3_665_000))
        assertNull(MediaMath.progress(100, 0))
        assertEquals(0.5f, MediaMath.progress(50, 100)!!, 1e-6f)
    }
}
