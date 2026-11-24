package com.weiktube.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PlayerSettings(model: WeiktubeModel, value: PlayerPreferences, activity: MainActivity) {
    var confirmDislikes by remember { mutableStateOf(false) }
    val player = activity.player
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Videoplayer", style = MaterialTheme.typography.titleLarge)
            Text("Gesten im Querformat-Vollbild. Im Hochformat bleibt Herunterziehen zum Minimieren erhalten.", style = MaterialTheme.typography.bodySmall)
            PlayerSwitch("Helligkeitsgesten (links)", value.brightness) { model.boolean("playerBrightness", it) }
            PlayerSwitch("Lautstärkegesten (rechts)", value.volume) { model.boolean("playerVolume", it) }
            PlayerSwitch("Doppeltipp-Spulen", value.doubleTap) { model.boolean("playerDoubleTap", it) }
            PlayerChoice("Sprungweite", "${value.seekSeconds} Sekunden", PlayerPreferences.steps.map { it.toString() to "$it Sekunden" }) { model.string("playerSeek", it) }
            PlayerSwitch("Gedrückt halten für schnelle Wiedergabe", value.hold) { model.boolean("playerHold", it) }
            PlayerChoice("Geschwindigkeit beim Gedrückthalten", "${value.holdSpeed.toString().replace('.', ',')}×",
                player.supportedHoldRates.map { it.toString() to "${it.toString().replace('.', ',')}×" }) { model.string("playerHoldSpeed", it) }
            Text(if (player.supportedHoldRates.isEmpty()) "Zum Prüfen der Geschwindigkeiten ein Video öffnen. Ohne gemeldete Unterstützung wird keine Beschleunigung erzwungen."
                else "Angeboten werden nur die vom aktuellen Player gemeldeten Geschwindigkeiten.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Videoqualität", style = MaterialTheme.typography.titleMedium)
            val qualities = (listOf(0) + player.supportedQualities).distinct().map { it.toString() to qualityLabel(it) }
            PlayerChoice("Bevorzugte Qualität im WLAN", qualityLabel(value.wifiQuality), qualities) { model.string("playerWifiQuality", it) }
            PlayerChoice("Bevorzugte Qualität bei mobilen Daten", qualityLabel(value.mobileQuality), qualities) { model.string("playerMobileQuality", it) }
            Text(player.qualityStatus, style = MaterialTheme.typography.bodySmall)
            Text("Präferenz für das nächste Video; auch getaktetes WLAN verwendet die mobile Einstellung. Die Auswahl zeigt nur Stufen des aktuell geöffneten Videos. Fehlt die gewünschte Stufe, wird die nächstniedrigere versucht. Ohne bestätigte Steuerung entscheidet YouTube automatisch. Manuelle Änderungen bleiben erhalten.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Erweiterungen", style = MaterialTheme.typography.titleMedium)
            PlayerSwitch("Return YouTube Dislike", value.dislikes) {
                if (it) confirmDislikes = true else model.boolean("playerDislikes", false)
            }
            Text("Dislike-Zahlen von Return YouTube Dislike sind teilweise geschätzt und keine garantierten offiziellen YouTube-Zahlen. Nur Anzeige, keine Übermittlung deiner Bewertungen.", style = MaterialTheme.typography.bodySmall)
            if (value.dislikes) Text(player.dislikeStatus, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { activity.external("https://returnyoutubedislike.com/") }) { Text("Datenquelle: Return YouTube Dislike") }
        }
    }
    if (confirmDislikes) AlertDialog(onDismissRequest = { confirmDislikes = false },
        title = { Text("Dislike-Daten extern abrufen?") },
        text = { Text("Weiktube sendet die öffentliche Video-ID an returnyoutubedislikeapi.com. Der Dienst sieht dabei technisch bedingt auch deine IP-Adresse. Google-Cookies, Zugangsdaten und Tokens werden nicht übertragen. Die Zahlen sind teilweise geschätzt. Ergebnisse werden lokal bis zu einer Stunde zwischengespeichert.") },
        confirmButton = { TextButton(onClick = { model.boolean("playerDislikes", true); confirmDislikes = false }) { Text("Aktivieren") } },
        dismissButton = { TextButton(onClick = { confirmDislikes = false }) { Text("Abbrechen") } })
}
private fun qualityLabel(value: Int) = if (value == 0) "Automatisch" else "${value}p"
@Composable
private fun PlayerSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, change)
    }
}
@Composable
private fun PlayerChoice(label: String, current: String, choices: List<Pair<String,String>>, change: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { open = true }, enabled = choices.isNotEmpty()) { Text(current) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                choices.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { change(key); open = false }) }
            }
        }
    }
}
