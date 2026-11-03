package com.weiktube.app

import java.net.URI

/** Native home replaces only an unambiguous YouTube root URL, never another web destination. */
object HomeRoutePolicy {
    fun isHome(address: String): Boolean = runCatching {
        if (address.length > 8192 || address != address.trim()) return false
        val uri = URI(address)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in setOf("youtube.com", "www.youtube.com", "m.youtube.com") &&
            uri.rawUserInfo == null && uri.port == -1 &&
            uri.rawPath.orEmpty() in setOf("", "/") && uri.rawQuery == null && uri.rawFragment == null
    }.getOrDefault(false)
}
