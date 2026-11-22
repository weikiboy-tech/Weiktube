package com.weiktube.app

import android.content.pm.ActivityInfo.*
import org.junit.Assert.*
import org.junit.Test

class UprightVideoOrientationTest {
    @Test fun allAnglesAndRestoredStatesExcludeReversePortrait() {
        for (previous in -1..14) for (degrees in -1..359) {
            val result = UprightVideoOrientation.fromSensor(degrees, previous)
            assertTrue(result in setOf(SCREEN_ORIENTATION_PORTRAIT, SCREEN_ORIENTATION_LANDSCAPE,
                SCREEN_ORIENTATION_REVERSE_LANDSCAPE, SCREEN_ORIENTATION_SENSOR_LANDSCAPE))
        }
    }
    @Test fun bothLandscapeDirectionsRemainInFullscreenWhenTurnedUpsideDown() {
        var current = SCREEN_ORIENTATION_PORTRAIT
        repeat(10) {
            current = UprightVideoOrientation.fromSensor(90, current)
            assertEquals(SCREEN_ORIENTATION_REVERSE_LANDSCAPE, current)
            assertEquals(current, UprightVideoOrientation.fromSensor(135, current))
            current = UprightVideoOrientation.fromSensor(180, current)
            assertEquals(SCREEN_ORIENTATION_REVERSE_LANDSCAPE, current)
            current = UprightVideoOrientation.fromSensor(270, current)
            assertEquals(SCREEN_ORIENTATION_LANDSCAPE, current)
            for (angle in 121..239) {
                assertEquals(current, UprightVideoOrientation.fromSensor(angle, current))
                assertFalse(UprightVideoOrientation.isUpright(angle))
            }
            assertTrue(UprightVideoOrientation.isUpright(0))
            current = UprightVideoOrientation.fromSensor(0, current)
            assertEquals(SCREEN_ORIENTATION_PORTRAIT, current)
        }
    }
}
