package com.utilityhub.logic.text

import com.utilityhub.logic.Validation
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

object EncodingTools {
    const val MAX_CHARS = 1_000_000

    private fun tooLong(text: String) = if (text.length > MAX_CHARS) "Input is larger than $MAX_CHARS characters." else null

    fun base64Encode(text: String, urlSafe: Boolean = false): Validation<String> {
        tooLong(text)?.let { return Validation.Invalid(it) }
        val encoder = if (urlSafe) Base64.getUrlEncoder() else Base64.getEncoder()
        return Validation.Valid(encoder.encodeToString(text.toByteArray(Charsets.UTF_8)))
    }

    fun base64Decode(text: String): Validation<String> {
        tooLong(text)?.let { return Validation.Invalid(it) }
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return Validation.Invalid("Enter Base64 text to decode.")
        val decoder = if (compact.contains('-') || compact.contains('_')) Base64.getUrlDecoder() else Base64.getDecoder()
        val bytes = try { decoder.decode(compact) } catch (e: IllegalArgumentException) {
            return Validation.Invalid("This is not valid Base64.")
        }
        return try {
            val decodedText = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
            Validation.Valid(decodedText.toString())
        } catch (e: java.nio.charset.CharacterCodingException) {
            Validation.Invalid("The decoded data is binary, not UTF-8 text.")
        }
    }

    fun urlEncode(text: String): Validation<String> {
        tooLong(text)?.let { return Validation.Invalid(it) }
        return Validation.Valid(URLEncoder.encode(text, "UTF-8").replace("+", "%20"))
    }

    fun urlDecode(text: String): Validation<String> {
        tooLong(text)?.let { return Validation.Invalid(it) }
        return try { Validation.Valid(URLDecoder.decode(text, "UTF-8")) } catch (e: IllegalArgumentException) {
            Validation.Invalid("This is not valid percent-encoded text.")
        }
    }
}
