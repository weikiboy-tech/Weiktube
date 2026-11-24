package com.weiktube.app

import android.app.Activity
import android.os.Looper
import android.view.MotionEvent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AdvancedPlayerGestureTest {
    private class Fixture {
        val replay = mutableListOf<Int>(); val levels = mutableListOf<Float>(); val seeks = mutableListOf<Int>()
        val holds = mutableListOf<Boolean>(); var left = false; var allowed = true
        val gesture = AdvancedPlayerGesture(8f, { replay += it.actionMasked }, { _, _, cb -> cb(allowed) },
            { left = it }, { _, delta -> levels += delta }, { seeks += it }, { holds += it }, {})
        var time = 100L
        fun event(action: Int, x: Float = 100f, y: Float = 100f) {
            val e = MotionEvent.obtain(time, ++time, action, x, y, 0)
            if (!gesture.event(e, 1000, 500)) replay += action
            e.recycle()
        }
        fun tap(x: Float = 100f) { event(0, x); event(1, x) }
        fun advance(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
    }
    @Test fun verticalSideGestureConsumesAndClampsWindowBrightness() {
        val f = Fixture(); f.event(0); f.event(2, y = 0f); f.event(1, y = 0f)
        assertTrue(f.left); assertTrue(f.levels.single() > 0); assertTrue(f.replay.isEmpty())
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        a.window.attributes = a.window.attributes.apply { screenBrightness = -1f }
        val c = PlayerWindowControls(a); c.begin(true); assertEquals(100, c.change(10f))
        assertEquals(2, c.change(-10f)); c.restore(); assertEquals(-1f, a.window.attributes.screenBrightness)
        c.restore(); assertEquals(-1f, a.window.attributes.screenBrightness)
    }
    @Test fun volumeUsesMusicWithinBounds() {
        val a = Robolectric.buildActivity(Activity::class.java).setup().get()
        val c = PlayerWindowControls(a); c.begin(false)
        assertTrue(c.change(100f) in 0..100); assertTrue(c.change(-100f) in 0..100)
        val f = Fixture(); f.event(0, 900f); f.event(2, 900f, 0f); assertFalse(f.left)
    }
    @Test fun disabledSideAndHorizontalMotionReachOriginalChild() {
        val f = Fixture(); f.gesture.options = PlayerPreferences(brightness = false)
        f.event(0); f.event(2, y = 0f); f.event(1, y = 0f)
        assertTrue(f.levels.isEmpty()); assertEquals(listOf(0,2,1), f.replay)
        val h = Fixture(); h.event(0); h.event(2, 250f); h.event(1, 250f)
        assertEquals(listOf(0,2,1),h.replay)
    }
    @Test fun controlsAndSingleTapAreDeliveredExactlyOnce() {
        val f = Fixture(); f.allowed=false; f.tap(); assertEquals(listOf(0,1),f.replay)
        val h = Fixture(); h.tap(); assertTrue(h.replay.isEmpty()); h.advance(400)
        assertEquals(listOf(0,1),h.replay)
    }
    @Test fun doubleAndRepeatedTapsSeekWithoutYoutubeTapEvents() {
        val f = Fixture(); f.gesture.options = PlayerPreferences(seekSeconds=15)
        f.tap(900f); f.tap(900f); f.tap(900f); f.advance(400)
        assertEquals(listOf(15,15),f.seeks); assertTrue(f.replay.isEmpty())
        f.tap(); f.tap(); assertEquals(-15,f.seeks.last())
    }
    @Test fun holdRestoresOnUpCancelAndModeChange() {
        for (end in listOf(1,3)) {
            val f = Fixture(); f.event(0); f.advance(600); f.event(end)
            assertEquals(listOf(true,false),f.holds); assertTrue(f.replay.isEmpty())
        }
        val f = Fixture(); f.event(0); f.advance(600); f.gesture.cancel()
        assertEquals(listOf(true,false),f.holds)
    }
    @Test fun cancelledPinchCandidateCannotSeekOrHoldAndNextGestureWorks() {
        val f = Fixture(); f.event(0); f.gesture.cancel(); f.advance(600)
        assertTrue(f.seeks.isEmpty()); assertTrue(f.holds.isEmpty()); assertTrue(f.replay.isEmpty())
        f.event(0); f.event(2,y=0f); f.event(1,y=0f); assertEquals(1,f.levels.size)
    }
    @Test fun slowOrMissingHitTestsReplayOriginalSequenceRatherThanBlockingControls() {
        val sent=mutableListOf<Int>(); var callback: ((Boolean)->Unit)?=null
        val g=AdvancedPlayerGesture(8f,{sent+=it.actionMasked},{_,_,cb->callback=cb},{},{_,_->},{},{},{})
        fun event(action:Int){val e=MotionEvent.obtain(0,1,action,100f,100f,0);g.event(e,1000,500);e.recycle()}
        event(0);event(1)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(121))
        assertEquals(listOf(0,1),sent)
        callback?.invoke(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1000))
        assertEquals(listOf(0,1),sent)
    }
    @Test fun delayedHitTestDoesNotTurnCompletedSwipeIntoDoubleTap() {
        var callback: ((Boolean)->Unit)?=null;var moves=0;var seeks=0
        val g=AdvancedPlayerGesture(8f,{},{_,_,cb->callback=cb},{},{_,_->moves++},{seeks++},{},{})
        for ((action,y) in listOf(0 to 200f,2 to 100f,1 to 100f)) {
            val e=MotionEvent.obtain(0,1,action,100f,y,0);g.event(e,1000,500);e.recycle()
        }
        callback?.invoke(true)
        assertEquals(1,moves);assertEquals(0,seeks)
    }

}
