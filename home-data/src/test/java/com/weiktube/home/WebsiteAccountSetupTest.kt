package com.weiktube.home

import org.junit.Assert.*
import org.junit.Test

class WebsiteAccountSetupTest {
    private class Store {
        var presented = false
        var writes = 0
        fun state() = WebsiteAccountSetup({ presented }, { presented = true; writes++ })
    }
    private val home = "https://m.youtube.com/"
    private val account = WebsiteAccountSetup.ACCOUNT_URL
    private val signIn = "https://accounts.google.com/v3/signin/identifier"

    @Test fun firstOfferIsQueuedWithoutClaimingItWasShown() {
        val store = Store(); val flow = store.state()
        assertFalse(flow.offerAutomatically(eligible = false))
        assertTrue(flow.offerAutomatically(eligible = true))
        assertTrue(flow.active); assertTrue(flow.automatic)
        assertFalse(store.presented)
        assertFalse(flow.offerAutomatically(eligible = true))
    }

    @Test fun displayedOfferIsPersistedOnceAndNeverAutomaticallyRepeatedAfterRestart() {
        val store = Store(); val flow = store.state()
        flow.offerAutomatically(true)
        flow.presented(); flow.presented()
        assertEquals(1, store.writes)
        assertFalse(store.state().offerAutomatically(true))
        assertTrue(store.state().begin()) // Manual account access remains available.
    }

    @Test fun skipBeforePostedPresentationConsumesTheOffer() {
        val store = Store(); val flow = store.state()
        flow.offerAutomatically(true)
        flow.dismiss()
        assertTrue(store.presented); assertFalse(flow.active)
        assertFalse(flow.offerAutomatically(true))
        assertFalse(store.state().offerAutomatically(true))
    }

    @Test fun rapidRepeatedAccountRequestsDoNotRestartTheJourney() {
        val flow = Store().state()
        assertTrue(flow.begin()); flow.loadIssued(); flow.pageStarted(signIn)
        assertFalse(flow.begin()); assertFalse(flow.automatic)
        flow.locationChanged(home)
        assertFalse(flow.active)
    }

    @Test fun oldHomeCallbacksCannotCompleteQueuedOrJustIssuedAccountNavigation() {
        val flow = Store().state()
        flow.begin()
        flow.pageStarted(home); flow.locationChanged(home)
        assertTrue(flow.active)
        flow.loadIssued()
        flow.locationChanged(home)
        assertTrue(flow.active)
        flow.requestObserved(account)
        flow.locationChanged(home) // Old location before any fresh account/redirect document.
        assertTrue(flow.active)
    }

    @Test fun blankBootstrapCannotCompleteAccountNavigation() {
        val flow = Store().state()
        flow.begin(); flow.loadIssued(); flow.requestObserved(account)
        flow.pageStarted("about:blank"); flow.locationChanged("about:blank")
        flow.locationChanged(home)
        assertTrue(flow.active)
    }

    @Test fun consentAndLoginContinueUntilARealYouTubeDocumentReturns() {
        val flow = Store().state()
        flow.begin(); flow.loadIssued()
        flow.pageStarted(account); flow.locationChanged(account)
        flow.pageStarted("https://consent.youtube.com/m"); flow.locationChanged("https://consent.youtube.com/m")
        flow.pageStarted(signIn); flow.locationChanged(signIn)
        assertTrue(flow.active)
        flow.pageStarted(home); flow.locationChanged(home)
        assertFalse(flow.active) // Navigation finished; this deliberately does not assert authentication.
    }

    @Test fun existingWebsiteSessionCanRedirectStraightBackHome() {
        val flow = Store().state()
        flow.begin(); flow.loadIssued(); flow.requestObserved(account)
        flow.pageStarted(home); flow.locationChanged(home)
        assertFalse(flow.active)
    }

    @Test fun sameDocumentAccountNavigationCanReturnHome() {
        val flow = Store().state()
        flow.begin(); flow.loadIssued(); flow.locationChanged(account)
        flow.locationChanged(home)
        assertFalse(flow.active)
    }

    @Test fun videoForeignAndOAuthDestinationsDoNotFinishSetup() {
        val flow = Store().state()
        flow.begin(); flow.loadIssued(); flow.pageStarted(signIn)
        for (url in listOf("https://m.youtube.com/watch?v=abcDEFghi_1",
            "https://m.youtube.com/shorts/abcDEFghi_1", "https://example.com/",
            "https://accounts.google.com/o/oauth2/auth?client_id=test&response_type=code")) {
            flow.locationChanged(url); assertTrue(url, flow.active)
        }
    }

    @Test fun offlineRetryKeepsPresentationAndWaitsForItsOwnDocument() {
        val store = Store(); val flow = store.state()
        flow.offerAutomatically(true); flow.presented(); flow.loadIssued()
        flow.requestObserved(account); flow.pageStarted(signIn)
        flow.loadIssued() // Retry after network failure starts a new account navigation.
        flow.locationChanged(home)
        assertTrue(flow.active)
        assertFalse(store.state().offerAutomatically(true))
    }

    @Test fun ordinarySessionCleanupDoesNotReofferButExplicitDataDeletionDoes() {
        val store = Store(); val flow = store.state()
        flow.offerAutomatically(true); flow.presented(); flow.finish()
        assertFalse(flow.offerAutomatically(true))
        store.presented = false // HomeEngine's explicit website-data clear, same surviving controller.
        assertTrue(flow.offerAutomatically(true))
        assertTrue(flow.active)
    }
}
