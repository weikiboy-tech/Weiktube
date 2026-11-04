package com.weiktube.app

import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class PlayerDiagnosticsTest {
    @Test fun classifiesNewActivityAndSameProcessRecreationWithoutGuessingLegacyState() {
        assertEquals(PlayerDiagnostics.RestoreKind.COLD_START, PlayerDiagnostics.classifyRestore(false, null))
        assertEquals(PlayerDiagnostics.RestoreKind.ACTIVITY_RECREATION,
            PlayerDiagnostics.classifyRestore(true, PlayerDiagnostics.processMarker))
        assertEquals(PlayerDiagnostics.RestoreKind.RESTORE_UNKNOWN, PlayerDiagnostics.classifyRestore(true, null))
        assertEquals(PlayerDiagnostics.RestoreKind.RESTORE_UNKNOWN, PlayerDiagnostics.classifyRestore(true, "not-a-process-marker"))
    }

    @Test fun differentSavedProcessMarkerIdentifiesProcessRestoreWithoutReusingPid() {
        val oldProcess = java.util.UUID.randomUUID().toString()
        assertNotEquals(PlayerDiagnostics.processMarker, oldProcess)
        assertEquals(PlayerDiagnostics.RestoreKind.PROCESS_RESTORE, PlayerDiagnostics.classifyRestore(true, oldProcess))
        assertEquals(PlayerDiagnostics.RestoreKind.COLD_START, PlayerDiagnostics.classifyRestore(false, oldProcess))
        assertEquals(PlayerDiagnostics.processMarker, java.util.UUID.fromString(PlayerDiagnostics.processMarker).toString())
    }

    @Test fun allocatesDistinctActivityControllerAndViewIdentitiesWithinTheSameProcess() {
        for (allocate in listOf(PlayerDiagnostics::nextActivityId, PlayerDiagnostics::nextControllerId, PlayerDiagnostics::nextViewId)) {
            val first = allocate()
            val second = allocate()
            assertTrue(first > 0)
            assertTrue(second > first)
        }
    }

    @Test fun formatKeepsLegacyCallShapeAndIncludesProcessAndOptionalNativeIdentities() {
        val legacy = PlayerDiagnostics.format(PlayerDiagnostics.Event.VIEW_CREATED, 2, "READY")
        assertEquals("VIEW_CREATED generation=2 state=READY process=${PlayerDiagnostics.processMarker} activity=0 controller=0 view=0", legacy)
        val correlated = PlayerDiagnostics.format(PlayerDiagnostics.Event.VIEW_ATTACHED, 3, "PAUSED", 4, 5, 6)
        assertTrue(correlated.endsWith("activity=4 controller=5 view=6"))
        assertTrue(correlated.contains("process=${PlayerDiagnostics.processMarker}"))
    }

    @Test fun formatRejectsUntrustedFreeTextAndBoundsNumericFields() {
        val secret = "https://private.invalid/watch?v=PRIVATE\nCookie: private; token=secret"
        val snapshot = PlayerDiagnostics.ViewSnapshot(false, false, false, null,
            PlayerDiagnostics.Visibility.GONE, PlayerDiagnostics.Visibility.INVISIBLE, false, -1, Int.MAX_VALUE)
        val log = PlayerDiagnostics.format(PlayerDiagnostics.Event.VIEW_HOST_UPDATE, -1, secret, -2, -3, -4, snapshot)
        assertFalse(log.contains(secret))
        assertFalse(log.contains("private"))
        assertTrue(log.contains("generation=0 state=UNKNOWN"))
        assertTrue(log.contains("activity=0 controller=0 view=0"))
        assertTrue(log.endsWith("width=0 height=65535"))
        assertTrue(log.contains("parentMatches=UNKNOWN"))
    }

    @Test fun capturesActualParentOwnershipAndLayoutWithoutRetainingViewOrPageData() {
        val context = RuntimeEnvironment.getApplication()
        val expected = FrameLayout(context)
        val other = FrameLayout(context)
        val view = View(context)
        expected.addView(view)
        view.layout(0, 0, 196, 110)
        view.visibility = View.INVISIBLE

        val attachedToParent = PlayerDiagnostics.captureView(view, expected)
        assertTrue(attachedToParent.hasParent)
        assertEquals(true, attachedToParent.parentMatches)
        assertFalse(attachedToParent.attached) // A parent alone does not mean a window attachment.
        assertFalse(attachedToParent.shown)
        assertEquals(PlayerDiagnostics.Visibility.INVISIBLE, attachedToParent.visibility)
        assertEquals(196, attachedToParent.width)
        assertEquals(110, attachedToParent.height)
        assertEquals(false, PlayerDiagnostics.captureView(view, other).parentMatches)
        assertNull(PlayerDiagnostics.captureView(view).parentMatches)

        expected.removeView(view)
        val detached = PlayerDiagnostics.captureView(view, expected)
        assertFalse(detached.hasParent)
        assertEquals(false, detached.parentMatches)
    }

    @Test fun lifecycleStatesAndTypedAttachmentFieldsAreEmittedWithoutArbitraryStrings() {
        for (kind in PlayerDiagnostics.RestoreKind.entries) {
            assertTrue(PlayerDiagnostics.format(PlayerDiagnostics.Event.ACTIVITY_RESTORE, state = kind.name)
                .contains("state=${kind.name}"))
        }
        val snapshot = PlayerDiagnostics.ViewSnapshot(true, true, true, true,
            PlayerDiagnostics.Visibility.VISIBLE, PlayerDiagnostics.Visibility.VISIBLE, true, 320, 180)
        val log = PlayerDiagnostics.format(PlayerDiagnostics.Event.VIEW_ATTACHED, snapshot = snapshot)
        assertTrue(log.endsWith("attached=true shown=true hasParent=true parentMatches=true visibility=VISIBLE windowVisibility=VISIBLE windowFocus=true width=320 height=180"))
    }
}
