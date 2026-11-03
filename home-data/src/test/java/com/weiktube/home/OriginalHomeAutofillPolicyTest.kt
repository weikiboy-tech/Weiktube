package com.weiktube.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OriginalHomeAutofillPolicyTest {
    private val ready = OriginalHomeAutofillPolicy.Snapshot(
        session = Any(), view = Any(), revision = 12, active = true, authPage = true, documentReady = true,
        attached = true, shown = true, focused = true, geckoEnabled = true, supported = true, enabled = true,
        field = Any(), fieldId = 9, usableBounds = true,
    )

    @Test fun actualFocusedInputOnVisibleAuthDocumentIsEligible() {
        assertEquals(OriginalHomeAutofillPolicy.Result.READY, OriginalHomeAutofillPolicy.evaluate(ready))
        assertEquals(OriginalHomeAutofillPolicy.Result.READY, OriginalHomeAutofillPolicy.revalidate(ready, ready.copy()))
    }

    @Test fun navigationCannotReuseOldFocusedInputEvenWithSameVirtualId() {
        assertEquals(OriginalHomeAutofillPolicy.Result.DOCUMENT_LOADING,
            OriginalHomeAutofillPolicy.revalidate(ready, ready.copy(documentReady = false)))
        assertEquals(OriginalHomeAutofillPolicy.Result.STALE,
            OriginalHomeAutofillPolicy.revalidate(ready, ready.copy(revision = 13)))
        assertEquals(OriginalHomeAutofillPolicy.Result.STALE,
            OriginalHomeAutofillPolicy.revalidate(ready, ready.copy(field = Any())))
    }

    @Test fun popupCannotReuseMainViewRequestOrCallbacks() {
        assertEquals(OriginalHomeAutofillPolicy.Result.STALE,
            OriginalHomeAutofillPolicy.revalidate(ready, ready.copy(session = Any(), view = Any())))
        assertEquals(OriginalHomeAutofillPolicy.Result.STALE,
            OriginalHomeAutofillPolicy.revalidate(ready, ready.copy(view = Any())))
        assertEquals(OriginalHomeAutofillPolicy.Result.STALE,
            OriginalHomeAutofillPolicy.revalidate(ready, ready.copy(session = Any())))
    }

    @Test fun backgroundAndFocusLossBlockSuggestionRequests() {
        assertEquals(OriginalHomeAutofillPolicy.Result.INACTIVE,
            OriginalHomeAutofillPolicy.revalidate(ready, ready.copy(active = false)))
        listOf(ready.copy(focused = false), ready.copy(shown = false), ready.copy(attached = false)).forEach {
            assertEquals(OriginalHomeAutofillPolicy.Result.VIEW_UNAVAILABLE, OriginalHomeAutofillPolicy.evaluate(it))
        }
    }

    @Test fun aDifferentFocusedFieldRejectsEarlierProviderCallback() {
        assertEquals(OriginalHomeAutofillPolicy.Result.STALE,
            OriginalHomeAutofillPolicy.revalidate(ready, ready.copy(fieldId = 10)))
        assertEquals(OriginalHomeAutofillPolicy.Result.NO_FIELD,
            OriginalHomeAutofillPolicy.revalidate(ready, ready.copy(field = null, fieldId = null)))
        assertEquals(OriginalHomeAutofillPolicy.Result.NO_FIELD,
            OriginalHomeAutofillPolicy.evaluate(ready.copy(usableBounds = false)))
    }

    @Test fun disabledProviderIsNotReportedAsMissingSavedPassword() {
        assertEquals(OriginalHomeAutofillPolicy.Result.DISABLED,
            OriginalHomeAutofillPolicy.evaluate(ready.copy(enabled = false)))
        assertEquals(OriginalHomeAutofillPolicy.Result.UNSUPPORTED,
            OriginalHomeAutofillPolicy.evaluate(ready.copy(supported = false)))
        assertEquals(OriginalHomeAutofillPolicy.Result.UNSUPPORTED,
            OriginalHomeAutofillPolicy.evaluate(ready.copy(geckoEnabled = false)))
    }

    @Test fun onlyNormalGoogleWebsiteSignInOffersTheAction() {
        assertTrue(OriginalHomeAutofillPolicy.isGoogleSignIn("https://accounts.google.com/v3/signin/identifier"))
        listOf("https://accounts.google.com/o/oauth2/v2/auth?client_id=test", "https://accounts.google.com/ServiceLogin?client_id=test&response_type=code",
            "https://accounts.google.com.evil.test/", "https://m.youtube.com/", "http://accounts.google.com/", "https://consent.google.com/")
            .forEach { assertFalse(it, OriginalHomeAutofillPolicy.isGoogleSignIn(it)) }
        assertEquals(OriginalHomeAutofillPolicy.Result.WRONG_PAGE,
            OriginalHomeAutofillPolicy.evaluate(ready.copy(authPage = false)))
    }
}
