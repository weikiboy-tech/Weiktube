package com.weiktube.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real Compose semantics/clicks under Robolectric; not a screenshot or physical phone test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RecentHistoryUiTest {
    @get:Rule val compose = createComposeRule()
    private val entry = HistoryEntry("https://m.youtube.com/watch?v=abcdefghijk", "Mein Video", 10)

    @Test fun localHistoryOpensVideoAndAllHistoryWithoutAnAccount() {
        var opened: String? = null
        var all = 0
        compose.setContent { MaterialTheme {
            RecentHistorySection(Preferences(history = listOf(entry)), { opened = it }, { all++ })
        } }
        compose.onNodeWithText("Kürzlich angesehen").assertIsDisplayed()
        compose.onNodeWithText("Mein Video").performClick()
        assertEquals(entry.url, opened)
        compose.onNodeWithText("Alle anzeigen").performClick()
        assertEquals(1, all)
    }

    @Test fun emptyAndDisabledHistoryAreExplained() {
        compose.setContent { MaterialTheme { RecentHistorySection(Preferences(saveHistory = false), {}, {}) } }
        compose.onNodeWithText("Noch keine Videos im lokalen Verlauf.").assertIsDisplayed()
        compose.onNodeWithText("Verlauf pausiert. Neue Videos werden nicht gespeichert.").assertIsDisplayed()
    }

    @Test fun datastoreSnapshotChangesUpdateTheCarouselWithoutReopeningTheTab() {
        val prefs = mutableStateOf(Preferences(history = listOf(entry)))
        compose.setContent { MaterialTheme { RecentHistorySection(prefs.value, {}, {}) } }
        compose.onNodeWithText("Mein Video").assertIsDisplayed()
        compose.runOnIdle { prefs.value = Preferences() }
        compose.onNodeWithText("Mein Video").assertDoesNotExist()
        compose.onNodeWithText("Noch keine Videos im lokalen Verlauf.").assertIsDisplayed()
    }

    @Test fun carouselCanScrollToTheTwelfthMostRecentVideo() {
        val entries = (1..14).map { entry.copy(url = "https://example.org/$it.mp4", title = "Video $it") }
        compose.setContent { MaterialTheme { RecentHistorySection(Preferences(history = entries), {}, {}) } }
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(11)
        compose.onNodeWithText("Video 12").assertIsDisplayed()
        compose.onNodeWithText("Video 13").assertDoesNotExist()
    }

    @Test fun deletionRequiresConfirmationAndCanBeCancelled() {
        var deleted = 0
        var dismissed = 0
        compose.setContent { MaterialTheme { HistoryDeleteDialog({ dismissed++ }, { deleted++ }) } }
        assertEquals(0, deleted)
        compose.onNodeWithText("Abbrechen").performClick()
        assertEquals(1, dismissed)
        assertEquals(0, deleted)
        compose.onNodeWithText("Löschen").performClick()
        assertEquals(1, deleted)
    }
}
