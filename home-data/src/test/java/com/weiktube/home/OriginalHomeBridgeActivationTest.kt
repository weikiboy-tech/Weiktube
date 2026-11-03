package com.weiktube.home

import org.junit.Assert.*
import org.junit.Test

class OriginalHomeBridgeActivationTest {
    @Test fun repeatedReadyAndRetryKeepTheSamePlayerPermission() {
        val state = OriginalHomeBridgeActivation()
        var issued = 0
        fun enable() = state.enable { "permission-${++issued}" }
        val first = enable()
        repeat(10) { assertEquals(first, enable()) }
        assertEquals(1, issued)
        assertFalse(state.acknowledged)
        assertTrue(state.acknowledge(first))
        assertEquals(first, enable())
        assertTrue(state.acknowledged)
        assertEquals(1, issued)
    }

    @Test fun suspensionRetiresPermissionEvenWhileItsDisableMessageIsInFlight() {
        val state = OriginalHomeBridgeActivation()
        val previous = state.enable { "before-player" }
        assertTrue(state.acknowledge(previous))
        assertEquals(previous, state.suspend())
        assertFalse(state.acknowledged)
        assertFalse(state.matches(previous))
        assertFalse(state.acknowledge(previous))
        assertEquals(previous, state.token) // The disable is scoped to the retired permission.
    }

    @Test fun lateAcknowledgementFromPreviousReturnCannotEnableCurrentReturn() {
        val state = OriginalHomeBridgeActivation()
        val previous = state.enable { "before-player" }
        state.suspend()
        val resumed = state.enable { "after-player" }
        assertNotEquals(previous, resumed)
        assertFalse(state.acknowledge(previous))
        assertFalse(state.acknowledged)
        assertTrue(state.acknowledge(resumed))
        assertTrue(state.acknowledged)
    }

    @Test fun tenReturnsRearmExactlyOnceEachWithoutAcceptingOldCallbacks() {
        val state = OriginalHomeBridgeActivation()
        var previous: String? = null
        repeat(10) { cycle ->
            val current = state.enable { "return-$cycle" }
            assertEquals(current, state.enable { error("Duplicate readiness created another permission") })
            assertFalse(state.acknowledged)
            assertFalse(state.acknowledge(previous))
            assertTrue(state.acknowledge(current))
            assertTrue(state.matches(current))
            assertEquals(current, state.suspend())
            assertFalse(state.acknowledge(current))
            previous = current
        }
    }

    @Test fun aNewConnectionCannotAcceptAnAcknowledgementFromTheLostPort() {
        val previous = OriginalHomeBridgeActivation()
        val oldToken = previous.enable { "lost-port" }
        previous.suspend()
        val replacement = OriginalHomeBridgeActivation()
        assertFalse(replacement.acknowledge(oldToken))
        val token = replacement.enable { "replacement-port" }
        assertFalse(replacement.acknowledge(oldToken))
        assertFalse(replacement.acknowledged)
        assertTrue(replacement.acknowledge(token))
    }
}
