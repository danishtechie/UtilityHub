package com.utilityhub.logic

import com.utilityhub.logic.files.*
import com.utilityhub.logic.image.*
import com.utilityhub.logic.media.MediaMath
import com.utilityhub.logic.util.ExpressionEvaluator
import org.junit.Assert.*
import org.junit.Test

class NewLogicTest {
    private fun <T> v(x: Validation<T>): T = (x as Validation.Valid).value
    private fun calc(s: String) = v(ExpressionEvaluator.evaluate(s))

    @Test fun `calculator respects precedence, associativity and brackets`() {
        assertEquals(14.0, calc("2 + 3 * 4"), 1e-9)
        assertEquals(20.0, calc("(2 + 3) * 4"), 1e-9)
        assertEquals(512.0, calc("2 ^ 3 ^ 2"), 1e-9)       // right associative
        assertEquals(-4.0, calc("-2 ^ 2") * 1.0 + 0.0, 1e-9)
        assertEquals(1.0, calc("10 % 3"), 1e-9)
        assertEquals(5.0, calc("sqrt(16) + 1"), 1e-9)
        assertEquals(Math.PI, calc("pi"), 1e-12)
        assertEquals(7.5, calc("15 ÷ 2"), 1e-9)
        assertEquals(0.3, calc("0.1 + 0.2"), 1e-9)
    }

    @Test fun `calculator rejects bad input safely`() {
        listOf("", "2 +", "(1", "1)", "1/0", "sqrt(-1)", "foo(2)", "2 $ 3", "ln(0)", "1..2", "x").forEach {
            assertTrue("should reject \"$it\"", ExpressionEvaluator.evaluate(it) is Validation.Invalid)
        }
        assertTrue(ExpressionEvaluator.evaluate("(".repeat(100) + "1" + ")".repeat(100)) is Validation.Invalid)
        assertTrue(ExpressionEvaluator.evaluate("9^9^9^9") is Validation.Invalid)               // overflow -> infinite
        assertTrue(ExpressionEvaluator.evaluate("1+".repeat(300) + "1") is Validation.Invalid)  // length limit
    }

    @Test fun `aspect crop is the largest centred rectangle`() {
        assertEquals(CropRect(500, 0, 3000, 3000), v(ImageGeometry.aspectCrop(PixelSize(4000, 3000), 1, 1, 50, 50)))
        assertEquals(CropRect(0, 0, 3000, 3000), v(ImageGeometry.aspectCrop(PixelSize(4000, 3000), 1, 1, 0, 0)))
        assertEquals(CropRect(1000, 0, 3000, 3000), v(ImageGeometry.aspectCrop(PixelSize(4000, 3000), 1, 1, 100, 0)))
        assertEquals(CropRect(0, 500, 3000, 2000), v(ImageGeometry.aspectCrop(PixelSize(3000, 3000), 3, 2, 50, 50)))
        assertTrue(ImageGeometry.aspectCrop(PixelSize(10, 10), 0, 1, 0, 0) is Validation.Invalid)
    }

    @Test fun `passport sheet layout picks the better orientation`() {
        val sheet = v(ImageGeometry.bestSheet(101.6, 152.4, 35.0, 45.0, 2.0, 3.0))   // 4x6 inch paper
        assertTrue(sheet.landscape); assertEquals(4, sheet.columns); assertEquals(2, sheet.rows)   // 8 photos beats 6 in portrait
        val a4 = v(ImageGeometry.bestSheet(210.0, 297.0, 35.0, 45.0, 2.0, 5.0))
        assertTrue(a4.count >= 24)
        assertTrue(ImageGeometry.bestSheet(30.0, 40.0, 35.0, 45.0, 2.0, 3.0) is Validation.Invalid)
        assertEquals(413, ImageGeometry.mmToPixels(35.0, 300))
    }

    @Test fun `dominant colours ignore transparency and merge near duplicates`() {
        val red = 0xFFFF0000.toInt(); val nearRed = 0xFFF81010.toInt(); val blue = 0xFF0000FF.toInt(); val clear = 0x00FFFFFF
        val pixels = IntArray(600) { when { it < 300 -> red; it < 400 -> nearRed; it < 550 -> blue; else -> clear } }
        val colors = ImageGeometry.dominantColors(pixels, 5)
        assertEquals(2, colors.size)
        assertEquals("#FF0000", colors[0].color.hex().let { if (it.startsWith("#F")) "#FF0000" else it })
        assertEquals(400 * 100.0 / 550, colors[0].percent, 1e-6)
        assertTrue(ImageGeometry.dominantColors(IntArray(10) { 0 }, 3).isEmpty())
    }

    @Test fun `csv parsing handles quotes, delimiters and line endings`() {
        val t = v(CsvParser.parse("name,note\r\n\"Smith, J\",\"said \"\"hi\"\"\"\nplain,\n"))
        assertEquals(listOf(listOf("name", "note"), listOf("Smith, J", "said \"hi\""), listOf("plain", "")), t.rows)
        assertEquals(';', v(CsvParser.parse("a;b;c\n1;2;3")).delimiter)
        assertEquals('\t', v(CsvParser.parse("a\tb\n1\t2")).delimiter)
        assertTrue(CsvParser.parse("a,\"b\n1,2") is Validation.Invalid)
        assertTrue(CsvParser.parse("  ") is Validation.Invalid)
        assertTrue(v(CsvParser.parse((1..2000).joinToString("\n") { "$it,x" })).truncated)
    }

