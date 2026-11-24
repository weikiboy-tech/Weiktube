package com.weiktube.app

import android.content.Context
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class SponsorBlockClientTest {
    private val id="dQw4w9WgXcQ"
    private fun store()=RuntimeEnvironment.getApplication().getSharedPreferences("sb_test_${System.nanoTime()}",Context.MODE_PRIVATE)
    private fun item(category:String="sponsor",action:String="skip",start:Double=10.0,end:Double=30.0,uuid:String="a")=JSONObject()
        .put("segment",JSONArray().put(start).put(end)).put("category",category).put("actionType",action).put("UUID",uuid).put("videoDuration",100)
    private fun bucket(vararg values:JSONObject)=JSONArray().put(JSONObject().put("videoID","otherxxxxxx").put("segments",JSONArray().put(item(uuid="wrong"))))
        .put(JSONObject().put("videoID",id).put("segments",JSONArray(values.toList()))).toString()
    @Test fun prefixRequestDoesNotTransmitVideoIdAndOnlyRequestsSkipCategories(){
        assertEquals("5f6b",SponsorBlockCodec.prefix(id))
        val url=SponsorBlockCodec.url(id)
        assertTrue(url.startsWith("https://sponsor.ajay.app/api/skipSegments/5f6b?"))
        assertFalse(url.contains(id));assertTrue(url.contains("actionType=skip"));assertFalse(url.contains("exclusive_access"))
        assertEquals(8,SponsorCategory.entries.size)
    }
    @Test fun exactVideoCategoryActionDurationAndBoundsAreValidated(){
        val decoded=SponsorBlockCodec.response(bucket(item(),item("intro",uuid="b"),item("exclusive_access","full",uuid="c"),
            item("sponsor","mute",uuid="d"),item(start=-1.0,uuid="e"),item(end=101.5,uuid="f"),item(start=40.0,end=20.0,uuid="g")),id)
        assertEquals(listOf("a","b"),decoded.map {it.uuid});assertEquals(100.0,decoded.first().videoDuration,0.0)
        assertEquals(decoded,SponsorBlockCodec.response(SponsorBlockCodec.cache(id,decoded),id))
    }
    @Test fun duplicateUuidAndWrongVideoAreNotAdded(){
        assertEquals(1,SponsorBlockCodec.response(bucket(item(),item()),id).size)
        assertTrue(SponsorBlockCodec.response(bucket(item()),"abcdefghijk").isEmpty())
    }
    @Test fun positiveCacheDeduplicatesConcurrentRequestsAndExpires()=runBlocking{
        var now=1000L;var calls=0;val prefs=store()
        val r=SponsorBlockClient(prefs,{now}){calls++;SponsorBlockClient.Response(200,bucket(item()))}
        val results=(1..10).map {async {r.get(id)}}.awaitAll()
        assertTrue(results.all {it.available&&it.segments.size==1});assertEquals(1,calls)
        val recreated=SponsorBlockClient(prefs,{now}){calls++;SponsorBlockClient.Response(200,bucket(item()))}
        assertEquals(1,recreated.get(id).segments.size);assertEquals(1,calls)
        now+=21_600_001;r.get(id);assertEquals(2,calls)
    }
    @Test fun notFoundIsNegativeResultAndHasShorterTtl()=runBlocking{
        var now=1000L;var calls=0;val r=SponsorBlockClient(store(),{now}){calls++;SponsorBlockClient.Response(404)}
        assertTrue(r.get(id).available);assertTrue(r.get(id).segments.isEmpty());assertEquals(1,calls)
        now+=1_800_001;r.get(id);assertEquals(2,calls)
    }
    @Test fun rateLimitAndServerFailureBackOffAcrossVideoIdsAndCacheClears()=runBlocking{
        var now=1000L;var calls=0;val r=SponsorBlockClient(store(),{now}){calls++;SponsorBlockClient.Response(429,retryAfter="120")}
        assertFalse(r.get(id).available);r.clear();now+=2000
        assertFalse(r.get("abcdefghijk").available);assertEquals(1,calls)
        now+=121000;r.get("abcdefghijk");assertEquals(2,calls)
        var failures=0;val down=SponsorBlockClient(store(),{now}){failures++;SponsorBlockClient.Response(503)}
        assertFalse(down.get(id).available);now+=2000;down.get("abcdefghijk");assertEquals(1,failures)
    }
    @Test fun malformedJsonAndTimeoutNeverAffectPlaybackOrFabricateSegments()=runBlocking{
        val malformed=SponsorBlockClient(store(),{1000}){SponsorBlockClient.Response(200,"invalid")}
        assertFalse(malformed.get(id).available)
        val timeout=SponsorBlockClient(store(),{1000}){throw java.net.SocketTimeoutException()}
        assertFalse(timeout.get(id).available)
        assertFalse(timeout.get("https://wrong").available)
    }
    @Test fun cacheBoundAndRefreshDoNotEvictJustUpdatedEntry()=runBlocking{
        val prefs=store();val edit=prefs.edit()
        edit.putLong("until_$id",1).putString("data_$id","[]")
        repeat(63){edit.putLong("until_${it.toString().padStart(11,'0')}",2).putString("data_${it.toString().padStart(11,'0')}","[]")};edit.commit()
        var calls=0;val r=SponsorBlockClient(prefs,{1000}){calls++;SponsorBlockClient.Response(200,bucket(item()))}
        assertEquals(1,r.get(id).segments.size);assertEquals(1,r.get(id).segments.size);assertEquals(1,calls)
        assertTrue(prefs.all.keys.count {it.startsWith("until_")}<=64)
        r.clear();assertFalse(prefs.all.keys.any {it.startsWith("data_")})
    }
}
