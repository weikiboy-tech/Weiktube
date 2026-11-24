package com.weiktube.app

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PlayerVideoZoomGestureTest {
    private class Fixture {
        val host=PlayerGestureLayout(RuntimeEnvironment.getApplication())
        val child=View(host.context)
        val actions=mutableListOf<Int>()
        val scales=mutableListOf<Float>()
        var minimized=0
        var time=1000L
        init {
            child.setOnTouchListener { _, e -> actions.add(e.actionMasked); true }
            host.addView(child,FrameLayout.LayoutParams(-1,-1))
            host.measure(View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.EXACTLY))
            host.layout(0,0,1000,1000)
            host.configure(true,true,{}, { minimized++ })
            configure(true,"a")
            scales.clear()
        }
        fun configure(enabled:Boolean,key:String)=host.configureZoom(enabled,false,key){s,_,_->scales.add(s)}
        fun event(action:Int,vararg points:Pair<Float,Float>) {
            time+=40
            val properties=points.indices.map { MotionEvent.PointerProperties().apply { id=it;toolType=MotionEvent.TOOL_TYPE_FINGER } }.toTypedArray()
            val coords=points.map { MotionEvent.PointerCoords().apply { x=it.first;y=it.second;pressure=1f;size=1f } }.toTypedArray()
            val e=MotionEvent.obtain(1000L,time,action,points.size,properties,coords,0,0,1f,1f,0,0,0,0)
            host.dispatchTouchEvent(e);e.recycle()
        }
        fun pinch() {
            event(MotionEvent.ACTION_DOWN,300f to 200f)
            event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),300f to 200f,700f to 200f)
            event(MotionEvent.ACTION_MOVE,250f to 200f,750f to 200f)
            event(MotionEvent.ACTION_MOVE,150f to 200f,850f to 200f)
            event(MotionEvent.ACTION_MOVE,50f to 200f,950f to 200f)
        }
        fun end() {
            event(MotionEvent.ACTION_POINTER_UP or (1 shl 8),50f to 200f,950f to 200f)
            event(MotionEvent.ACTION_MOVE,50f to 500f)
            event(MotionEvent.ACTION_UP,50f to 500f)
        }
    }
    @Test fun pinchCancelsChildAndNeverBecomesDragAfterOneFingerLeaves() {
        val f=Fixture();f.pinch();f.end()
        assertTrue(f.scales.any { it>1f });assertEquals(0,f.minimized)
        assertEquals(1,f.actions.count { it==MotionEvent.ACTION_CANCEL })
        assertSame(f.child,f.host.getChildAt(0));assertEquals(1,f.host.childCount)
    }
    @Test fun freshSingleFingerDragStillMinimizesAfterPinch() {
        val f=Fixture();f.pinch();f.end()
        f.event(MotionEvent.ACTION_DOWN,300f to 200f)
        f.event(MotionEvent.ACTION_MOVE,300f to 420f)
        f.event(MotionEvent.ACTION_UP,300f to 450f)
        assertEquals(1,f.minimized)
    }
    @Test fun pinchingBackRestoresOneAndFullscreenAcceptsLowerVideoArea() {
        val f=Fixture();f.pinch()
        f.event(MotionEvent.ACTION_MOVE,300f to 200f,700f to 200f)
        f.event(MotionEvent.ACTION_MOVE,440f to 200f,560f to 200f)
        assertEquals(1f,f.scales.last())
        f.end()
        f.host.configureZoom(true,true,"full"){s,_,_->f.scales.add(s)}
        f.event(MotionEvent.ACTION_DOWN,300f to 800f)
        f.event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),300f to 800f,700f to 800f)
        f.event(MotionEvent.ACTION_MOVE,200f to 800f,800f to 800f)
        f.event(MotionEvent.ACTION_MOVE,50f to 800f,950f to 800f)
        assertTrue(f.scales.last()>1f)
        f.event(MotionEvent.ACTION_CANCEL,50f to 800f,950f to 800f)
    }
    @Test fun cancelledGestureAndPointerChangesDoNotMinimize() {
        val f=Fixture();f.pinch()
        f.event(MotionEvent.ACTION_POINTER_DOWN or (2 shl 8),50f to 200f,950f to 200f,500f to 300f)
        f.event(MotionEvent.ACTION_POINTER_UP or (0 shl 8),50f to 200f,950f to 200f,500f to 300f)
        f.event(MotionEvent.ACTION_CANCEL,950f to 200f,500f to 300f)
        f.pinch();f.end();assertEquals(0,f.minimized)
    }
    @Test fun miniPipNewSourceAndRotationResetWithoutReplacingChild() {
        val f=Fixture()
        repeat(10){
            f.pinch();f.end();assertTrue(f.scales.last()>1)
            f.configure(false,"a");assertEquals(1f,f.scales.last())
            f.configure(true,"a");f.pinch();f.end()
            f.configure(true,"b$it");assertEquals(1f,f.scales.last())
            assertSame(f.child,f.host.getChildAt(0))
        }
        f.pinch();f.end();f.host.layout(0,0,900,1000);assertEquals(1f,f.scales.last())
    }
    @Test fun controlsAndOutsideVideoKeepTheirOriginalTouchSequence() {
        val f=Fixture()
        f.event(MotionEvent.ACTION_DOWN,300f to 800f)
        f.event(MotionEvent.ACTION_POINTER_DOWN or (1 shl 8),300f to 800f,700f to 800f)
        f.event(MotionEvent.ACTION_MOVE,100f to 800f,900f to 800f)
        f.event(MotionEvent.ACTION_CANCEL,100f to 800f,900f to 800f)
        assertTrue(f.actions.contains(MotionEvent.ACTION_CANCEL))
        assertTrue(f.scales.isEmpty());assertEquals(0,f.minimized)
        f.actions.clear()
        f.event(MotionEvent.ACTION_DOWN,300f to 200f);f.event(MotionEvent.ACTION_UP,300f to 200f)
        assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP),f.actions)
    }
}
