package com.weiktube.home

import org.junit.Assert.*
import org.junit.Test

class OriginalHomeApprovalTest {
    @Test fun installationRequiresManualObservation() {
        val gate = OriginalHomeApproval()
        assertNull(gate.begin())
        gate.setConfirmed(true)
        assertTrue(gate.permits(gate.begin()!!))
    }

    @Test fun pauseResumeCannotAuthorizeLateInstallation() {
        val gate = OriginalHomeApproval()
        gate.setConfirmed(true)
        val pending = gate.begin()!!
        gate.setConfirmed(false) // onPause; activity may resume before the async result.
        assertFalse(gate.permits(pending))
        gate.setConfirmed(true) // User observes the page again; old callbacks still have no authority.
        assertFalse(gate.permits(pending))
        assertTrue(gate.permits(gate.begin()!!))
    }

    @Test fun profileSwitchRevokesAnAlreadyStartedEnableRequest() {
        val gate = OriginalHomeApproval()
        gate.setConfirmed(true)
        val installing = gate.begin()!!
        assertTrue(gate.permits(installing))
        gate.setConfirmed(false) // Account navigation while Gecko enable() is in flight.
        gate.setConfirmed(true)
        assertFalse(gate.permits(installing))
    }
}
