package com.weiktube.home

import org.junit.Assert.*
import org.junit.Test

class OriginalHomeTabBackgroundTest {
    @Test fun hiddenYouTubeTabCanFinishItsSearchChannelAndHomeRedirects() {
        val parent = "https://m.youtube.com/results?search_query=mario"
        listOf("https://m.youtube.com/", "https://www.youtube.com/@channel/videos",
            "https://m.youtube.com/results?search_query=next", "https://m.youtube.com/feed/subscriptions")
            .forEach { assertTrue(it, OriginalHomeTabBackground.navigation(parent, it)) }
    }

    @Test fun hidingBetweenNativeInitialLoadRequestAndPageStartDoesNotStrandBlankPage() {
        for (parent in listOf("", "about:blank")) {
            assertTrue(OriginalHomeTabBackground.navigation(parent, "https://m.youtube.com/", "https://m.youtube.com/"))
            assertTrue(OriginalHomeTabBackground.navigation(parent, "https://www.youtube.com/", "https://m.youtube.com/"))
            assertFalse(OriginalHomeTabBackground.navigation(parent, "https://m.youtube.com/"))
        }
    }

    @Test fun hiddenTabNeverStartsVideoAuthOrForeignNavigation() {
        val parent = "https://m.youtube.com/"
        listOf("https://m.youtube.com/watch?v=abcDEFghi_1", "https://m.youtube.com/watch?v=bad",
            "https://m.youtube.com/shorts/abcDEFghi_1", "https://youtu.be/abcDEFghi_1",
            "https://www.youtube.com/embed/abcDEFghi_1", "https://www.youtube.com/v/abcDEFghi_1",
            "https://m.youtube.com/channel_switcher", "https://m.youtube.com/signin",
            "https://accounts.google.com/v3/signin/", "https://accounts.google.com/o/oauth2/auth?client_id=test",
            "https://example.com/", "http://m.youtube.com/", "about:blank")
            .forEach { assertFalse(it, OriginalHomeTabBackground.navigation(parent, it, parent)) }
    }

    @Test fun initialNativeTargetDoesNotAuthorizeForeignOrAccountParents() {
        val target = "https://m.youtube.com/"
        for (parent in listOf("https://example.com/", "https://accounts.google.com/v3/signin/", "https://m.youtube.com/signin")) {
            assertFalse(parent, OriginalHomeTabBackground.navigation(parent, target, target))
        }
        for (pending in listOf("https://example.com/", "https://m.youtube.com/watch?v=abcDEFghi_1", "https://m.youtube.com/signin")) {
            assertFalse(pending, OriginalHomeTabBackground.navigation("about:blank", target, pending))
        }
    }
}
