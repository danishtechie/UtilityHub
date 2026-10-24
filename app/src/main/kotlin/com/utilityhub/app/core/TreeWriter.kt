package com.utilityhub.app.core

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException

/**
 * Copies results into a folder the user picked (Storage Access Framework). New documents only:
 * the provider renames on collision, so nothing is ever overwritten.
 */
object TreeWriter {
    fun copyFile(context: Context, source: File, destination: Uri) {
        val out = context.contentResolver.openOutputStream(destination, "w") ?: throw IOException("Could not open the destination.")
        out.use { o -> source.inputStream().use { it.copyTo(o) } }
    }

    fun copyAll(context: Context, tree: Uri, outputs: List<OutputFile>): Int {
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val dirs = HashMap<String, Uri>()
        var copied = 0
        for (output in outputs) {
            val parts = output.relativePath.split('/').filter { it.isNotEmpty() }
            var parent = root
            var key = ""
            for (dir in parts.dropLast(1)) {
                key = "$key/$dir"
                parent = dirs.getOrPut(key) { findChildDirectory(resolver, tree, parent, dir) ?: createDirectory(resolver, parent, dir) }
            }
            val name = parts.lastOrNull() ?: output.displayName
            val target = DocumentsContract.createDocument(resolver, parent, output.mimeType, name) ?: throw IOException("Could not create a file in the chosen folder.")
            copyFile(context, output.file, target)
            copied++
        }
        return copied
    }

    private fun createDirectory(resolver: ContentResolver, parent: Uri, name: String): Uri =
        DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: throw IOException("Could not create a folder in the chosen location.")

    private fun findChildDirectory(resolver: ContentResolver, tree: Uri, parent: Uri, name: String): Uri? {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE)
        resolver.query(children, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name && c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        }
        return null
    }
}
