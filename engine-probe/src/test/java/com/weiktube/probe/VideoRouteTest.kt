package com.weiktube.probe

import org.junit.Assert.*
import org.junit.Test

class VideoRouteTest {
    @Test fun publicVideoShapesRouteToSameInternalPlayer() {
        listOf("https://m.youtube.com/watch?v=abcdefghijk", "https://youtu.be/abcdefghijk", "https://www.youtube.com/shorts/abcdefghijk", "https://youtube.com/live/abcdefghijk", "https://youtube.com/embed/abcdefghijk").forEach {
            assertEquals("abcdefghijk", VideoRoute.parse(it)?.id)
        }
    }
    @Test fun nonVideoPagesRemainInFeed() {
        listOf("https://m.youtube.com/", "https://m.youtube.com/feed/library", "https://m.youtube.com/results?search_query=cat").forEach { assertNull(VideoRoute.parse(it)) }
    }
    @Test fun foreignMalformedAndCredentialUrlsCannotRoute() {
        listOf("https://youtube.com.evil.com/watch?v=abcdefghijk", "https://evil.com/abcdefghijk", "http://youtube.com/watch?v=abcdefghijk", "https://user@youtube.com/watch?v=abcdefghijk", "https://youtube.com:8443/watch?v=abcdefghijk", "https://youtube.com/watch?v=short", "javascript:alert(1)", "https://youtube.com/watch?v=%zz").forEach { assertNull(it, VideoRoute.parse(it)) }
    }
    @Test fun preservesOnlyBoundedPublicPlaybackContext() {
        assertEquals(VideoRoute("abcdefghijk", 3723,"PL123"), VideoRoute.parse("https://youtube.com/watch?v=abcdefghijk&t=1h2m3s&list=PL123&tracking=drop"))
        assertEquals(86400, VideoRoute.parse("https://youtube.com/watch?v=abcdefghijk&t=999999999")?.seconds)
        assertEquals(86400, VideoRoute.parse("https://youtube.com/watch?v=abcdefghijk&t=9223372036854775807h")?.seconds)
        assertFalse(VideoRoute.parse("https://youtube.com/watch?v=abcdefghijk&list=%3Cscript%3E")!!.url.contains("list="))
    }
}
