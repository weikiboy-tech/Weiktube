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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** A chronological view of actual uploads from the subscribed channels loaded by the controller. */
@Composable
fun SubscriptionsScreen(
    feed: SubscriptionFeedController,
    account: YoutubeAccountController,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    DisposableEffect(feed) {
        feed.onOpened()
        onDispose { feed.onHidden() }
    }
    val listState = rememberLazyListState()
    val channelState = rememberLazyListState()
    val busy = feed.busy || account.busy
    val channelsById = remember(feed.channels) { feed.channels.associateBy { it.id } }

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 20.dp)) {
            item(key = "heading") {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Neue Videos deiner Abos", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = feed::refresh, enabled = account.connected && !busy) {
                        Icon(Icons.Rounded.Refresh, "Abovideos aktualisieren")
                    }
                }
            }
            if (!account.connected) {
                item(key = "signin") {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.AccountCircle, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Sieh die neuesten Videos deiner abonnierten Kanäle.", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = account::connect, enabled = !busy) { Text("Mit Google anmelden") }
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        if (account.status.isNotBlank()) Text(account.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                if (feed.channels.isNotEmpty()) item(key = "channels") {
                    LazyRow(state = channelState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(feed.channels, key = { it.id }) { channel ->
                            Column(Modifier.width(72.dp).clickable { onOpenChannel(channel.url) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                RemoteThumbnail(channel.thumbnailUrl, null, Modifier.size(60.dp), shape = CircleShape, fallback = Icons.Rounded.AccountCircle)
                                Text(channel.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                item(key = "status") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (feed.loadedChannelCount > 0) Text(
                            "Neueste Uploads aus ${feed.loadedChannelCount} geladenen Kanälen",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (feed.status.isNotBlank()) Text(feed.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (account.busy && account.status.isNotBlank()) Text(account.status, style = MaterialTheme.typography.bodySmall)
                        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                items(feed.videos, key = { "video:${it.id}" }) { video ->
                    val channel = channelsById[video.channelId]
                    SubscriptionVideoCard(video, channel, onOpenVideo, onOpenChannel)
                }
                if (!busy && feed.videos.isEmpty()) item(key = "empty") {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (feed.loaded) "Keine verfügbaren Videos in den bisher geladenen Abos." else "Deine Abovideos konnten noch nicht geladen werden.", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = feed::refresh) { Text("Erneut laden") }
                    }
                }
                if (feed.hasMoreChannels) item(key = "more") {
                    OutlinedButton(onClick = feed::loadMoreChannels, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("Weitere abonnierte Kanäle laden")
                    }
                }
            }
        }
    }
}

@Composable
internal fun SubscriptionVideoCard(
    video: SubscriptionVideo,
    channel: YoutubeAccountItem?,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val date = remember(video.publishedAt) {
        runCatching {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
                .format(Instant.parse(video.publishedAt).atZone(ZoneId.systemDefault()))
        }.getOrDefault("")
    }
    Surface(onClick = { onOpenVideo(video.url) }, modifier = Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                RemoteThumbnail(video.thumbnailUrl, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), shape = RoundedCornerShape(0.dp), fallback = Icons.Rounded.PlayCircle)
                if (video.duration.isNotBlank()) Surface(
                    color = Color.Black.copy(alpha = 0.82f), contentColor = Color.White, shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(video.duration, Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                RemoteThumbnail(
                    channel?.thumbnailUrl.orEmpty(), channel?.title, Modifier.size(38.dp).then(
                        if (channel != null) Modifier.clickable { onOpenChannel(channel.url) } else Modifier
                    ), shape = CircleShape, fallback = Icons.Rounded.AccountCircle
                )
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(listOf(video.channelTitle, date).filter { it.isNotBlank() }.joinToString(" · "), maxLines = 2,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
