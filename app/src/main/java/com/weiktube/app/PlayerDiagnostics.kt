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
    }
    fun record(event: Event, generation: Long = 0, state: String = "HOST") {
        android.util.Log.i("WeiktubePlayer", "${event.name} generation=$generation state=$state")
    }
}
