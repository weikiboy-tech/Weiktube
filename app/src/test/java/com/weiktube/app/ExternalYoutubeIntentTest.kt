package com.weiktube.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.weiktube.filter.YoutubeAdBlock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class ExternalYoutubeIntentTest {
    private fun view(url:String)=Intent(Intent.ACTION_VIEW,Uri.parse(url))
    @Test fun allSupportedHostsAndVideoFormsRouteToExistingVideoEntry() {
        for (raw in listOf("https://youtube.com/watch?v=abcdefghijk", "https://www.youtube.com/watch?v=abcdefghijk", "https://m.youtube.com/live/abcdefghijk", "https://youtu.be/abcdefghijk?si=extra", "https://m.youtube.com/shorts/abcdefghijk?feature=share")) {
            val parsed=ExternalYoutubeIntent.url(view(raw));assertNotNull(raw,parsed)
            var video:String?=null;var page:String?=null
            ExternalYoutubeIntent.route(parsed!!,{video=it},{page=it})
            assertNotNull(video);assertNull(page)
        }
    }
    @Test fun timestampsIncludingExplicitZeroAndPlaylistContextSurvive() {
        assertEquals("https://m.youtube.com/watch?v=abcdefghijk&t=83&list=PL_123&index=2",ExternalYoutubeIntent.url(view("https://youtu.be/abcdefghijk?t=1m23s&list=PL_123&index=2&si=tracking")))
        assertEquals("https://m.youtube.com/watch?v=abcdefghijk&t=0",ExternalYoutubeIntent.url(view("https://youtu.be/abcdefghijk?t=0")))
        assertEquals(90,Links.startSeconds(ExternalYoutubeIntent.url(view("https://youtube.com/watch?v=abcdefghijk#t=1m30s"))!!))
    }
    @Test fun pagesStayInExistingPageEntryAndRedirectWrappersAreRejected() {
        for(raw in listOf("https://youtube.com/playlist?list=PL123&si=x", "https://www.youtube.com/@somebody", "https://m.youtube.com/channel/UC123/videos", "https://youtube.com/user/person", "https://youtube.com/c/person/playlists", "https://youtube.com/@person/live", "https://youtube.com/results?search_query=cats%20dogs&si=track")) {
            val parsed=ExternalYoutubeIntent.url(view(raw));assertNotNull(parsed)
            var page:String?=null
            ExternalYoutubeIntent.route(parsed!!,{fail("Not a video")},{page=it});assertNotNull(page)
        }
        assertNull(ExternalYoutubeIntent.url(view("https://youtube.com/redirect?q=https://evil.test")))
        assertNull(ExternalYoutubeIntent.url(view("https://youtube.com/attribution_link?u=%2Fwatch%3Fv%3Dabcdefghijk")))
    }
    @Test fun rejectsSpoofedSchemesAuthoritiesMalformedIdsAndEncodedPathTricks() {
        val unsafe=listOf("javascript:https://youtube.com/watch?v=abcdefghijk","http://youtube.com/watch?v=abcdefghijk","https://youtube.com.evil.org/watch?v=abcdefghijk","https://youtube.com@evil.org/watch?v=abcdefghijk","https://evil@youtube.com/watch?v=abcdefghijk","https://youtube.com:80/watch?v=abcdefghijk","https://youtube.com:8443/watch?v=abcdefghijk","https://youtube.com./watch?v=abcdefghijk","https://youtu.be/abcdefghijk/extra","https://youtube.com/watch?v=bad","https://youtube.com/%2f%2fevil.org","https://youtube.com/@name/../redirect","https://youtube.com/@name%0a","intent://youtube.com/watch?v=abcdefghijk")
        unsafe.forEach{assertNull(it,ExternalYoutubeIntent.url(view(it)))}
    }
    @Test fun onlyViewAndPlainTextShareAreAccepted() {
        val raw="https://youtu.be/abcdefghijk"
        assertNull(ExternalYoutubeIntent.url(Intent("unknown",Uri.parse(raw))))
        assertNull(ExternalYoutubeIntent.url(Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT,raw)))
        assertNull(ExternalYoutubeIntent.url(Intent(Intent.ACTION_SEND).setType("text/html").putExtra(Intent.EXTRA_TEXT,raw)))
        assertEquals("https://m.youtube.com/watch?v=abcdefghijk",ExternalYoutubeIntent.url(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Video: ($raw).")))
        assertNull(ExternalYoutubeIntent.url(view("https://evil.org/?next=$raw")))
        assertNull(ExternalYoutubeIntent.url(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"x".repeat(17000)+raw)))
    }
    @Test fun savedLaunchDoesNotReplayButDifferentLinkOverridesRestoration() {
        val a=view("https://youtu.be/abcdefghijk");val b=view("https://youtu.be/12345678901")
        assertTrue(ExternalYoutubeIntent.isFreshLaunch(a,null))
        assertFalse(ExternalYoutubeIntent.isFreshLaunch(a,ExternalYoutubeIntent.url(a)))
        assertTrue(ExternalYoutubeIntent.isFreshLaunch(b,ExternalYoutubeIntent.url(a)))
        assertFalse(ExternalYoutubeIntent.isFreshLaunch(Intent(Intent.ACTION_MAIN),ExternalYoutubeIntent.url(a)))
    }
    @Config(sdk = [28]) // The JVM WebView lacks API-33 algorithmic-darkening plumbing.
    @Test fun runningActivityReusesPlayerAndShortsViewsAndLastDeliveredIntentWins() {
        val enabled=YoutubeAdBlock.enabled;YoutubeAdBlock.enabled=false
        val a=Robolectric.buildActivity(MainActivity::class.java).get()
        a.player=PlayerController(a);a.browser=BrowserController(a,a.shield,a::openVideo)
        try {
            val playerWeb=a.player.view
            fun deliver(url:String) { ReflectionHelpers.callInstanceMethod<Unit>(a,"onNewIntent",ReflectionHelpers.ClassParameter.from(Intent::class.java,view(url))) }
            ReflectionHelpers.setField(a,"pendingOriginalHomeVideo","https://youtu.be/12345678901")
            deliver("https://youtu.be/abcdefghijk?t=10")
            assertEquals("abcdefghijk",Links.videoId(a.player.source!!));assertSame(playerWeb,a.player.view)
            assertTrue(shadowOf(playerWeb).lastLoadedUrl!!.contains("t=10"))
            assertNull(ReflectionHelpers.getField<String?>(a,"pendingOriginalHomeVideo"))
            assertTrue(a.back());assertFalse(a.player.expanded);assertSame(playerWeb,a.player.view)
            repeat(10){deliver("https://youtu.be/12345678901");deliver("https://youtu.be/abcdefghijk")}
            assertEquals("abcdefghijk",Links.videoId(a.player.source!!));assertSame(playerWeb,a.player.view)
            assertEquals("https://youtu.be/abcdefghijk",a.intent.dataString)
            deliver("https://youtube.com/shorts/12345678901");assertEquals(1,a.tab)
            val shortsWeb=a.shorts!!.view
            deliver("https://youtube.com/shorts/abcdefghijk");assertSame(shortsWeb,a.shorts!!.view)
            assertTrue(a.shorts!!.currentUrl.contains("abcdefghijk"));assertSame(playerWeb,a.player.view)
            deliver("https://evil.org/watch?v=12345678901");assertSame(playerWeb,a.player.view)
        } finally { a.shorts?.destroy();a.browser.destroy();a.player.destroy();YoutubeAdBlock.enabled=enabled }
    }
    @Test fun settingsChooseSupportedApiAndFallbackWithoutChangingOtherApps() {
        for(sdk in listOf(26,28,30,31,35,36)) {
            val choices=YoutubeLinkSettings.intents("com.weiktube.app",sdk)
            assertEquals(if(sdk>=31)Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS else Settings.ACTION_APPLICATION_DETAILS_SETTINGS,choices.first().action)
            assertEquals("package:com.weiktube.app",choices.first().dataString)
        }
        val tried=mutableListOf<String?>()
        assertTrue(YoutubeLinkSettings.launch(YoutubeLinkSettings.intents("com.weiktube.app",36)){tried+=it.action;if(tried.size==1)throw ActivityNotFoundException()})
        assertEquals(2,tried.size)
        assertFalse(YoutubeLinkSettings.launch(YoutubeLinkSettings.intents("com.weiktube.app",26)){throw SecurityException()})
    }
}
