package com.weiktube.app

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import java.time.Duration

/**
 * Deliberately delivers out-of-order callbacks for successive documents on the SAME WebView.
 * These are real controller/client calls under Robolectric, not device, network, media or
 * Chromium-rendering tests. Activity.onCreate is not run, so no Gecko/account/UI is started.
 * Filters are disabled only in this JVM fixture because it has no document-start JS engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerDocumentLifecycleTest {
    private lateinit var activity: MainActivity
    private lateinit var player: PlayerController
    private var filterWasEnabled = true

    @Before fun attachWithoutStartingTheApp() {
        filterWasEnabled = YoutubeAdBlock.enabled
        YoutubeAdBlock.enabled = false
        activity = Robolectric.buildActivity(MainActivity::class.java).get()
        player = PlayerController(activity) { RecordingWebView(it) }
        activity.player = player
    }

    @After fun release() {
        if (::player.isInitialized) player.destroy()
        YoutubeAdBlock.enabled = filterWasEnabled
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun queuedCloseBlankCannotFinishTheNextSelection() {
        val retained = closeAAndSelectB()
        client().onPageStarted(retained, "about:blank", null)
        client().onPageFinished(retained, "about:blank")

        assertPendingB(retained)
        finishCurrent(WATCH_B)
        assertFalse(player.loading)
        assertTrue("Only B finishing should consume B's explicit Play request", requestsStart())
    }

    @Test fun previousVideoPageStartCannotReplaceTheNewSelection() {
        val retained = closeAAndSelectB()
        client().onPageStarted(retained, WATCH_A, null)

        assertPendingB(retained)
    }

    @Test fun previousVideoPageFinishCannotFinishTheNewSelection() {
        val retained = closeAAndSelectB()
        client().onPageFinished(retained, WATCH_A)

        assertPendingB(retained)
        finishCurrent(WATCH_B)
        assertTrue("The selected video's Play request must survive an unrelated finish", requestsStart())
    }

    @Test fun previousVideoHistoryCannotReplaceAPendingSelection() {
        val retained = closeAAndSelectB()
        client().doUpdateVisitedHistory(retained, WATCH_A, false)

        assertPendingB(retained)
    }

    @Test fun previousVideoNetworkErrorCannotPoisonTheNewSelection() {
        val retained = closeAAndSelectB()
        client().onReceivedError(retained, request(WATCH_A), connectionError())

        assertPendingB(retained)
    }

    @Test fun previousVideoHttpErrorCannotPoisonTheNewSelection() {
        val retained = closeAAndSelectB()
        client().onReceivedHttpError(retained, request(WATCH_A), unavailable())

        assertPendingB(retained)
    }

    @Test fun closeBlankErrorCannotPoisonTheNewSelection() {
        val retained = closeAAndSelectB()
        client().onReceivedError(retained, request("about:blank"), connectionError())

        assertPendingB(retained)
    }

    @Test fun pollRequestedAfterSelectingBBeforeItsCommitCannotReadAAsB() {
        player.open(VIDEO_A)
        finishCurrent(WATCH_A)
        player.poll()
        // Prime a real callback even if the fixed controller skips polling pending documents.
        val previous = shadowOf(player.view).lastEvaluatedJavascriptCallback
        assertNotNull(previous)
        previous.onReceiveValue(snapshot(WATCH_A, 14.5))

        player.open(VIDEO_B)
        val retained = player.view
        player.poll()
        // loadUrl has been requested, but the old document may still execute queued JS.
        val pending = shadowOf(retained).lastEvaluatedJavascriptCallback
        pending?.onReceiveValue(snapshot(WATCH_A, 99.5, playing = true))

        assertPendingB(retained)
        assertEquals("A's timestamp cannot become B's restore position", 0.0, player.position, 0.0)
        assertFalse(player.playing)
        assertEquals(0, player.videoWidth)
    }

    @Test fun pollRequestedBeforeSelectionCannotOverwriteBWhenItsResultArrivesLate() {
        player.open(VIDEO_A)
        finishCurrent(WATCH_A)
        player.poll()
        val pending = shadowOf(player.view).lastEvaluatedJavascriptCallback
        assertNotNull(pending)

        player.open(VIDEO_B)
        pending.onReceiveValue(snapshot(WATCH_A, 99.5, playing = true))

        assertPendingB(player.view)
        assertEquals(0.0, player.position, 0.0)
        assertFalse(player.playing)
    }

    @Test fun selectedDocumentStillFinishesAndReportsItsOwnPlayback() {
        val retained = closeAAndSelectB()
        finishCurrent(WATCH_B)

        assertSame(retained, player.view)
        assertEquals(VIDEO_B, player.source)
        assertFalse(player.loading)
        assertNull(player.error)
        assertTrue(requestsStart())
        player.poll()
        shadowOf(retained).lastEvaluatedJavascriptCallback.onReceiveValue(snapshot(WATCH_B, 8.5, playing = true))
        assertEquals(VIDEO_B, player.source)
        assertEquals(8.5, player.position, 0.0)
        assertTrue(player.playing)
    }

    @Test fun selectedYoutubeCommitStartsWithoutWaitingForUnrelatedResourcesToFinish() {
        player.open(VIDEO_B)
        client().onPageStarted(player.view, WATCH_B, null)
        player.setPlaybackAllowed(true)
        assertFalse("An uncommitted selection cannot start the previous document", requestsStart())

        // This fixture's disabled filter is already ready. Real filtering retains its own gate.
        client().onPageCommitVisible(player.view, WATCH_B)

        assertTrue("Commit must unblock the existing explicit Play request before pageFinished", requestsStart())
        assertTrue("Resource loading may still be in progress", player.loading)
        assertEquals(VIDEO_B, player.source)
        assertNull(player.error)
    }

    @Test fun userPauseBeforeCommitCancelsTheOutstandingExplicitPlayRequest() {
        player.open(VIDEO_B)
        client().onPageStarted(player.view, WATCH_B, null)
        player.pause()
        client().onPageCommitVisible(player.view, WATCH_B)
        client().onPageFinished(player.view, WATCH_B)
        player.setPlaybackAllowed(true)

        assertFalse("Permission/commit cannot reverse the newer explicit user Pause", requestsStart())
        assertFalse(player.playing)
        assertFalse(player.loading)
        assertTrue(player.pipReady)
    }

    @Test fun directVideoDataCommitWaitsForItsOwnedBaseDocumentFinish() {
        player.open(DIRECT_VIDEO)
        val document = shadowOf(player.view).lastLoadDataWithBaseURL
        assertEquals(DIRECT_BASE, document.baseUrl)
        assertTrue(document.data.contains(JSONObject.quote(DIRECT_VIDEO)))
        client().onPageStarted(player.view, DIRECT_BASE, null)
        // Chromium uses a data: commit URL/header for loadDataWithBaseURL, while the
        // generated document and onPageFinished use the base URL. Do not trust arbitrary data:.
        client().onPageCommitVisible(player.view, DIRECT_COMMIT)
        assertFalse(player.pipReady)
        assertFalse(requestsStart())

        client().onPageFinished(player.view, DIRECT_BASE)

        assertFalse(player.isYoutube)
        assertEquals(DIRECT_VIDEO, player.source)
        assertFalse(player.loading)
        assertTrue("The app-generated HTTPS-base document remains playable", player.pipReady)
        assertTrue(requestsStart())
    }

    @Test fun pausedDirectVideoKeepsItsPositionThroughOneConfirmedBlankDocumentRecovery() {
        player.restore(DIRECT_VIDEO, 36.25)
        val retained = player.view as RecordingWebView
        finishDirectDocument()
        player.pause()
        assertFalse(requestsStart())

        deliverBlankSnapshot()
        idle()

        assertSame(retained, player.view)
        assertEquals(2, retained.directLoads)
        assertEquals(36.25, player.position, 0.0)
        assertEquals(DIRECT_VIDEO, player.source)
        assertFalse(player.expanded)
        assertTrue(shadowOf(retained).lastLoadDataWithBaseURL.data.contains("v.currentTime=36.25"))
        finishDirectDocument()
        player.setPlaybackAllowed(true)
        assertFalse("A recovered direct-video document preserves the user Pause", requestsStart())
        assertFalse(player.playing)
        assertTrue(player.pipReady)
    }

    @Test fun selectedDocumentNetworkErrorStillOffersAnExplicitRetry() {
        player.open(VIDEO_B)
        client().onPageStarted(player.view, WATCH_B, null)
        client().onReceivedError(player.view, request(WATCH_B), connectionError())

        assertFalse(player.loading)
        assertNotNull(player.error)
        player.retry()
        assertEquals(VIDEO_B, player.source)
        assertEquals(WATCH_B, shadowOf(player.view).lastLoadedUrl)
        assertNull(player.error)
        assertTrue(player.loading)
    }

    @Test fun selectedDocumentHttpErrorStillAppears() {
        player.open(VIDEO_B)
        client().onPageStarted(player.view, WATCH_B, null)
        client().onReceivedHttpError(player.view, request(WATCH_B), unavailable())

        assertEquals(VIDEO_B, player.source)
        assertFalse(player.loading)
        assertNotNull(player.error)
    }

    @Test fun completedEmptyBlankDocumentRecoversOnceInTheSameWebViewAtThePausedPosition() {
        openAndReportA(42.75, playing = true)
        player.pause()
        player.expanded = false
        val retained = player.view as RecordingWebView

        deliverBlankSnapshot()
        idle()

        assertSame("A responsive renderer must be retained", retained, player.view)
        assertEquals("Only one replacement document should load", 2, retained.loads.size)
        assertEquals(RECOVERED_A, retained.loads.last())
        assertEquals(VIDEO_A, player.source)
        assertEquals(42.75, player.position, 0.0)
        assertFalse(player.expanded)
        assertFalse(player.playing)
        finishCurrent(RECOVERED_A)
        assertFalse("Recovering a paused document must not issue Play", requestsStart())
    }

    @Test fun secondEmptyDocumentLossReportsAnErrorWithoutAnotherAutomaticLoad() {
        openAndReportA(42.75)
        val retained = player.view as RecordingWebView
        deliverBlankSnapshot()
        idle()
        finishCurrent(RECOVERED_A)

        deliverBlankSnapshot()
        idle()
        repeat(10) { player.resumeView(); player.poll(); idle() }

        assertSame(retained, player.view)
        assertEquals("The recovery budget is not reset by its own new document", 2, retained.loads.size)
        assertNotNull("A second confirmed loss needs an actionable error", player.error)
        assertFalse(player.loading)
        assertFalse(player.playing)
    }

    @Test fun selectingBWhileEmptyDocumentRecoveryIsQueuedCancelsRecoveryOfA() {
        openAndReportA(42.75)
        val retained = player.view as RecordingWebView
        deliverBlankSnapshot()

        player.open(VIDEO_B)
        idle()

        assertPendingB(retained)
        assertEquals(listOf(WATCH_A, WATCH_B), retained.loads)
        finishCurrent(WATCH_B)
        assertTrue("B remains a fresh explicit video selection", requestsStart())
    }

    @Test fun pauseWhileEmptyDocumentRecoveryIsQueuedCancelsItsSavedPlayRequest() {
        openAndReportA(42.75, playing = true)
        assertTrue(player.playing)
        deliverBlankSnapshot()

        player.pause()
        idle()
        finishCurrent(RECOVERED_A)

        assertEquals(RECOVERED_A, shadowOf(player.view).lastLoadedUrl)
        assertEquals(42.75, player.position, 0.0)
        assertFalse(player.playing)
        assertFalse("The newer user Pause wins over recovery's saved state", requestsStart())
    }

    @Test fun selectingTheSameVideoWhileDocumentRecoveryIsQueuedCannotReuseTheEmptyPage() {
        openAndReportA(42.75)
        val retained = player.view as RecordingWebView
        deliverBlankSnapshot()

        player.open(VIDEO_A)
        idle()

        assertSame(retained, player.view)
        assertEquals("The explicit selection must replace the known empty document", listOf(WATCH_A, WATCH_A), retained.loads)
        assertTrue(player.loading)
        finishCurrent(WATCH_A)
        assertTrue(requestsStart())
        assertNull(player.error)
    }

    @Test fun missingJavascriptResponseNeverTriggersATimedReload() {
        openAndReportA(42.75)
        val retained = player.view as RecordingWebView
        player.poll()
        assertNotNull(shadowOf(retained).lastEvaluatedJavascriptCallback)
        // Deliberately never deliver the response: silence proves neither a blank page nor death.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(10))

        assertSame(retained, player.view)
        assertEquals(listOf(WATCH_A), retained.loads)
        assertEquals(42.75, player.position, 0.0)
        assertNull(player.error)
    }

    @Test fun temporaryMissingVideoKeepsTheLastContentPositionAndDoesNotReload() {
        openAndReportA(42.75)
        val retained = player.view as RecordingWebView
        respondToPoll(snapshot(WATCH_A, 0.0, hasVideo = false))
        idle()

        assertEquals("YouTube may temporarily remove VIDEO during SPA updates", 42.75, player.position, 0.0)
        assertEquals(120.0, player.duration, 0.0)
        assertEquals(VIDEO_A, player.source)
        assertEquals(listOf(WATCH_A), retained.loads)
        assertNull(player.error)
    }

    @Test fun unfinishedOrNonemptyBlankSnapshotCannotTriggerRecovery() {
        openAndReportA(42.75)
        val retained = player.view as RecordingWebView
        shadowOf(retained).pushEntryToHistory("about:blank")
        respondToPoll(snapshot("about:blank", 0.0, hasVideo = false, bodyEmpty = true, readyState = "loading"))
        idle()
        respondToPoll(snapshot("about:blank", 0.0, hasVideo = false, bodyEmpty = false))
        idle()

        assertEquals("Only a positively confirmed completed empty document qualifies", listOf(WATCH_A), retained.loads)
        assertEquals(42.75, player.position, 0.0)
        assertNull(player.error)
    }

    @Test fun genuineSpaHistoryAfterCommitCanSelectBWithoutReloadingTheDocument() {
        openAndReportA(42.75)
        val retained = player.view as RecordingWebView
        // Model the browser's already-updated current URL, not a native loadUrl call.
        shadowOf(retained).pushEntryToHistory(WATCH_B)
        assertEquals(WATCH_B, retained.url)
        client().doUpdateVisitedHistory(retained, WATCH_B, false)
        respondToPoll(snapshot(WATCH_B, 2.5, playing = true))

        assertEquals(VIDEO_B, player.source)
        assertEquals(2.5, player.position, 0.0)
        assertFalse(player.loading)
        assertNull(player.error)
        assertEquals("A same-document navigation must not need a replacement WebView/load", listOf(WATCH_A), retained.loads)
    }

    @Test fun legitimateMainFrameVideoNavigationStillReachesTheNewDocument() {
        openAndReportA(42.75)
        val retained = player.view as RecordingWebView
        assertTrue("Route a new main-frame video through the existing controller's selection path",
            client().shouldOverrideUrlLoading(retained, request(WATCH_B)))
        assertEquals(listOf(WATCH_A, WATCH_B), retained.loads)
        // Chromium commits the new document requested by that selection.
        client().onPageStarted(retained, WATCH_B, null)
        shadowOf(retained).pushEntryToHistory(WATCH_B)
        client().onPageCommitVisible(retained, WATCH_B)
        client().onPageFinished(retained, WATCH_B)
        respondToPoll(snapshot(WATCH_B, 2.5, playing = true))

        assertSame(retained, player.view)
        assertEquals(VIDEO_B, player.source)
        assertEquals(2.5, player.position, 0.0)
        assertFalse(player.loading)
        assertNull(player.error)
        assertEquals("A new selection must request exactly one document load", listOf(WATCH_A, WATCH_B), retained.loads)
    }

    @Test fun restoredSelectionUsesANewControllerAndIgnoresOldControllerCallbacks() {
        player.open(VIDEO_A)
        finishCurrent(WATCH_A)
        player.poll()
        shadowOf(player.view).lastEvaluatedJavascriptCallback.onReceiveValue(snapshot(WATCH_A, 65.75))
        val savedSource = requireNotNull(player.source)
        val savedPosition = player.position
        val oldController = player
        val oldView = player.view
        val oldClient = client()
        oldController.destroy()

        // Mirrors MainActivity's saved source/position handoff to its new controller. Running
        // Activity.onCreate itself would start Gecko and therefore is outside this JVM test.
        activity = Robolectric.buildActivity(MainActivity::class.java).get()
        player = PlayerController(activity) { RecordingWebView(it) }
        activity.player = player
        player.restore(savedSource, savedPosition)
        val restored = player.view
        val restoredUrl = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=65"
        oldClient.onPageStarted(oldView, WATCH_B, null)
        oldClient.onPageFinished(oldView, WATCH_B)
        oldClient.onReceivedError(oldView, request(WATCH_B), connectionError())

        assertNotSame(oldController, player)
        assertNotSame(oldView, restored)
        assertTrue(shadowOf(oldView).wasDestroyCalled())
        assertEquals(VIDEO_A, player.source)
        assertEquals(65.75, player.position, 0.0)
        assertEquals(restoredUrl, shadowOf(restored).lastLoadedUrl)
        assertTrue(player.loading)
        assertFalse(player.expanded)
        assertNull(player.error)
        finishCurrent(restoredUrl)
        assertFalse(player.loading)
        assertFalse(player.playing)
        assertFalse("Activity restoration must not become an explicit Play request", requestsStart())
    }

    private fun closeAAndSelectB(): WebView {
        player.open(VIDEO_A)
        finishCurrent(WATCH_A)
        val retained = player.view
        player.close()
        assertEquals("about:blank", shadowOf(retained).lastLoadedUrl)
        player.open(VIDEO_B)
        assertPendingB(retained)
        return retained
    }

    private fun assertPendingB(retained: WebView) {
        assertSame("Document changes should retain the existing renderer", retained, player.view)
        assertEquals("Only the selected document owns the current video", VIDEO_B, player.source)
        assertEquals(WATCH_B, shadowOf(retained).lastLoadedUrl)
        assertTrue("An unrelated callback must not finish B's pending load", player.loading)
        assertNull("An unrelated callback must not report B as failed", player.error)
    }

    private fun client(): WebViewClient = shadowOf(player.view).webViewClient
    private fun idle() = shadowOf(Looper.getMainLooper()).idle()
    private fun openAndReportA(seconds: Double, playing: Boolean = false) {
        player.open(VIDEO_A)
        finishCurrent(WATCH_A)
        respondToPoll(snapshot(WATCH_A, seconds, playing))
    }
    private fun respondToPoll(value: String) {
        player.poll()
        val callback = shadowOf(player.view).lastEvaluatedJavascriptCallback
        assertNotNull("A committed responsive document should be observable", callback)
        callback.onReceiveValue(value)
    }
    private fun deliverBlankSnapshot() {
        shadowOf(player.view).pushEntryToHistory("about:blank")
        respondToPoll(snapshot("about:blank", 0.0, hasVideo = false, bodyEmpty = true))
    }
    private fun finishCurrent(url: String) {
        client().onPageStarted(player.view, url, null)
        client().onPageCommitVisible(player.view, url)
        client().onPageFinished(player.view, url)
    }
    private fun finishDirectDocument() {
        client().onPageStarted(player.view, DIRECT_BASE, null)
        client().onPageCommitVisible(player.view, DIRECT_COMMIT)
        client().onPageFinished(player.view, DIRECT_BASE)
    }
    private fun requestsStart() = shadowOf(player.view).lastEvaluatedJavascript
        .orEmpty().contains("window.weiktubeSetPlayback(true,true)")

    private fun snapshot(url: String, seconds: Double, playing: Boolean = false,
                         hasVideo: Boolean = true, bodyEmpty: Boolean = false,
                         readyState: String = "complete"): String = JSONObject.quote(JSONObject()
        .put("playing", playing).put("position", seconds).put("duration", if (hasVideo) 120.0 else 0.0)
        .put("width", if (hasVideo) 1920 else 0).put("height", if (hasVideo) 1080 else 0)
        .put("title", "").put("url", url).put("hasVideo", hasVideo)
        .put("bodyEmpty", bodyEmpty).put("readyState", readyState).toString())

    /** Count native loads, while all navigation behavior remains Robolectric's normal WebView. */
    private class RecordingWebView(context: Context) : WebView(context) {
        val loads = mutableListOf<String>()
        var directLoads = 0
            private set
        override fun loadUrl(url: String) {
            loads += url
            super.loadUrl(url)
        }
        override fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
            directLoads++
            super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
        }
    }
    // The SDK stub hides this constructor; Robolectric's real framework exposes it publicly.
    @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
    private fun connectionError() = object : WebResourceError() {
        override fun getErrorCode() = WebViewClient.ERROR_CONNECT
        override fun getDescription(): CharSequence = "Fixture connection failure"
    }
    private fun unavailable() = WebResourceResponse("text/plain", "UTF-8", 503, "Unavailable", emptyMap(), null)
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
        const val RECOVERED_A = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42"
        const val DIRECT_VIDEO = "https://media.example.test/video.mp4"
        const val DIRECT_BASE = "https://com.weiktube.app/"
        const val DIRECT_COMMIT = "data:text/html;charset=utf-8;base64,"
    }
}
