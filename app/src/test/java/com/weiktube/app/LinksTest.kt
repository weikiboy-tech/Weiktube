package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class LinksTest {
    @Test fun acceptsOfficialYoutubeUrlForms() {
        listOf("https://youtu.be/abcdefghijk", "https://m.youtube.com/watch?v=abcdefghijk&t=20", "https://www.youtube.com/shorts/abcdefghijk", "https://youtube.com/live/abcdefghijk")
            .forEach { assertEquals("abcdefghijk", Links.videoId(it)) }
    }
    @Test fun rejectsHostSpoofingAndInvalidIds() {
        listOf("https://youtube.com.evil.org/watch?v=abcdefghijk", "https://evil.org/youtube.com/watch?v=abcdefghijk", "https://youtube.com/watch?v=bad", "javascript:abcdefghijk")
            .forEach { assertNull(Links.videoId(it)) }
    }
    @Test fun convertsSupportedTimestamps() {
        assertEquals(83, Links.startSeconds("https://youtu.be/abcdefghijk?t=1m23s"))
        assertEquals(42, Links.startSeconds("https://youtu.be/abcdefghijk?start=42"))
        assertEquals(0, Links.startSeconds("https://youtu.be/abcdefghijk?t=bad"))
    }
    @Test fun rejectsUnsafeAddresses() {
        assertFalse(Links.https("http://example.org/video.mp4"))
        assertFalse(Links.https("file:///etc/passwd"))
        assertFalse(Links.https("https://user:password@example.org/video.mp4"))
        assertTrue(Links.https("https://example.org/video.mp4?x=1"))
    }
    @Test fun directPlayerDoesNotAcceptYoutubeMediaUrls() {
        assertFalse(Links.directVideoAllowed("https://r1.googlevideo.com/videoplayback"))
        assertFalse(Links.directVideoAllowed("https://youtube-nocookie.com/embed/abcdefghijk"))
        assertTrue(Links.directVideoAllowed("https://example.org/video.mp4"))
    }
}
