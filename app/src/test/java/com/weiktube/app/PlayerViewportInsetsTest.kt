package com.weiktube.app

import android.content.Context
import android.graphics.Insets
import android.graphics.Rect
import android.view.DisplayCutout
import android.view.ViewGroup
import android.view.WindowInsets
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Exercises the real Android/AndroidX inset objects and the retained WebView dispatch path. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerViewportInsetsTest {
    private lateinit var player: PlayerGestureLayout
    private lateinit var web: RecordingWebView
    private val bars = WindowInsets.Type.systemBars()
    private val cutout = WindowInsets.Type.displayCutout()

    @Before fun createRetainedPlayer() {
        val context = RuntimeEnvironment.getApplication()
        player = PlayerGestureLayout(context)
        web = RecordingWebView(context)
        player.attach(web)
    }

    @After fun releasePlayer() {
        player.removeAllViews()
        web.destroy()
    }

    @Test fun portraitReturnDeliversExplicitZerosInsteadOfRetainingLandscapeSafeArea() {
        val original = landscapeInsets(leftCutout = 41)
        player.setSystemInsetsHandledByHost(false)
        player.dispatchPlayerInsets(WindowInsetsCompat.toWindowInsetsCompat(original))
        assertEquals(41, web.latest.getInsets(cutout).left)
        assertEquals(24, web.latest.getInsets(bars).right)

        player.setSystemInsetsHandledByHost(true)
        // Even if the final root geometry is not available yet, replay clears handled types.
        player.dispatchPlayerInsets(WindowInsetsCompat.toWindowInsetsCompat(original))
        assertEquals(Insets.NONE, web.latest.getInsets(bars or cutout))
        assertEquals(Insets.NONE, web.latest.getInsetsIgnoringVisibility(bars or cutout))
        assertNull(web.latest.displayCutout)
        assertFalse("A zero update must reach WebView, not stop dispatch as CONSUMED", web.latest.isConsumed)
        assertSame(web, player.getChildAt(0))
        assertEquals(1, player.childCount)
        assertEquals("The source root insets must remain untouched", 41, original.getInsets(cutout).left)
    }

    @Test fun tenAlternatingLandscapeAndPortraitReturnsKeepTheSameWebView() {
        repeat(10) { cycle ->
            val landscape = landscapeInsets(leftCutout = if (cycle % 2 == 0) 41 else 0,
                rightCutout = if (cycle % 2 == 0) 0 else 41)
            player.setSystemInsetsHandledByHost(false)
            player.dispatchPlayerInsets(WindowInsetsCompat.toWindowInsetsCompat(landscape))
            assertEquals(landscape.getInsets(cutout), web.latest.getInsets(cutout))
            assertEquals(landscape.getInsets(bars), web.latest.getInsets(bars))

            player.setSystemInsetsHandledByHost(true)
            player.dispatchPlayerInsets(WindowInsetsCompat.toWindowInsetsCompat(landscape))
            assertEquals("portrait cycle $cycle", Insets.NONE, web.latest.getInsets(bars or cutout))
            assertNull(web.latest.displayCutout)
            assertSame("No renderer replacement on cycle $cycle", web, player.getChildAt(0))
        }
        assertEquals(20, web.received.size)
    }

    @Test fun tenMiniPipAndRestoreCyclesKeepPhoneInsetsOutOfTheRetainedSmallViewport() {
        // Android can still expose the phone's old safe area during PiP's window transition.
        // Mini and PiP both need explicit zeros; UI wiring is checked by the source-contract script.
        repeat(10) { cycle ->
            val phone = landscapeInsets(leftCutout = if (cycle % 2 == 0) 41 else 0,
                rightCutout = if (cycle % 2 == 0) 0 else 41)
            for ((stage, size) in listOf("mini" to (196 to 110), "pip" to (320 to 180), "restored mini" to (196 to 110))) {
                player.setSystemInsetsHandledByHost(true)
                resizeAndDispatch(size.first, size.second, phone)
                assertEquals("$stage cycle $cycle", Insets.NONE, web.latest.getInsets(bars or cutout))
                assertEquals("stable $stage cycle $cycle", Insets.NONE, web.latest.getInsetsIgnoringVisibility(bars or cutout))
                assertNull(web.latest.displayCutout)
                assertSame("No player replacement on $stage cycle $cycle", web, player.getChildAt(0))
                assertEquals(1, player.childCount)
                // JVM WebView has no Chromium layout engine. Check the Android host/child
                // layout contract; actual rendered video geometry belongs to the browser fixture.
                assertEquals(size.first, player.width)
                assertEquals(size.second, player.height)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, web.layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, web.layoutParams.height)
                assertFalse(web.latest.isConsumed)
            }
        }
        assertEquals(30, web.received.size)
    }

    @Test fun landscapeToPipClearsCutoutAndReturningToLandscapeRestoresRealInsets() {
        val phone = landscapeInsets(leftCutout = 41)
        player.setSystemInsetsHandledByHost(false)
        resizeAndDispatch(1000, 560, phone)
        assertEquals(41, web.latest.getInsets(cutout).left)
        assertEquals(24, web.latest.getInsets(bars).right)

        player.setSystemInsetsHandledByHost(true)
        resizeAndDispatch(320, 180, phone)
        assertEquals(Insets.NONE, web.latest.getInsets(bars or cutout))
        assertEquals(Insets.NONE, web.latest.getInsetsIgnoringVisibility(bars or cutout))
        assertNull(web.latest.displayCutout)

        player.setSystemInsetsHandledByHost(false)
        resizeAndDispatch(1000, 560, phone)
        assertEquals(phone.getInsets(bars or cutout), web.latest.getInsets(bars or cutout))
        assertEquals(phone.getInsetsIgnoringVisibility(bars or cutout), web.latest.getInsetsIgnoringVisibility(bars or cutout))
        assertEquals(phone.displayCutout, web.latest.displayCutout)
        assertEquals("The incoming phone insets were never mutated", 41, phone.getInsets(cutout).left)
        assertSame(web, player.getChildAt(0))
        assertEquals(3, web.received.size)
    }

    @Test fun consumedAncestorReproducesStaleInsetsButDirectReplayReachesRetainedWebView() {
        val original = landscapeInsets(leftCutout = 41)
        player.setSystemInsetsHandledByHost(false)
        player.dispatchPlayerInsets(WindowInsetsCompat.toWindowInsetsCompat(original))
        val receivedBeforeReturn = web.received.size
        val ancestor = FrameLayout(RuntimeEnvironment.getApplication()).apply { addView(player) }
        ViewCompat.setOnApplyWindowInsetsListener(ancestor) { _, _ -> WindowInsetsCompat.CONSUMED }
        val portrait = WindowInsets.Builder()
            .setInsets(bars, Insets.of(0, 30, 0, 24))
            .build()

        // Models Compose's native padding listener, which stops the ordinary child update.
        ViewCompat.dispatchApplyWindowInsets(ancestor, WindowInsetsCompat.toWindowInsetsCompat(portrait))
        assertEquals(receivedBeforeReturn, web.received.size)
        assertEquals("The renderer is left with its old landscape value", 41, web.latest.getInsets(cutout).left)

        player.setSystemInsetsHandledByHost(true)
        player.dispatchPlayerInsets(WindowInsetsCompat.toWindowInsetsCompat(portrait))
        assertEquals(receivedBeforeReturn + 1, web.received.size)
        assertEquals(Insets.NONE, web.latest.getInsets(bars or cutout))
        assertSame(web, player.getChildAt(0))
    }

    @Test fun portraitClearPreservesKeyboardAndGestureInsetsForWebViewsOwnBoundsHandling() {
        val original = WindowInsets.Builder(landscapeInsets(leftCutout = 41))
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, 280))
            .setVisible(WindowInsets.Type.ime(), true)
            .setInsets(WindowInsets.Type.systemGestures(), Insets.of(12, 0, 12, 24))
            .build()
        player.dispatchPlayerInsets(WindowInsetsCompat.toWindowInsetsCompat(original))
        assertEquals(Insets.NONE, web.latest.getInsets(bars or cutout))
        assertEquals(Insets.of(0, 0, 0, 280), web.latest.getInsets(WindowInsets.Type.ime()))
        assertTrue(web.latest.isVisible(WindowInsets.Type.ime()))
        assertEquals(Insets.of(12, 0, 12, 24), web.latest.getInsets(WindowInsets.Type.systemGestures()))
        assertFalse(web.latest.isConsumed)
    }

    @Test fun hiddenStableSystemBarsAndLegacyCutoutCannotLeakIntoPortrait() {
        val original = WindowInsets.Builder(landscapeInsets(leftCutout = 41))
            .setVisible(bars, false)
            .setInsetsIgnoringVisibility(bars, Insets.of(0, 30, 24, 0))
            .build()
        assertEquals(24, original.getInsetsIgnoringVisibility(bars).right)
        assertNotNull(original.displayCutout)
        player.dispatchPlayerInsets(WindowInsetsCompat.toWindowInsetsCompat(original))
        assertEquals(Insets.NONE, web.latest.getInsetsIgnoringVisibility(bars or cutout))
        assertNull(web.latest.displayCutout)
        assertEquals(0, web.latest.stableInsetRight)
    }

    private fun landscapeInsets(leftCutout: Int = 0, rightCutout: Int = 0): WindowInsets {
        val bounds = if (leftCutout > 0) Rect(0, 0, leftCutout, 80) else Rect(1000 - rightCutout, 0, 1000, 80)
        val displayCutout = DisplayCutout(Rect(leftCutout, 0, rightCutout, 0), listOf(bounds))
        return WindowInsets.Builder()
            .setInsets(bars, Insets.of(0, 0, 24, 0))
            .setInsetsIgnoringVisibility(bars, Insets.of(0, 30, 24, 0))
            .setVisible(bars, true)
            .setDisplayCutout(displayCutout)
            .build()
    }

    private fun resizeAndDispatch(width: Int, height: Int, insets: WindowInsets) {
        player.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY),
        )
        player.layout(0, 0, width, height)
        player.dispatchPlayerInsets(WindowInsetsCompat.toWindowInsetsCompat(insets))
    }

    private class RecordingWebView(context: Context) : WebView(context) {
        val received = mutableListOf<WindowInsets>()
        val latest: WindowInsets get() = received.last()
        override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
            received += insets
            return insets
        }
    }
}
