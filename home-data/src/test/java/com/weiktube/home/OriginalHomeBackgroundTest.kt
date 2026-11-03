package com.weiktube.home

import org.junit.Assert.*
import org.junit.Test

class OriginalHomeBackgroundTest {
    @Test fun firstPartySignInCanReturnHomeAfterDeviceConfirmation() {
        assertTrue(OriginalHomeBackground.navigation("https://accounts.google.com/v3/signin/challenge/ipp"))
        assertTrue(OriginalHomeBackground.navigation("https://accounts.youtube.com/accounts/SetSID"))
        assertTrue(OriginalHomeBackground.navigation("https://m.youtube.com/"))
    }
    @Test fun retainedSignInCannotStartVideoOauthOrForeignBrowsing() {
        listOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ", "https://www.youtube.com/results?search_query=test",
            "https://accounts.google.com/o/oauth2/v2/auth?client_id=test", "https://accounts.google.com.evil.test/", "http://m.youtube.com/")
            .forEach { assertFalse(it, OriginalHomeBackground.navigation(it)) }
    }
    @Test fun challengeSubframesRequireAnAuthenticationParent() {
        val challenge = "https://www.google.com/recaptcha/api2/anchor"
        assertTrue(OriginalHomeBackground.subframe("https://accounts.google.com/v3/signin/", challenge))
        assertFalse(OriginalHomeBackground.subframe("https://m.youtube.com/", challenge))
        assertFalse(OriginalHomeBackground.subframe("https://accounts.google.com/", "https://www.google.com/search?q=test"))
    }
}
