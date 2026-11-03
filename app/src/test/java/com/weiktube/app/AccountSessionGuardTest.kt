package com.weiktube.app

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class AccountSessionGuardTest {
    @Test fun anOldResponseCannotBeAcceptedAfterAccountReplacement() {
        val guard = AccountSessionGuard()
        val oldRequest = guard.capture()
        val started = CountDownLatch(1)
        val complete = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        val request = Thread {
            started.countDown(); complete.await()
            try { guard.check(oldRequest) } catch (caught: Throwable) { failure.set(caught) }
        }
        request.start(); started.await()
        guard.advance(); complete.countDown(); request.join(2000)
        assertFalse(request.isAlive)
        assertTrue(failure.get() is AccountSessionChanged)
    }
    @Test fun aWritePreparedBeforeConsentCannotUseTheNewlySelectedGrant() {
        val guard = AccountSessionGuard()
        val preparedWrite = guard.capture()
        guard.advance()
        var sent = false
        try { guard.check(preparedWrite); sent = true } catch (_: AccountSessionChanged) { }
        assertFalse(sent)
        guard.check(guard.capture())
    }
    @Test fun revocationInvalidatesEveryEarlierGrantIncludingAnIntermediateRefresh() {
        val guard = AccountSessionGuard()
        val first = guard.capture(); val second = guard.advance(); guard.advance()
        for (old in listOf(first, second)) {
            try { guard.check(old); fail("Old grant remained usable") } catch (_: AccountSessionChanged) { }
        }
    }
    @Test fun refusingWritePermissionStillRetainsTheReturnedReadGrant() {
        assertEquals(NativeGrantDecision.READ_ONLY, NativeGrantPolicy.decide(setOf("read"), setOf("read", "write"), "read", true))
        assertEquals(NativeGrantDecision.FULL, NativeGrantPolicy.decide(setOf("read", "write"), setOf("read", "write"), "read", true))
    }
    @Test fun aMissingTokenOrReadPermissionNeverRetainsAnotherAccountsSession() {
        assertEquals(NativeGrantDecision.NONE, NativeGrantPolicy.decide(setOf("write"), setOf("read", "write"), "read", true))
        assertEquals(NativeGrantDecision.NONE, NativeGrantPolicy.decide(setOf("read"), setOf("read", "write"), "read", false))
    }
}
