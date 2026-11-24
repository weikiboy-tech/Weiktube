package com.weiktube.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.CookieHandler
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Read-only RYD client. No WebView/CookieManager/OAuth client is involved. */
internal class ReturnDislikeRepository(
    private val store: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
    private val transport: (String) -> Response = ::request,
) {
    constructor(context: Context) : this(context.getSharedPreferences("ryd_cache", Context.MODE_PRIVATE))
    data class Response(val status: Int, val body: String = "", val retrySeconds: Long = 0)
    private val mutex = Mutex()
    suspend fun get(id: String): Long? = withContext(Dispatchers.IO) { mutex.withLock {
        if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) return@withLock null
        val time = now()
        val expires = store.getLong("until_$id", 0)
        if (expires > time) return@withLock store.getLong("value_$id", -1).takeIf { it >= 0 }
        if (store.getLong("backoff", 0) > time || store.getLong("next_request", 0) > time) return@withLock null
        val day = time / 86_400_000
        val count = if (store.getLong("day", -1) == day) store.getInt("requests", 0) else 0
        if (count >= 9500) return@withLock null
        store.edit().putLong("next_request", time + 1100).putLong("day", day).putInt("requests", count + 1).apply()
        val response = runCatching { transport(id) }.getOrElse { Response(0) }
        val value = if (response.status == 200) decode(response.body, id) else null
        if (response.status == 429) store.edit().putLong("backoff", time + response.retrySeconds.coerceIn(60, 31_536_000) * 1000).apply()
        // Success TTL one hour; errors five minutes. Never invent a zero on failure.
        val edit = store.edit().putLong("until_$id", time + if (value != null) 3_600_000 else 300_000)
            .putLong("value_$id", value ?: -1)
        val entries = store.all.keys.filter { it.startsWith("until_") && it != "until_$id" }.sortedBy { store.getLong(it, 0) }
        entries.take((entries.size - 199).coerceAtLeast(0)).forEach { key -> edit.remove(key).remove("value_${key.removePrefix("until_")}") }
        edit.apply()
        value
    } }
    companion object {
        fun decode(body: String, id: String): Long? = runCatching {
            if (body.length > 65536) return null
            val data = JSONObject(body)
            if (data.optString("id") != id || data.optBoolean("deleted", false)) return null
            val count = data.opt("dislikes") as? Number ?: return null
            val number = count.toDouble()
            if (!number.isFinite() || number < 0 || number > 1e12 || number != count.toLong().toDouble()) return null
            count.toLong()
        }.getOrNull()
        internal fun retrySeconds(header: String?, now: Long): Long {
            header?.toLongOrNull()?.let { return it.coerceIn(60, 31_536_000) }
            val date = runCatching { java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", java.util.Locale.US).parse(header.orEmpty())?.time }.getOrNull()
            return if (date == null) 60 else ((date - now) / 1000 + 1).coerceIn(60, 31_536_000)
        }
        private fun request(id: String): Response {
            // A process-wide cookie handler is not expected here. Fail closed if another component installs one.
            if (CookieHandler.getDefault() != null) return Response(0)
            val connection = URL("https://returnyoutubedislikeapi.com/votes?videoId=$id").openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = 5000; connection.readTimeout = 5000
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("User-Agent", "Weiktube/RYD-readonly")
                val status = connection.responseCode
                val retry = retrySeconds(connection.getHeaderField("Retry-After"), System.currentTimeMillis())
                if (status != 200) return Response(status, retrySeconds = retry)
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val chunk = ByteArray(4096)
                    while (output.size() <= 65536) {
                        val read = input.read(chunk, 0, minOf(chunk.size, 65537 - output.size()))
                        if (read < 0) break
                        output.write(chunk, 0, read)
                    }
                    output.toByteArray()
                }
                if (bytes.size > 65536) return Response(0)
                return Response(status, bytes.toString(Charsets.UTF_8))
            } finally { connection.disconnect() }
        }
    }
}
