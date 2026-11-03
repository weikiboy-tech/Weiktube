package com.weiktube.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AccountScreen(controller: YoutubeAccountController, onOpenUrl: (String) -> Unit, subscriptionsOnly: Boolean = false, homeConnection: @Composable () -> Unit = {}) {
    LaunchedEffect(controller) { controller.onAccountOpened() }
    val selected = controller.selectedPlaylist.takeUnless { subscriptionsOnly }
    BackHandler(selected != null) { controller.closePlaylist() }
    if (selected != null) {
        PlaylistScreen(controller, selected, onOpenUrl)
        return
    }
    var confirmDisconnect by remember { mutableStateOf(false) }
    var accountMenu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!subscriptionsOnly) item(key = "home_connection") { homeConnection() }
            if (controller.connected) {
                item {
                    val channel = controller.ownChannels.firstOrNull()
                    val name = channel?.title?.ifBlank { "Dein Konto" } ?: "Dein Konto"
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RemoteThumbnail(channel?.thumbnailUrl.orEmpty(), null, Modifier.size(56.dp), shape = CircleShape, fallback = Icons.Rounded.AccountCircle)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text("YouTube-Konto", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = controller::refresh, enabled = !controller.busy) { Icon(Icons.Rounded.Refresh, "Kontodaten aktualisieren") }
                        Box {
                            IconButton(onClick = { accountMenu = true }) { Icon(Icons.Rounded.MoreVert, "Kontooptionen") }
                            DropdownMenu(accountMenu, { accountMenu = false }) {
                                DropdownMenuItem(text = { Text("Google-Verbindung trennen") }, enabled = !controller.busy,
                                    onClick = { accountMenu = false; confirmDisconnect = true })
                            }
                        }
                    }
                }
                item {
                    Text(controller.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                if (subscriptionsOnly) {
                    item { Text("Abonnierte Kanäle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(controller.subscriptions, key = { "sub:${it.id}" }) { AccountItem(it, onOpenUrl, false) }
                    if (controller.loaded && controller.subscriptions.isEmpty() && !controller.busy) item { Text("Hier erscheinen deine abonnierten Kanäle.") }
                    if (controller.moreSubscriptions) item { OutlinedButton(onClick = controller::loadMoreSubscriptions, enabled = !controller.busy) { Text("Weitere Abos laden") } }
                } else {
                    if (controller.ownChannels.isNotEmpty()) item {
                        TextButton(onClick = { controller.ownChannels.firstOrNull()?.url?.let(onOpenUrl) }, contentPadding = PaddingValues(horizontal = 0.dp)) { Text("Deinen Kanal ansehen") }
                    }
                    items(controller.ownChannels.drop(1), key = { "own:${it.id}" }) { AccountItem(it, onOpenUrl, false) }
                    item { HorizontalDivider(); Text("Playlists", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                    items(controller.playlists, key = { "playlist:${it.id}" }) { playlist -> AccountItem(playlist, { controller.openPlaylist(playlist) }, true) }
                    if (controller.loaded && controller.playlists.isEmpty() && !controller.busy) item { Text("Hier erscheinen deine eigenen Playlists.") }
                    if (controller.morePlaylists) item { OutlinedButton(onClick = controller::loadMorePlaylists, enabled = !controller.busy) { Text("Weitere Playlists laden") } }
                }
            } else {
                item { Text(if (subscriptionsOnly) "Deine Abos" else "Dein YouTube", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.AccountCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Deine Kanäle, Abos und Playlists – direkt in Weiktube.", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = controller::connect, enabled = !controller.busy) { Text("Mit Google anmelden") }
                        if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(controller.status, style = MaterialTheme.typography.bodySmall)
                        Text("Du meldest dich sicher bei Google an. Manche privaten Videos können im Player weiterhin nicht verfügbar sein.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (controller.linked || controller.revocationPending) TextButton(onClick = { confirmDisconnect = true }, enabled = !controller.busy) {
                            Text(if (controller.revocationPending) "Google-Widerruf erneut versuchen" else "Google-Verbindung trennen")
                        }
                    }
                }
            }
        }
    }
    if (confirmDisconnect) AlertDialog(onDismissRequest = { confirmDisconnect = false }, title = { Text("Google-Verbindung trennen?") },
        text = { Text("Weiktube löscht die geladenen Kontodaten und widerruft den Google-Zugriff. Dein Google-/YouTube-Konto und deine Abos bleiben bestehen.") },
        confirmButton = { TextButton(onClick = { confirmDisconnect = false; controller.disconnect() }) { Text("Trennen") } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Abbrechen") } })
}

@Composable
private fun PlaylistScreen(controller: YoutubeAccountController, playlist: YoutubeAccountItem, openVideo: (String) -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = controller::closePlaylist) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück zu deinen Playlists") }
                    Text(playlist.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
            item {
                Text(controller.status)
                if (controller.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                else OutlinedButton(onClick = { controller.openPlaylist(playlist) }) { Text("Playlist aktualisieren") }
            }
            items(controller.playlistItems, key = { it.id }) { video ->
                Surface(onClick = { openVideo(video.url) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RemoteThumbnail(video.thumbnailUrl, null, Modifier.width(144.dp).aspectRatio(16f / 9f), fallback = Icons.Rounded.PlayCircle)
                        Text(video.title, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 3)
                    }
                }
            }
            if (controller.playlistLoaded && controller.playlistItems.isEmpty() && !controller.busy) item { Text("Keine verfügbaren Videoeinträge in dieser Playlist.") }
            if (controller.morePlaylistItems) item { OutlinedButton(onClick = controller::loadMorePlaylistItems, enabled = !controller.busy) { Text("Weitere Videos laden") } }
        }
    }
}

@Composable
private fun AccountItem(item: YoutubeAccountItem, open: (String) -> Unit, playlist: Boolean) {
    Surface(onClick = { open(item.url) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (playlist) RemoteThumbnail(item.thumbnailUrl, null, Modifier.width(136.dp).aspectRatio(16f / 9f), fallback = Icons.Rounded.PlaylistPlay)
            else RemoteThumbnail(item.thumbnailUrl, null, Modifier.size(52.dp), shape = CircleShape, fallback = Icons.Rounded.AccountCircle)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, style = MaterialTheme.typography.bodyLarge)
                if (item.description.isNotBlank()) Text(item.description, maxLines = if (playlist) 1 else 2, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!playlist) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

