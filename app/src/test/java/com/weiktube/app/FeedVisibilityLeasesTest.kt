package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class FeedVisibilityLeasesTest {
    @Test fun oldHomeDisposalCannotCancelAnAlreadyOpenedSubscriptionsSurface() {
        val lease = FeedVisibilityLeases()
        lease.acquire() // Home
        lease.acquire() // Abos enters before Home's disposal callback runs.
        assertFalse(lease.release())
        assertTrue(lease.visible)
        assertTrue(lease.release())
        assertFalse(lease.visible)
    }
    @Test fun leavingThenReopeningStartsANewVisibleLifetime() {
        val lease = FeedVisibilityLeases()
        lease.acquire(); assertTrue(lease.release())
        lease.acquire(); assertTrue(lease.visible)
        lease.clear(); assertFalse(lease.visible)
        assertTrue(lease.release())
    }
}
