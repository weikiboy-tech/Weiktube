package com.weiktube.app

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WatchStartPreviewTest {
    @get:Rule val compose = createComposeRule()

    @org.junit.After fun restoreClock() { compose.mainClock.autoAdvance = true }

    @Test fun commitKeepsCoverButReleasesDetailsAndSlowStartNeverRetriesAutomatically() {
        val committed = mutableStateOf(false)
        var retries = 0
        compose.setContent {
            Box { WatchStartPreview("test", "Ausgewähltes Video", committed.value, false, { retries++ }, {}) }
        }
        compose.onNodeWithText("Ausgewähltes Video").assertExists()
        compose.mainClock.autoAdvance = false
        committed.value = true
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        repeat(3) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        compose.onNodeWithText("Ausgewähltes Video").assertDoesNotExist()
        compose.onNodeWithText("Video wird geladen").assertExists()
        compose.mainClock.advanceTimeBy(4100)
        compose.waitForIdle()
        compose.onNodeWithText("Video wird geladen").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(6100)
        compose.waitForIdle()
        compose.onNodeWithText("Erneut laden").performClick()
        assertEquals(1, retries)
    }
}
