package com.weiktube.app

import android.app.Application
import android.content.Context
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class ReturnDislikeRepositoryTest {
    private val id="abcdefghijk"
    private fun store() = RuntimeEnvironment.getApplication().getSharedPreferences("ryd_test_${System.nanoTime()}",Context.MODE_PRIVATE)
    @Test fun successCachesAcrossRepositoryRecreationAndExpires() = runBlocking {
        var time=1000L; var calls=0; val prefs=store()
        val transport: (String)->ReturnDislikeRepository.Response={ calls++; ReturnDislikeRepository.Response(200,"{\"id\":\"$it\",\"dislikes\":1234}") }
        val r=ReturnDislikeRepository(prefs,{time},transport)
        assertEquals(1234L,r.get(id)); assertEquals(1234L,r.get(id)); assertEquals(1,calls)
        assertEquals(1234L,ReturnDislikeRepository(prefs,{time},transport).get(id)); assertEquals(1,calls)
        time+=3_600_001; assertEquals(1234L,r.get(id)); assertEquals(2,calls)
    }
    @Test fun parallelCallsForSameIdHaveOneTransportRequest() = runBlocking {
        var calls=0
        val r=ReturnDislikeRepository(store(),{1000}) { calls++; ReturnDislikeRepository.Response(200,"{\"id\":\"$it\",\"dislikes\":0}") }
        val results=(1..10).map { async { r.get(id) } }.awaitAll()
        assertTrue(results.all { it==0L }); assertEquals(1,calls)
    }
    @Test fun failuresNeverFabricateZeroAnd429BacksOffAcrossVideos() = runBlocking {
        var time=1000L;var calls=0
        val r=ReturnDislikeRepository(store(),{time}) { calls++; ReturnDislikeRepository.Response(429,retrySeconds=120) }
        assertNull(r.get(id));time+=2000;assertNull(r.get("zzzzzzzzzzz"));assertEquals(1,calls)
        time+=121000;assertNull(r.get("zzzzzzzzzzz"));assertEquals(2,calls)
        assertNull(r.get("../private"));assertEquals(2,calls)
    }
    @Test fun malformedDeletedNegativeAndWrongVideoResponsesAreRejected() {
        for(body in listOf("{}","not json","{\"id\":\"$id\",\"dislikes\":-1}","{\"id\":\"$id\",\"dislikes\":1,\"deleted\":true}","{\"id\":\"wrong\",\"dislikes\":42}")) assertNull(ReturnDislikeRepository.decode(body,id))
    }
    @Test fun networkFailureIsNegativelyCached() = runBlocking {
        var calls=0; val r=ReturnDislikeRepository(store(),{1000}) { calls++;throw java.io.IOException() }
        assertNull(r.get(id));assertNull(r.get(id));assertEquals(1,calls)
    }
    @Test fun retryAfterSupportsDeltaAndHttpDateWithoutImmediateRetry() {
        assertEquals(120L,ReturnDislikeRepository.retrySeconds("120",0))
        assertEquals(60L,ReturnDislikeRepository.retrySeconds("invalid",0))
        assertEquals(121L,ReturnDislikeRepository.retrySeconds("Thu, 01 Jan 1970 00:02:00 GMT",0))
    }

    @Test fun refreshingOldestEntryDoesNotEvictItsNewCachedValue() = runBlocking {
        val prefs=store(); val edit=prefs.edit()
        edit.putLong("until_$id",1).putLong("value_$id",1)
        repeat(199){i->edit.putLong("until_${i.toString().padStart(11,'0')}",2).putLong("value_${i.toString().padStart(11,'0')}",2)}
        edit.commit()
        var calls=0
        val r=ReturnDislikeRepository(prefs,{1000}) {calls++;ReturnDislikeRepository.Response(200,"{\"id\":\"$it\",\"dislikes\":77}")}
        assertEquals(77L,r.get(id));assertEquals(77L,r.get(id));assertEquals(1,calls)
        assertTrue(prefs.all.keys.count {it.startsWith("until_")}<=200)
    }

}
