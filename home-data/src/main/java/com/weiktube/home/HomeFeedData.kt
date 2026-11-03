package com.weiktube.home

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

data class HomeVideo(
    val id: String,
    val title: String,
    val thumbnailUrl: String,
    val channelId: String,
    val channelTitle: String,
    val durationText: String = "",
    val metadataText: String = ""
)

data class HomePage(val videos: List<HomeVideo>, val continuation: String?)

/**
 * Parses a Home browse response, not a generic recursive search for video IDs.
 * Authentication/profile verification belongs to the caller; parsing a video never proves login.
 * Unknown renderers are omitted. This does not synthesize recommendations or thumbnail addresses.
 */
object HomeFeedData {
    private val videoId = Regex("[A-Za-z0-9_-]{11}")
    private val channelId = Regex("UC[A-Za-z0-9_-]{22}")
    private val clock = Regex("[0-9]{1,3}(?::[0-9]{2}){1,2}")

    fun parse(raw: JSONObject): HomePage {
        val reader = Reader()
        // These are the only initial and continuation response entry points.
        raw.optJSONObject("contents")?.let { reader.visit(it, 0) }
        for (key in listOf("onResponseReceivedActions", "onResponseReceivedEndpoints", "onResponseReceivedCommands")) {
            raw.optJSONArray(key).objects().forEach { action ->
                for (operation in listOf("appendContinuationItemsAction", "reloadContinuationItemsCommand")) {
                    action.optJSONObject(operation)?.optJSONArray("continuationItems")?.let { reader.items(it, 0) }
                }
            }
        }
        raw.optJSONObject("continuationContents")?.let { continuations ->
            for (name in listOf("richGridContinuation", "sectionListContinuation", "itemSectionContinuation", "gridContinuation")) {
                continuations.optJSONObject(name)?.let { container ->
                    reader.items(container.optJSONArray(if (name == "gridContinuation") "items" else "contents"), 0)
                    reader.containerContinuation(container)
                }
            }
        }
        return HomePage(reader.videos.values.toList(), reader.continuation)
    }

    /** Keep the original position and record for overlapping pages; append new IDs in server order. */
    fun merge(existing: List<HomeVideo>, incoming: List<HomeVideo>): List<HomeVideo> =
        (existing + incoming).distinctBy { it.id }

    private class Reader {
        val videos = linkedMapOf<String, HomeVideo>()
        var continuation: String? = null
        private var visited = 0

        fun items(array: JSONArray?, depth: Int) { array.objects().forEach { visit(it, depth + 1) } }

        fun visit(node: JSONObject, depth: Int) {
            if (depth > 16 || ++visited > 10_000 || isAdvertising(node)) return
            for (renderer in listOf("videoRenderer", "videoWithContextRenderer", "gridVideoRenderer")) {
                node.optJSONObject(renderer)?.let { value ->
                    if (videos.size < 500 && !isAdvertising(value)) classicVideo(value)?.let { videos.putIfAbsent(it.id, it) }
                    return
                }
            }
            node.optJSONObject("lockupViewModel")?.let { value ->
                if (videos.size < 500 && !isAdvertising(value)) lockupVideo(value)?.let { videos.putIfAbsent(it.id, it) }
                return
            }
            node.optJSONObject("continuationItemRenderer")?.let { value ->
                token(value.optJSONObject("continuationEndpoint")?.optJSONObject("continuationCommand")?.string("token"))
                return
            }
            for (name in listOf("twoColumnBrowseResultsRenderer", "singleColumnBrowseResultsRenderer")) {
                node.optJSONObject(name)?.let { browse ->
                    val tabs = browse.optJSONArray("tabs").objects().mapNotNull { it.optJSONObject("tabRenderer") }
                    val selected = tabs.firstOrNull { it.optBoolean("selected") }
                        ?: tabs.firstOrNull { it.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.string("browseId") == "FEwhat_to_watch" }
                        ?: tabs.singleOrNull()
                    selected?.optJSONObject("content")?.let { visit(it, depth + 1) }
                    return
                }
            }
            for (name in listOf("richGridRenderer", "sectionListRenderer", "itemSectionRenderer", "richShelfRenderer")) {
                node.optJSONObject(name)?.let { container ->
                    if (!isAdvertising(container)) {
                        items(container.optJSONArray("contents"), depth)
                        containerContinuation(container)
                    }
                    return
                }
            }
            node.optJSONObject("gridRenderer")?.let { container ->
                items(container.optJSONArray("items"), depth)
                containerContinuation(container)
                return
            }
            for (name in listOf("richItemRenderer", "richSectionRenderer")) {
                node.optJSONObject(name)?.let { wrapper ->
                    if (!isAdvertising(wrapper)) wrapper.optJSONObject("content")?.let { visit(it, depth + 1) }
                    return
                }
            }
            // Deliberately no fallback recursion: menus, ads, endpoints and sidebars are not feed rows.
        }

        fun containerContinuation(container: JSONObject) {
            container.optJSONArray("continuations").objects().forEach { wrapper ->
                token(wrapper.optJSONObject("nextContinuationData")?.string("continuation"))
            }
        }

        private fun token(value: String?) {
            if (continuation == null && value != null && value.isNotBlank() && value.length <= 16_384 && value.none { it.isISOControl() }) continuation = value
        }
    }

