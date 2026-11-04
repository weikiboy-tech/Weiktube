package com.weiktube.app

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.MutableState
import com.weiktube.filter.YoutubeAdBlock
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

/**
 * Real PlayerController/WebViewClient lifecycle, with Android WebViews supplied by Robolectric.
 * The attached Activity is deliberately not created: no Gecko session, Compose tree, account,
 * network, or device playback is started. These tests cannot prove Chromium pixels or audio.
 * Lifecycle fixtures disable the filter because the JVM has no document-start JavaScript engine;
 * a separate test keeps it enabled and verifies that a failed filter still prevents loading.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerControllerLifecycleTest {
    private lateinit var activity: MainActivity
    private lateinit var player: PlayerController
    private var filterWasEnabled = true

    @Before fun attachControllerWithoutStartingTheApp() {
        filterWasEnabled = YoutubeAdBlock.enabled
        YoutubeAdBlock.enabled = false
        activity = Robolectric.buildActivity(MainActivity::class.java).get()
        player = PlayerController(activity)
        activity.player = player
    }

    @After fun releaseController() {
        if (::activity.isInitialized && activity.fullscreen) activity.hideFullscreen()
        if (::player.isInitialized) player.destroy()
        YoutubeAdBlock.enabled = filterWasEnabled
        idle()
    }

    @Test fun selectingAnotherVideoBeforeRecoveryNeverLoadsTheDeadWebView() {
        player.open(VIDEO_A)
        val retired = player.view
        rendererGone(retired)
        assertTrue("A dead renderer must be disposed synchronously", shadowOf(retired).wasDestroyCalled())

        player.open(VIDEO_B)
        val selected = player.view
        assertNotSame(retired, selected)
        assertEquals(WATCH_B, shadowOf(selected).lastLoadedUrl)
        idle()

        assertSame("An old recovery task must not replace the new selection", selected, player.view)
        assertEquals(VIDEO_B, player.source)
        assertEquals(WATCH_B, shadowOf(selected).lastLoadedUrl)
        assertEquals("No selection may be delivered to the retired renderer", WATCH_A, shadowOf(retired).lastLoadedUrl)
    }

    @Test fun selectingTheSameVideoAfterRendererLossAlsoCreatesAFreshDocument() {
        player.open(VIDEO_A)
        val retired = player.view
        rendererGone(retired)
        player.open(VIDEO_A)
        idle()

        assertNotSame(retired, player.view)
        assertEquals(WATCH_A, shadowOf(player.view).lastLoadedUrl)
        assertNull(player.error)
        assertTrue(player.loading)
    }

    @Test fun lateCallbacksFromRetiredViewCannotCorruptTheNewSelection() {
        player.open(VIDEO_A)
        val retired = player.view
        val client = client(retired)
        rendererGone(retired)
        player.open(VIDEO_B)
        val current = player.view

        client.onPageStarted(retired, WATCH_A, null)
        client.doUpdateVisitedHistory(retired, WATCH_A, false)
        client.onPageFinished(retired, WATCH_A)
        client.onReceivedHttpError(retired, request(WATCH_A), WebResourceResponse(
            "text/plain", "UTF-8", 503, "Unavailable", emptyMap(), null))
        client.onRenderProcessGone(retired, rendererDeath())
        idle()

        assertSame(current, player.view)
        assertEquals(VIDEO_B, player.source)
        assertEquals(WATCH_B, shadowOf(current).lastLoadedUrl)
        assertTrue("Old pageFinished must not finish the new document", player.loading)
        assertNull("Old errors must not poison the new document", player.error)
    }

    @Test fun pendingPollFromDeadRendererCannotOverwriteTheSavedPosition() {
        player.open(VIDEO_A)
        reportPosition(23.5)
        player.poll()
        val stale = shadowOf(player.view).lastEvaluatedJavascriptCallback
        assertNotNull(stale)
        hostVisible(false)
        rendererGone(player.view)

        stale.onReceiveValue(pollResult(99.0))
        assertEquals(23.5, player.position, 0.0)
        assertFalse(player.playing)
    }

    @Test fun rendererLossRemovesItsFullscreenSurfaceInsteadOfCoveringNewVideos() {
        player.open(VIDEO_A)
        val retired = player.view
        val surface = View(activity)
        var hidden = 0
        shadowOf(retired).webChromeClient.onShowCustomView(surface, WebChromeClient.CustomViewCallback { hidden++ })
        assertTrue(activity.fullscreen)
        assertNotNull(surface.parent)

        rendererGone(retired)
        player.open(VIDEO_B)
        idle()

        assertFalse(activity.fullscreen)
        assertNull("The dead fullscreen surface must leave the decor hierarchy", surface.parent)
        assertEquals("Release the surface without calling back into the dead renderer", 0, hidden)
        assertEquals(WATCH_B, shadowOf(player.view).lastLoadedUrl)
    }

    @Test fun retiredFullscreenCallbacksCannotCoverOrDismissTheNewRenderer() {
        player.open(VIDEO_A)
        val retired = player.view
        val staleChrome = shadowOf(retired).webChromeClient
        rendererGone(retired)
        player.open(VIDEO_B)
        val currentSurface = View(activity)
        var currentHidden = 0
        shadowOf(player.view).webChromeClient.onShowCustomView(currentSurface,
            WebChromeClient.CustomViewCallback { currentHidden++ })

        staleChrome.onHideCustomView()
        assertTrue(activity.fullscreen)
        assertNotNull(currentSurface.parent)
        assertEquals(0, currentHidden)

        val staleSurface = View(activity)
        var staleHidden = 0
        staleChrome.onShowCustomView(staleSurface, WebChromeClient.CustomViewCallback { staleHidden++ })
        assertNull(staleSurface.parent)
        assertEquals("A rejected platform fullscreen request must be acknowledged", 1, staleHidden)
        assertTrue(activity.fullscreen)
        activity.hideFullscreen()
    }

    @Test fun backgroundRendererLossWaitsForForegroundAndRestoresPausedPosition() {
        player.open(VIDEO_A)
        reportPosition(23.5)
        player.expanded = false
        hostVisible(false)
        player.pauseView()
        val retired = player.view
        rendererGone(retired)
        idle()

        assertSame("No replacement document should load in the background", retired, player.view)
        hostVisible(true)
        player.resumeView()
        idle()
        val replacement = player.view
        assertNotSame(retired, replacement)
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=23", shadowOf(replacement).lastLoadedUrl)
        assertFalse(player.expanded)
        assertTrue(shadowOf(replacement).wasOnResumeCalled())
        finishPage()
        assertFalse("Restoring a paused document must not request autoplay", lastScriptRequestsStart())
    }

    @Test fun foregroundRendererRecoveryIsBoundedUntilAnotherExplicitSelection() {
        player.open(VIDEO_A)
        val first = player.view
        rendererGone(first)
        idle()
        val recovered = player.view
        assertNotSame("A visible player receives one recovery attempt", first, recovered)
        assertEquals(WATCH_A, shadowOf(recovered).lastLoadedUrl)

        rendererGone(recovered)
        repeat(10) { player.resumeView(); idle() }
        assertSame("Repeated failures/resume callbacks must not create a reload loop", recovered, player.view)
        assertNotNull(player.error)

        player.open(VIDEO_B)
        val newSelection = player.view
        assertNotSame(recovered, newSelection)
        rendererGone(newSelection)
        idle()
        assertNotSame("A new user selection gets its own recovery budget", newSelection, player.view)
        assertEquals(WATCH_B, shadowOf(player.view).lastLoadedUrl)
    }

    @Test fun foregroundRendererRecoveryPreservesAnObservedPlayingRequest() {
        player.open(VIDEO_A)
        finishPage()
        reportPosition(32.5, playing = true)
        assertTrue(player.playing)

        rendererGone(player.view)
        idle()
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=32", shadowOf(player.view).lastLoadedUrl)
        finishPage()

        assertTrue("Only an already playing foreground video requests continued playback", lastScriptRequestsStart())
    }

    @Test fun rendererRecoveryPreservesAManualPauseBeforeTheFailure() {
        player.open(VIDEO_A)
        finishPage()
        reportPosition(32.5, playing = true)
        player.pause()

        rendererGone(player.view)
        idle()
        finishPage()

        assertFalse(player.playing)
        assertFalse("A paused video must not resume because its renderer was replaced", lastScriptRequestsStart())
    }

    @Test fun manualPauseWhileRecoveryIsQueuedCancelsTheSavedPlayRequest() {
        player.open(VIDEO_A)
        finishPage()
        reportPosition(32.5, playing = true)
        rendererGone(player.view)

        player.pause()
        idle()
        finishPage()

        assertFalse(player.playing)
        assertFalse("The newer user pause wins over the saved pre-failure state", lastScriptRequestsStart())
    }

    @Test fun closingWhileRecoveryIsPendingCancelsTheSavedVideo() {
        player.open(VIDEO_A)
        rendererGone(player.view)
        player.close()
        player.resumeView()
        idle()

        assertNull(player.source)
        assertFalse(player.loading)
        assertFalse(player.pipReady)
        assertTrue("Close must not restore a video behind the homepage",
            shadowOf(player.view).wasDestroyCalled() || shadowOf(player.view).lastLoadedUrl in listOf(null, "about:blank"))
    }

    @Test fun destroyCancelsRecoveryAndAllLaterControllerCallsStayInert() {
        player.open(VIDEO_A)
        val retired = player.view
        val oldClient = client(retired)
        rendererGone(retired)
        player.destroy()
        val finalView = player.view
        val finalUrl = shadowOf(finalView).lastLoadedUrl

        player.open(VIDEO_B)
        player.retry()
        player.resumeView()
        player.setPlaybackAllowed(true)
        player.poll()
        oldClient.onPageStarted(retired, WATCH_A, null)
        oldClient.onPageFinished(retired, WATCH_A)
        oldClient.onRenderProcessGone(retired, rendererDeath())
        idle()

        assertSame("A destroyed controller cannot allocate another renderer", finalView, player.view)
        assertTrue(shadowOf(finalView).wasDestroyCalled())
        assertEquals(finalUrl, shadowOf(finalView).lastLoadedUrl)
        assertFalse(player.pipReady)
    }

    @Test fun choosingTheSameVideoAfterALoadErrorRetriesInsteadOfReusingTheFailedDocument() {
        player.open(VIDEO_A)
        client().onReceivedHttpError(player.view, request(WATCH_A), WebResourceResponse(
            "text/plain", "UTF-8", 503, "Unavailable", emptyMap(), null))
        assertNotNull(player.error)
        assertFalse(player.loading)

        player.open(VIDEO_A)

        assertNull(player.error)
        assertTrue("A failed same-video selection needs a fresh document load", player.loading)
        assertEquals(WATCH_A, shadowOf(player.view).lastLoadedUrl)
    }

    @Test fun tenHealthyBackgroundReturnsKeepOneDocumentAndNeverRequestAutoplay() {
        player.open(VIDEO_A, userInitiated = false)
        finishPage()
        val retained = player.view
        repeat(10) {
            hostVisible(false)
            player.pauseView()
            hostVisible(true)
            player.resumeView()
            player.setPlaybackAllowed(true)
            idle()

            assertSame(retained, player.view)
            assertEquals(WATCH_A, shadowOf(retained).lastLoadedUrl)
            assertFalse("Resume should not start another page load", player.loading)
            assertFalse(lastScriptRequestsStart())
        }
        assertTrue(shadowOf(retained).wasOnPauseCalled())
        assertTrue(shadowOf(retained).wasOnResumeCalled())
        assertFalse(shadowOf(retained).wasDestroyCalled())
    }

    @Test fun freshSavedSelectionRemainsPausedUntilExplicitPlay() {
        player.open(VIDEO_A, userInitiated = false)
        finishPage()
        player.resumeView()
        player.setPlaybackAllowed(true)

        assertTrue(player.pipReady)
        assertFalse(lastScriptRequestsStart())
        player.play()
        assertTrue(lastScriptRequestsStart())
    }

    @Test fun processRestoreLoadsTheSavedYoutubePositionMinimizedAndPaused() {
        player.restore(VIDEO_A, 65.75)

        assertEquals(VIDEO_A, player.source)
        assertEquals(65.75, player.position, 0.0)
        assertEquals("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=65", shadowOf(player.view).lastLoadedUrl)
        assertFalse(player.expanded)
        finishPage()
        assertFalse(lastScriptRequestsStart())
        assertFalse(player.playing)
    }

    @Test fun processRestoreRetainsDirectHttpsPositionWithoutStartingPlayback() {
        val url = "https://media.example.test/video.mp4"
        player.restore(url, 45.625)

        assertEquals(url, player.source)
        assertEquals(45.625, player.position, 0.0)
        assertFalse(player.isYoutube)
        assertFalse(player.expanded)
        val document = shadowOf(player.view).lastLoadDataWithBaseURL
        assertNotNull(document)
        assertEquals("https://com.weiktube.app/", document.baseUrl)
        // Verify the real loadData payload, not execution by a nonexistent JVM media engine.
        assertTrue(document.data.contains("v.currentTime=45.625"))
        assertTrue(document.data.contains(JSONObject.quote(url)))
        finishPage()
        assertFalse(lastScriptRequestsStart())
        assertFalse(player.playing)
    }

    @Test fun processRestoreSanitizesNonFiniteOrNegativeDirectVideoPositions() {
        val url = "https://media.example.test/video.mp4"
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { invalid ->
            player.restore(url, invalid)
            assertEquals(0.0, player.position, 0.0)
            assertFalse(player.expanded)
            assertTrue(shadowOf(player.view).lastLoadDataWithBaseURL.data.contains("v.currentTime=0.0"))
        }
    }

    @Test fun rendererReplacementNeverBypassesTheFilterReadinessGate() {
        player.open(VIDEO_A)
        rendererGone(player.view)
        // Robolectric has no Chromium document-start engine. The real adapter must fail closed.
        YoutubeAdBlock.enabled = true
        player.open(VIDEO_B)
        idle()

        assertNull("A protected document must not load before its adapter is ready",
            shadowOf(player.view).lastLoadedUrl)
        assertNotNull(player.error)
        assertFalse(player.loading)
        assertFalse(player.pipReady)
    }

    @Test fun replacementResumeFailureDisposesTheCandidateAndNextSelectionCreatesAnother() {
        assertFailedReplacementIsDiscarded(failDuringConfiguration = false)
    }

    @Test fun partialConfigurationFailureDisposesEvenACandidateWithoutInstalledHooks() {
        assertFailedReplacementIsDiscarded(failDuringConfiguration = true)
    }

    private fun assertFailedReplacementIsDiscarded(failDuringConfiguration: Boolean) {
        player.destroy()
        val created = mutableListOf<FailingWebView>()
        player = PlayerController(activity) { context ->
            val second = created.size == 1
            FailingWebView(context,
                failBackground = second && failDuringConfiguration,
                failResume = second && !failDuringConfiguration,
            ).also(created::add)
        }
        activity.player = player
        player.open(VIDEO_A)
        val retired = player.view
        rendererGone(retired)
        idle()

        assertEquals("Recovery attempted exactly one replacement", 2, created.size)
        val failed = created[1]
        assertTrue("A partially initialized candidate still needs destroy", shadowOf(failed).wasDestroyCalled())
        assertSame("Never publish a candidate that failed configuration or resume", retired, player.view)
        assertNull(shadowOf(failed).lastLoadedUrl)
        assertNotNull(player.error)
        assertFalse(player.loading)
        assertFalse(player.pipReady)

        player.open(VIDEO_B)
        idle()
        assertEquals(3, created.size)
        assertSame(created[2], player.view)
        assertNotSame(failed, player.view)
        assertEquals(WATCH_B, shadowOf(player.view).lastLoadedUrl)
        assertNull(player.error)
        assertTrue(player.loading)
    }

    /** Fault injection delegates all successful operations to Android's actual WebView shadow. */
    private class FailingWebView(
        context: Context,
        private var failBackground: Boolean,
        private var failResume: Boolean,
    ) : WebView(context) {
        override fun setBackgroundColor(color: Int) {
            if (failBackground) {
                failBackground = false
                throw IllegalStateException("Fixture configuration failure")
            }
            super.setBackgroundColor(color)
        }

        override fun onResume() {
            if (failResume) {
                failResume = false
                throw IllegalStateException("Fixture resume failure")
            }
            super.onResume()
        }
    }

    private fun hostVisible(visible: Boolean) {
        // Exercise the same host gate read by the controller without starting Gecko/Compose.
        ReflectionHelpers.getField<MutableState<Boolean>>(activity, "foreground\$delegate").value = visible
    }

    private fun rendererGone(web: WebView) {
        assertTrue(client(web).onRenderProcessGone(web, rendererDeath()))
    }

    private fun client(web: WebView = player.view): WebViewClient = shadowOf(web).webViewClient
    private fun finishPage() = client().onPageFinished(player.view, shadowOf(player.view).lastLoadedUrl ?: WATCH_A)
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun lastScriptRequestsStart() = shadowOf(player.view).lastEvaluatedJavascript
        .orEmpty().contains("window.weiktubeSetPlayback(true,true)")

    private fun reportPosition(seconds: Double, playing: Boolean = false) {
        player.poll()
        shadowOf(player.view).lastEvaluatedJavascriptCallback.onReceiveValue(pollResult(seconds, playing))
    }

    private fun pollResult(seconds: Double, playing: Boolean = false): String = JSONObject.quote(JSONObject()
        .put("playing", playing).put("position", seconds).put("duration", 120.0)
        .put("width", 1920).put("height", 1080).put("title", "").put("url", WATCH_A).toString())

    private fun rendererDeath() = object : RenderProcessGoneDetail() {
        override fun didCrash() = false
        override fun rendererPriorityAtExit() = WebView.RENDERER_PRIORITY_WAIVED
    }

    private fun request(url: String) = object : WebResourceRequest {
        override fun getUrl() = Uri.parse(url)
        override fun isForMainFrame() = true
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders(): MutableMap<String, String> = mutableMapOf()
    }

    private companion object {
        const val VIDEO_A = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        const val VIDEO_B = "https://www.youtube.com/watch?v=9bZkp7q19f0"
        const val WATCH_A = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=0"
        const val WATCH_B = "https://m.youtube.com/watch?v=9bZkp7q19f0&t=0"
    }
}
