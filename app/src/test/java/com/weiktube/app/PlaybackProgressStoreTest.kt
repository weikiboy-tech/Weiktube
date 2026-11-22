package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PlaybackProgressStoreTest {
    private val url = "https://youtu.be/abcdefghijk"
    @Test fun persistedProgressSurvivesNewStoreAndInvalidSnapshots() {
        val context = RuntimeEnvironment.getApplication()
        val store = PlaybackProgressStore(context)
        store.clear(); store.enabled = true
        store.save(url, 124.5, 600.0)
        val reopened = PlaybackProgressStore(context)
        assertEquals(124.5, reopened.get("https://m.youtube.com/watch?v=abcdefghijk")!!.seconds, 0.0)
        for (value in listOf(0.0, Double.NaN, Double.POSITIVE_INFINITY)) store.save(url, value, 600.0)
        assertEquals("2:04", store.get(url)!!.label)
        store.save(url, 60.0, 600.0, force = true)
        assertEquals(60.0, store.get(url)!!.seconds, 0.0) // deliberate backwards seek
    }
    @Test fun completionPrivacyAndClearAreRespected() {
        val store = PlaybackProgressStore(RuntimeEnvironment.getApplication())
        store.clear(); store.enabled = true
        store.save(url, 50.0, 100.0)
        store.save(url, 100.0, 100.0, ended = true)
        assertNull(store.get(url))
        store.enabled = false; store.save(url, 30.0, 100.0)
        store.enabled = true; assertNull(store.get(url))
        store.save(url, 50.0, 100.0); store.clear(); assertNull(store.get(url))
        store.save("https://m.youtube.com/shorts/abcdefghijk", 10.0, 30.0)
        assertNull(store.get(url))
    }
    @Test fun explicitZeroTimestampIsDifferentFromAbsentTimestamp() {
        assertTrue(Links.hasStartTime("$url?t=0"))
        assertTrue(Links.hasStartTime("$url#t=2m"))
        assertFalse(Links.hasStartTime(url))
    }
}
