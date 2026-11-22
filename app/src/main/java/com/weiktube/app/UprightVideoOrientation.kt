package com.weiktube.app

import android.content.pm.ActivityInfo

/** Three allowed orientations, with dead bands to avoid chatter near diagonals. */
internal object UprightVideoOrientation {
    fun fromSensor(degrees: Int, previous: Int): Int = when (degrees) {
        in 60..120 -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        in 240..300 -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        in 0..30, in 330..359 -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else -> safe(previous)
    }
    fun isUpright(degrees: Int) = degrees in 0..30 || degrees in 330..359

    fun safe(orientation: Int): Int = when (orientation) {
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE -> orientation
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
