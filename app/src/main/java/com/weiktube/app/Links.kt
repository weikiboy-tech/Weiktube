package com.weiktube.app

import java.net.URI
import java.net.URLDecoder

object Links {
    fun host(url: String): String = runCatching { URI(url).host?.lowercase()?.trimEnd('.') ?: "" }.getOrDefault("")
    fun youtube(url: String): Boolean = host(url) in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")
    fun videoId(url: String): String? {
        if (!youtube(url)) return null
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme !in listOf("https", "http")) return null
        val path = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        val id = when {
            host(url) == "youtu.be" -> path.firstOrNull()
            path.firstOrNull() in listOf("shorts", "embed", "live") -> path.getOrNull(1)
            uri.path == "/watch" -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter("v=")
            else -> null
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }
    fun startSeconds(url: String): Int {
        val query = runCatching { URI(url).rawQuery }.getOrNull().orEmpty()
        val value = query.split('&').firstOrNull { it.startsWith("t=") || it.startsWith("start=") }?.substringAfter('=') ?: return 0
        if (value.all(Char::isDigit)) return value.toIntOrNull()?.coerceIn(0, 86400) ?: 0
        return Regex("(\\d+)([hms])").findAll(value).sumOf {
            (it.groupValues[1].toIntOrNull() ?: 0).coerceAtMost(86400) * when (it.groupValues[2]) { "h" -> 3600; "m" -> 60; else -> 1 }
        }.coerceIn(0, 86400)
    }
    fun https(url: String): Boolean = runCatching { val u = URI(url); u.scheme == "https" && !u.host.isNullOrEmpty() && u.userInfo == null }.getOrDefault(false)
    fun directVideoAllowed(url: String): Boolean = https(url) && !ShieldEngine.matches(host(url),
        setOf("youtube.com", "youtu.be", "youtube-nocookie.com", "googlevideo.com"))
    fun decode(value: String): String = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)
}
