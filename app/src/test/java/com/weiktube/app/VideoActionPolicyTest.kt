package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class VideoActionPolicyTest {
    @Test fun likeAndUnlikeKeepTheUsersIntent() {
        assertEquals("none", VideoActionPolicy.requestedRating("like"))
        assertEquals("like", VideoActionPolicy.requestedRating("none"))
        assertEquals("like", VideoActionPolicy.requestedRating("dislike"))
    }
    @Test fun writesAreNeverAutomaticallyReplayed() {
        for (status in listOf(401, 403, 429, 500, 503)) for (method in listOf("POST", "DELETE"))
            assertFalse(VideoActionPolicy.retryAutomatically(method, status, 0))
        assertTrue(VideoActionPolicy.retryAutomatically("GET", 401, 0))
        assertFalse(VideoActionPolicy.retryAutomatically("GET", 401, 1))
    }
    @Test fun commentRetainsContentAndTrimsOnlyOuterWhitespace() { assertEquals("hello\nworld", VideoActionPolicy.comment("  hello\nworld  ")) }
    @Test(expected = IllegalArgumentException::class) fun emptyCommentIsNotSent() { VideoActionPolicy.comment(" \n ") }
    @Test(expected = IllegalArgumentException::class) fun oversizedCommentIsNotSent() { VideoActionPolicy.comment("x".repeat(5001)) }
    @Test(expected = IllegalArgumentException::class) fun systemWatchLaterIsNotPretendedToBeWritable() { VideoActionPolicy.playlistId("WL") }
    @Test(expected = IllegalArgumentException::class) fun invalidVideoIsRejected() { VideoActionPolicy.videoId("https://youtube.com") }
    @Test fun videoAndPlaylistIdsRemainExact() { assertEquals("abcdefghijk", VideoActionPolicy.videoId("abcdefghijk")); assertEquals("PL_example123", VideoActionPolicy.playlistId("PL_example123")) }
}
