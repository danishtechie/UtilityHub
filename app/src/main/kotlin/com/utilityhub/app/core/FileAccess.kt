package com.utilityhub.app.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.utilityhub.logic.files.ContentSniffer
import com.utilityhub.logic.files.DetectedType
import com.utilityhub.logic.files.FileNames
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/** All reads of user-selected files go through here (content URIs only, never raw paths). */
class FileAccess(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    fun describe(uri: Uri): InputFile {
        var name: String? = null
        var size = -1L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (_: Exception) {
            // Some providers refuse queries; fall back to the URI's last segment.
        }
        return InputFile(uri, FileNames.sanitize(name ?: uri.lastPathSegment ?: "file"), size, resolver.getType(uri))
    }

    fun open(uri: Uri): InputStream = resolver.openInputStream(uri) ?: throw IOException("The file could not be opened.")

    fun header(uri: Uri): ByteArray = open(uri).use { s ->
        val buf = ByteArray(ContentSniffer.HEADER_BYTES)
        var n = 0
        while (n < buf.size) {
            val r = s.read(buf, n, buf.size - n)
            if (r < 0) break
            n += r
        }
        buf.copyOf(n)
    }

    fun detect(uri: Uri): DetectedType = ContentSniffer.detect(header(uri))

    fun lastModified(uri: Uri): Long? = try {
        if (!DocumentsContract.isDocumentUri(context, uri)) null else
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
            }
    } catch (_: Exception) {
        null
    }

    /** Copies a selected file into private storage with a hard size limit. Returns bytes copied. */
    fun copyToFile(uri: Uri, target: File, maxBytes: Long, ctx: ToolRunContext? = null): Long {
        var total = 0L
        open(uri).use { input ->
            FileOutputStream(target).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    ctx?.checkCancelled()
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > maxBytes) {
                        target.delete()
                        throw FileTooLargeException(maxBytes)
                    }
                    out.write(buf, 0, n)
                }
            }
        }
        return total
    }
}

/** Turns exceptions into short, path-free, user-facing messages. */
object ErrorMessages {
    fun from(t: Throwable): String = when (t) {
        is OutOfMemoryError -> "Not enough memory to process this file on this device."
        is FileTooLargeException -> "This file is larger than the supported limit."
        is UnsupportedContentException -> t.message ?: "This file type is not supported."
        is java.io.FileNotFoundException -> "The file could not be found or opened. It may have been moved or deleted."
        is SecurityException -> "Access to the file was denied. Please select it again."
        is IOException ->
            if ((t.message ?: "").contains("ENOSPC") || (t.message ?: "").contains("No space left")) "There is not enough free storage."
            else "The file could not be read or written."
        else -> "Something went wrong while processing the file."
    }
}
