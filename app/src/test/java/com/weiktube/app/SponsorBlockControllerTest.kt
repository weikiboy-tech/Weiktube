package com.weiktube.app

import android.webkit.ValueCallback
import android.webkit.WebView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class SponsorBlockControllerTest {
    private class Page:WebView(RuntimeEnvironment.getApplication()) {
        val scripts=mutableListOf<String>()
        val replies=mutableListOf<ValueCallback<String>>()
        override fun evaluateJavascript(script:String,callback:ValueCallback<String>?) {
            scripts+=script
            if(callback!=null)replies+=callback
        }
        fun token():String=JSONObject(scripts.last {it.contains(".configure(")}.substringAfterLast(".configure(").removeSuffix(");")).getString("token")
    }
    private val enabled=SponsorBlockPreferences(enabled=true)
    private fun event(token:String,type:String="skip")=JSONObject.quote("""{"token":"$token","markers":"available","events":[{"type":"$type","seq":1,"from":10.0,"to":20.0,"ids":["a"],"category":"sponsor"}]}""")
    private fun known(c:SponsorBlockController) {
        ReflectionHelpers.setField(c,"segments",listOf(SponsorBlockSegment(10.0,20.0,SponsorCategory.SPONSOR,"a",100.0,0,false)))
    }
    @Test fun disabledCreatesNoJavascriptOrRequestAndOptOutStopsActiveDocument(){
        val c=SponsorBlockController(RuntimeEnvironment.getApplication());val p=Page()
        c.sync(p,1,"abcdefghijk",true,SponsorBlockPreferences());assertTrue(p.scripts.isEmpty())
        c.sync(p,1,"abcdefghijk",false,enabled);assertTrue(p.scripts.any {it.contains(".configure(")})
        c.sync(p,1,"abcdefghijk",true,SponsorBlockPreferences());assertTrue(p.scripts.last().contains(".stop()"))
        c.close()
    }
    @Test fun staleCallbackCannotCountForNewVideoOrRecreatedRenderer(){
        val c=SponsorBlockController(RuntimeEnvironment.getApplication());c.statistics.reset();val p=Page()
        c.sync(p,1,"abcdefghijk",false,enabled);val old=p.token();val reply=p.replies.last()
        c.sync(p,2,"zzzzzzzzzzz",false,enabled);known(c)
        reply.onReceiveValue(event(old));assertEquals(0L,c.statistics.totals.count)
        assertNotEquals(old,p.token());c.close()
    }
    @Test fun onlyAcknowledgedKnownSegmentsCountAndUndoRollsBack(){
        val c=SponsorBlockController(RuntimeEnvironment.getApplication());c.statistics.reset();val p=Page()
        c.sync(p,1,"abcdefghijk",false,enabled);known(c)
        assertEquals(0L,c.statistics.totals.count)
        p.replies.last().onReceiveValue(event(p.token()));assertEquals(1L,c.statistics.totals.count)
        c.sync(p,1,"abcdefghijk",false,enabled);p.replies.last().onReceiveValue(event(p.token()));assertEquals(1L,c.statistics.totals.count)
        c.sync(p,1,"abcdefghijk",false,enabled);p.replies.last().onReceiveValue(event(p.token(),"undo"));assertEquals(0L,c.statistics.totals.count)
        c.close()
    }
    @Test fun statisticsResetRejectsInflightSnapshotAndRetireDoesNotTouchDeadWebView(){
        val c=SponsorBlockController(RuntimeEnvironment.getApplication());val p=Page()
        c.sync(p,1,"abcdefghijk",false,enabled);known(c);val token=p.token()
        c.resetStatistics();p.replies.last().onReceiveValue(event(token));assertEquals(0L,c.statistics.totals.count)
        val count=p.scripts.size;c.retire(p);assertEquals(count,p.scripts.size);c.close()
    }
    @Test fun tenModeSynchronizationsKeepSameTokenAndNeverIssuePlaybackCommands(){
        val c=SponsorBlockController(RuntimeEnvironment.getApplication());val p=Page()
        c.sync(p,1,"abcdefghijk",false,enabled);val token=p.token()
        repeat(10){c.gate(false);c.sync(p,1,"abcdefghijk",false,enabled)}
        assertEquals(token,p.token());assertEquals(1,p.scripts.count {it.contains(".configure(")})
        assertFalse(p.scripts.any {it.contains(".play()")||it.contains(".pause()")||it.contains(".load()")})
        c.close()
    }
}

