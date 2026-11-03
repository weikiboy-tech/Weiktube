package com.weiktube.home

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.browser.customtabs.ContentActionSelectedData
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.ExperimentalCustomContentAction

/** Non-exported, explicit PendingIntent target; never receives a background navigation callback. */
class OriginalHomeHandoffActivity : ComponentActivity() {
    @androidx.annotation.OptIn(markerClass = [ExperimentalCustomContentAction::class])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ChromeHomeTestState.handoffEnabled(this)) {
            unavailable("Der Videoübergabe-Test ist nicht aktiv. Öffne zuerst den Chrome-Test und aktiviere dort Schritt 2.")
            return
        }
        val canonical = runCatching {
            val selected = if (intent.action == ChromeHomeTestState.LINK_ACTION) ContentActionSelectedData.fromIntent(intent) else null
            ChromeHandoffSelection.videoUrl(
                intent.action, intent.dataString, selected?.linkUrl,
                selected?.clickedContentTargetType == CustomTabsIntent.CONTENT_TARGET_TYPE_LINK,
                selected?.triggeredActionId
            )
        }.getOrNull()
        // A missing long-press link must not fall back to a different, currently open video.
        if (canonical == null) {
            unavailable("Chrome hat keine unterstützte YouTube-Videoadresse übergeben. Die Leistenaktion benötigt eine geöffnete Videoseite; bei einem langen Druck muss Chrome den ausgewählten Videolink liefern.")
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(canonical)).setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            ChromeHomeTestState.setHandoffEnabled(this, false)
            finish()
        } catch (_: Exception) {
            unavailable("Der bisherige Weiktube-Player konnte nicht geöffnet werden. Kein anderer Player wurde gestartet.")
        }
    }

    private fun unavailable(message: String) {
        AlertDialog.Builder(this).setTitle("Videoübergabe nicht möglich").setMessage(message)
            .setPositiveButton("Zurück") { _, _ -> finish() }
            .setOnCancelListener { finish() }.show()
    }
}

/** Rejects ambiguous or unsupported callback data before the existing player sees a URL. */
internal object ChromeHandoffSelection {
    fun videoUrl(action: String?, pageUrl: String?, linkUrl: String?, linkTarget: Boolean, actionId: Int?): String? {
        val candidate = when (action) {
            ChromeHomeTestState.TOOLBAR_ACTION -> pageUrl
            ChromeHomeTestState.LINK_ACTION -> if (linkTarget && actionId == ChromeHomeTestState.LINK_ACTION_ID) linkUrl else null
            else -> null
        }
        return candidate?.let { OriginalHomePolicy.videoUrl(it) }
    }
}
