package com.weiktube.app

import org.junit.Assert.*
import org.junit.Test

class PlayerDragGestureTest {
    private fun detector() = PlayerDragGesture(slop = 8f, distanceThreshold = 64f, flingDistance = 24f, flingVelocity = 900f)

    @Test fun tapAndSmallMotionDoNotMinimize() {
        val drag = detector()
        drag.begin(true, 100f, 100f)
        assertFalse(drag.move(102f, 105f))
        assertFalse(drag.finish(102f, 105f, 2000f))
        assertFalse(drag.candidate)
    }

    @Test fun contentAndControlRegionsRemainWithWebView() {
        // 360x640 dp watch document has a 202.5 dp video at its top.
        assertFalse(PlayerDragGesture.insideVideo(12f, 360f, 640f, 1f, true))
        assertTrue(PlayerDragGesture.insideVideo(90f, 360f, 640f, 1f, true))
        assertFalse(PlayerDragGesture.insideVideo(190f, 360f, 640f, 1f, true))
        assertFalse(PlayerDragGesture.insideVideo(400f, 360f, 640f, 1f, true))
        val drag = detector()
        drag.begin(false, 100f, 400f)
        assertFalse(drag.move(100f, 600f))
        assertFalse(drag.finish(100f, 600f, 3000f))
    }

    @Test fun eligibilityScalesWithDensityAndCenteredDirectVideo() {
        assertTrue(PlayerDragGesture.insideVideo(270f, 1080f, 1920f, 3f, true))
        assertFalse(PlayerDragGesture.insideVideo(570f, 1080f, 1920f, 3f, true))
        assertFalse(PlayerDragGesture.insideVideo(90f, 360f, 640f, 1f, false))
        assertTrue(PlayerDragGesture.insideVideo(300f, 360f, 640f, 1f, false))
        assertFalse(PlayerDragGesture.insideVideo(410f, 360f, 640f, 1f, false))
    }

    @Test fun horizontalSeekNeverChangesIntoMinimize() {
        val drag = detector()
        drag.begin(true, 100f, 100f)
        assertFalse(drag.move(140f, 105f))
        assertFalse(drag.move(145f, 280f))
        assertFalse(drag.finish(145f, 280f, 3000f))
    }

    @Test fun upwardAndDiagonalGesturesAreRejectedForWholeSequence() {
        val drag = detector()
        drag.begin(true, 100f, 100f)
        assertFalse(drag.move(100f, 80f))
        assertFalse(drag.move(100f, 200f))
        assertFalse(drag.finish(100f, 200f, 3000f))
        drag.begin(true, 100f, 100f)
        assertFalse(drag.move(115f, 115f))
        assertFalse(drag.move(115f, 220f))
    }

    @Test fun deliberateDownwardDragCommitsWithoutFling() {
        val drag = detector()
        drag.begin(true, 100f, 100f)
        assertTrue(drag.move(105f, 120f))
        assertEquals(20f, drag.distance, 0f)
        assertTrue(drag.move(105f, 164f))
        assertTrue(drag.finish(105f, 164f, 0f))
        assertEquals(0f, drag.distance, 0f)
        assertFalse(drag.dragging)
    }

    @Test fun shortDragNeedsDistanceAndDownwardFling() {
        val drag = detector()
        drag.begin(true, 100f, 100f)
        assertTrue(drag.move(100f, 124f))
        assertFalse(drag.finish(100f, 124f, 899f))
        drag.begin(true, 100f, 100f)
        assertTrue(drag.move(100f, 124f))
        assertTrue(drag.finish(100f, 124f, 900f))
        drag.begin(true, 100f, 100f)
        assertTrue(drag.move(100f, 123f))
        assertFalse(drag.finish(100f, 123f, 3000f))
    }

    @Test fun returningToStartCancelsLongDrag() {
        val drag = detector()
        drag.begin(true, 100f, 100f)
        assertTrue(drag.move(100f, 200f))
        assertTrue(drag.move(100f, 104f))
        assertFalse(drag.finish(100f, 104f, -1000f))
    }

    @Test fun cancellationAndMultitouchCannotCommitLater() {
        val drag = detector()
        drag.begin(true, 100f, 100f)
        assertTrue(drag.move(100f, 190f))
        drag.cancel()
        assertFalse(drag.finish(100f, 220f, 3000f))
        drag.begin(true, 100f, 100f)
        assertTrue(drag.move(100f, 190f))
        assertFalse(drag.move(100f, 210f, pointers = 2))
        assertFalse(drag.move(100f, 240f))
        assertFalse(drag.finish(100f, 240f, 3000f))
    }

    @Test fun tenConsecutiveGesturesResetDecisionAndDistance() {
        val drag = detector()
        repeat(10) { step ->
            val start = 100f + step * 10
            drag.begin(true, 100f, start)
            assertEquals(0f, drag.distance, 0f)
            assertTrue(drag.move(100f, start + 70f))
            assertTrue(drag.finish(100f, start + 70f, 0f))
            assertFalse(drag.dragging)
        }
    }

    @Test fun screenCoordinatesRemainIndependentOfTranslatedContainer() {
        val drag = detector()
        drag.begin(true, 100f, 500f)
        assertTrue(drag.move(100f, 530f))
        // The caller keeps supplying raw values even if the view has moved down by 18 px.
        assertTrue(drag.move(100f, 570f))
        assertEquals(70f, drag.distance, 0f)
        assertTrue(drag.finish(100f, 570f, 0f))
    }

    @Test fun releaseWithoutInterceptedMoveCannotStartGesture() {
        val drag = detector()
        drag.begin(true, 100f, 100f)
        assertFalse(drag.finish(100f, 200f, 3000f))
    }
}
