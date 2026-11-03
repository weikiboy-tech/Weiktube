package com.weiktube.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

object PublicImageUrlPolicy {
    fun allowed(address: String): Boolean = runCatching {
        val uri = URI(address)
        val host = uri.host?.lowercase().orEmpty().trimEnd('.')
        address.length <= 4096 && uri.scheme == "https" && uri.userInfo == null && uri.fragment == null &&
            uri.port in setOf(-1, 443) && listOf("ytimg.com", "ggpht.com", "googleusercontent.com").any { host == it || host.endsWith(".$it") }
    }.getOrDefault(false)
}

/** Shared public-thumbnail loader: no bearer headers, cookies, disk files or synthetic images. */
private object RemoteImages {
    private const val MAX_BYTES = 2 * 1024 * 1024
    private val permits = Semaphore(3)
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    fun cached(url: String): Bitmap? = cache.get(url)

    suspend fun load(url: String): Bitmap? {
        if (!PublicImageUrlPolicy.allowed(url)) return null
        cache.get(url)?.let { return it }
        return permits.withPermit {
            withContext(Dispatchers.IO) {
                cache.get(url)?.let { return@withContext it }
                try {
                    val bytes = download(url)
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192 && bounds.outWidth.toLong() * bounds.outHeight <= 25000000L)
                    var sample = 1
                    while (bounds.outWidth / sample > 768 || bounds.outHeight / sample > 768) sample *= 2
                    val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return@withContext null
                    currentCoroutineContext().ensureActive()
                    cache.put(url, bitmap)
                    bitmap
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }
        }
    }

    private suspend fun download(url: String): ByteArray {
        var target = url
        val started = android.os.SystemClock.elapsedRealtime()
        repeat(4) { redirects ->
            require(PublicImageUrlPolicy.allowed(target)) { "Unsupported public image host" }
            currentCoroutineContext().ensureActive()
            require(android.os.SystemClock.elapsedRealtime() - started < 30000)
            val connection = URL(target).openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 8000; connection.readTimeout = 8000
                connection.setRequestProperty("Accept", "image/*")
                connection.setRequestProperty("Accept-Encoding", "identity")
                // This independent connection never receives Google access tokens or WebView cookies.
                val response = connection.responseCode
                if (response in setOf(301, 302, 303, 307, 308)) {
                    require(redirects < 3)
                    target = URL(URL(target), connection.getHeaderField("Location") ?: error("Missing redirect")).toString()
                } else {
                    require(response == 200)
                    require(connection.contentType?.substringBefore(';')?.lowercase()?.startsWith("image/") == true)
                    val length = connection.contentLengthLong
                    require(length <= MAX_BYTES)
                    val bytes = java.io.ByteArrayOutputStream()
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            require(android.os.SystemClock.elapsedRealtime() - started < 30000)
                            val count = input.read(buffer); if (count < 0) break
                            require(bytes.size() + count <= MAX_BYTES)
                            bytes.write(buffer, 0, count)
                        }
                    }
                    require(bytes.size() > 0 && (length < 0 || bytes.size().toLong() == length))
                    return bytes.toByteArray()
                }
            } finally { connection.disconnect() }
        }
        error("Too many image redirects")
    }
}

@Composable
fun RemoteThumbnail(
    url: String,
    description: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
    contentScale: ContentScale = ContentScale.Crop,
    fallback: ImageVector = Icons.Rounded.Image
) {
    val bitmap by produceState<Bitmap?>(RemoteImages.cached(url), url) {
        value = RemoteImages.cached(url)
        if (value == null) value = RemoteImages.load(url)
    }
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), description, Modifier.fillMaxSize(), contentScale = contentScale)
        else Icon(fallback, description, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
