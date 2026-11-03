package com.weiktube.home

import org.junit.Assert.*
import org.junit.Test

class OriginalHomePolicyTest {
    @Test fun invalidPlayerRoutesStillCannotFallThroughToWebsitePlayback() {
        for (url in listOf("https://m.youtube.com/watch", "https://m.youtube.com/watch?v=short",
            "https://m.youtube.com/watch?v=dQw4w9WgXcQ&v=abcdefghijk", "https://m.youtube.com/%77atch?v=dQw4w9WgXcQ",
            "https://m.youtube.com/shorts/invalid", "https://m.youtube.com/live", "https://youtu.be/invalid",
            "https://m.youtube.com/embed/dQw4w9WgXcQ", "https://m.youtube.com/v/dQw4w9WgXcQ")) {
            assertTrue(url, OriginalHomePolicy.isVideoNavigation(url))
        }
        for (url in listOf("https://m.youtube.com/", "https://m.youtube.com/results?search_query=test",
            "https://m.youtube.com/@channel", "https://m.youtube.com/feed/subscriptions", "https://accounts.google.com/v3/signin/",
            "https://m.youtube.com.evil.test/watch?v=dQw4w9WgXcQ")) {
            assertFalse(url, OriginalHomePolicy.isVideoNavigation(url))
        }
    }
    private val id = "abcDEFghi_1"
    private val expected = "https://www.youtube.com/watch?v=$id"

    @Test fun validatedPublicVideoTypesOpenTheOriginalPlayerWithoutOtherQueryData() {
        for (url in listOf("https://m.youtube.com/watch?v=$id&list=private&t=60#private", "https://www.youtube.com/shorts/$id",
            "https://youtube.com/live/$id", "https://youtu.be/$id?si=private", "https://www.youtube.com:443/watch?v=$id")) {
            assertEquals(url, expected, OriginalHomePolicy.videoUrl(url))
        }
    }
    @Test fun malformedAndNonPublicVideoLinksAreRejected() {
        for (url in listOf("http://www.youtube.com/watch?v=$id", "https://youtube.com.evil.test/watch?v=$id", "https://evil@youtube.com/watch?v=$id",
            "https://www.youtube.com:444/watch?v=$id", "https://www.youtube.com./watch?v=$id", "https://youtu.be/$id/extra",
            "https://m.youtube.com/watch?v=short", "https://m.youtube.com/watch?v=$id&v=$id", "https://m.youtube.com/watch?v=$id%0A",
            "https://m.youtube.com/%77atch?v=$id", "https://m.youtube.com/redirect?q=https://youtu.be/$id", "javascript:alert(1)",
            "https://m.youtube.com\\@evil.test/watch?v=$id", " https://m.youtube.com/watch?v=$id", "https://m.youtube.com/watch?v=%ZZ")) {
            assertNull(url, OriginalHomePolicy.videoUrl(url))
        }
    }
    @Test fun manualHomeProofCannotComeFromAnAccountOrChannelPage() {
        for (url in listOf("https://m.youtube.com/", "https://www.youtube.com/feed/what_to_watch", "https://youtube.com/?app=m")) {
            assertTrue(url, OriginalHomePolicy.isHomePage(url))
        }
        for (url in listOf("https://m.youtube.com/@someone", "https://m.youtube.com/signin", "https://accounts.google.com/", "https://m.youtube.com/watch?v=$id")) {
            assertFalse(url, OriginalHomePolicy.isHomePage(url))
        }
    }
    @Test fun normalWebsiteSignInAndConsentAreAllowedWithoutBeingClassifiedAsOAuth() {
        for (url in listOf("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F",
            "https://accounts.google.com/v3/signin/identifier?flowName=GlifWebSignIn&state=example",
            "https://accounts.google.com/AccountChooser?scope=example", "https://accounts.youtube.com/accounts/SetSID",
            "https://consent.youtube.com/m", "https://consent.google.com/m", "https://m.youtube.com/signin", "https://www.youtube.com/t/signin")) {
            assertTrue(url, OriginalHomePolicy.isNormalWebAuthUrl(url))
            assertTrue(url, OriginalHomePolicy.allowedNavigation(url))
            assertFalse(url, OriginalHomePolicy.isExplicitOAuthUrl(url))
            assertFalse(url, OriginalHomePolicy.isYoutubePage(url))
        }
    }
    @Test fun explicitOAuthAndUnrelatedOriginsAreBlockedWithoutBroadQueryKeywordHeuristics() {
        for (url in listOf("https://accounts.google.com/o/oauth2/v2/auth", "https://accounts.google.com/signin/oauth/identifier",
            "https://accounts.google.com/v3/signin?client_id=example&response_type=code", "https://oauth2.googleapis.com/token")) {
            assertTrue(url, OriginalHomePolicy.isExplicitOAuthUrl(url))
            assertFalse(url, OriginalHomePolicy.allowedNavigation(url))
            assertFalse(url, OriginalHomePolicy.isNormalWebAuthUrl(url))
        }
        for (url in listOf("https://accounts.google.com.evil.test/", "https://www.google.com/search?q=test", "https://example.com/", "intent://youtube")) {
            assertFalse(url, OriginalHomePolicy.allowedNavigation(url))
        }
        assertFalse(OriginalHomePolicy.isExplicitOAuthUrl("https://m.youtube.com/results?scope=x&state=x"))
    }
    @Test fun normalGoogleChallengesHaveANarrowSubframeOnlyAllowance() {
        for (url in listOf("https://www.google.com/recaptcha/api2/anchor?k=public", "https://recaptcha.google.com/recaptcha/api2/bframe",
            "https://www.recaptcha.net/recaptcha/enterprise/anchor")) {
            assertTrue(url, OriginalHomePolicy.allowedAuthSubframe(url))
            assertFalse(url, OriginalHomePolicy.allowedNavigation(url))
        }
        for (url in listOf("https://www.google.com/search?q=recaptcha", "https://www.google.com/recaptcha.evil/anchor",
            "https://www.google.com.evil.test/recaptcha/api2/anchor", "https://recaptcha.net/recaptcha/api2/anchor",
            "http://www.google.com/recaptcha/api2/anchor", "https://www.google.com/o/oauth2/auth",
            "https://www.google.com/recaptcha/api2/anchor?client_id=example&response_type=code",
            "https://www.recaptcha.net/recaptcha/anchor?client_id=example&response_type=code",
            "https://recaptcha.google.com/recaptcha/anchor?client_id=example&response_type=code",
            "https://www.google.com/recaptcha/%2e%2e/search")) {
            assertFalse(url, OriginalHomePolicy.allowedAuthSubframe(url))
        }
    }
}
