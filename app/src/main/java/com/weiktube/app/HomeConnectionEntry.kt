package com.weiktube.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weiktube.home.HomeConnectionActivity
import com.weiktube.home.HomeEngine

/** Explicit feasibility connection; never replaces Home or opens itself automatically. */
@Composable
fun HomeConnectionEntry(activity: MainActivity, account: YoutubeAccountController) {
    LaunchedEffect(account.linked) {
        if (!account.linked) HomeEngine.clearIfInitialized(activity)
    }
    val channel = account.ownChannels.firstOrNull().takeIf { account.connected }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Persönliche YouTube-Startseite", style = MaterialTheme.typography.titleMedium)
            Text("Verbindungstest: Prüfe deine YouTube-Sitzung und den echten Home-Datenabruf. Deine bisherige Startseite und dein Player bleiben aktiv.", style = MaterialTheme.typography.bodySmall)
            Button(enabled = channel != null && !account.busy, onClick = {
                val selected = channel ?: return@Button
                activity.startActivity(Intent(activity, HomeConnectionActivity::class.java)
                    .putExtra(HomeConnectionActivity.EXTRA_EXPECTED_CHANNEL_ID, selected.id)
                    .putExtra(HomeConnectionActivity.EXTRA_EXPECTED_CHANNEL_TITLE, selected.title))
            }) { Text("YouTube-Startseite verbinden") }
            if (channel == null) Text("Zuerst das gewünschte Google-Konto verbinden und die Kanaldaten laden.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
