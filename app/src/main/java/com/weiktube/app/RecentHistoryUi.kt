package com.weiktube.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Uses the existing DataStore history, independently of Google authorization. */
@Composable
internal fun RecentHistorySection(prefs: Preferences, onOpenVideo: (String) -> Unit, onShowAll: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Kürzlich angesehen", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold)
            TextButton(onClick = onShowAll) { Text("Alle anzeigen") }
        }
        Text("Verlauf auf diesem Gerät · nicht mit YouTube synchronisiert",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!prefs.saveHistory) Text("Verlauf pausiert. Neue Videos werden nicht gespeichert.",
            style = MaterialTheme.typography.bodySmall)
        if (prefs.history.isEmpty()) Text("Noch keine Videos im lokalen Verlauf.", style = MaterialTheme.typography.bodyMedium)
        else LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(prefs.history.take(12), key = { it.url }) { HistoryPreview(it, onOpenVideo) }
        }
    }
}

@Composable
internal fun HistoryDeleteDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Lokalen Verlauf löschen?") },
        text = { Text("Alle in Weiktube gespeicherten Verlaufseinträge werden entfernt. Dein YouTube-Konto bleibt unverändert.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Löschen") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } })
}
