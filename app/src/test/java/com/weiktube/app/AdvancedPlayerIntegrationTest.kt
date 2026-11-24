package com.weiktube.app

import android.content.Context
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import com.weiktube.filter.YoutubeAdBlock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** Real gesture wrapper and controller; JS replies are explicitly simulated, not device playback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class AdvancedPlayerIntegrationTest {
    class RecordingWeb(context: Context): WebView(context) {
        val commands=mutableListOf<String>()
        var delayHit=false
        var hitCallback: ValueCallback<String>?=null
        override fun evaluateJavascript(script:String,callback:ValueCallback<String>?) {
            commands+=script
            when {
                script.endsWith("window.weiktubeAdvanced.hit(0.1,0.4)") -> if(delayHit) hitCallback=callback else callback?.onReceiveValue("true")
                script.contains(";window.weiktubeAdvanced.hit(") -> callback?.onReceiveValue("true")
                script.contains(";window.weiktubeAdvanced.seek(") -> callback?.onReceiveValue("10")
                script.contains(";window.weiktubeAdvanced.hold(") -> callback?.onReceiveValue("true")
            }
        }
    }
    private lateinit var player:PlayerController
    private lateinit var web:RecordingWeb
    private lateinit var host:PlayerGestureLayout
    private var oldFilter=true
    @Before fun setup() {
        oldFilter=YoutubeAdBlock.enabled;YoutubeAdBlock.enabled=false
        val a=Robolectric.buildActivity(MainActivity::class.java).get()
        player=PlayerController(a){RecordingWeb(it).also { w->web=w }};a.player=player
        player.open("https://www.youtube.com/watch?v=abcdefghijk")
        shadowOf(web).webViewClient.onPageCommitVisible(web,shadowOf(web).lastLoadedUrl)
        host=PlayerGestureLayout(a);host.attach(web)
        host.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(500,View.MeasureSpec.EXACTLY));host.layout(0,0,1000,500)
        host.configure(false,true,{}, {fail("Fullscreen gesture must not minimize")})
        host.configureZoom(true,true,"a",player::setVideoZoom)
        host.configureAdvanced(true,"a",player,PlayerPreferences())
        web.commands.clear()
    }
    @After fun cleanup(){player.destroy();YoutubeAdBlock.enabled=oldFilter}
    private var time=1000L
    private fun touch(action:Int,vararg points:Pair<Float,Float>) {
        val props=points.indices.map { MotionEvent.PointerProperties().apply {id=it;toolType=MotionEvent.TOOL_TYPE_FINGER} }.toTypedArray()
        val coords=points.map { MotionEvent.PointerCoords().apply{x=it.first;y=it.second;pressure=1f;size=1f} }.toTypedArray()
        val e=MotionEvent.obtain(1000,++time,action,points.size,props,coords,0,0,1f,1f,0,0,0,0);host.dispatchTouchEvent(e);e.recycle()
    }
    @Test fun pinchCancelsHoldAndLeavesNoSingleFingerSeekOrSecondPlayer() {
        val url=shadowOf(web).lastLoadedUrl
        touch(0,100f to 200f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        assertTrue(web.commands.any { it.contains(";window.weiktubeAdvanced.hold(") })
        touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),100f to 200f,700f to 200f)
        assertTrue(web.commands.any { it.contains("window.weiktubeAdvanced.release();") })
        touch(2,50f to 200f,950f to 200f)
        touch(MotionEvent.ACTION_POINTER_UP or (1 shl 8),50f to 200f,950f to 200f)
        touch(1,50f to 200f)
        assertFalse(web.commands.any {it.contains(";window.weiktubeAdvanced.seek(")})
        assertSame(web,player.view);assertSame(web,host.getChildAt(0));assertEquals(1,host.childCount)
        assertEquals(url,shadowOf(web).lastLoadedUrl)
    }
    @Test fun oldDocumentHitResultCannotStartGestureForNewVideo() {
        web.delayHit=true; var delivered=false
        player.gestureHit(.1f,.4f){delivered=true}
        val old=web.hitCallback;assertNotNull(old)
        player.open("https://www.youtube.com/watch?v=zzzzzzzzzzz")
        old!!.onReceiveValue("true");assertFalse(delivered)
        assertSame(web,player.view)
    }
}
