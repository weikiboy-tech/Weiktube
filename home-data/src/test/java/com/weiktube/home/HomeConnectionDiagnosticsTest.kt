package com.weiktube.home

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomeConnectionDiagnosticsTest {
    @Test fun errorRouteIsErrorEvenIfResponseWouldBe200() {
        assertEquals("PAGE_ERROR", HomeConnectionDiagnostics.locationOf("https://www.youtube.com/oops?token=private").kind)
        val state = HomeConnectionDiagnostics(); state.location("https://www.youtube.com/oops", "location")
        state.details(JSONObject("""{"pageHttpStatus":200}"""))
        assertEquals("PAGE_ERROR", state.missingBridgeCode())
    }
    @Test fun diagnosticAddressesNeverExposeAuthQueriesOrIdentifiers() {
        for (url in listOf("https://accounts.google.com/v3/signin/identifier?email=secret@example.com&authuser=123#token", "https://m.youtube.com/watch?v=private1234&list=private", "https://secret.example.com/user/email", "https://m.youtube.com/@privateUser", "https://secret@youtube.com/")) {
            val safe = HomeConnectionDiagnostics.locationOf(url)
            assertFalse(safe.toString(), safe.toString().contains("secret"))
            assertFalse(safe.toString(), safe.toString().contains("private"))
            assertFalse(safe.toString(), safe.toString().contains("?"))
        }
    }
    @Test fun googleAndYoutubeSigninRequireExternalRouteButHomeDoesNot() {
        assertTrue(HomeConnectionDiagnostics.isExternalSignIn("https://accounts.google.com/o/oauth2/v2/auth?client_id=secret"))
        assertTrue(HomeConnectionDiagnostics.isExternalSignIn("https://www.youtube.com/signin?action_handle_signin=true"))
        assertFalse(HomeConnectionDiagnostics.isExternalSignIn("https://m.youtube.com/"))
        for (url in listOf("https://www.youtube.com/login", "https://m.youtube.com/account_login", "https://www.googleapis.com/oauth2/v1/auth?token=secret")) {
            assertTrue(url, HomeConnectionDiagnostics.isExternalSignIn(url))
            assertFalse(url, HomeConnectionDiagnostics.allowedEmbedded(url))
        }
    }
    @Test fun embeddedNavigationIsAllowlistedIncludingSubframeUse() {
        assertTrue(HomeConnectionDiagnostics.allowedEmbedded("https://m.youtube.com/"))
        assertTrue(HomeConnectionDiagnostics.allowedEmbedded("https://consent.youtube.com/m"))
        assertFalse(HomeConnectionDiagnostics.allowedEmbedded("https://unknown.example.com/oauth/login"))
        assertFalse(HomeConnectionDiagnostics.allowedEmbedded("https://accounts.google.com/v3/signin"))
        assertFalse(HomeConnectionDiagnostics.allowedEmbedded("https://youtube.com.evil.com/"))
    }
    @Test fun blockedDestinationDoesNotPretendToBeFinalLoadedPage() {
        val state = HomeConnectionDiagnostics(); state.enabled = true
        state.location("https://m.youtube.com/", "location")
        state.location("https://accounts.google.com/v3/signin?client_id=secret", "blocked")
        state.failure("SIGNIN_EXTERNAL_REQUIRED")
        assertEquals("https://m.youtube.com/", state.current.address)
        assertTrue(state.render().contains("Blockiertes Ziel: https://accounts.google.com/<Anmeldung>"))
        assertFalse(state.render().contains("secret"))
    }
    @Test fun rawTitleIsReducedToKnownClassification() {
        assertEquals("error", HomeConnectionDiagnostics.titleOf("Hoppla! secret@example.com - YouTube"))
        assertEquals("youtube", HomeConnectionDiagnostics.titleOf("Private user - YouTube"))
        assertEquals("other", HomeConnectionDiagnostics.titleOf("private@example.com"))
    }
    @Test fun bridgeDetailsAllowOnlyEnumsBooleansAndActualStatusNumbers() {
        val state = HomeConnectionDiagnostics()
        state.details(JSONObject("""{"pageKind":"secret","configPresent":"secret","loggedIn":true,"source":"inline","pageHttpStatus":0,"httpStatus":403,"missing":["config","secret"],"cookie":"secret","titleKind":"private@example.com"}"""))
        val report = state.render()
        assertFalse(report.contains("secret")); assertFalse(report.contains("private@example.com"))
        assertTrue(report.contains("Dokument-HTTP: unknown")); assertTrue(report.contains("API-HTTP: 403"))
        assertTrue(report.contains("loggedIn: true")); assertTrue(report.contains("source: inline"))
    }
    @Test fun noBridgeErrorsArePageSpecificAndNeverPretendHttpSuccess() {
        val state = HomeConnectionDiagnostics()
        state.location("https://consent.youtube.com/m?continue=secret", "location")
        assertEquals("CONSENT_REQUIRED", state.missingBridgeCode())
        state.location("https://m.youtube.com/", "location")
        assertEquals("BRIDGE_UNAVAILABLE", state.missingBridgeCode())
        assertTrue(state.render().contains("Dokument-HTTP: unknown"))
    }
    @Test fun historyIsOptInBoundedAndClearedWhenDisabled() {
        val state = HomeConnectionDiagnostics()
        state.location("https://www.youtube.com/oops", "requested")
        state.enabled = true
        state.location("https://m.youtube.com/", "location")
        assertFalse(state.render().contains("<Fehlerseite>"))
        repeat(20) { state.location(if (it % 2 == 0) "https://www.youtube.com/" else "https://m.youtube.com/", "start") }
        assertEquals(12, state.render().lines().count { it.startsWith("start:") || it.startsWith("location:") })
        state.enabled = false
        assertFalse(state.render().contains("start:"))
    }
    @Test fun unknownFailureAndFractionalHttpCodeCannotInjectText() {
        val state = HomeConnectionDiagnostics(); state.failure("secret_cookie")
        state.details(JSONObject("""{"httpStatus":200.5,"pageHttpStatus":"200"}"""))
        assertTrue(state.render().contains("UNKNOWN_FAILURE"))
        assertFalse(state.render().contains("secret_cookie"))
        assertTrue(state.render().contains("API-HTTP: unknown"))
    }
}
