package com.weiktube.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

class WeiktubeModel(application: Application) : AndroidViewModel(application) {
    private val store = application.settingsStore
    val error = MutableStateFlow<String?>(null)
    val preferences = store.data.catch { e ->
        if (e is IOException) { error.value = "Einstellungen konnten nicht geladen werden."; emit(emptyPreferences()) } else throw e
    }.map { p ->
        Preferences(p[stringPreferencesKey("theme")] ?: "System", p[booleanPreferencesKey("shield")] ?: true,
            p[booleanPreferencesKey("ads")] ?: true, p[booleanPreferencesKey("trackers")] ?: true,
            p[stringPreferencesKey("domains")] ?: "", p[stringPreferencesKey("exceptions")] ?: "",
            p[booleanPreferencesKey("saveHistory")] ?: true,
            runCatching {
                val a = JSONArray(p[stringPreferencesKey("history")] ?: "[]")
                (0 until a.length()).map { i -> a.getJSONObject(i).let { HistoryEntry(it.getString("url"), it.getString("title"), it.getLong("time")) } }
            }.getOrDefault(emptyList()))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Preferences())

    private fun edit(block: (MutablePreferences) -> Unit) { viewModelScope.launch {
        try { store.edit(block) } catch (_: IOException) { error.value = "Speichern fehlgeschlagen. Bitte Speicherplatz prüfen." }
    } }
    fun boolean(key: String, value: Boolean) = edit { it[booleanPreferencesKey(key)] = value }
    fun string(key: String, value: String) = edit { it[stringPreferencesKey(key)] = value }
    fun clearHistory() = string("history", "[]")
    fun record(url: String, title: String) = edit { p ->
        if (p[booleanPreferencesKey("saveHistory")] == false) return@edit
        val old = runCatching { JSONArray(p[stringPreferencesKey("history")] ?: "[]") }.getOrDefault(JSONArray())
        val next = JSONArray().put(JSONObject().put("url", url).put("title", title.take(200)).put("time", System.currentTimeMillis()))
        for (i in 0 until old.length()) if (next.length() < 100 && old.getJSONObject(i).optString("url") != url) next.put(old.getJSONObject(i))
        p[stringPreferencesKey("history")] = next.toString()
    }
}
