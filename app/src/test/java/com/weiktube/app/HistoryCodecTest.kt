package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class HistoryCodecTest {
    @Test fun corruptIndividualEntriesDoNotLoseGoodEntriesOrCrashTheNextWrite() {
        val broken = """[1,null,{"url":"javascript:bad"},{"url":"https://youtu.be/abcdefghijk","title":"Old","time":1}]"""
        assertEquals(1, HistoryCodec.decode(broken).size)
        val repaired = HistoryCodec.decode(HistoryCodec.add(broken, "https://example.org/movie.mp4", "New", 2))
        assertEquals(listOf("New", "Old"), repaired.map { it.title })
    }
    @Test fun invalidOuterJsonAndOversizeDataRecoverToAnEmptyHistory() {
        assertTrue(HistoryCodec.decode("[broken").isEmpty())
        assertTrue(HistoryCodec.decode("x".repeat(1024 * 1024 + 1)).isEmpty())
        assertEquals(1, HistoryCodec.decode(HistoryCodec.add("[broken", "https://youtu.be/abcdefghijk", "Video", 2)).size)
    }
    @Test fun repeatedVideoUrlsUpdateTitleAndPositionInTheHistory() {
        val first = HistoryCodec.add(null, "https://youtu.be/abcdefghijk?t=3", "Placeholder", 1)
        val updated = HistoryCodec.decode(HistoryCodec.add(first, "https://www.youtube.com/watch?v=abcdefghijk&t=42", "Actual title", 4))
        assertEquals(1, updated.size)
        assertEquals("Actual title", updated.single().title)
        assertEquals(4L, updated.single().time)
    }
    @Test fun boundedHistoryRetainsNewestAndEscapesUserContent() {
        val list = (1..130).map { HistoryEntry("https://example.org/video$it.mp4", "Title \"$it\"\nNext", it.toLong()) }
        val history = HistoryCodec.decode(HistoryCodec.encode(list))
        assertEquals(100, history.size)
        assertEquals("Title \"1\"\nNext", history.first().title)
    }
}
