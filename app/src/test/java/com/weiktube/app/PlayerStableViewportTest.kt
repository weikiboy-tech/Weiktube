package com.weiktube.app

import android.content.Context
import android.view.View.MeasureSpec
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerStableViewportTest {
    // Native layout fixture: Robolectric's WebView has no Chromium size implementation.
    // Actual WebView identity/playback are covered separately by controller/device tests.
    private class RecordingWeb(context: Context) : View(context) {
        val sizes = mutableListOf<Pair<Int, Int>>()
        var laidWidth = 0
        var laidHeight = 0
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            laidWidth = right - left; laidHeight = bottom - top
            super.onLayout(changed, left, top, right, bottom)
        }
        override fun onMeasure(w: Int, h: Int) {
            sizes += MeasureSpec.getSize(w) to MeasureSpec.getSize(h)
            setMeasuredDimension(MeasureSpec.getSize(w), MeasureSpec.getSize(h))
        }
    }
    private fun layout(host: PlayerGestureLayout, w: Int, h: Int) {
        host.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        host.layout(0, 0, w, h)
    }
    private fun fixture(block: (PlayerGestureLayout, RecordingWeb) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val host = PlayerGestureLayout(context)
        val web = RecordingWeb(context)
        host.addView(web, FrameLayout.LayoutParams(-1,-1))
        try { block(host, web) } finally { host.removeAllViews() }
    }

    @Test fun tenMiniPipAndExpansionCyclesKeepTheChildAndItsLayoutSize() = fixture { host, web ->
        host.setViewportPresentation(false, "first", 1920, 1080, 384, 800)
        layout(host, 384, 800)
        repeat(10) {
            host.setViewportPresentation(true, "first", 1920, 1080, 384, 800)
            web.sizes.clear()
            for ((w,h) in listOf(340 to 600, 196 to 110, 320 to 180, 180 to 320, 196 to 110, 300 to 500, 384 to 800)) {
                layout(host,w,h)
                assertEquals(384,web.laidWidth); assertEquals(800,web.laidHeight)
                assertSame(web,host.getChildAt(0)); assertEquals(1,host.childCount)
                assertEquals(web.scaleX,web.scaleY,0f)
            }
            assertTrue(web.sizes.all { it == (384 to 800) })
            host.setViewportPresentation(false, "first", 1920, 1080, 384, 800)
            layout(host,384,800)
            assertEquals(1f,web.scaleX,0f); assertEquals(1f,web.scaleY,0f)
            assertEquals(0f,web.translationX,0f); assertEquals(0f,web.translationY,0f)
        }
    }

    @Test fun newSourceDoesNotReusePreviousLandscapeGeometry() = fixture { host, web ->
        host.setViewportPresentation(false,"old",1920,1080,1000,560)
        layout(host,1000,560)
        host.setViewportPresentation(true,"old",1920,1080,384,800)
        layout(host,196,110)
        assertEquals(1000,web.laidWidth)
        host.setViewportPresentation(true,"new",1080,1920,384,800)
        layout(host,196,110)
        assertEquals(384,web.laidWidth);assertEquals(800,web.laidHeight)
        host.setViewportPresentation(false,"new",1080,1920,384,800)
        layout(host,384,800)
        assertEquals(1f,web.scaleX,0f)
    }

    @Test fun missingFullMeasurementDefersChildLayoutInsteadOfCreatingTinyDocument() = fixture { host, web ->
        host.setViewportPresentation(true,"restored",1920,1080,0,0)
        layout(host,196,110)
        assertTrue(web.sizes.isEmpty())
        host.setViewportPresentation(true,"restored",1920,1080,384,800)
        layout(host,196,110)
        assertEquals(384,web.laidWidth);assertEquals(800,web.laidHeight)
    }

    @Test fun disabledComparisonPathUsesUnchangedHostLayout() = fixture { host, web ->
        host.setViewportPresentation(false,"first",1920,1080,384,800)
        layout(host,384,800);layout(host,196,110)
        assertEquals(196,web.laidWidth);assertEquals(110,web.laidHeight)
        assertEquals(1f,web.scaleX,0f);assertEquals(0f,web.translationY,0f)
    }

    @Test fun realContainRectangleIsFullyVisibleAndCenteredForAllVideoShapes() {
        for ((vw,vh) in listOf(1920 to 1080,1080 to 1920,1080 to 1080,2560 to 1080)) {
            for ((dw,dh) in listOf(384 to 800,1000 to 560)) {
                for ((tw,th) in listOf(196 to 110,320 to 180,180 to 320)) {
                    val fit = PlayerViewportTransform.fit(dw,dh,tw,th,vw,vh)!!
                    val ratio = vw.toDouble()/vh
                    val w = minOf(dw.toDouble(),dh*ratio);val h=w/ratio
                    val left=(dw-w)/2*fit.scale+fit.x;val top=(dh-h)/2*fit.scale+fit.y
                    val right=left+w*fit.scale;val bottom=top+h*fit.scale
                    assertTrue(left >= -0.001 && top >= -0.001)
                    assertTrue(right <= tw+0.001 && bottom <= th+0.001)
                    assertEquals(left,tw-right,0.001);assertEquals(top,th-bottom,0.001)
                    assertTrue(kotlin.math.abs(right-left-tw)<0.001 || kotlin.math.abs(bottom-top-th)<0.001)
                }
            }
        }
        assertNull(PlayerViewportTransform.fit(0,800,196,110,1920,1080))
        assertNotNull(PlayerViewportTransform.fit(384,800,196,110,0,0))
    }
}
