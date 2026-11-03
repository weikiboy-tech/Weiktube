package com.weiktube.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChromeHandoffSelectionTest {
    private val video = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    private val otherVideo = "https://www.youtube.com/watch?v=jNQXAC9IVRw"

    @Test fun toolbarUsesOnlyTheCurrentVideoPage() {
        assertEquals(video, ChromeHandoffSelection.videoUrl(ChromeHomeTestState.TOOLBAR_ACTION, video, otherVideo, true, ChromeHomeTestState.LINK_ACTION_ID))
    }

    @Test fun linkActionUsesSelectedLinkRatherThanCurrentPage() {
        assertEquals(otherVideo, ChromeHandoffSelection.videoUrl(ChromeHomeTestState.LINK_ACTION, video, otherVideo, true, ChromeHomeTestState.LINK_ACTION_ID))
    }

    @Test fun missingLinkNeverOpensADifferentCurrentVideo() {
        assertNull(ChromeHandoffSelection.videoUrl(ChromeHomeTestState.LINK_ACTION, video, null, true, ChromeHomeTestState.LINK_ACTION_ID))
    }

    @Test fun wrongActionOrContentTypeCannotTriggerPlayer() {
        assertNull(ChromeHandoffSelection.videoUrl("unknown", video, otherVideo, true, ChromeHomeTestState.LINK_ACTION_ID))
        assertNull(ChromeHandoffSelection.videoUrl(ChromeHomeTestState.LINK_ACTION, video, otherVideo, false, ChromeHomeTestState.LINK_ACTION_ID))
        assertNull(ChromeHandoffSelection.videoUrl(ChromeHomeTestState.LINK_ACTION, video, otherVideo, true, -1))
    }

    @Test fun homeAndAuthenticationUrlsNeverReachPlayer() {
        for (url in listOf("https://m.youtube.com/", "https://accounts.google.com/signin?token=private", "https://www.youtube.com.evil.test/watch?v=dQw4w9WgXcQ")) {
            assertNull(ChromeHandoffSelection.videoUrl(ChromeHomeTestState.TOOLBAR_ACTION, url, null, false, null))
        }
    }
}
