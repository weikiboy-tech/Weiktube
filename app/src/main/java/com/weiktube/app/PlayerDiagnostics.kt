package com.weiktube.app

/** Fixed event vocabulary only: no page URLs, media IDs, bridge payloads or exception messages. */
internal object PlayerDiagnostics {
    enum class Event {
        ACTIVITY_CREATE, ACTIVITY_RESTORE, ACTIVITY_START, ACTIVITY_RESUME, ACTIVITY_PAUSE,
        ACTIVITY_STOP, ACTIVITY_DESTROY, CONFIGURATION, PIP_ENTER, PIP_EXIT,
        VIEW_CREATED, VIEW_PAUSED, VIEW_RESUMED, VIEW_DESTROYED, RENDERER_CRASHED,
        RENDERER_KILLED, RENDERER_UNRESPONSIVE, RENDERER_RESPONSIVE, NAVIGATION_STARTED,
        NAVIGATION_FINISHED, NAVIGATION_FAILED, RECOVERY_PENDING, RECOVERY_STARTED,
        RECOVERY_LIMIT, RECOVERY_CANCELLED, VIEW_CREATE_FAILED, CLEANUP_FAILED,
        PRESENTATION_NORMAL, PRESENTATION_MINI, PRESENTATION_PIP, USER_PLAY,
        GATE_ALLOWED, GATE_HOST_BLOCKED, GATE_POLICY_BLOCKED, GATE_FILTER_BLOCKED,
    }
    enum class PauseReason { USER, HOST_STOP, SCREEN_LOCK, SHORTS_SELECTED, ACCOUNT_ACTION, FILTER_FAILURE, FILTER_RECOVERY, CLOSE }
    fun pause(reason: PauseReason, generation: Long) {
        android.util.Log.i("WeiktubePlayer", "APP_PAUSE reason=${reason.name} generation=$generation")
    }
    fun record(event: Event, generation: Long = 0, state: String = "HOST") {
        android.util.Log.i("WeiktubePlayer", "${event.name} generation=$generation state=$state")
    }
}
