package com.weiktube.app

import android.view.View
import android.view.ViewParent
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Fixed event vocabulary only: no page URLs, media IDs, bridge payloads or exception messages. */
internal object PlayerDiagnostics {
    // Fresh for each process, unlike a PID which Android may reuse. Never persisted to preferences.
    val processMarker: String = UUID.randomUUID().toString()
    const val PROCESS_MARKER_STATE_KEY = "weiktube_player_process"
    private val activityIds = AtomicLong()
    private val controllerIds = AtomicLong()
    private val viewIds = AtomicLong()
    fun nextActivityId(): Long = activityIds.incrementAndGet()
    fun nextControllerId(): Long = controllerIds.incrementAndGet()
    fun nextViewId(): Long = viewIds.incrementAndGet()

    enum class Event {
        ACTIVITY_CREATE, ACTIVITY_RESTORE, ACTIVITY_START, ACTIVITY_RESUME, ACTIVITY_PAUSE,
        ACTIVITY_STOP, ACTIVITY_DESTROY, ACTIVITY_SAVE_STATE, CONFIGURATION, PIP_ENTER, PIP_EXIT,
        VIEW_CREATED, VIEW_PAUSED, VIEW_RESUMED, VIEW_DESTROYED, RENDERER_CRASHED,
        VIEW_ATTACHED, VIEW_DETACHED, VIEW_HOST_UPDATE, VIEW_HOST_RELEASE,
        RENDERER_KILLED, RENDERER_UNRESPONSIVE, RENDERER_RESPONSIVE, NAVIGATION_STARTED,
        NAVIGATION_FINISHED, NAVIGATION_FAILED, RECOVERY_PENDING, RECOVERY_STARTED,
        RECOVERY_LIMIT, RECOVERY_CANCELLED, VIEW_CREATE_FAILED, CLEANUP_FAILED,
        STALE_DOCUMENT_IGNORED, DOCUMENT_COMMITTED, DOCUMENT_LOST, RECOVERY_DOCUMENT_READY,
        PRESENTATION_NORMAL, PRESENTATION_MINI, PRESENTATION_PIP, USER_PLAY,
        GATE_ALLOWED, GATE_HOST_BLOCKED, GATE_POLICY_BLOCKED, GATE_FILTER_BLOCKED,
    }
    enum class PauseReason { USER, HOST_STOP, SCREEN_LOCK, SHORTS_SELECTED, ACCOUNT_ACTION, FILTER_FAILURE, FILTER_RECOVERY, CLOSE }
    enum class ScreenSignal { ACTIVITY_START, ACTIVITY_RESUME, SCREEN_OFF, SCREEN_ON, USER_PRESENT, WINDOW_FOCUS }
    enum class RestoreKind { COLD_START, ACTIVITY_RECREATION, PROCESS_RESTORE, RESTORE_UNKNOWN }
    enum class Visibility { VISIBLE, INVISIBLE, GONE, UNKNOWN }

    /** Primitive-only snapshot: no View references, class names, document state or page text. */
    data class ViewSnapshot(
        val attached: Boolean,
        val shown: Boolean,
        val hasParent: Boolean,
        val parentMatches: Boolean?,
        val visibility: Visibility,
        val windowVisibility: Visibility,
        val windowFocus: Boolean,
        val width: Int,
        val height: Int,
    )

    /** Read on the UI thread. A null expectedParent means ownership was not checked. */
    fun captureView(view: View, expectedParent: ViewParent? = null) = ViewSnapshot(
        attached = view.isAttachedToWindow,
        shown = view.isShown,
        hasParent = view.parent != null,
        parentMatches = expectedParent?.let { view.parent === it },
        visibility = visibility(view.visibility),
        windowVisibility = visibility(view.windowVisibility),
        windowFocus = view.hasWindowFocus(),
        width = view.width,
        height = view.height,
    )

    private fun visibility(value: Int) = when (value) {
        View.VISIBLE -> Visibility.VISIBLE
        View.INVISIBLE -> Visibility.INVISIBLE
        View.GONE -> Visibility.GONE
        else -> Visibility.UNKNOWN
    }

    /** Missing/legacy markers cannot prove process death; never log the supplied saved value. */
    fun classifyRestore(hasSavedState: Boolean, savedProcessMarker: String?): RestoreKind = when {
        !hasSavedState -> RestoreKind.COLD_START
        savedProcessMarker == null || !PROCESS_MARKER.matches(savedProcessMarker) -> RestoreKind.RESTORE_UNKNOWN
        savedProcessMarker == processMarker -> RestoreKind.ACTIVITY_RECREATION
        else -> RestoreKind.PROCESS_RESTORE
    }

    fun pause(reason: PauseReason, generation: Long, activityId: Long = 0, controllerId: Long = 0, viewId: Long = 0) {
        android.util.Log.i("WeiktubePlayer", "APP_PAUSE reason=${reason.name} generation=${generation.coerceAtLeast(0)} " +
            identities(activityId, controllerId, viewId))
    }

    fun screen(signal: ScreenSignal, activityId: Long, interactive: Boolean, locked: Boolean,
               previouslyBlocked: Boolean, blocked: Boolean, foreground: Boolean) {
        android.util.Log.i("WeiktubePlayer", "SCREEN_STATE signal=${signal.name} " +
            identities(activityId, 0, 0) + " interactive=$interactive keyguard=$locked " +
            "previouslyBlocked=$previouslyBlocked blocked=$blocked foreground=$foreground")
    }

    fun record(event: Event, generation: Long = 0, state: String = "HOST", activityId: Long = 0,
               controllerId: Long = 0, viewId: Long = 0, snapshot: ViewSnapshot? = null) {
        android.util.Log.i("WeiktubePlayer", format(event, generation, state, activityId, controllerId, viewId, snapshot))
    }

    internal fun format(event: Event, generation: Long = 0, state: String = "HOST", activityId: Long = 0,
                        controllerId: Long = 0, viewId: Long = 0, snapshot: ViewSnapshot? = null): String = buildString {
        append("${event.name} generation=${generation.coerceAtLeast(0)} state=${state.takeIf { it in STATES } ?: "UNKNOWN"} ")
        append(identities(activityId, controllerId, viewId))
        snapshot?.let {
            append(" attached=${it.attached} shown=${it.shown} hasParent=${it.hasParent}")
            append(" parentMatches=${it.parentMatches?.toString() ?: "UNKNOWN"}")
            append(" visibility=${it.visibility.name} windowVisibility=${it.windowVisibility.name} windowFocus=${it.windowFocus}")
            append(" width=${it.width.coerceIn(0, 65_535)} height=${it.height.coerceIn(0, 65_535)}")
        }
    }

    private fun identities(activityId: Long, controllerId: Long, viewId: Long): String =
        "process=$processMarker activity=${activityId.coerceAtLeast(0)} controller=${controllerId.coerceAtLeast(0)} view=${viewId.coerceAtLeast(0)}"

    private val PROCESS_MARKER = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val STATES = setOf(
        "HOST", "DESTROYED", "RENDERER_GONE", "UNRESPONSIVE", "PAUSED", "LOADING", "IDLE", "READY",
        "COLD_START", "ACTIVITY_RECREATION", "PROCESS_RESTORE", "RESTORE_UNKNOWN", "CONFIGURATION_CHANGE", "FINISHING",
    )
}
