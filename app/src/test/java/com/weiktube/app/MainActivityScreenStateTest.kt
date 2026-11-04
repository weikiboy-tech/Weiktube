package com.weiktube.app

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.os.PowerManager
import android.webkit.WebView
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

/**
 * Exercises MainActivity's real focus callback against current PowerManager/Keyguard state.
 * Only PlayerController and (for broadcast cases) NativePipController are initialized: no
 * Activity.onCreate, Compose, Gecko, accounts, network or media decoding. A stale cached flag
 * is injected deliberately in the focus fixtures; this
 * proves the missing reconciliation, not the order of broadcasts on a particular handset.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityScreenStateTest {
    private lateinit var activity: MainActivity
    private lateinit var player: PlayerController
    private lateinit var web: RecordingWebView
    private lateinit var power: PowerManager
    private lateinit var keyguard: KeyguardManager
    private var screenController: NativePipController? = null
    private val receivedSignals = mutableListOf<PlayerDiagnostics.ScreenSignal>()
    private var filterWasEnabled = true

    @Before fun attachOnlyThePlayer() {
        filterWasEnabled = YoutubeAdBlock.enabled
        YoutubeAdBlock.enabled = false
        activity = Robolectric.buildActivity(MainActivity::class.java).get()
        power = activity.getSystemService(PowerManager::class.java)
        keyguard = activity.getSystemService(KeyguardManager::class.java)
        actualScreen(interactive = true, locked = false)
        cachedFlag("foreground", true)
        cachedFlag("hostResumed", true)
        player = PlayerController(activity) { RecordingWebView(it).also { candidate -> web = candidate } }
        activity.player = player
        player.open(VIDEO, userInitiated = false)
        shadowOf(web).webViewClient.apply {
            onPageStarted(web, WATCH, null)
            onPageCommitVisible(web, WATCH)
            onPageFinished(web, WATCH)
        }
        reportPlayback(playing = false)
    }

    @After fun releaseOnlyThePlayer() {
        screenController?.close()
        if (::player.isInitialized) player.destroy()
        YoutubeAdBlock.enabled = filterWasEnabled
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun focusAfterUnlockReconcilesStaleScreenOffAndResumesTheSameWebView() {
        pauseWithStaleScreenOff()
        val resumes = web.resumes
        assertFalse(activity.playerMayRun)

        activity.onWindowFocusChanged(true)

        assertTrue("Current interactive/unlocked state must supersede cached SCREEN_OFF", activity.playerMayRun)
        assertEquals("Resume the retained paused view immediately; no Compose tree is needed", resumes + 1, web.resumes)
        assertSame(web, player.view)
        assertEquals(listOf(WATCH), web.loads)
        assertEquals(65.75, player.position, 0.0)
        assertFalse("Focus is not an explicit Play command", player.playing)
        assertFalse(requestsStart())
    }

    @Test fun focusWhileKeyguardStillShowsMustNotResumePlayback() {
        pauseWithStaleScreenOff()
        actualScreen(interactive = true, locked = true)
        val resumes = web.resumes

        activity.onWindowFocusChanged(true)

        assertFalse(activity.playerMayRun)
        assertEquals(resumes, web.resumes)
        assertEquals(listOf(WATCH), web.loads)
        assertFalse(player.playing)
    }

    @Test fun aDifferentVideoAfterUnlockCanUseTheResumedPlayerAndRequestPlayback() {
        pauseWithStaleScreenOff()
        activity.onWindowFocusChanged(true)
        val retained = player.view
        player.close()
        val next = "https://www.youtube.com/watch?v=9bZkp7q19f0"
        val nextWatch = "https://m.youtube.com/watch?v=9bZkp7q19f0&t=0"
        player.open(next)
        shadowOf(web).webViewClient.apply {
            onPageStarted(web, nextWatch, null)
            onPageCommitVisible(web, nextWatch)
            onPageFinished(web, nextWatch)
        }

        assertTrue(activity.playerMayRun)
        assertSame(retained, player.view)
        assertEquals(next, player.source)
        assertTrue(player.pipReady)
        assertTrue("A new explicit selection must no longer be denied by stale screen state", requestsStart())
        assertEquals(listOf(WATCH, "about:blank", nextWatch), web.loads)
        assertNull(player.error)
    }

    @Test fun focusWhileScreenIsNoninteractiveMustNotResumePlayback() {
        pauseWithStaleScreenOff()
        actualScreen(interactive = false, locked = false)
        val resumes = web.resumes

        activity.onWindowFocusChanged(true)

        assertFalse(activity.playerMayRun)
        assertEquals(resumes, web.resumes)
        assertEquals(listOf(WATCH), web.loads)
        assertFalse(player.playing)
    }

    @Test fun focusCallbackCannotResumeAStoppedActivityEvenWhenDeviceIsUnlocked() {
        pauseWithStaleScreenOff()
        cachedFlag("foreground", false)
        cachedFlag("hostResumed", false)
        val resumes = web.resumes

        activity.onWindowFocusChanged(true)

        assertFalse(activity.playerMayRun)
        assertEquals(resumes, web.resumes)
        assertEquals(listOf(WATCH), web.loads)
        assertFalse(player.playing)
    }

    @Test fun losingFocusInVisiblePipDoesNotPauseTheRetainedPlayer() {
        activity.pip = true
        cachedFlag("hostResumed", false)
        reportPlayback(playing = true)
        val pauses = web.pauses
        val resumes = web.resumes

        activity.onWindowFocusChanged(false)

        assertTrue(activity.playerMayRun)
        assertTrue("PiP may remain visible and playing without window focus", player.playing)
        assertEquals(pauses, web.pauses)
        assertEquals(resumes, web.resumes)
        assertEquals(listOf(WATCH), web.loads)
    }

    @Test fun tenFocusReturnCyclesRecoverTheGateWithoutReloadingOrStartingPlayback() {
        val original = player.view
        val initialResumes = web.resumes
        repeat(10) { cycle ->
            pauseWithStaleScreenOff()
            activity.onWindowFocusChanged(false)
            assertFalse(activity.playerMayRun)
            activity.onWindowFocusChanged(true)

            assertTrue("Cycle ${cycle + 1} must reconcile the device's actual state", activity.playerMayRun)
            assertSame(original, player.view)
            assertEquals(initialResumes + cycle + 1, web.resumes)
            assertEquals(listOf(WATCH), web.loads)
            assertEquals(65.75, player.position, 0.0)
            assertFalse(player.playing)
            assertFalse(requestsStart())
        }
    }

    @Test fun repeatedHealthyFocusDoesNotDuplicateWebViewResumeOrPause() {
        reportPlayback(playing = true)
        val resumes = web.resumes
        val pauses = web.pauses
        repeat(10) { activity.onWindowFocusChanged(true) }

        assertTrue(activity.playerMayRun)
        assertTrue(player.playing)
        assertEquals(resumes, web.resumes)
        assertEquals(pauses, web.pauses)
        assertEquals(listOf(WATCH), web.loads)
    }

    @Test fun delayedScreenOffBroadcastAfterUnlockDoesNotPauseTheCurrentPlayer() {
        attachScreenReceiver()
        reportPlayback(playing = true)
        val pauses = web.pauses
        val resumes = web.resumes
        actualScreen(interactive = true, locked = false)

        broadcast(Intent.ACTION_SCREEN_OFF)

        assertEquals(listOf(PlayerDiagnostics.ScreenSignal.SCREEN_OFF), receivedSignals)
        assertTrue("The old broadcast name cannot override current device state", activity.playerMayRun)
        assertTrue(player.playing)
        assertEquals(pauses, web.pauses)
        assertEquals(resumes, web.resumes)
        assertEquals(listOf(WATCH), web.loads)
    }

    @Test fun screenOnWithoutUserPresentResumesAnUnlockedRetainedView() {
        attachScreenReceiver()
        pauseWithStaleScreenOff()
        val resumes = web.resumes

        broadcast(Intent.ACTION_SCREEN_ON)

        assertEquals(listOf(PlayerDiagnostics.ScreenSignal.SCREEN_ON), receivedSignals)
        assertTrue(activity.playerMayRun)
        assertEquals(resumes + 1, web.resumes)
        assertSame(web, player.view)
        assertEquals(listOf(WATCH), web.loads)
        assertFalse(player.playing)
        assertFalse(requestsStart())
    }

    @Test fun screenOnBroadcastWhileKeyguardShowsKeepsThePlayerPaused() {
        attachScreenReceiver()
        pauseWithStaleScreenOff()
        actualScreen(interactive = true, locked = true)
        val resumes = web.resumes

        broadcast(Intent.ACTION_SCREEN_ON)

        assertEquals(listOf(PlayerDiagnostics.ScreenSignal.SCREEN_ON), receivedSignals)
        assertFalse(activity.playerMayRun)
        assertEquals(resumes, web.resumes)
        assertFalse(player.playing)
        assertEquals(listOf(WATCH), web.loads)
    }

    @Test fun userPresentBroadcastCannotResumeANoninteractiveDevice() {
        attachScreenReceiver()
        pauseWithStaleScreenOff()
        actualScreen(interactive = false, locked = false)
        val resumes = web.resumes

        broadcast(Intent.ACTION_USER_PRESENT)

        assertEquals(listOf(PlayerDiagnostics.ScreenSignal.USER_PRESENT), receivedSignals)
        assertFalse(activity.playerMayRun)
        assertEquals(resumes, web.resumes)
        assertFalse(player.playing)
        assertEquals(listOf(WATCH), web.loads)
    }

    @Test fun tenMixedScreenBroadcastCyclesKeepOneViewAndPreserveThePausedPosition() {
        attachScreenReceiver()
        val original = player.view
        val initialResumes = web.resumes
        repeat(10) { cycle ->
            actualScreen(interactive = false, locked = true)
            broadcast(Intent.ACTION_SCREEN_OFF)
            assertFalse(activity.playerMayRun)
            actualScreen(interactive = true, locked = true)
            broadcast(Intent.ACTION_SCREEN_ON)
            assertFalse(activity.playerMayRun)
            assertEquals(initialResumes + cycle, web.resumes)

            actualScreen(interactive = true, locked = false)
            broadcast(if (cycle % 2 == 0) Intent.ACTION_USER_PRESENT else Intent.ACTION_SCREEN_ON)
            assertTrue(activity.playerMayRun)
            assertEquals(initialResumes + cycle + 1, web.resumes)
            val pauses = web.pauses
            // A deferred old OFF must not leave the already-unlocked screen blocked again.
            broadcast(Intent.ACTION_SCREEN_OFF)
            assertTrue(activity.playerMayRun)
            assertEquals(pauses, web.pauses)
            assertSame(original, player.view)
            assertEquals(listOf(WATCH), web.loads)
            assertEquals(65.75, player.position, 0.0)
            assertFalse(player.playing)
            assertFalse(requestsStart())
        }
        assertEquals("Every broadcast must pass through the real registered receiver", 40, receivedSignals.size)
    }

    @Test fun userPauseSurvivesAllScreenNotificationsUntilAnotherExplicitPlay() {
        attachScreenReceiver()
        reportPlayback(playing = true)
        player.pause()
        actualScreen(interactive = false, locked = true)
        broadcast(Intent.ACTION_SCREEN_OFF)
        actualScreen(interactive = true, locked = false)
        broadcast(Intent.ACTION_USER_PRESENT)
        broadcast(Intent.ACTION_SCREEN_ON)
        broadcast(Intent.ACTION_SCREEN_OFF)

        assertTrue(activity.playerMayRun)
        assertFalse(player.playing)
        assertFalse(requestsStart())
        assertEquals(listOf(WATCH), web.loads)
        player.play()
        assertTrue("Only the later explicit Play command may request playback", requestsStart())
    }

    @Test fun screenOnBroadcastCannotResumeAnActivityThatHasStopped() {
        attachScreenReceiver()
        pauseWithStaleScreenOff()
        cachedFlag("foreground", false)
        cachedFlag("hostResumed", false)
        val resumes = web.resumes

        broadcast(Intent.ACTION_SCREEN_ON)

        assertEquals(listOf(PlayerDiagnostics.ScreenSignal.SCREEN_ON), receivedSignals)
        assertFalse(activity.playerMayRun)
        assertEquals(resumes, web.resumes)
        assertFalse(player.playing)
        assertEquals(listOf(WATCH), web.loads)
    }

    private fun attachScreenReceiver() {
        check(screenController == null)
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        fun reconcile(signal: PlayerDiagnostics.ScreenSignal) {
            receivedSignals += signal
            activity.reconcileScreenState(signal)
        }
        // Same callbacks as onCreate, without starting its Gecko, account or Compose work.
        // The production receiver actually receives Android broadcasts, then invokes the real
        // Activity reconciliation and retained PlayerController; no fake lifecycle policy.
        screenController = NativePipController(activity,
            snapshot = { ReflectionHelpers.callInstanceMethod(activity, "pipSnapshot") },
            play = player::play, pause = player::pause,
            screenOff = { reconcile(PlayerDiagnostics.ScreenSignal.SCREEN_OFF) }, message = {},
            screenUnlocked = { reconcile(PlayerDiagnostics.ScreenSignal.USER_PRESENT) },
            screenOn = { reconcile(PlayerDiagnostics.ScreenSignal.SCREEN_ON) })
        ReflectionHelpers.setField(activity, "nativePip", screenController)
    }

    private fun broadcast(action: String) {
        activity.sendBroadcast(Intent(action).setPackage(activity.packageName))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun actualScreen(interactive: Boolean, locked: Boolean) {
        shadowOf(power).setIsInteractive(interactive)
        shadowOf(keyguard).setKeyguardLocked(locked)
    }

    private fun pauseWithStaleScreenOff() {
        // MainActivity caches this flag in its SCREEN_OFF callback. The actual device is now
        // unlocked/interactive, e.g. after an unlock that did not trigger another onResume.
        actualScreen(interactive = true, locked = false)
        cachedFlag("screenOff", true)
        player.pauseView(PlayerDiagnostics.PauseReason.SCREEN_LOCK)
    }

    private fun cachedFlag(name: String, value: Boolean) {
        ReflectionHelpers.getField<MutableState<Boolean>>(activity, name + "\$delegate").value = value
    }

    private fun reportPlayback(playing: Boolean) {
        player.poll()
        val callback = shadowOf(web).lastEvaluatedJavascriptCallback
        assertNotNull(callback)
        callback.onReceiveValue(JSONObject.quote(JSONObject()
            .put("url", WATCH).put("title", "").put("playing", playing)
            .put("position", 65.75).put("duration", 120.0).put("width", 1920).put("height", 1080)
            .put("hasVideo", true).put("readyState", "complete").put("bodyEmpty", false).toString()))
    }

    private fun requestsStart() = shadowOf(web).lastEvaluatedJavascript
        .orEmpty().contains("window.weiktubeSetPlayback(true,true)")

    private class RecordingWebView(context: Context) : WebView(context) {
        val loads = mutableListOf<String>()
        var resumes = 0
        var pauses = 0
        override fun loadUrl(url: String) { loads += url; super.loadUrl(url) }
        override fun onResume() { resumes++; super.onResume() }
        override fun onPause() { pauses++; super.onPause() }
    }

    private companion object {
        const val VIDEO = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        const val WATCH = "https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=0"
    }
}
