package com.weiktube.home

import org.junit.Assert.*
import org.junit.Test

class OriginalHomeReturnPolicyTest {
    private val video = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    @Test fun onlyExactParkedSelectionCanOpenThePlayerOnce() {
        val state = OriginalHomeReturnPolicy("ticket", video)
        assertFalse(state.accept("different", video))
        assertFalse(state.accept("ticket", "https://www.youtube.com/watch?v=abcdefghijk"))
        assertTrue(state.accept("ticket", video))
        assertFalse(state.accept("ticket", video))
    }
    @Test fun aParkedResultIsNotAReturnUntilParentAcceptedIt() {
        val state = OriginalHomeReturnPolicy("ticket", video)
        assertNull(state.resumeToken())
        assertFalse(state.take("ticket"))
        assertTrue(state.accept("ticket", video))
        assertEquals("ticket", state.resumeToken())
    }
    @Test fun returnTransfersOwnershipExactlyOnce() {
        val state = OriginalHomeReturnPolicy("ticket", video)
        assertTrue(state.accept("ticket", video))
        assertFalse(state.take("other"))
        assertEquals("ticket", state.resumeToken())
        assertTrue(state.take("ticket"))
        assertNull(state.resumeToken())
        assertFalse(state.take("ticket"))
        assertFalse(state.accept("ticket", video))
    }
    @Test fun discardedFeedCannotBeOpenedOrResumedByDelayedCallbacks() {
        val state = OriginalHomeReturnPolicy("ticket", video)
        state.discard()
        assertFalse(state.accept("ticket", video))
        assertFalse(state.take("ticket"))
        assertNull(state.resumeToken())
    }
    @Test fun explicitDataClearInvalidatesPlayerReturn() {
        val state = OriginalHomeReturnPolicy("ticket", video)
        assertTrue(state.accept("ticket", video))
        state.discard()
        assertNull(state.resumeToken())
        assertFalse(state.take("ticket"))
    }
    @Test fun previousRoundTripTicketCannotClaimANewSelection() {
        val next = OriginalHomeReturnPolicy("new-ticket", video)
        assertFalse(next.accept("old-ticket", video))
        assertTrue(next.accept("new-ticket", video))
        assertFalse(next.take("old-ticket"))
    }
}
