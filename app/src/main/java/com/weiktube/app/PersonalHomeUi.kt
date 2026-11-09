package com.weiktube.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Real account uploads and device-local history in the native home screen. */
@Composable
fun PersonalHomeScreen(
    feed: SubscriptionFeedController,
    account: YoutubeAccountController,
    history: List<HistoryEntry>,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    DisposableEffect(feed) {
        feed.onOpened()
        onDispose { feed.onHidden() }
    }
    val listState = rememberLazyListState()
    val historyState = rememberLazyListState()
    val recent = remember(history) { history.distinctBy { it.url }.take(12) }
    val ownChannel = account.ownChannels.firstOrNull().takeIf { account.connected }
    val channelsById = remember(feed.channels) { feed.channels.associateBy { it.id } }
    val busy = feed.busy || account.busy

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 20.dp)) {
            item(key = "account") {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RemoteThumbnail(
                        ownChannel?.thumbnailUrl.orEmpty(), ownChannel?.title,
                        Modifier.size(48.dp).then(if (ownChannel != null) Modifier.clickable { onOpenChannel(ownChannel.url) } else Modifier),
                        shape = CircleShape, fallback = Icons.Rounded.AccountCircle
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(ownChannel?.title?.ifBlank { "Deine Startseite" } ?: "Deine Startseite",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Deine Abos und dein Verlauf",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (recent.isNotEmpty()) {
                item(key = "history_title") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Zuletzt angesehen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Verlauf auf diesem Gerät", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item(key = "history") {
                    LazyRow(state = historyState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(recent, key = { it.url }) { entry ->
                            HistoryPreview(entry, onOpenVideo)
                        }
                    }
                }
            }
            item(key = "subscriptions_title") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Videos deiner Abos", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = feed::refresh, enabled = account.connected && !busy) {
                        Icon(Icons.Rounded.Refresh, "Abovideos aktualisieren")
                    }
                }
            }
            if (!account.connected) {
                item(key = "signin") {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Melde dich an, um hier neue Uploads deiner abonnierten Kanäle zu sehen.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = account::connect, enabled = !busy) { Text("Mit Google anmelden") }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (account.status.isNotBlank()) Text(account.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item(key = "subscriptions_status") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (feed.loadedChannelCount > 0) Text("Neueste Uploads aus ${feed.loadedChannelCount} geladenen Kanälen",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (feed.status.isNotBlank()) Text(feed.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (account.busy && account.status.isNotBlank()) Text(account.status, style = MaterialTheme.typography.bodySmall)
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                items(feed.videos, key = { "video:${it.id}" }) { video ->
                    SubscriptionVideoCard(video, channelsById[video.channelId], onOpenVideo, onOpenChannel)
                }
                if (!busy && feed.videos.isEmpty()) item(key = "empty_subscriptions") {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (feed.loaded) "Keine verfügbaren Videos in den bisher geladenen Abos." else "Deine Abovideos konnten noch nicht geladen werden.")
                        OutlinedButton(onClick = feed::refresh) { Text("Erneut laden") }
                    }
                }
                if (feed.hasMoreChannels) item(key = "more_channels") {
                    OutlinedButton(onClick = feed::loadMoreChannels, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Weitere abonnierte Kanäle laden")
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryPreview(entry: HistoryEntry, onOpenVideo: (String) -> Unit) {
    val thumbnail = remember(entry.url) { Links.videoId(entry.url)?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }.orEmpty() }
    Surface(onClick = { onOpenVideo(entry.url) }, modifier = Modifier.width(208.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            RemoteThumbnail(thumbnail, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = RoundedCornerShape(0.dp), fallback = Icons.Rounded.PlayCircle)
            Text(entry.title.ifBlank { "Video" }, Modifier.padding(10.dp), maxLines = 2, minLines = 2,
                overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
