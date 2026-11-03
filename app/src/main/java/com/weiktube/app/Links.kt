package com.weiktube.app

import java.net.URI
import java.net.URLDecoder

/** Public URL handling only: never resolves or extracts a protected media URL. */
object Links {
    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
    private val idPattern = Regex("[A-Za-z0-9_-]{11}")
    fun host(url: String): String = runCatching { URI(url).host?.lowercase()?.trimEnd('.') ?: "" }.getOrDefault("")
    private fun uri(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()?.takeIf {
        url.length <= 8192 && it.scheme?.lowercase() in setOf("https", "http") && !it.host.isNullOrBlank() && it.rawUserInfo == null
    }
    fun youtube(url: String): Boolean {
        val u = uri(url) ?: return false
        return host(url.trim()) in youtubeHosts && u.port in setOf(-1, 80, 443)
    }
    private fun parameters(query: String?): Map<String, String> = query.orEmpty().split('&').mapNotNull {
        val key = decode(it.substringBefore('='))
        if (key.isBlank()) null else key to decode(it.substringAfter('=', ""))
    }.asReversed().toMap()

    fun isShorts(url: String): Boolean = youtube(url) && runCatching { URI(url.trim()).path.startsWith("/shorts") }.getOrDefault(false)

    fun videoId(url: String): String? {
        if (!youtube(url)) return null
        val u = uri(url) ?: return null
        val path = u.path.orEmpty().split('/').filter(String::isNotEmpty)
        val id = when {
            host(url.trim()) == "youtu.be" && path.size == 1 -> path[0]
            path.size == 2 && path[0] in setOf("shorts", "embed", "live") -> path[1]
            u.path == "/watch" -> parameters(u.rawQuery)["v"]
            else -> null
        }
        return id?.takeIf(idPattern::matches)
    }

    fun startSeconds(url: String): Int {
        val u = uri(url) ?: return 0
        val query = parameters(u.rawQuery)
        val fragment = parameters(u.rawFragment)
        val value = query["t"] ?: query["start"] ?: fragment["t"] ?: return 0
        if (value.isNotEmpty() && value.all(Char::isDigit)) return boundedNumber(value)
        val parts = Regex("(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?").matchEntire(value) ?: return 0
        if (value.isEmpty()) return 0
        val hours = boundedNumber(parts.groupValues[1]).toLong()
        val minutes = boundedNumber(parts.groupValues[2]).toLong()
        val seconds = boundedNumber(parts.groupValues[3]).toLong()
        return (hours * 3600L + minutes * 60L + seconds).coerceAtMost(86400L).toInt()
    }
    private fun boundedNumber(value: String): Int = if (value.isEmpty()) 0 else (value.toLongOrNull() ?: 86400L).coerceIn(0L, 86400L).toInt()

    /** Upgrade public YouTube HTTP links and retain relevant public playback context. */
    fun canonicalYoutubeUrl(url: String): String? {
        if (!youtube(url)) return null
        val u = uri(url) ?: return null
        val id = videoId(url)
        if (id != null) {
            val query = parameters(u.rawQuery)
            return buildString {
                append("https://m.youtube.com/watch?v=").append(id)
                val seconds = startSeconds(url)
                if (seconds > 0) append("&t=").append(seconds)
                query["list"]?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,128}")) }?.let { append("&list=").append(it) }
                query["index"]?.toIntOrNull()?.takeIf { it >= 0 }?.let { append("&index=").append(it) }
            }
        }
        if (host(url.trim()) == "youtu.be") return null
        return "https://m.youtube.com" + u.rawPath.orEmpty().ifEmpty { "/" } + (u.rawQuery?.let { "?$it" } ?: "")
    }
    /** Feed navigation sends watch/live/embed links to the watch surface; Shorts stay in the feed. */
    fun browseWatchUrl(url: String): String? = if (isShorts(url) || videoId(url) == null) null else canonicalYoutubeUrl(url)
    fun https(url: String): Boolean = uri(url)?.scheme?.equals("https", ignoreCase = true) == true
    fun directVideoAllowed(url: String): Boolean = https(url) && !ShieldEngine.matches(host(url),
        setOf("youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com"))
    fun historyUrl(url: String): String? = videoId(url)?.let { "https://m.youtube.com/watch?v=$it" }
        ?: url.trim().takeIf(::directVideoAllowed)
    fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
