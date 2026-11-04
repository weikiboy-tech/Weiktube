package com.weiktube.filter

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL

/** Applies equally to the original update URL and every redirect before opening a connection. */
internal object FilterUpdatePolicy {
    private val hosts = setOf("github.com", "raw.githubusercontent.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")

    fun trusted(url: URL): Boolean = url.protocol == "https" && url.userInfo == null &&
        (url.port == -1 || url.port == 443) && url.host.lowercase() in hosts

    fun readLimited(stream: InputStream, limit: Int): ByteArray {
        require(limit > 0) { "FILTER_SIZE" }
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            require(read <= limit - output.size()) { "FILTER_SIZE" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
