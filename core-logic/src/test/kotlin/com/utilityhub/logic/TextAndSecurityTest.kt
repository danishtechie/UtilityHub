package com.utilityhub.logic

import com.utilityhub.logic.security.*
import com.utilityhub.logic.text.*
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.Random

class TextAndSecurityTest {
    private fun <T> v(x: Validation<T>): T = (x as Validation.Valid).value

    @Test fun `password honours length and every selected character class`() {
        repeat(200) {
            val p = v(PasswordGenerator.generate(PasswordOptions(length = 12), SecureRandom()))
            assertEquals(12, p.length)
            assertTrue(p.any { it.isLowerCase() }); assertTrue(p.any { it.isUpperCase() })
            assertTrue(p.any { it.isDigit() }); assertTrue(p.any { !it.isLetterOrDigit() })
        }
    }

    @Test fun `password option validation and ambiguous exclusion`() {
        assertTrue(PasswordGenerator.generate(PasswordOptions(length = 4), Random(1)) is Validation.Invalid)
        assertTrue(PasswordGenerator.generate(PasswordOptions(length = 500), Random(1)) is Validation.Invalid)
        assertTrue(PasswordGenerator.generate(PasswordOptions(lower = false, upper = false, digits = false, symbols = false), Random(1)) is Validation.Invalid)
        repeat(100) {
            val p = v(PasswordGenerator.generate(PasswordOptions(length = 64, excludeAmbiguous = true), Random(it.toLong())))
            assertTrue(p.none { it in "0O1lI|" })
        }
        val digitsOnly = v(PasswordGenerator.generate(PasswordOptions(length = 20, lower = false, upper = false, symbols = false), Random(3)))
        assertTrue(digitsOnly.all { it.isDigit() })
    }

    @Test fun `random string`() {
        assertEquals(30, v(PasswordGenerator.randomString(30, "abc", SecureRandom())).length)
        assertTrue(PasswordGenerator.randomString(0, "abc", Random()) is Validation.Invalid)
        assertTrue(PasswordGenerator.randomString(5, "", Random()) is Validation.Invalid)
    }

    @Test fun `json validator accepts valid documents`() {
        listOf("{}", "[]", "123", "-0.5e+10", "\"x\"", "null", "true", "{\"a\":[1,2,{\"b\":null}],\"c\":\"\\u00e9\\n\"}", "  [1 , 2]  ").forEach {
            assertTrue("should accept: $it", JsonTools.validate(it) is Validation.Valid)
        }
    }

    @Test fun `json validator rejects invalid documents`() {
        listOf("", "{", "[1,]", "{\"a\":1,}", "{a:1}", "[01]", "1.", "'x'", "{\"a\" 1}", "[1] x", "\"a\nb\"", "tru", "\"\\x\"", "[1 2]", "+1").forEach {
            assertTrue("should reject: $it", JsonTools.validate(it) is Validation.Invalid)
        }
    }

    @Test fun `json error message carries a position`() {
        val msg = (JsonTools.validate("{\n  \"a\": ,\n}") as Validation.Invalid).message
        assertTrue(msg, msg.contains("line 2"))
    }

    @Test fun `json format and minify`() {
        assertEquals("{\n  \"a\": [\n    1,\n    2\n  ],\n  \"b\": {}\n}", v(JsonTools.format("{\"a\":[1,2],\"b\":{}}")))
        assertEquals("{\"a\":[1,2],\"b\":\"x y\"}", v(JsonTools.minify("{ \"a\" : [ 1 , 2 ] , \"b\" : \"x y\" }")))
        assertEquals("\"a\\\"b\\n\"", v(JsonTools.minify("\"a\\\"b\\n\"")))
    }

    @Test fun `json depth limit stops stack exhaustion`() {
        assertTrue(JsonTools.validate("[".repeat(5000) + "]".repeat(5000)) is Validation.Invalid)
    }

    @Test fun `text stats`() {
        assertEquals(TextStats(0, 0, 0, 0), TextTools.stats(""))
        val s = TextTools.stats("Hello  world\nsecond line")
        assertEquals(4, s.words); assertEquals(2, s.lines); assertEquals(20, s.charactersNoSpaces)
    }

    @Test fun `case conversion`() {
        assertEquals("HELLO WORLD", TextTools.convertCase("Hello World", CaseMode.UPPER))
        assertEquals("hello world", TextTools.convertCase("Hello World", CaseMode.LOWER))
        assertEquals("Hello Big World", TextTools.convertCase("hELLO bIG world", CaseMode.TITLE))
        assertEquals("Hello there. How are you? Fine!", TextTools.convertCase("HELLO THERE. HOW ARE YOU? FINE!", CaseMode.SENTENCE))
    }

    @Test fun `dedupe and sort lines`() {
        val r = TextTools.dedupeLines("a\nb\nA\na \nc", ignoreCase = true, trimWhitespace = true)
        assertEquals("a\nb\nc", r.text); assertEquals(2, r.removed)
        assertEquals("a\nA\nb", TextTools.dedupeLines("a\nA\nb\na", false, false).text)
        assertEquals("a\nB\nc", TextTools.sortLines("c\nB\na", false, true, false))
        assertEquals("c\nB\na", TextTools.sortLines("c\nB\na", true, true, false))
        assertEquals("2\n10\nabc", TextTools.sortLines("10\nabc\n2", false, false, true))
    }

    @Test fun `base64 and url codecs`() {
        assertEquals("SGVsbG8g8J+Zgg==", v(EncodingTools.base64Encode("Hello 🙂")))
        assertEquals("Hello 🙂", v(EncodingTools.base64Decode("SGVsbG8g8J+Zgg==")))
        assertEquals("Hello 🙂", v(EncodingTools.base64Decode("SGVsbG8g\n8J+Zgg==")))
        assertTrue(EncodingTools.base64Decode("!!!") is Validation.Invalid)
        assertTrue(EncodingTools.base64Decode("/w==") is Validation.Invalid)         // 0xFF is not UTF-8
        assertEquals("a%20b%26c%3Dd", v(EncodingTools.urlEncode("a b&c=d")))
        assertEquals("a b&c=d", v(EncodingTools.urlDecode("a%20b%26c%3Dd")))
        assertTrue(EncodingTools.urlDecode("%zz") is Validation.Invalid)
        assertTrue(EncodingTools.base64Encode("x".repeat(EncodingTools.MAX_CHARS + 1)) is Validation.Invalid)
    }

    @Test fun `regex tester finds matches and groups`() {
        val m = v(SafeRegex.findAll("(\\d+)-(\\w+)", "12-ab and 7-z"))
        assertEquals(2, m.size); assertEquals("12-ab", m[0].text); assertEquals(listOf("7", "z"), m[1].groups)
        assertEquals(1, v(SafeRegex.findAll("^b", "a\nb", multiline = true)).size)
        assertEquals(1, v(SafeRegex.findAll("ABC", "xabcx", ignoreCase = true)).size)
    }

    @Test fun `regex tester rejects bad input and stops catastrophic patterns`() {
        assertTrue(SafeRegex.findAll("(", "x") is Validation.Invalid)
        assertTrue(SafeRegex.findAll("", "x") is Validation.Invalid)
        assertTrue(SafeRegex.findAll("a".repeat(600), "x") is Validation.Invalid)
        val started = System.nanoTime()
        val r = SafeRegex.findAll("(a+)+$", "a".repeat(5000) + "b", timeoutMs = 300)
        assertTrue(r is Validation.Invalid)
        assertTrue("must stop quickly", (System.nanoTime() - started) / 1_000_000 < 5_000)
    }
}
