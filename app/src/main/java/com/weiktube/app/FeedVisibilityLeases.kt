package com.weiktube.app

/** Each composed feed surface owns one acquisition until its DisposableEffect is disposed. */
internal class FeedVisibilityLeases {
    private var owners = 0
    val visible: Boolean get() = owners > 0
    fun acquire() { owners++ }
    fun release(): Boolean { owners = (owners - 1).coerceAtLeast(0); return !visible }
    fun clear() { owners = 0 }
}
