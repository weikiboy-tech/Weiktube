package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class PlayerProgressTest {
    @Test fun closeSequenceNeverProducesNaNForStillMountedIndicator() {
        assertEquals(.25f, playerProgress(30.0, 120.0), 0f)
        assertEquals(0f, playerProgress(0.0, 120.0), 0f)
        val afterClose = playerProgress(0.0, 0.0)
        assertEquals(0f, afterClose, 0f)
        assertTrue(afterClose.isFinite())
        // Also safe if duration clears before position.
        assertEquals(0f, playerProgress(30.0, 0.0), 0f)
    }

    @Test fun liveOrUnknownDurationReportsZero() {
        for (duration in listOf(0.0, -0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(0f, playerProgress(10.0, duration), 0f)
        }
    }

    @Test fun nonFinitePositionReportsZero() {
        for (position in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertEquals(0f, playerProgress(position, 100.0), 0f)
        }
    }

    @Test fun normalProgressAndBoundsArePreserved() {
        assertEquals(.5f, playerProgress(5.0, 10.0), 0f)
        assertEquals(0f, playerProgress(-10.0, 10.0), 0f)
        assertEquals(0f, playerProgress(-0.0, 10.0), 0f)
        assertEquals(1f, playerProgress(10.0, 10.0), 0f)
        assertEquals(1f, playerProgress(20.0, 10.0), 0f)
    }

    @Test fun extremeFiniteValuesCannotOverflowOrProduceNaN() {
        assertEquals(1f, playerProgress(Double.MAX_VALUE, Double.MIN_VALUE), 0f)
        assertEquals(0f, playerProgress(Double.MIN_VALUE, Double.MAX_VALUE), 0f)
        assertEquals(.5f, playerProgress(Double.MAX_VALUE / 2.0, Double.MAX_VALUE), 0f)
        assertTrue(playerProgress(Double.MIN_VALUE, Double.MIN_VALUE * 2.0).isFinite())
    }
}
