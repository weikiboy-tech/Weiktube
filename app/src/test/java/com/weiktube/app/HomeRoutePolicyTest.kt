package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class HomeRoutePolicyTest {
    @Test fun acceptsOnlyTheThreeCanonicalYoutubeHomeHosts() {
        listOf("https://m.youtube.com/", "https://www.youtube.com/", "https://youtube.com", "HTTPS://WWW.YOUTUBE.COM/").forEach {
            assertTrue(it, HomeRoutePolicy.isHome(it))
        }
    }
    @Test fun preservesSearchWatchAndChannelRoutes() {
        listOf("https://m.youtube.com/results?search_query=cats", "https://m.youtube.com/watch?v=abcdefghijk",
            "https://m.youtube.com/channel/UC123", "https://www.youtube.com/@channel", "https://m.youtube.com/shorts/abcdefghijk",
            "https://m.youtube.com/feed/subscriptions", "https://m.youtube.com/playlist?list=PL123").forEach {
            assertFalse(it, HomeRoutePolicy.isHome(it))
        }
    }
    @Test fun rejectsForeignHostsSuffixSpoofingAndShortLinks() {
        listOf("https://youtube.com.example.org/", "https://evilyoutube.com/", "https://youtu.be/", "https://example.com/", "https://youtube.com./").forEach {
            assertFalse(it, HomeRoutePolicy.isHome(it))
        }
    }
    @Test fun rejectsEveryExplicitPortIncludingTheDefaultTlsPort() {
        listOf("https://youtube.com:443/", "https://m.youtube.com:8443/", "https://youtube.com:80/").forEach {
            assertFalse(it, HomeRoutePolicy.isHome(it))
        }
    }
    @Test fun rejectsCredentialsNonHttpsAndMalformedUrls() {
        listOf("https://user@youtube.com/", "http://m.youtube.com/", "javascript:alert(1)", "file:///", "not a URL", " https://youtube.com/").forEach {
            assertFalse(it, HomeRoutePolicy.isHome(it))
        }
    }
    @Test fun ambiguousQueriesFragmentsAndEncodedPathsRemainWithTheBrowser() {
        listOf("https://m.youtube.com/?search_query=cats", "https://m.youtube.com/#/watch", "https://youtube.com/%2F", "https://youtube.com//").forEach {
            assertFalse(it, HomeRoutePolicy.isHome(it))
        }
    }
}
