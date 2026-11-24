package com.weiktube.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

internal object YoutubeLinkSettings {
    internal fun intents(packageName: String, sdk: Int): List<Intent> = buildList {
        val app = Uri.fromParts("package", packageName, null)
        if (sdk >= 31) add(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, app))
        add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app))
        add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }
    fun open(activity: Activity): Boolean = launch(intents(activity.packageName, Build.VERSION.SDK_INT), activity::startActivity)
    internal fun launch(candidates: List<Intent>, start: (Intent) -> Unit): Boolean {
        for (candidate in candidates) {
            try { start(candidate); return true }
            catch (_: ActivityNotFoundException) { /* OEM fallback */ }
            catch (_: SecurityException) { /* restricted settings on this device */ }
        }
        return false
    }
}