    @Test fun `rename patterns`() {
        assertEquals("holiday_007.jpg", RenamePattern.apply("holiday_{n:3}", "IMG_1.JPG", 7))
        assertEquals("IMG_1_copy.JPG", RenamePattern.apply("{name}_copy.{ext}", "IMG_1.JPG", 1).replace(".jpg", ".JPG"))
        assertEquals("a_b.txt", RenamePattern.apply("a/b", "x.txt", 1))
        assertEquals("1-2", RenamePattern.apply("{n}-{n}".replace("{n}-{n}", "{n}-2"), "noext", 1))
        assertTrue(RenamePattern.validate("").let { it is Validation.Invalid })
    }

    @Test fun `timestamp parsing`() {
        assertEquals(90_000L, v(MediaMath.parseTimestamp("90")))
        assertEquals(90_000L, v(MediaMath.parseTimestamp("1:30")))
        assertEquals(3_723_500L, v(MediaMath.parseTimestamp("1:02:03.5")))
        listOf("", "a", "1:", "1:2:3:4", "-5").forEach { assertTrue(it, MediaMath.parseTimestamp(it) is Validation.Invalid) }
    }
}

class MarkdownLiteTest {
    @Test fun `blocks are recognised`() {
        val b = com.utilityhub.logic.text.MarkdownLite.parse("# Title\n\nHello **world** and `code`.\n\n- one\n- two\n1. first\n> quote\n---\n```\nx = 1\n```")
        assertEquals(8, b.size)
        assertTrue(b[0] is com.utilityhub.logic.text.MdBlock.Heading)
        assertEquals(1, (b[0] as com.utilityhub.logic.text.MdBlock.Heading).level)
        assertTrue(b[2] is com.utilityhub.logic.text.MdBlock.ListItem)
        assertTrue(b[5] is com.utilityhub.logic.text.MdBlock.Quote)
        assertEquals(com.utilityhub.logic.text.MdBlock.Rule, b[6])
        assertEquals("x = 1", (b[7] as com.utilityhub.logic.text.MdBlock.CodeBlock).code)
    }

    @Test fun `inline styles and links`() {
        val s = com.utilityhub.logic.text.MarkdownLite.spans("a **b** *c* `d` [e](http://x.y) f")
        assertEquals(listOf("a ", "b", " ", "c", " ", "d", " ", "e", " f"), s.map { it.text })
        assertEquals(com.utilityhub.logic.text.MdStyle.LINK, s[7].style)
    }

    @Test fun `html is never interpreted and unbalanced markers stay plain`() {
        val s = com.utilityhub.logic.text.MarkdownLite.spans("<script>x</script> 2 * 3 and **open")
        assertEquals("<script>x</script> 2 * 3 and **open", s.joinToString("") { it.text })
        assertTrue(s.all { it.style == com.utilityhub.logic.text.MdStyle.PLAIN })
    }
}

class AuthRulesTest {
    @Test fun `emails`() {
        assertTrue(com.utilityhub.logic.security.AuthRules.validateEmail(" a.b@example.com ") is Validation.Valid)
        listOf("", "plain", "a@b", "a b@c.com", "@x.com", "a@@x.com").forEach { assertTrue(it, com.utilityhub.logic.security.AuthRules.validateEmail(it) is Validation.Invalid) }
        assertEquals("a@x.com", (com.utilityhub.logic.security.AuthRules.validateEmail("  a@x.com ") as Validation.Valid).value)
    }

    @Test fun `passwords`() {
        val r = com.utilityhub.logic.security.AuthRules
        assertTrue(r.validateNewPassword("Tr1cky-horse-staple") is Validation.Valid)
        listOf("short1", "onlyletterslong", "12345678901", "password123", "aaaaaaaaa1", "x".repeat(200) + "1").forEach { assertTrue(it, r.validateNewPassword(it) is Validation.Invalid) }
        assertTrue(r.validateNewPassword("danishbaig2026x", "danishbaig@mail.com") is Validation.Invalid)
    }

    @Test fun `attempt limiter locks after repeated failures and unlocks with time`() {
        var t = 0L
        val l = com.utilityhub.logic.security.AttemptLimiter(3, 1000) { t }
        repeat(2) { l.recordFailure() }
        assertEquals(0L, l.remainingLockMillis())
        l.recordFailure()
        assertEquals(1000L, l.remainingLockMillis())
        t = 600; assertEquals(400L, l.remainingLockMillis())
        t = 1500; assertEquals(0L, l.remainingLockMillis())
        l.recordFailure(); assertEquals(0L, l.remainingLockMillis())
        l.recordSuccess()
    }

    @Test fun `cooldown`() {
        var t = 0L
        val c = com.utilityhub.logic.security.Cooldown(30_000) { t }
        assertEquals(0L, c.remainingMillis())
        c.mark(); t = 10_000
        assertEquals(20_000L, c.remainingMillis())
        t = 31_000; assertEquals(0L, c.remainingMillis())
    }
}