    private fun classicVideo(node: JSONObject): HomeVideo? {
        val id = node.string("videoId").takeIf { videoId.matches(it) } ?: return null
        val endpointId = node.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")?.string("videoId")
        if (!endpointId.isNullOrEmpty() && endpointId != id) return null
        val title = text(node.optJSONObject("title")).ifBlank { text(node.optJSONObject("headline")) }
        if (title.isBlank()) return null
        val author = listOf("ownerText", "shortBylineText", "longBylineText").mapNotNull(node::optJSONObject).firstOrNull { text(it).isNotBlank() }
        val channel = author?.optJSONArray("runs").objects().firstNotNullOfOrNull { run ->
            run.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.string("browseId")?.takeIf { channelId.matches(it) }
        }.orEmpty()
        val duration = text(node.optJSONObject("lengthText")).ifBlank {
            node.optJSONArray("thumbnailOverlays").objects().firstNotNullOfOrNull {
                it.optJSONObject("thumbnailOverlayTimeStatusRenderer")?.let { overlay -> text(overlay.optJSONObject("text")).takeIf(String::isNotBlank) }
            }.orEmpty()
        }
        val metadata = listOf(text(node.optJSONObject("viewCountText")), text(node.optJSONObject("publishedTimeText")))
            .filter(String::isNotBlank).joinToString(" · ")
        return HomeVideo(id, title, thumbnail(node.optJSONObject("thumbnail")?.optJSONArray("thumbnails")), channel, text(author), duration.take(80), metadata.take(600))
    }

    private fun lockupVideo(node: JSONObject): HomeVideo? {
        if (node.string("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
        val id = node.string("contentId").takeIf { videoId.matches(it) } ?: return null
        val endpoint = node.optJSONObject("rendererContext")?.optJSONObject("commandContext")?.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("watchEndpoint")
        if (endpoint != null && endpoint.string("videoId") != id) return null
        val metadata = node.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel") ?: return null
        val title = text(metadata.optJSONObject("title"))
        if (title.isBlank()) return null
        val parts = metadata.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
            .objects().flatMap { it.optJSONArray("metadataParts").objects() }.mapNotNull { it.optJSONObject("text") }
        var channel = ""
        var author = ""
        val details = mutableListOf<String>()
        parts.forEach { part ->
            val channelRun = part.optJSONArray("commandRuns").objects().firstOrNull {
                it.optJSONObject("onTap")?.optJSONObject("innertubeCommand")?.optJSONObject("browseEndpoint")?.string("browseId")?.matches(channelId) == true
            }
            if (channelRun != null && channel.isEmpty()) {
                channel = channelRun.getJSONObject("onTap").getJSONObject("innertubeCommand").getJSONObject("browseEndpoint").string("browseId")
                // Restrict the author to the text range actually linked to the channel.
                val content = part.string("content")
                val start = channelRun.optInt("startIndex", 0)
                val length = channelRun.optInt("length", content.length)
                if (start >= 0 && length >= 0 && start <= content.length && length <= content.length - start) author = content.substring(start, start + length).take(300)
            } else text(part).takeIf(String::isNotBlank)?.let(details::add)
        }
        val image = node.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")
        val duration = image?.optJSONArray("overlays").objects().flatMap { overlay ->
            listOf("thumbnailOverlayBadgeViewModel", "thumbnailBottomOverlayViewModel").flatMap { key ->
                val value = overlay.optJSONObject(key)
                (value?.optJSONArray("thumbnailBadges") ?: value?.optJSONArray("badges")).objects()
            }
        }.firstNotNullOfOrNull { it.optJSONObject("thumbnailBadgeViewModel")?.string("text")?.takeIf(clock::matches) }.orEmpty()
        return HomeVideo(id, title, thumbnail(image?.optJSONObject("image")?.optJSONArray("sources")), channel, author, duration, details.joinToString(" · ").take(600))
    }

    private fun isAdvertising(node: JSONObject): Boolean = node.optBoolean("isAd") ||
        node.keys().asSequence().any { it in setOf("adSlotRenderer", "adPlacementRenderer", "displayAdRenderer", "promotedVideoRenderer", "promotedSparklesWebRenderer", "promotedSparklesTextSearchRenderer", "adServiceEndpoint", "adTrackingParams", "adInfo", "adBadge") } ||
        node.optJSONArray("badges").objects().any { it.optJSONObject("metadataBadgeRenderer")?.string("style") == "BADGE_STYLE_TYPE_AD" }

    private fun thumbnail(options: JSONArray?): String = options.objects().filter { safeThumbnail(it.string("url")) }
        .maxByOrNull { it.optLong("width", 0).coerceIn(0, 16_384) }?.string("url").orEmpty()

    private fun safeThumbnail(url: String): Boolean = runCatching {
        if (url.length > 4096 || url != url.trim()) return false
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return false
        uri.scheme == "https" && uri.rawUserInfo == null && uri.port == -1 && uri.rawFragment == null &&
            listOf("ytimg.com", "googleusercontent.com").any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)

    private fun text(value: JSONObject?): String {
        if (value == null) return ""
        return value.string("simpleText").ifBlank { value.string("content") }.ifBlank {
            value.optJSONArray("runs").objects().joinToString("") { it.string("text") }
        }.take(600)
    }

    private fun JSONObject.string(key: String): String = (opt(key) as? String).orEmpty()
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until minOf(length(), 2000)).mapNotNull(::optJSONObject)
}
