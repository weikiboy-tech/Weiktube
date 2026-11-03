package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class VideoReadReloadGateTest {
    @Test fun firstOpenWaitsUntilTheExistingAccountIsReady() {
        val gate = VideoReadReloadGate()
        assertFalse(gate.take(true, true, true, false, true, false))
        assertTrue(gate.take(true, true, false, false, true, false))
    }
    @Test fun completedSessionChangeGetsOnlyOneAutomaticReadRestart() {
        val gate = VideoReadReloadGate()
        assertTrue(gate.take(true, true, false, false, false, true))
        assertFalse(gate.take(true, true, false, false, false, true))
    }
    @Test fun anInFlightWriteMustFinishOrAbortBeforeAnyReadRestart() {
        val gate = VideoReadReloadGate()
        assertFalse(gate.take(true, true, false, true, false, true))
        assertTrue(gate.take(true, true, false, false, false, true))
    }
    @Test fun leavingTheSheetOrDisconnectingNeverRestartsReads() {
        val gate = VideoReadReloadGate()
        assertFalse(gate.take(false, true, false, false, false, true))
        assertFalse(gate.take(true, false, false, false, false, true))
        assertFalse(gate.take(true, true, false, false, false, false))
    }
    @Test fun explicitUserRetryAllowsOneFurtherSessionRecovery() {
        val gate = VideoReadReloadGate()
        assertTrue(gate.take(true, true, false, false, false, true))
        gate.userOperation()
        assertTrue(gate.take(true, true, false, false, false, true))
    }
}
