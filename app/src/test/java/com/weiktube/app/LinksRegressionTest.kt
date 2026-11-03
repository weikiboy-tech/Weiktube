package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class LinksRegressionTest {
    @Test fun canonicalizesPublicHttpWithoutAcceptingCredentialsOrPorts() {
        assertEquals("https://m.youtube.com/watch?v=abcdefghijk&t=83", Links.canonicalYoutubeUrl("http://youtu.be/abcdefghijk?t=1m23s"))
        assertNull(Links.canonicalYoutubeUrl("https://password@youtube.com/watch?v=abcdefghijk"))
        assertNull(Links.canonicalYoutubeUrl("https://youtube.com:8443/watch?v=abcdefghijk"))
        assertNull(Links.videoId("https://youtu.be/abcdefghijk/extra"))
    }
    @Test fun keepsPublicPlaylistContextWithoutCopyingTrackingParameters() {
        assertEquals("https://m.youtube.com/watch?v=abcdefghijk&t=42&list=PL_123&index=2",
            Links.canonicalYoutubeUrl("https://www.youtube.com/watch?v=abcdefghijk&si=tracking&list=PL_123&index=2&t=42"))
    }
    @Test fun separatesWatchLinksFromShortsAndAccountPages() {
        assertNotNull(Links.browseWatchUrl("https://www.youtube.com/live/abcdefghijk"))
        assertNotNull(Links.browseWatchUrl("https://m.youtube.com/watch?v=abcdefghijk"))
        assertNull(Links.browseWatchUrl("https://m.youtube.com/shorts/abcdefghijk"))
        assertNull(Links.browseWatchUrl("https://m.youtube.com/feed/you"))
        assertNull(Links.browseWatchUrl("https://youtube.com.evil.org/watch?v=abcdefghijk"))
    }
    @Test fun boundsTimestampAndRejectsPartialOrNegativeGrammar() {
        assertEquals(86400, Links.startSeconds("https://youtu.be/abcdefghijk?t=999999999999999999999999"))
        assertEquals(86400, Links.startSeconds("https://youtu.be/abcdefghijk?t=86400h86400m86400s"))
        listOf("-1m", "1mgarbage", "1m2m", "junk1h", "").forEach {
            assertEquals(it, 0, Links.startSeconds("https://youtu.be/abcdefghijk?t=$it"))
        }
        assertEquals(90, Links.startSeconds("https://youtu.be/abcdefghijk#t=1m30s"))
    }
    @Test fun historyDeduplicatesAliasesAndStartPositionsButKeepsDirectSources() {
        assertEquals(Links.historyUrl("https://youtu.be/abcdefghijk?t=1"), Links.historyUrl("https://m.youtube.com/watch?v=abcdefghijk&t=99"))
        assertEquals("https://example.org/movie.mp4?token=x", Links.historyUrl("https://example.org/movie.mp4?token=x"))
        assertNull(Links.historyUrl("https://user:pass@example.org/movie.mp4"))
    }
}
