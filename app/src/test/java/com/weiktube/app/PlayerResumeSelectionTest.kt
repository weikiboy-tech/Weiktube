package com.weiktube.app

import com.weiktube.filter.YoutubeAdBlock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerResumeSelectionTest {
    @Test fun observedPositionPersistsAcrossCloseAndIgnoresLoadingAndLateReplies() {
        val old = YoutubeAdBlock.enabled
        YoutubeAdBlock.enabled = false
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val player = PlayerController(activity)
        activity.player = player
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        try {
            activity.model.boolean("saveHistory", true)
            val deadline = System.nanoTime() + 10_000_000_000L
            while (!activity.model.preferencesReady && System.nanoTime() < deadline) {
                shadowOf(android.os.Looper.getMainLooper()).idle(); Thread.sleep(10)
            }
            assertTrue(activity.model.preferencesReady)
            activity.model.progress.clear()
            player.open(url)
            fun commit() { shadowOf(player.view).webViewClient.onPageCommitVisible(player.view, shadowOf(player.view).lastLoadedUrl) }
            fun sample(seconds: Double, ready: Int = 4, frame: Double = -1.0, seeking: Boolean = false) {
                player.poll()
                shadowOf(player.view).lastEvaluatedJavascriptCallback.onReceiveValue(org.json.JSONObject.quote(org.json.JSONObject()
                    .put("url", url).put("hasVideo", true).put("mediaReady", ready).put("position", seconds)
                    .put("resumeFrameTime", frame).put("seeking", seeking).put("duration", 600).put("playing", true).toString()))
            }
            commit(); sample(123.0)
            assertEquals(123.0, activity.model.progress.get(url)!!.seconds, 0.0)
            player.poll()
            val late = shadowOf(player.view).lastEvaluatedJavascriptCallback
            player.close(); player.open(url); commit()
            sample(0.0, 0)
            assertEquals(123.0, activity.model.progress.get(url)!!.seconds, 0.0)
            late.onReceiveValue(org.json.JSONObject.quote(org.json.JSONObject().put("url", url).put("hasVideo", true)
                .put("mediaReady", 4).put("position", 500).put("duration", 600).toString()))
            assertEquals(123.0, activity.model.progress.get(url)!!.seconds, 0.0)
            assertEquals(0.0, player.resumedAt, 0.0)
            sample(123.0, frame = 123.0, seeking = true)
            sample(123.0)
            sample(123.0, frame = 123.0)
            assertEquals(0.0, player.resumedAt, 0.0)
            sample(124.0, frame = 123.2)
            assertEquals(123.0, player.resumedAt, 0.0)
            shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(6))
            assertEquals(0.0, player.resumedAt, 0.0)
            sample(130.0)
            assertEquals(130.0, activity.model.progress.get(url)!!.seconds, 0.0)
            activity.model.clearHistory()
            sample(140.0)
            player.close()
            assertNull(activity.model.progress.get(url))
        } finally { player.destroy(); YoutubeAdBlock.enabled = old }
    }
    @Test fun reopeningUsesSavedPositionButExplicitStartAndRestartWin() {
        val old = YoutubeAdBlock.enabled
        YoutubeAdBlock.enabled = false
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val player = PlayerController(activity)
        activity.player = player
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        try {
            activity.model.progress.enabled = true
            activity.model.progress.save(url, 123.5, 600.0)
            player.open(url)
            assertEquals(123.5, player.position, 0.0)
            assertTrue(shadowOf(player.view).lastLoadedUrl.endsWith("&t=123"))
            player.close()
            player.open("$url&t=0")
            assertEquals(0.0, player.position, 0.0)
            player.close()
            player.open(url)
            assertEquals(123.5, player.position, 0.0)
            val web = player.view
            val shadow = shadowOf(web)
            val loaded = shadow.lastLoadedUrl
            shadow.webViewClient.onPageCommitVisible(web, loaded)
            player.restartFromBeginning()
            shadow.lastEvaluatedJavascriptCallback.onReceiveValue("false")
            assertNotNull(activity.model.progress.get(url))
            player.restartFromBeginning()
            assertTrue(shadow.lastEvaluatedJavascript.contains("v.currentTime=0"))
            shadow.lastEvaluatedJavascriptCallback.onReceiveValue("true")
            assertSame(web, player.view)
            assertEquals(loaded, shadow.lastLoadedUrl)
            assertEquals(0.0, player.position, 0.0)
            assertNull(activity.model.progress.get(url))
            player.close(); player.open(url)
            assertEquals(0.0, player.position, 0.0)
        } finally { player.destroy(); YoutubeAdBlock.enabled = old }
    }

    @Test fun explicitActivityRecoveryPositionWinsOverHistory() {
        val old = YoutubeAdBlock.enabled
        YoutubeAdBlock.enabled = false
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val player = PlayerController(activity)
        activity.player = player
        val url = "https://www.youtube.com/watch?v=abcdefghijk"
        try {
            activity.model.progress.enabled = true
            activity.model.progress.save(url, 100.0, 600.0)
            player.restore(url, 150.0)
            assertEquals(150.0, player.position, 0.0)
            assertFalse(player.playing)
            assertFalse(player.expanded)
        } finally { player.destroy(); YoutubeAdBlock.enabled = old }
    }
}
