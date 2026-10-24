package com.utilityhub.app.core

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** App-private cache for generated results. Cleared by the user, and automatically when stale. */
class OutputStore(private val context: Context) {
    private val root = File(context.cacheDir, "outputs")

    fun newSession(): File = File(root, UUID.randomUUID().toString()).also { it.mkdirs() }

    fun hasFreeSpace(): Boolean = context.cacheDir.usableSpace > Limits.MIN_FREE_CACHE_BYTES

    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun deleteSession(dir: File?) {
        if (dir != null && dir.canonicalPath.startsWith(root.canonicalPath + File.separator)) dir.deleteRecursively()
    }

    fun sizeBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun clearAll(): Long {
        val freed = sizeBytes()
        root.deleteRecursively()
        return freed
    }

    fun purgeStale(now: Long = System.currentTimeMillis()) {
        root.listFiles()?.forEach { if (now - it.lastModified() > Limits.TEMP_MAX_AGE_MS) it.deleteRecursively() }
    }
}
