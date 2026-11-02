package com.weiktube.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UpdateSettings(controller: UpdateController) {
    var source by remember(controller.feedUrl) { mutableStateOf(controller.feedUrl) }
    var advanced by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("App-Updates", style = MaterialTheme.typography.titleLarge)
        Text("Installiert: Weiktube ${controller.installedVersion}", style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Automatisch nach Updates suchen")
                Text("Beim Öffnen, höchstens einmal täglich", style = MaterialTheme.typography.bodySmall)
            }
            Switch(controller.automatic, controller::updateAutomaticEnabled, enabled = !controller.busy)
        }
        Text(controller.status, style = MaterialTheme.typography.bodyMedium)
        if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        controller.release?.let { release ->
            if (release.notes.isNotBlank()) Text(release.notes, style = MaterialTheme.typography.bodySmall)
            if (controller.ready) Button(onClick = controller::install, enabled = !controller.busy) { Text("Update installieren") }
            else Button(onClick = controller::downloadUpdate, enabled = !controller.busy) { Text("${release.versionName} herunterladen") }
        }
        OutlinedButton(onClick = controller::check, enabled = !controller.busy) { Text("Jetzt nach Updates suchen") }
        Text("Downloads starten erst nach deinem Tippen. Vor der Installation werden Prüfsumme, App-Paket, Version und Signierschlüssel kontrolliert. Android verlangt deine Bestätigung.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Updatequelle ausblenden" else "Updatequelle ändern") }
        if (advanced) {
            OutlinedTextField(source, { source = it }, label = { Text("HTTPS-Adresse der update.json") }, modifier = Modifier.fillMaxWidth(), enabled = !controller.busy, maxLines = 4)
            TextButton(onClick = { controller.saveSource(source) }, enabled = !controller.busy) { Text("Quelle speichern") }
        }
    }
}

/** Small notice for a successfully discovered newer version; route to the native settings page. */
@Composable
fun UpdateNotice(controller: UpdateController, openSettings: () -> Unit) {
    val update = controller.release ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SystemUpdate, null)
            Text("Weiktube ${update.versionName} verfügbar", Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = openSettings) { Text("Ansehen") }
        }
    }
}

