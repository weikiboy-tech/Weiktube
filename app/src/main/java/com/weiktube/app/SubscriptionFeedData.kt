package com.weiktube.app

import java.time.Duration
import java.time.Instant
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class SubscriptionVideo(
    val id: String,
    val title: String,
    val channelId: String,
    val channelTitle: String,
    val thumbnailUrl: String,
    val publishedAt: String,
    val duration: String,
    val url: String = "https://www.youtube.com/watch?v=$id"
)

/** Parses only returned YouTube resources; unavailable videos are never replaced with sample rows. */
object SubscriptionFeedData {
    fun subscriptionChannels(response: JSONObject): List<YoutubeAccountItem> = response.optJSONArray("items").objects().mapNotNull { item ->
        val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
        val id = snippet.optJSONObject("resourceId")?.optString("channelId").orEmpty()
        if (!validChannelId(id)) return@mapNotNull null
        YoutubeAccountItem(id, snippet.optString("title").take(300), snippet.optString("description").take(500),
            "https://m.youtube.com/channel/$id", thumbnail(snippet), item.optString("id"))
    }.distinctBy { it.id }

    fun uploadPlaylists(response: JSONObject, requested: Set<String>): Map<String, String> = response.optJSONArray("items").objects().mapNotNull { item ->
        val id = item.optString("id")
        val playlist = item.optJSONObject("contentDetails")?.optJSONObject("relatedPlaylists")?.optString("uploads").orEmpty()
        if (id !in requested || !playlist.matches(Regex("[A-Za-z0-9_-]{1,128}"))) null else id to playlist
    }.toMap()

    fun uploadVideoIds(response: JSONObject): List<String> = response.optJSONArray("items").objects().mapNotNull {
        it.optJSONObject("contentDetails")?.optString("videoId")?.takeIf { id -> id.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }.distinct()

    fun videos(response: JSONObject, requested: Set<String>, channelIds: Set<String>): List<SubscriptionVideo> = response.optJSONArray("items").objects().mapNotNull { item ->
        val id = item.optString("id")
        val snippet = item.optJSONObject("snippet") ?: return@mapNotNull null
        val channelId = snippet.optString("channelId")
        val title = snippet.optString("title")
        if (id !in requested || !id.matches(Regex("[A-Za-z0-9_-]{11}")) || channelId !in channelIds || title.isBlank()) return@mapNotNull null
        val live = snippet.optString("liveBroadcastContent")
        val duration = when (live) {
            "live" -> "LIVE"
            "upcoming" -> "Demnächst"
            else -> duration(item.optJSONObject("contentDetails")?.optString("duration").orEmpty())
        }
        SubscriptionVideo(id, title.take(300), channelId, snippet.optString("channelTitle").take(300), thumbnail(snippet),
            snippet.optString("publishedAt"), duration)
    }

    fun merge(previous: List<SubscriptionVideo>, incoming: List<SubscriptionVideo>): List<SubscriptionVideo> =
        (previous + incoming).associateBy { it.id }.values.sortedWith(compareByDescending<SubscriptionVideo> {
            runCatching { Instant.parse(it.publishedAt) }.getOrDefault(Instant.EPOCH)
        }.thenBy { it.id })

    fun duration(iso: String): String = runCatching {
        val seconds = Duration.parse(iso).seconds
        if (seconds <= 0) "" else if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }.getOrDefault("")

    private fun thumbnail(snippet: JSONObject): String {
        val options = snippet.optJSONObject("thumbnails") ?: return ""
        return listOf("high", "medium", "default").firstNotNullOfOrNull { key ->
            options.optJSONObject(key)?.optString("url")?.takeIf { it.startsWith("https://") }
        }.orEmpty()
    }
    private fun validChannelId(id: String) = id.matches(Regex("UC[A-Za-z0-9_-]{22}"))
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
