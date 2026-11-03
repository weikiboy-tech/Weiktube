package com.weiktube.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weiktube.home.OriginalHomeActivity
import com.weiktube.home.ChromeHomeTestActivity

/** Website account is selected by YouTube; independent of the native Data API grant. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeConnectionEntry(activity: MainActivity, account: YoutubeAccountController) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Deine YouTube-Startseite", style = MaterialTheme.typography.titleMedium)
            Text("Melde dich bei Bedarf einmal auf der YouTube-Website an. Eine gültige Anmeldung wird beim nächsten Öffnen wiederverwendet. Die separate Freigabe für Abos und Playlists brauchst du dafür nicht.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                activity.startActivity(Intent(activity, OriginalHomeActivity::class.java))
            }) { Text("YouTube-Startseite öffnen") }
            TextButton(onClick = {
                activity.startActivity(Intent(activity, ChromeHomeTestActivity::class.java))
            }) { Text("Diagnose: mit Chrome vergleichen") }
            Text("Chrome verwendet eine eigene Anmeldung. Diese wird nicht in Weiktube übernommen.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
