package com.weiktube.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.CookieHandler
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal class SponsorBlockClient(
    private val store: SharedPreferences,
    private val now: () -> Long = System::currentTimeMillis,
    private val transport: (String) -> Response = ::request,
) {
    constructor(context: Context): this(context.getSharedPreferences("sponsor_block_cache", Context.MODE_PRIVATE))
    data class Response(val code: Int, val body: String = "", val retryAfter: String? = null)
    data class Result(val segments: List<SponsorBlockSegment>, val available: Boolean)
    private val mutex = Mutex()
    suspend fun clear() = withContext(Dispatchers.IO) { mutex.withLock {
        // Keep rate-limit metadata; clearing the cache must not bypass server backoff.
        val edit=store.edit();store.all.keys.filter {it.startsWith("data_")||it.startsWith("until_")||it.startsWith("ok_")}.forEach(edit::remove);edit.apply()
    } }
    suspend fun get(id: String): Result = withContext(Dispatchers.IO) { mutex.withLock {
        if (!SponsorBlockCodec.validId(id)) return@withLock Result(emptyList(), false)
        val time=now()
        if (store.getLong("until_$id",0)>time) {
            val cached=runCatching {SponsorBlockCodec.response(store.getString("data_$id","[]")!!,id)}.getOrNull()
            if(cached!=null)return@withLock Result(cached,store.getBoolean("ok_$id",false))
        }
        if(store.getLong("backoff",0)>time||store.getLong("next",0)>time)return@withLock Result(emptyList(),false)
        currentCoroutineContext().ensureActive()
        store.edit().putLong("next",time+1100).apply()
        val response=runCatching {transport(SponsorBlockCodec.url(id))}.getOrElse {Response(0)}
        currentCoroutineContext().ensureActive()
        val segments=when(response.code){200->runCatching {SponsorBlockCodec.response(response.body,id)}.getOrNull();404->emptyList();else->null}
        val available=segments!=null
        val edit=store.edit()
        if(response.code==429||response.code>=500||response.code==0){
            val failures=(store.getInt("failures",0)+1).coerceAtMost(6)
            val delay=if(response.code==429)ReturnDislikeRepository.retrySeconds(response.retryAfter,time) else (60L shl (failures-1)).coerceAtMost(3600)
            edit.putLong("backoff",time+delay*1000).putInt("failures",failures)
        }else if(available)edit.putInt("failures",0)
        val value=segments.orEmpty()
        val ttl=when { !available->300_000L;value.isEmpty()->1_800_000L;else->21_600_000L }
        edit.putLong("until_$id",time+ttl).putBoolean("ok_$id",available).putString("data_$id",SponsorBlockCodec.cache(id,value))
        val others=store.all.keys.filter {it.startsWith("until_")&&it!="until_$id"}.sortedBy {store.getLong(it,0)}
        others.take((others.size-63).coerceAtLeast(0)).forEach {key->val old=key.removePrefix("until_");edit.remove(key).remove("data_$old").remove("ok_$old")}
        edit.apply();Result(value,available)
    } }
    companion object {
        private fun request(url: String): Response {
            // Do not share a browser/OAuth cookie jar or follow server redirects.
            if(CookieHandler.getDefault()!=null)return Response(0)
            val connection=URL(url).openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects=false;connection.useCaches=false
                connection.connectTimeout=5000;connection.readTimeout=5000
                connection.setRequestProperty("Accept","application/json")
                connection.setRequestProperty("User-Agent","Weiktube/SponsorBlock-readonly")
                val status=connection.responseCode
                if(status!=200)return Response(status,retryAfter=connection.getHeaderField("Retry-After"))
                val bytes=connection.inputStream.use {input->
                    val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                    while(out.size()<=2_097_152){val n=input.read(buffer,0,minOf(buffer.size,2_097_153-out.size()));if(n<0)break;out.write(buffer,0,n)}
                    out.toByteArray()
                }
                if(bytes.size>2_097_152)return Response(0)
                return Response(200,bytes.toString(Charsets.UTF_8))
            }finally{connection.disconnect()}
        }
    }
}
