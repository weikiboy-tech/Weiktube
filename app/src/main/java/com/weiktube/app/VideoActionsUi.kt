package com.weiktube.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Writes originate only from a button press. Rendering and sheet opening request read data only. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VideoActionsSheet(
    controller: VideoActionsController,
    account: YoutubeAccountController,
    videoId: String,
    onDismiss: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var comment by rememberSaveable(videoId) { mutableStateOf("") }
    // Keep the reply intent with the draft. Restoring only its text would turn it into a new comment.
    var replyParentId by rememberSaveable(videoId) { mutableStateOf<String?>(null) }
    var replyAuthor by rememberSaveable(videoId) { mutableStateOf("") }
    // A process-local authorization revision cannot establish identity across process recreation.
    // Restored replies therefore require an explicit target choice in the newly loaded comments.
    var replyConfirmed by remember(videoId, account) { mutableStateOf(false) }
    var replySession by remember(videoId, account) { mutableLongStateOf(account.sessionRevision) }
    var showPlaylists by remember(videoId) { mutableStateOf(false) }
    var submitted by remember(videoId) { mutableStateOf<Pair<String, String?>?>(null) }
    var seenPost by remember(videoId) { mutableIntStateOf(controller.postedCommentSequence) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = controller.busy || account.busy
    val replyTargetValid = replyParentId == null || (replyConfirmed && replySession == account.sessionRevision &&
        controller.videoId == videoId && controller.loaded && controller.comments.any { it.id == replyParentId })

    DisposableEffect(controller, videoId) {
        controller.onOpened(videoId)
        onDispose { controller.onHidden() }
    }
    LaunchedEffect(account, account.sessionRevision) {
        replyConfirmed = false
        submitted = null
    }
    LaunchedEffect(controller.postedCommentSequence) {
        val sequence = controller.postedCommentSequence
        if (sequence > seenPost) {
            // Do not erase edits typed while an earlier request was in progress.
            if (submitted == Pair(comment, replyParentId)) {
                comment = ""; replyParentId = null; replyAuthor = ""; replyConfirmed = false; keyboard?.hide()
            }
            submitted = null
        }
        seenPost = sequence
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.94f).imePadding()) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(controller.title.ifBlank { "Video" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                    if (controller.channelTitle.isNotBlank()) Text(controller.channelTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Schließen") }
            }
            if (account.connected) {
                FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = controller.ratingKnown && controller.rating == "like",
                        enabled = !busy && controller.ratingKnown,
                        onClick = controller::toggleLike,
                        label = { Text(if (!controller.ratingKnown) "Bewertung laden" else if (controller.rating == "like") "Gefällt dir" else "Gefällt mir") },
                        leadingIcon = { Icon(Icons.Rounded.ThumbUp, null, Modifier.size(18.dp)) }
                    )
                    FilterChip(
                        selected = controller.subscriptionKnown && controller.subscriptionId != null,
                        enabled = !busy && controller.subscriptionKnown && controller.channelId.isNotBlank(),
                        onClick = controller::toggleSubscription,
                        label = { Text(if (!controller.subscriptionKnown) "Abostatus laden" else if (controller.subscriptionId != null) "Abonniert" else "Abonnieren") },
                        leadingIcon = { Icon(if (controller.subscriptionKnown && controller.subscriptionId != null) Icons.Rounded.Check else Icons.Rounded.Add, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(onClick = {
                        showPlaylists = true
                        if (!account.loaded && !account.busy) account.refresh()
                    }, enabled = !busy && controller.loaded, label = { Text("Speichern") },
                        leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(18.dp)) })
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    if (controller.status.isNotBlank()) Text(controller.status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (account.busy && account.status.isNotBlank()) Text(account.status, style = MaterialTheme.typography.bodySmall)
                    if (!account.connected) {
                        Text("Melde dich an, um deine Bewertungen, Abos und Kommentare hier zu verwalten.", Modifier.padding(top = 8.dp))
                        Button(onClick = account::connect, enabled = !busy, modifier = Modifier.padding(top = 8.dp)) { Text("Mit Google anmelden") }
                    } else if (!busy) TextButton(onClick = { controller.load(videoId) }, contentPadding = PaddingValues(0.dp)) { Text("Aktualisieren") }
                }
                item { Text("Kommentare", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                controller.commentsMessage?.takeIf { it.isNotBlank() }?.let { message -> item { Text(message, style = MaterialTheme.typography.bodyMedium) } }
                if (controller.commentsNeedAuthorization) item {
                    OutlinedButton(onClick = controller::authorizeComments, enabled = !busy && controller.loaded) { Text("Kommentare freigeben") }
                }
                items(controller.comments, key = { "comment:${it.id}" }) { item ->
                    CommentRow(item, !busy && account.connected, onReply = {
                        replyParentId = item.id; replyAuthor = item.author
                        replySession = account.sessionRevision; replyConfirmed = true
                        focus.requestFocus(); keyboard?.show()
                    })
                    val replies = controller.replies[item.id].orEmpty()
                    if (item.replyCount > 0 && replies.isEmpty()) TextButton(onClick = { controller.loadReplies(item.id) }, enabled = !busy) {
                        Text("${item.replyCount} Antworten ansehen")
                    }
                    replies.forEach { reply ->
                        Column(Modifier.padding(start = 20.dp, top = 10.dp)) {
                            CommentRow(reply, !busy && account.connected, onReply = {
                                // YouTube replies belong to the top-level comment, not another reply.
                                replyParentId = item.id; replyAuthor = item.author
                                replySession = account.sessionRevision; replyConfirmed = true
                                focus.requestFocus(); keyboard?.show()
                            })
                        }
                    }
                    if (item.id in controller.moreReplies) TextButton(onClick = { controller.loadReplies(item.id) }, enabled = !busy, modifier = Modifier.padding(start = 20.dp)) {
                        Text("Weitere Antworten laden")
                    }
                }
                if (controller.moreComments) item {
                    OutlinedButton(onClick = controller::loadMoreComments, enabled = !busy) { Text("Weitere Kommentare laden") }
                }
            }
            if (account.connected) {
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (replyParentId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Antwort an $replyAuthor", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            TextButton(onClick = {
                                replyParentId = null; replyAuthor = ""; replyConfirmed = false; submitted = null
                            }, enabled = !busy) { Text("Als Kommentar") }
                        }
                        if (!busy && !replyTargetValid) Text(
                            "Wähle „Antworten“ beim gewünschten Kommentar erneut. Dein Text bleibt erhalten.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(comment, { comment = it }, modifier = Modifier.weight(1f).focusRequester(focus),
                            placeholder = { Text(if (replyParentId == null) "Kommentar hinzufügen …" else "Antwort hinzufügen …") },
                            minLines = 1, maxLines = 3, enabled = !busy)
                        IconButton(onClick = {
                            if (replyTargetValid) {
                                submitted = Pair(comment, replyParentId)
                                controller.postComment(comment.trim(), replyParentId)
                            }
                        }, enabled = !busy && controller.loaded && replyTargetValid && comment.isNotBlank(), modifier = Modifier.padding(start = 4.dp)) {
                            Icon(Icons.AutoMirrored.Rounded.Send, if (replyParentId == null) "Kommentar veröffentlichen" else "Antwort veröffentlichen")
                        }
                    }
                    Text("Beim ersten Schreiben fragt Google nach deiner Freigabe.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
    if (showPlaylists) AlertDialog(
        onDismissRequest = { showPlaylists = false },
        title = { Text("In Playlist speichern") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (account.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (account.playlists.isEmpty() && !account.busy) {
                    Text("Keine eigenen Playlists geladen. Erstelle eine Playlist auf YouTube oder lade deine Liste neu.")
                    TextButton(onClick = account::refresh, enabled = !account.busy) { Text("Playlists laden") }
                }
                account.playlists.forEach { playlist ->
                    TextButton(onClick = {
                        showPlaylists = false
                        controller.saveToPlaylist(playlist.id)
                    }, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Icon(Icons.Rounded.PlaylistAdd, null, Modifier.size(20.dp))
                        Text(playlist.title, Modifier.weight(1f).padding(start = 10.dp), maxLines = 2)
                    }
                }
                if (account.morePlaylists) TextButton(onClick = account::loadMorePlaylists, enabled = !account.busy) { Text("Weitere Playlists laden") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { showPlaylists = false }) { Text("Abbrechen") } }
    )
}

@Composable
private fun CommentRow(comment: YoutubeComment, canReply: Boolean, onReply: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(comment.author, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text(comment.text, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (comment.likeCount > 0) {
                Icon(Icons.Rounded.ThumbUp, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(comment.likeCount.toString(), Modifier.padding(start = 5.dp, end = 12.dp), style = MaterialTheme.typography.labelSmall)
            }
            TextButton(onClick = onReply, enabled = canReply, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
                Icon(Icons.AutoMirrored.Rounded.Reply, null, Modifier.size(18.dp))
                Text("Antworten", Modifier.padding(start = 5.dp))
            }
        }
    }
}
