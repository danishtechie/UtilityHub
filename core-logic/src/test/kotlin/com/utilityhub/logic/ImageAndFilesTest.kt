package com.utilityhub.logic

import com.utilityhub.logic.files.FileNames
import com.utilityhub.logic.image.ImageMath
import com.utilityhub.logic.image.PixelSize
import org.junit.Assert.*
import org.junit.Test

class ImageAndFilesTest {
    private val photo = PixelSize(4000, 3000)
    private fun ok(v: Validation<PixelSize>) = (v as Validation.Valid).value

    @Test fun `width only keeps aspect ratio`() = assertEquals(PixelSize(1000, 750), ok(ImageMath.resolveTarget(photo, 1000, null, true)))
    @Test fun `height only keeps aspect ratio`() = assertEquals(PixelSize(800, 600), ok(ImageMath.resolveTarget(photo, null, 600, true)))
    @Test fun `both with aspect fits inside the box`() = assertEquals(PixelSize(1000, 750), ok(ImageMath.resolveTarget(photo, 1000, 1000, true)))
    @Test fun `independent dimensions are honoured`() = assertEquals(PixelSize(500, 900), ok(ImageMath.resolveTarget(photo, 500, 900, false)))
    @Test fun `missing dimension falls back to original when not keeping aspect`() = assertEquals(PixelSize(500, 3000), ok(ImageMath.resolveTarget(photo, 500, null, false)))

    @Test fun `invalid sizes are rejected`() {
        assertTrue(ImageMath.resolveTarget(photo, null, null, true) is Validation.Invalid)
        assertTrue(ImageMath.resolveTarget(photo, 0, 10, false) is Validation.Invalid)
        assertTrue(ImageMath.resolveTarget(photo, -5, null, true) is Validation.Invalid)
        assertTrue(ImageMath.resolveTarget(photo, 20_000, null, true) is Validation.Invalid)
        assertTrue(ImageMath.resolveTarget(PixelSize(0, 10), 5, 5, true) is Validation.Invalid)
    }

    @Test fun `memory budget is enforced`() {
        assertTrue(ImageMath.resolveTarget(photo, 10_000, 10_000, false) is Validation.Invalid)
    }

    @Test fun `sample size never undershoots the target`() {
        assertEquals(1, ImageMath.inSampleSize(photo, 4000, 3000))
        assertEquals(2, ImageMath.inSampleSize(photo, 1500, 1500))
        assertEquals(4, ImageMath.inSampleSize(photo, 1000, 750))
        assertEquals(1, ImageMath.inSampleSize(photo, 3000, 3000))
        assertEquals(4, ImageMath.previewSampleSize(photo, 1000))
    }

    @Test fun `reduction percent`() {
        assertEquals(75.0, ImageMath.reductionPercent(1000, 250), 1e-9)
        assertTrue(ImageMath.reductionPercent(1000, 1500) < 0)
        assertEquals(0.0, ImageMath.reductionPercent(0, 10), 1e-9)
    }

    @Test fun `file name helpers`() {
        assertEquals("a_b_c", FileNames.sanitize("a/b:c"))
        assertEquals("file", FileNames.sanitize("..."))
        assertEquals("photo_resized.png", FileNames.outputName("photo.jpg", "resized", "png"))
        assertEquals("jpg", FileNames.extension("IMG.JPG"))
        assertEquals("", FileNames.extension("noext"))
        assertEquals("1.5 KB", FileNames.humanSize(1536))
        assertEquals("12 B", FileNames.humanSize(12))
        assertEquals("2.0 MB", FileNames.humanSize(2L * 1024 * 1024))
    }
}

class ContentSnifferTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun text(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    @Test fun `detects common formats from magic bytes`() {
        assertEquals(com.utilityhub.logic.files.DetectedType.JPEG, com.utilityhub.logic.files.ContentSniffer.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0)))
        assertEquals(com.utilityhub.logic.files.DetectedType.PNG, com.utilityhub.logic.files.ContentSniffer.detect(bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0)))
        assertEquals(com.utilityhub.logic.files.DetectedType.PDF, com.utilityhub.logic.files.ContentSniffer.detect(text("\n\n%PDF-1.7\n")))
        assertEquals(com.utilityhub.logic.files.DetectedType.ZIP, com.utilityhub.logic.files.ContentSniffer.detect(bytes(0x50, 0x4B, 0x03, 0x04, 0)))
        assertEquals(com.utilityhub.logic.files.DetectedType.WEBP, com.utilityhub.logic.files.ContentSniffer.detect(text("RIFF....WEBPVP8 ")))
        assertEquals(com.utilityhub.logic.files.DetectedType.WAV, com.utilityhub.logic.files.ContentSniffer.detect(text("RIFF....WAVEfmt ")))
        assertEquals(com.utilityhub.logic.files.DetectedType.MP4, com.utilityhub.logic.files.ContentSniffer.detect(bytes(0, 0, 0, 0x18) + text("ftypmp42")))
    }

    @Test fun `a renamed or empty file is not trusted`() {
        assertEquals(com.utilityhub.logic.files.DetectedType.UNKNOWN, com.utilityhub.logic.files.ContentSniffer.detect(text("just some text pretending to be a.jpg")))
        assertEquals(com.utilityhub.logic.files.DetectedType.UNKNOWN, com.utilityhub.logic.files.ContentSniffer.detect(ByteArray(0)))
        assertFalse(com.utilityhub.logic.files.ContentSniffer.isImage(com.utilityhub.logic.files.DetectedType.PDF))
        assertTrue(com.utilityhub.logic.files.ContentSniffer.isImage(com.utilityhub.logic.files.DetectedType.WEBP))
    }
}

class HashToolsTest {
    private val md5 = "d41d8cd98f00b204e9800998ecf8427e"
    private val sha1 = "da39a3ee5e6b4b0d3255bfef95601890afd80709"
    private val sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

    @Test fun `identifies algorithm by length and compares case-insensitively`() {
        assertEquals(com.utilityhub.logic.files.HashMatch("SHA-256", true), com.utilityhub.logic.files.HashTools.compare(sha256.uppercase(), md5, sha1, sha256))
        assertEquals(com.utilityhub.logic.files.HashMatch("MD5", true), com.utilityhub.logic.files.HashTools.compare(" $md5 \n", md5, sha1, sha256))
        assertEquals(com.utilityhub.logic.files.HashMatch("SHA-1", false), com.utilityhub.logic.files.HashTools.compare("0".repeat(40), md5, sha1, sha256))
    }

    @Test fun `blank or malformed expectations are not compared`() {
        assertNull(com.utilityhub.logic.files.HashTools.compare("", md5, sha1, sha256))
        assertNull(com.utilityhub.logic.files.HashTools.compare("abc123", md5, sha1, sha256))
    }

    @Test fun `label prefix is tolerated`() {
        assertEquals(true, com.utilityhub.logic.files.HashTools.compare("sha256: $sha256", md5, sha1, sha256)?.matches)
    }
}
