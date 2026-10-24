package com.utilityhub.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Deliberately minimal: tool, time and result count only. No file names, paths or contents are stored. */
data class HistoryEntry(val toolId: String, val toolTitle: String, val outputCount: Int, val timestampMillis: Long)

class HistoryRepository(context: Context) {
    private val file = File(context.filesDir, "history.json")
    private val mutex = Mutex()
    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries

    suspend fun load() = mutex.withLock {
        _entries.value = withContext(Dispatchers.IO) { read() }
    }

    suspend fun add(entry: HistoryEntry) = mutex.withLock {
        val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        withContext(Dispatchers.IO) { write(updated) }
        _entries.value = updated
    }

    suspend fun clear() = mutex.withLock {
        withContext(Dispatchers.IO) { file.delete() }
        _entries.value = emptyList()
    }

    private fun read(): List<HistoryEntry> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                HistoryEntry(o.getString("id"), o.getString("title"), o.getInt("count"), o.getLong("time"))
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun write(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.toolId).put("title", it.toolTitle).put("count", it.outputCount).put("time", it.timestampMillis)) }
        file.writeText(arr.toString())
    }

    private companion object { const val MAX_ENTRIES = 100 }
}
