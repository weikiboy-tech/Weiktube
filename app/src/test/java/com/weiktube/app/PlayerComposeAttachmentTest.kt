package com.weiktube.app

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.weiktube.filter.YoutubeAdBlock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

/**
 * Uses real Compose AndroidView/key replacement in an isolated Activity fixture and the real
 * PlayerController. This is not the full WeiktubeContent tree, a Chromium renderer, or a device test.
 * Positive bounds apply to the actual native host; Robolectric does not measure Chromium content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerComposeAttachmentTest {
    private lateinit var hostLifecycle: ActivityController<MountActivity>
    private lateinit var host: MountActivity
    private lateinit var player: PlayerController
    private var filterWasEnabled = true

    @Before fun createComposeHost() {
        filterWasEnabled = YoutubeAdBlock.enabled
        // The fixture loads no network and has no Chromium document-start runtime.
        YoutubeAdBlock.enabled = false
        player = newController()
        hostLifecycle = Robolectric.buildActivity(MountActivity::class.java).setup()
        host = hostLifecycle.get()
    }

    @After fun destroyHosts() {
        if (::player.isInitialized) player.destroy()
        if (::hostLifecycle.isInitialized) hostLifecycle.pause().stop().destroy()
        YoutubeAdBlock.enabled = filterWasEnabled
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun tenRendererReplacementsMountOnlyTheNewChildInTheRealComposeFixture() {
        player.open(VIDEO_A)
        mountCurrentView()
        var previousWrapper = assertMounted(player.view)

        repeat(10) { cycle ->
            val retired = player.view
            assertTrue(shadowOf(retired).webViewClient.onRenderProcessGone(retired, rendererDeath()))
            assertNull("Renderer cleanup detaches the child from its old host", retired.parent)
            assertTrue(shadowOf(retired).wasDestroyCalled())

            // A user selection arrives before the queued recovery. It must win in the mounted UI.
            player.open(if (cycle % 2 == 0) VIDEO_B else VIDEO_A)
            val replacement = player.view
            assertNotSame(retired, replacement)
            mountCurrentView()

            val currentWrapper = assertMounted(replacement)
            assertNotSame("key(newWebView) must create a new native wrapper", previousWrapper, currentWrapper)
            assertEquals("The retired wrapper must not retain any renderer", 0, previousWrapper.childCount)
            assertNull(retired.parent)
            assertSame(replacement, player.view)
            previousWrapper = currentWrapper
        }
    }

    @Test fun replacingTheControllerFromSavedStateNeverReusesTheOldMountedWebView() {
        player.restore(VIDEO_A, 65.75)
        mountCurrentView()
        val retiredController = player
        val retiredView = player.view
        val retiredWrapper = assertMounted(retiredView)
        // Save the actual fixture Activity plus controller inputs, then cross an Android Parcel
        // boundary. This does not execute MainActivity.onCreate or simulate killing a Linux process.
        val saved = Bundle().apply {
            putString("video", player.source)
            putDouble("video_position", player.position)
        }
        hostLifecycle.pause().saveInstanceState(saved).stop().destroy()
        retiredController.destroy()
        assertNull(retiredView.parent)
        val restored = parcelRoundTrip(saved)

        player = newController()
        hostLifecycle = Robolectric.buildActivity(MountActivity::class.java).setup(restored)
        host = hostLifecycle.get()
        player.restore(requireNotNull(restored.getString("video")), restored.getDouble("video_position"))
        mountCurrentView()
        val currentWrapper = assertMounted(player.view)

        assertNotSame(retiredController, player)
        assertNotSame(retiredView, player.view)
        assertNotSame(retiredWrapper, currentWrapper)
        assertEquals(0, retiredWrapper.childCount)
        assertTrue(shadowOf(retiredView).wasDestroyCalled())
        assertEquals(VIDEO_A, player.source)
        assertEquals(65.75, player.position, 0.0)
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=65", shadowOf(player.view).lastLoadedUrl)
        assertFalse(player.expanded)
        assertFalse(player.playing)
    }

    @Test fun tenHostBackgroundReturnsKeepTheSameMountedChildAndNativeBounds() {
        player.open(VIDEO_A, userInitiated = false)
        mountCurrentView()
        val retained = player.view
        val retainedWrapper = assertMounted(retained)
        val loadCount = (retained as RecordingWebView).loadCount
        repeat(10) {
            // These are real lifecycle transitions of the Compose fixture Activity. Controller
            // calls model MainActivity's existing onStop/onResume pairing, not its whole lifecycle.
            player.pauseView()
            hostLifecycle.pause().stop()
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            hostLifecycle.start().resume().visible()
            player.resumeView()
            player.setPlaybackAllowed(true)
            mountCurrentView()

            assertSame(retained, player.view)
            assertSame(retainedWrapper, assertMounted(retained))
            assertFalse(shadowOf(retained).wasDestroyCalled())
            assertEquals("Healthy background returns must not reload the document", loadCount, retained.loadCount)
            assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=0", shadowOf(retained).lastLoadedUrl)
        }
        assertTrue(shadowOf(retained).wasOnPauseCalled())
        assertTrue(shadowOf(retained).wasOnResumeCalled())
    }

    private fun newController(): PlayerController {
        // Attach a normal Android context without starting the app, Gecko, accounts, or network.
        val controllerActivity = Robolectric.buildActivity(MainActivity::class.java).get()
        return PlayerController(controllerActivity, ::RecordingWebView).also { controllerActivity.player = it }
    }

    private fun parcelRoundTrip(saved: Bundle): Bundle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(saved)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(MountActivity::class.java.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    /** Counting delegates the actual loading API to Robolectric; it does not synthesize callbacks. */
    private class RecordingWebView(context: Context) : WebView(context) {
        var loadCount = 0
            private set

        override fun loadUrl(url: String) {
            loadCount++
            super.loadUrl(url)
        }
    }

    private fun mountCurrentView() {
        host.currentWebView = player.view
        Snapshot.sendApplyNotifications()
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(32))
            host.window.decorView.apply {
                measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
                layout(0, 0, 1080, 1920)
            }
        }
    }

    private fun assertMounted(web: WebView): PlayerGestureLayout {
        val wrapper = web.parent as? PlayerGestureLayout
        assertNotNull("The current renderer must be a child of the Compose-created native host", wrapper)
        wrapper!!
        assertEquals(1, wrapper.childCount)
        assertSame(web, wrapper.getChildAt(0))
        assertEquals(View.VISIBLE, wrapper.visibility)
        assertTrue("The host belongs to the visible Activity window", wrapper.isShown)
        assertTrue("The native host has a positive layout width", wrapper.width > 0)
        assertTrue("The native host has a positive layout height", wrapper.height > 0)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, web.layoutParams.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, web.layoutParams.height)
        var ancestor: android.view.ViewParent? = wrapper
        while (ancestor != null && ancestor !== host.window.decorView) ancestor = ancestor.parent
        assertSame(host.window.decorView, ancestor)
        return wrapper
    }

    private fun rendererDeath() = object : RenderProcessGoneDetail() {
        override fun didCrash() = false
        override fun rendererPriorityAtExit() = WebView.RENDERER_PRIORITY_WAIVED
    }

    class MountActivity : ComponentActivity() {
        var currentWebView by mutableStateOf<WebView?>(null)

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent {
                val web = currentWebView
                if (web != null) key(web) {
                    AndroidView(
                        factory = { context -> PlayerGestureLayout(context).also { it.attach(web) } },
                        modifier = Modifier.fillMaxSize(),
                        update = { it.visibility = View.VISIBLE },
                    )
                }
            }
        }
    }

    private companion object {
        const val VIDEO_A = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        const val VIDEO_B = "https://www.youtube.com/watch?v=9bZkp7q19f0"
    }
}
