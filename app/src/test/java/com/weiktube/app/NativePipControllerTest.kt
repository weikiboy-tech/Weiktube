package com.weiktube.app

import android.app.Activity
import android.app.KeyguardManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.os.Looper
import android.util.Rational
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

/** Real Android params and broadcast controls; does not simulate a device's PiP window manager. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class NativePipControllerTest {
    private lateinit var activityLifecycle: ActivityController<RecordingActivity>
    private lateinit var activity: RecordingActivity
    private lateinit var controller: NativePipController
    private lateinit var state: NativePipController.Snapshot
    private var plays = 0
    private var pauses = 0
    private var screenOffs = 0
    private var screenUnlocks = 0
    private val messages = mutableListOf<String>()

    @Before fun createHost() {
        activityLifecycle = Robolectric.buildActivity(RecordingActivity::class.java).setup()
        activity = activityLifecycle.get()
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        // Mirrors AndroidX's merged manifest permission on pre-33 APIs with Config.NONE.
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${activity.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        state = NativePipController.Snapshot(
            source = "https://m.youtube.com/watch?v=dQw4w9WgXcQ", ready = true, playing = true,
            visible = true, resumed = true, blockedByUi = false,
            width = 1920, height = 1080, bounds = Rect(0, 42, 1080, 650),
        )
        controller = NativePipController(activity, { state },
            play = { plays++; state = state.copy(playing = true) },
            pause = { pauses++; state = state.copy(playing = false) },
            screenOff = { screenOffs++; state = state.copy(playing = false, resumed = false) },
            message = { messages += it },
            screenUnlocked = {
                screenUnlocks++
                state = state.copy(visible = true, resumed = false)
            },
        )
    }

    @After fun releaseHost() {
        if (::controller.isInitialized) controller.close()
        if (::activityLifecycle.isInitialized) activityLifecycle.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [26, 31, 33, 35], manifest = Config.NONE)
    fun androidParamsCarryVideoBoundsAspectAndPrivateImmutableActionAcrossApiLevels() {
        controller.sync()
        val params = activity.latest
        assertEquals(16.0 / 9, ratio(params).toDouble(), 0.0002)
        assertEquals(state.bounds, bounds(params))
        assertNotSame("Bounds must be copied before crossing the Android API", state.bounds, bounds(params))
        val remote = actions(params).single()
        assertEquals("Pausieren", remote.title.toString())
        val pending = shadowOf(remote.actionIntent)
        assertTrue(pending.isBroadcast)
        assertTrue(pending.isImmutable)
        assertTrue(pending.flags and PendingIntent.FLAG_IMMUTABLE != 0)
        assertEquals(activity.packageName, pending.savedIntent.`package`)
        assertEquals("${activity.packageName}.PIP_CONTROL", pending.savedIntent.action)
        assertEquals("pause", pending.savedIntent.getStringExtra("command"))
        if (Build.VERSION.SDK_INT >= 31) assertTrue(autoEnter(params))
        if (Build.VERSION.SDK_INT >= 33) {
            assertEquals("Weiktube", params.title.toString())
            assertNull("Keep Android's own close action", params.closeAction)
        }
    }

    @Test
    @Config(sdk = [26, 31, 33, 35], manifest = Config.NONE)
    fun userLeaveRequestsEntryOnlyOnOlderAndroidAndNeverStartsPlayback() {
        controller.onUserLeaveHint()
        assertEquals(if (Build.VERSION.SDK_INT < 31) 1 else 0, activity.enteredParams.size)
        if (Build.VERSION.SDK_INT >= 31) assertTrue(autoEnter(activity.latest))
        assertEquals(0, plays)
        assertEquals(0, pauses)
    }

    @Test fun unchangedSnapshotDoesNotRepeatedlySendParams() {
        controller.sync()
        repeat(20) { controller.sync() }
        assertEquals(1, activity.updatedParams.size)
        state = state.copy(playing = false)
        controller.sync()
        repeat(20) { controller.sync() }
        assertEquals(2, activity.updatedParams.size)
        assertFalse(autoEnter(activity.latest))
        assertEquals("Abspielen", actions(activity.latest).single().title.toString())
    }

    @Test fun autoEntryRequiresReadyPlayingVisibleResumedUnblockedSource() {
        val playable = state
        val rejected = listOf(
            playable.copy(playing = false), playable.copy(ready = false),
            playable.copy(visible = false), playable.copy(resumed = false),
            playable.copy(blockedByUi = true), playable.copy(source = null),
        )
        for (invalid in rejected) {
            state = playable
            controller.sync()
            assertTrue(autoEnter(activity.latest))
            state = invalid
            controller.sync()
            assertFalse("Must not auto-enter for $invalid", autoEnter(activity.latest))
        }
        state = playable
        controller.sync()
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        controller.sync()
        assertFalse(autoEnter(activity.latest))
        assertEquals(0, plays)
        assertEquals(0, pauses)
    }

    @Test fun manualEntryAcceptsPausedYoutubeVideoAndRejectsDuplicateTransitions() {
        state = state.copy(playing = false)
        repeat(10) { controller.enter() }
        assertEquals(1, activity.enteredParams.size)
        assertFalse(autoEnter(activity.enteredParams.single()))
        assertEquals(0, plays)
        changeMode(true)
        repeat(10) { controller.enter() }
        assertEquals(1, activity.enteredParams.size)
        assertEquals(0, pauses)
    }

    @Test fun manualEntryRejectsMissingUnreadyInvisibleBlockedOrLockedVideo() {
        val valid = state
        listOf(valid.copy(source = null), valid.copy(ready = false), valid.copy(visible = false),
            valid.copy(resumed = false), valid.copy(blockedByUi = true)).forEach {
            state = it
            controller.enter()
        }
        state = valid
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        controller.enter()
        assertTrue(activity.enteredParams.isEmpty())
        assertEquals(6, messages.size)
    }

    @Test fun unavailableFeatureAndPlatformRejectionsDoNotCrashOrTouchPlayback() {
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, false)
        controller.sync()
        controller.enter()
        assertTrue(activity.updatedParams.isEmpty())
        assertTrue(activity.enteredParams.isEmpty())
        shadowOf(activity.packageManager).setSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        activity.enterAccepted = false
        controller.enter()
        activity.enterFailure = IllegalStateException("window unavailable")
        controller.enter()
        activity.enterFailure = IllegalArgumentException("bounds rejected")
        controller.enter()
        activity.enterFailure = null
        activity.enterAccepted = true
        controller.enter()
        assertEquals("Failure must not latch the entering guard", 4, activity.enteredParams.size)
        assertEquals(4, messages.size)
        assertEquals(0, plays)
        assertEquals(0, pauses)
    }

    @Test fun rejectedParameterUpdateCanBeRetriedWithTheSameSnapshot() {
        activity.updateFailure = IllegalArgumentException("OEM rejection")
        controller.sync()
        activity.updateFailure = IllegalStateException("window unavailable")
        controller.sync()
        activity.updateFailure = null
        controller.sync()
        controller.sync()
        assertEquals(3, activity.updatedParams.size)
        assertTrue(autoEnter(activity.latest))
    }

    @Test fun playAndPauseActionsRemainCommandsWhenAnOldButtonIsPressedTwice() {
        controller.sync()
        changeMode(true)
        val pauseAction = actions(activity.latest).single()
        send(pauseAction)
        send(pauseAction)
        assertFalse(state.playing)
        assertEquals(2, pauses)
        assertEquals(0, plays)
        val playAction = actions(activity.latest).single()
        assertEquals("Abspielen", playAction.title.toString())
        send(playAction)
        send(playAction)
        assertTrue(state.playing)
        assertEquals(2, plays)
        assertEquals(2, pauses)
    }

    @Test fun immutableControlCannotBeChangedFromPauseIntoPlayByFillInIntent() {
        controller.sync()
        changeMode(true)
        val pauseAction = actions(activity.latest).single()
        pauseAction.actionIntent.send(activity, 0,
            Intent().putExtra("command", "play").putExtra("token", "forged"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, pauses)
        assertEquals(0, plays)
    }

    @Test fun controlReceiverIsNotExportedAndRejectsUnrecognizedCommands() {
        controller.sync()
        changeMode(true)
        val saved = shadowOf(actions(activity.latest).single().actionIntent).savedIntent
        val registered = shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers
            .single { it.intentFilter.hasAction(saved.action) }
        assertTrue(registered.flags and Context.RECEIVER_NOT_EXPORTED != 0)
        activity.sendBroadcast(Intent(saved).putExtra("command", "toggle"))
        activity.sendBroadcast(Intent(saved).putExtra("token", "invalid"))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, plays)
        assertEquals(0, pauses)
    }

    @Test fun staleControlIsRejectedBeforeAndAfterSwitchingVideosAndReturningToTheFirst() {
        controller.sync()
        changeMode(true)
        val originalSource = state.source
        val originalAction = actions(activity.latest).single()
        state = state.copy(source = "https://m.youtube.com/watch?v=9bZkp7q19f0")
        send(originalAction) // Source changed even before the next synchronization.
        assertEquals(0, pauses)
        controller.sync()
        val secondAction = actions(activity.latest).single()
        send(originalAction)
        assertEquals(0, pauses)
        assertNotEquals(shadowOf(originalAction.actionIntent).savedIntent.data,
            shadowOf(secondAction.actionIntent).savedIntent.data)
        state = state.copy(source = originalSource)
        controller.sync()
        send(originalAction)
        send(secondAction)
        assertEquals(0, pauses)
        send(actions(activity.latest).single())
        assertEquals(1, pauses)
    }

    @Test fun controlsCannotOperateOutsidePipWhileStoppedOrWhenLocked() {
        controller.sync()
        val action = actions(activity.latest).single()
        send(action)
        assertEquals(0, pauses)
        changeMode(true)
        state = state.copy(visible = false, resumed = false)
        controller.stopped()
        send(action)
        assertEquals(0, pauses)
        assertFalse(autoEnter(activity.latest))
        state = state.copy(visible = true)
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        send(action)
        assertEquals(0, pauses)
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        send(action)
        assertEquals("PiP is paused in the Activity lifecycle but remains controllable", 1, pauses)
    }

    @Test fun unreadyPlayerCannotBeStartedFromAnEarlierPlayAction() {
        state = state.copy(playing = false)
        controller.sync()
        changeMode(true)
        val playAction = actions(activity.latest).single()
        state = state.copy(ready = false)
        send(playAction)
        assertEquals(0, plays)
    }

    @Test fun tenExpandRoundTripsDoNotChangePlaybackOrFinishTheActivity() {
        repeat(10) { cycle ->
            val playingBefore = cycle % 2 == 0
            state = state.copy(playing = playingBefore, resumed = true, visible = true)
            controller.resumed()
            controller.enter()
            changeMode(true)
            state = state.copy(resumed = false)
            controller.sync()
            changeMode(false)
            state = state.copy(resumed = true)
            controller.resumed()
            assertEquals(playingBefore, state.playing)
            assertFalse(activity.isFinishing)
        }
        assertEquals(10, activity.enteredParams.size)
        assertEquals(0, plays)
        assertEquals(0, pauses)
        assertEquals(0, screenOffs)
    }

    @Test fun screenOffUsesItsDedicatedCallbackAndCloseUnregistersBothReceivers() {
        controller.sync()
        changeMode(true)
        val action = actions(activity.latest).single()
        activity.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF).setPackage(activity.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, screenOffs)
        assertFalse(state.playing)
        assertEquals(0, plays)
        val countBeforeClose = activity.updatedParams.size
        controller.close()
        controller.close()
        send(action)
        activity.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF).setPackage(activity.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        controller.sync()
        controller.enter()
        assertEquals(1, screenOffs)
        assertEquals(0, pauses)
        assertEquals(countBeforeClose, activity.updatedParams.size)
        assertTrue(shadowOf(RuntimeEnvironment.getApplication()).registeredReceivers.none {
            it.intentFilter.hasAction("${activity.packageName}.PIP_CONTROL")
        })
    }

    @Test fun userPresentReenablesVisiblePipWithoutAutoplayAndItsPlayActionWorks() {
        controller.sync()
        changeMode(true)
        activity.sendBroadcast(Intent(Intent.ACTION_SCREEN_OFF).setPackage(activity.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, screenOffs)
        state = state.copy(visible = false)
        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
        controller.sync()
        val playAction = actions(activity.latest).single()
        send(playAction)
        assertEquals(0, plays)

        shadowOf(activity.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(false)
        activity.sendBroadcast(Intent(Intent.ACTION_USER_PRESENT).setPackage(activity.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, screenUnlocks)
        assertTrue(state.visible)
        assertFalse(state.resumed)
        assertFalse(state.playing)
        assertFalse(autoEnter(activity.latest))
        assertEquals("Unlock must not issue a play command", 0, plays)
        assertEquals(0, pauses)

        send(actions(activity.latest).single())
        assertTrue(state.playing)
        assertEquals(1, plays)
    }

    @Test fun aspectRatioHandlesPortraitMissingAndExtremeVideoDimensions() {
        listOf(1080 to 1920, 0 to 0, 5000 to 1, 1 to 5000).forEach { (width, height) ->
            state = state.copy(width = width, height = height)
            controller.sync()
            val ratio = ratio(activity.latest).toDouble()
            assertTrue(ratio >= 1.0 / 2.39 && ratio <= 2.39)
            when {
                width == 0 -> assertEquals(16.0 / 9, ratio, 0.0002)
                height == 1920 -> assertEquals(9.0 / 16, ratio, 0.0002)
                width == 5000 -> assertEquals(2.39, ratio, 0.0002)
                else -> assertEquals(1.0 / 2.39, ratio, 0.0002)
            }
        }
        state = state.copy(bounds = null)
        controller.sync()
        assertTrue(bounds(activity.latest).isEmpty)
    }

    private fun changeMode(inPip: Boolean) {
        activity.inPip = inPip
        controller.modeChanged(inPip)
    }

    private fun send(action: RemoteAction) {
        action.actionIntent.send()
        shadowOf(Looper.getMainLooper()).idle()
    }

    // Older Android has no public getters; reflection remains confined to the test harness.
    private fun ratio(params: PictureInPictureParams): Rational =
        if (Build.VERSION.SDK_INT >= 33) params.aspectRatio!!
        else ReflectionHelpers.getField(params, "mAspectRatio")

    private fun bounds(params: PictureInPictureParams): Rect =
        if (Build.VERSION.SDK_INT >= 33) params.sourceRectHint!!
        else ReflectionHelpers.getField(params, "mSourceRectHint")

    private fun actions(params: PictureInPictureParams): List<RemoteAction> =
        if (Build.VERSION.SDK_INT >= 33) params.actions
        else ReflectionHelpers.getField(params, "mUserActions")

    private fun autoEnter(params: PictureInPictureParams): Boolean =
        if (Build.VERSION.SDK_INT >= 33) params.isAutoEnterEnabled
        else ReflectionHelpers.getField<Boolean?>(params, "mAutoEnterEnabled") == true

    class RecordingActivity : Activity() {
        val updatedParams = mutableListOf<PictureInPictureParams>()
        val enteredParams = mutableListOf<PictureInPictureParams>()
        val latest get() = updatedParams.last()
        var enterAccepted = true
        var enterFailure: RuntimeException? = null
        var updateFailure: RuntimeException? = null
        var inPip = false

        override fun setPictureInPictureParams(params: PictureInPictureParams) {
            updatedParams += params
            updateFailure?.let { throw it }
        }

        override fun enterPictureInPictureMode(params: PictureInPictureParams): Boolean {
            enteredParams += params
            enterFailure?.let { throw it }
            return enterAccepted
        }

        override fun isInPictureInPictureMode() = inPip
    }
}
