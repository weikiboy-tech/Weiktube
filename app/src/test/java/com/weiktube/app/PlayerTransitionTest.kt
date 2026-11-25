package com.weiktube.app

import androidx.compose.runtime.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerTransitionTest {
    @get:Rule val compose = createComposeRule()

    @org.junit.After fun restoreClock() { compose.mainClock.autoAdvance = true }

    @Test fun reversalUsesIntermediateFrameAndNewSessionsStartAtTheirDestination() {
        var source by mutableStateOf<String?>("first")
        var mini by mutableStateOf(false)
        var pip by mutableStateOf(false)
        var orientation by mutableIntStateOf(1)
        var fraction = -1f
        compose.setContent {
            val frame = compactPlayerFraction(source, pip, orientation, mini)
            SideEffect { fraction = frame }
        }
        compose.mainClock.autoAdvance = false
        fun frames(ms: Long) {
            androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
            repeat((ms / 16).toInt()) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
            }
        }
        mini = true
        frames(96)
        val middle = fraction
        assertTrue("intermediate frame $middle", middle > 0f && middle < 1f)
        mini = false
        frames(32)
        assertTrue(fraction in 0.001f..0.999f)
        frames(224)
        assertEquals(0f, fraction, 0f)
        mini = true
        frames(64)
        pip = true
        mini = false
        frames(32)
        assertEquals(0f, fraction, 0f)
        pip = false
        mini = true
        orientation = 2
        frames(32)
        assertEquals(1f, fraction, 0f)
        source = "next"
        mini = false
        frames(32)
        assertEquals(0f, fraction, 0f)
    }
}

