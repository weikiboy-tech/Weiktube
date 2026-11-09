package com.weiktube.app

import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class HistoryStoreTest {
    @Test fun existingDataStoreRespectsDisableClearAndViewModelRecreation() {
        val app = RuntimeEnvironment.getApplication()
        val store = ViewModelStore()
        fun createModel() = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[WeiktubeModel::class.java]
        val model = createModel()
        val a = "https://youtu.be/abcdefghijk"
        val b = "https://youtu.be/12345678901"
        try {
            model.boolean("saveHistory", true)
            model.clearHistory()
            model.record(a, "First")
            await { model.preferences.value.history.singleOrNull()?.title == "First" }
            model.record(b, "Second")
            await { model.preferences.value.history.size == 2 }
            model.record(a, "")
            await { model.preferences.value.history.firstOrNull()?.title == "First" }
            model.boolean("saveHistory", false)
            await { !model.preferences.value.saveHistory }
            model.record(b, "Must not be saved")
            // Queue a setting edit behind record as a completion barrier on the same DataStore.
            model.string("theme", "Dark")
            await { model.preferences.value.theme == "Dark" }
            assertEquals(listOf("First", "Second"), model.preferences.value.history.map { it.title })
            val saved = model.preferences.value.history
            assertTrue(app.filesDir.resolve("datastore/weiktube.preferences_pb").length() > 0)
            store.clear()
            val recreated = createModel()
            await { recreated.preferences.value.history == saved && !recreated.preferences.value.saveHistory }
            recreated.clearHistory()
            await { recreated.preferences.value.history.isEmpty() }
            recreated.boolean("saveHistory", true)
            recreated.updateHistoryTitle(a, "Late title after deletion")
            recreated.string("theme", "System")
            await { recreated.preferences.value.theme == "System" }
            assertTrue(recreated.preferences.value.history.isEmpty())
        } finally { store.clear() }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        fail("DataStore did not emit the expected snapshot")
    }
}
