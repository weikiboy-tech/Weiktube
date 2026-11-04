package com.weiktube.home

import org.junit.Assert.*
import org.junit.Test

/** Exercises the production ownership/cleanup handler with session and host fakes, not Gecko. */
class HomeContentRecoveryTest {
    private class Session(var open: Boolean = true)
    private class Host {
        var session: Session? = Session()
        val websiteProfile = Any()
        var bridgeConnected = true
        var leaseOwned = true
        var releases = 0
        val causes = mutableListOf<HomeContentLoss>()
        val recovery = HomeContentRecovery<Session>(
            { session === it },
            { session = null; bridgeConnected = false; leaseOwned = false; releases++ },
            causes::add,
        )
        fun resume() {
            session?.takeIf { !it.open }?.let { recovery.lost(it, HomeContentLoss.CLOSED) }
            if (session == null) { session = Session(); bridgeConnected = true; leaseOwned = true }
        }
    }

    @Test fun killedBackgroundContentReleasesBridgeAndLeaseAndCanRetryWithSameProfile() {
        val host = Host()
        val before = host.session!!
        val profile = host.websiteProfile
        before.open = false // GeckoSession closes the window before calling onKill.
        assertTrue(host.recovery.lost(before, HomeContentLoss.KILLED))
        assertNull(host.session)
        assertFalse(host.bridgeConnected)
        assertFalse(host.leaseOwned)
        host.resume()
        assertNotSame(before, host.session)
        assertTrue(host.session!!.open)
        assertSame(profile, host.websiteProfile)
        assertEquals(listOf(HomeContentLoss.KILLED), host.causes)
    }

    @Test fun delayedKillAndCrashCannotReleaseReplacementDocument() {
        val host = Host()
        val old = host.session!!
        old.open = false
        host.recovery.lost(old, HomeContentLoss.KILLED)
        host.resume()
        val replacement = host.session
        assertFalse(host.recovery.lost(old, HomeContentLoss.CRASHED))
        assertFalse(host.recovery.lost(old, HomeContentLoss.KILLED))
        assertSame(replacement, host.session)
        assertTrue(host.leaseOwned)
        assertTrue(host.bridgeConnected)
        assertEquals(1, host.releases)
    }

    @Test fun missedKillCallbackIsRecoveredBeforeClosedSessionIsReused() {
        val host = Host()
        val closed = host.session!!
        closed.open = false
        host.resume()
        assertNotSame(closed, host.session)
        assertEquals(listOf(HomeContentLoss.CLOSED), host.causes)
        assertEquals(1, host.releases)
    }

    @Test fun healthyBackgroundReturnsRetainSessionAndProfile() {
        val host = Host()
        val session = host.session
        repeat(10) { host.resume() }
        assertSame(session, host.session)
        assertEquals(0, host.releases)
        assertTrue(host.causes.isEmpty())
    }

    @Test fun killedParkedDocumentInvalidatesItsOneTimePlayerReturn() {
        val session = Session()
        val ticket = OriginalHomeReturnPolicy("return-ticket", "video")
        assertTrue(ticket.accept("return-ticket", "video"))
        var parked: Session? = session
        var leaseOwned = true
        var releaseCount = 0
        val recovery = HomeContentRecovery<Session>(
            { parked === it },
            { parked = null; ticket.discard(); leaseOwned = false; releaseCount++ },
            {},
        )
        session.open = false
        assertTrue(recovery.lost(session, HomeContentLoss.KILLED))
        assertNull(ticket.resumeToken())
        assertFalse(ticket.take("return-ticket"))
        assertFalse(leaseOwned)
        assertFalse(recovery.lost(session, HomeContentLoss.CRASHED))
        assertEquals(1, releaseCount)
    }
}
