package com.weiktube.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("weiktube")
data class HistoryEntry(val url: String, val title: String, val time: Long)
data class Preferences(
    val theme: String = "System", val shield: Boolean = true, val ads: Boolean = true,
    val trackers: Boolean = true, val domains: String = "", val exceptions: String = "",
    val saveHistory: Boolean = true, val history: List<HistoryEntry> = emptyList()
)

/** Shared tolerant codec: malformed individual legacy entries never poison the next write. */
object HistoryCodec {
    const val LIMIT = 100
    fun decode(raw: String?): List<HistoryEntry> {
        if (raw == null || raw.length > 1024 * 1024) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val items = linkedMapOf<String, HistoryEntry>()
            for (index in 0 until array.length().coerceAtMost(1000)) {
                val item = array.optJSONObject(index) ?: continue
                val url = Links.historyUrl(item.optString("url")) ?: continue
                val time = item.optLong("time", 0).coerceAtLeast(0)
                val title = item.optString("title").trim().take(200).ifBlank { "Video" }
                if (url !in items) items[url] = HistoryEntry(url, title, time)
                if (items.size == LIMIT) break
            }
            items.values.toList()
        }.getOrDefault(emptyList())
    }
    fun encode(entries: List<HistoryEntry>): String = JSONArray().also { json ->
        entries.take(LIMIT).forEach { entry ->
            val url = Links.historyUrl(entry.url) ?: return@forEach
            json.put(JSONObject().put("url", url).put("title", entry.title.take(200)).put("time", entry.time.coerceAtLeast(0)))
        }
    }.toString()
    fun add(raw: String?, url: String, title: String, now: Long): String {
        val canonical = Links.historyUrl(url) ?: return encode(decode(raw))
        val previous = decode(raw)
        val fallbackTitle = previous.firstOrNull { it.url == canonical }?.title ?: "Video"
        val item = HistoryEntry(canonical, title.trim().take(200).ifBlank { fallbackTitle }, now.coerceAtLeast(0))
        return encode(listOf(item) + previous.filterNot { it.url == canonical }.take(LIMIT - 1))
    }
}

class WeiktubeModel(application: Application) : AndroidViewModel(application) {
    private val store = application.settingsStore
    val error = MutableStateFlow<String?>(null)
    val preferences = store.data.catch { e ->
        if (e is IOException) { error.value = "Einstellungen konnten nicht geladen werden."; emit(emptyPreferences()) } else throw e
    }.map { p ->
        Preferences(p[stringPreferencesKey("theme")] ?: "System", p[booleanPreferencesKey("shield")] ?: true,
            p[booleanPreferencesKey("ads")] ?: true, p[booleanPreferencesKey("trackers")] ?: true,
            p[stringPreferencesKey("domains")] ?: "", p[stringPreferencesKey("exceptions")] ?: "",
            p[booleanPreferencesKey("saveHistory")] ?: true, HistoryCodec.decode(p[stringPreferencesKey("history")]))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())

    private data class FilterPreferences(val enabled: Boolean, val ads: Boolean, val trackers: Boolean, val domains: String, val exceptions: String)
    // History updates do not reparse filter lists, and large user lists do not block the UI thread.
    val shieldConfig = preferences.map { FilterPreferences(it.shield, it.ads, it.trackers, it.domains, it.exceptions) }
        .distinctUntilChanged().map { settings -> withContext(Dispatchers.Default) {
            ShieldConfig(settings.enabled, settings.ads, settings.trackers,
                runCatching { ShieldEngine.parse(settings.domains) }.getOrDefault(emptySet()),
                runCatching { ShieldEngine.parse(settings.exceptions) }.getOrDefault(emptySet()))
        } }.stateIn(viewModelScope, SharingStarted.Eagerly, ShieldConfig())

    private fun edit(block: (MutablePreferences) -> Unit) { viewModelScope.launch {
        try { store.edit(block) } catch (_: IOException) { error.value = "Speichern fehlgeschlagen. Bitte Speicherplatz prüfen." }
    } }
    fun boolean(key: String, value: Boolean) = edit { it[booleanPreferencesKey(key)] = value }
    fun string(key: String, value: String) = edit { it[stringPreferencesKey(key)] = value }
    fun saveFilters(domains: String, exceptions: String) {
        ShieldEngine.parse(domains)
        ShieldEngine.parse(exceptions)
        edit {
            it[stringPreferencesKey("domains")] = domains
            it[stringPreferencesKey("exceptions")] = exceptions
        }
    }
    fun clearHistory() = string("history", "[]")
    fun record(url: String, title: String) = edit { p ->
        if (p[booleanPreferencesKey("saveHistory")] == false) return@edit
        p[stringPreferencesKey("history")] = HistoryCodec.add(p[stringPreferencesKey("history")], url, title, System.currentTimeMillis())
    }
}
