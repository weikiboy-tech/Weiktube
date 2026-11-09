package com.weiktube.app

import android.content.Context
import android.webkit.ValueCallback
import android.webkit.WebView
import com.weiktube.filter.YoutubeAdBlock
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Controller commands only: no real Chromium, audio output, or Android PiP window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class PlayerModeContinuityTest {
    @Test fun youtubeTransitionsDoNotIssuePauseOrReload() = verify("https://www.youtube.com/watch?v=abcdefghijk")
    @Test fun directMediaTransitionsDoNotIssuePauseOrReload() = verify("https://example.org/test.mp4")

    private fun verify(url: String) {
        val enabled = YoutubeAdBlock.enabled
        YoutubeAdBlock.enabled = false // JVM has no filter document-start runtime.
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val player = PlayerController(activity, ::RecordingView)
        activity.player = player
        try {
            player.open(url)
            val web = player.view as RecordingView
            val document = if (player.isYoutube) shadowOf(web).lastLoadedUrl else "https://com.weiktube.app/"
            shadowOf(web).webViewClient.onPageCommitVisible(web, document)
            shadowOf(web).webViewClient.onPageFinished(web, document)
            player.poll()
            shadowOf(web).lastEvaluatedJavascriptCallback.onReceiveValue(JSONObject.quote(JSONObject()
                .put("url", document).put("hasVideo", true).put("playing", true).put("position", 42.5)
                .put("duration", 100).put("width", 1280).put("height", 720).toString()))
            assertTrue(player.playing)
            val loads = web.loads
            web.scripts.clear()
            repeat(10) {
                player.expanded = false
                player.setPresentation(true, true)
                player.resumeView()
                player.setPlaybackAllowed(true)
                activity.pip = true
                player.setPresentation(false, true, true, true)
                player.setPlaybackAllowed(true)
                activity.pip = false
                player.expanded = true
                player.setPresentation(false, true)
                assertSame(web, player.view)
                assertTrue(player.playing)
                assertEquals(42.5, player.position, 0.0)
                assertEquals(loads, web.loads)
            }
            assertEquals(0, web.pauses)
            assertFalse(web.scripts.any { it.contains("window.weiktubeSetPlayback(false,") || it.contains("window.weiktubeSetPlayback(true,true)") })
            player.pause()
            web.scripts.clear()
            repeat(10) {
                player.setPresentation(true, true)
                player.setPlaybackAllowed(true)
                player.setPresentation(false, true, true, true)
                player.setPlaybackAllowed(true)
                assertFalse(player.playing)
                assertEquals(loads, web.loads)
            }
            assertFalse(web.scripts.any { it.contains("window.weiktubeSetPlayback(true,true)") })
            player.play()
            assertTrue(web.scripts.last().contains("window.weiktubeSetPlayback(true,true)"))
        } finally { player.destroy(); YoutubeAdBlock.enabled = enabled }
    }

    private class RecordingView(context: Context) : WebView(context) {
        var loads = 0
        var pauses = 0
        val scripts = mutableListOf<String>()
        override fun loadUrl(url: String) { loads++; super.loadUrl(url) }
        override fun loadDataWithBaseURL(baseUrl: String?, data: String, mimeType: String?, encoding: String?, historyUrl: String?) {
            loads++; super.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, historyUrl)
        }
        override fun onPause() { pauses++; super.onPause() }
        override fun evaluateJavascript(script: String, resultCallback: ValueCallback<String>?) {
            scripts.add(script); super.evaluateJavascript(script, resultCallback)
        }
    }
}
