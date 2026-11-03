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
            Text("Originale YouTube-Startseite testen", style = MaterialTheme.typography.titleMedium)
            Text("Zeigt die echte Website mit dem dort aktuell angemeldeten Konto. Prüfe zuerst deine Startseite, danach die Übergabe an den bestehenden Player. Deine bisherige Startseite bleibt erhalten.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = {
                activity.startActivity(Intent(activity, OriginalHomeActivity::class.java))
            }) { Text("1. Original-Home in Weiktube testen") }
            OutlinedButton(onClick = {
                activity.startActivity(Intent(activity, ChromeHomeTestActivity::class.java))
            }) { Text("2. Mit Chrome vergleichen") }
            Text("Gecko und Chrome verwenden getrennte Website-Anmeldungen. Die separate Google-API-Verbindung ist dafür nicht erforderlich.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
