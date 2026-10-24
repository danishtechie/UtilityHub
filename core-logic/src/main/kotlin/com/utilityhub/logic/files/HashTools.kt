package com.utilityhub.logic.files

data class HashMatch(val algorithm: String, val matches: Boolean)

object HashTools {
    /** Accepts pasted checksums such as "AB:CD ef" or "sha256: ABCD..." and returns lowercase hex only. */
    fun normalize(text: String): String =
        text.trim().substringAfter(':', text.trim()).let { if (text.trim().count { it == ':' } > 1) text else it }
            .lowercase().filter { it in '0'..'9' || it in 'a'..'f' }

    fun algorithmFor(hex: String): String? = when (hex.length) {
        32 -> "MD5"
        40 -> "SHA-1"
        64 -> "SHA-256"
        else -> null
    }

    /** @return null when [expected] is blank or not a recognised checksum length. */
    fun compare(expected: String, md5: String, sha1: String, sha256: String): HashMatch? {
        val hex = normalize(expected)
        val algorithm = algorithmFor(hex) ?: return null
        val actual = when (algorithm) { "MD5" -> md5; "SHA-1" -> sha1; else -> sha256 }
        return HashMatch(algorithm, java.security.MessageDigest.isEqual(hex.toByteArray(), actual.lowercase().toByteArray()))
    }
}
