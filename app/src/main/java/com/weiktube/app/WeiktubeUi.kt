package com.weiktube.app

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

private val ink = Color(0xFF0F0F0F)
private val mint = Color(0xFF65D8C8)

@Composable
fun WeiktubeApp(activity: MainActivity, model: WeiktubeModel) {
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val shieldConfig by model.shieldConfig.collectAsStateWithLifecycle()
    val storageError by model.error.collectAsStateWithLifecycle()
    val dark = when (prefs.theme) { "Dunkel" -> true; "Hell" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) darkColorScheme(primary = mint, background = ink, surface = ink,
        surfaceContainer = Color(0xFF212121), surfaceContainerHigh = Color(0xFF272727), secondary = Color(0xFFAAAAAA))
        else lightColorScheme(primary = Color(0xFF006B60), background = Color.White, surface = Color.White,
            surfaceContainer = Color(0xFFF2F2F2), surfaceContainerHigh = Color(0xFFEAEAEA), secondary = Color(0xFF606060))
    LaunchedEffect(shieldConfig) { activity.shield.config = shieldConfig }
    LaunchedEffect(activity.foreground, activity.pip) {
        while (activity.foreground || activity.pip) {
            activity.player.poll()
            activity.browser.poll()
            activity.shorts?.poll()
            activity.blockedCount = activity.shield.blocked.get()
            activity.keepAwake(activity.player.playing || (activity.isWebTab && activity.activeBrowser.mediaPlaying))
            delay(1000)
        }
    }
    LaunchedEffect(storageError) { storageError?.let(activity::message) }
    BackHandler(enabled = activity.canHandleBack) { activity.back() }
    MaterialTheme(colorScheme = colors) {
        Surface(Modifier.fillMaxSize()) {
            WeiktubeContent(activity, model, prefs)
        }
    }
}

@Composable
private fun WeiktubeContent(a: MainActivity, model: WeiktubeModel, prefs: Preferences) {
    val p = a.player
    val hasPlayer = p.source != null
    val full = hasPlayer && p.expanded
    val personalHome = a.isPersonalHome
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var menu by remember { mutableStateOf(false) }
    var actions by remember { mutableStateOf(false) }
    var showLinkDialog by remember { mutableStateOf(false) }
    var directUrl by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    val tabStates = rememberSaveableStateHolder()
    LaunchedEffect(p.source) { actions = false }
    LaunchedEffect(dark) { a.browser.setTheme(dark); a.shorts?.setTheme(dark) }
    LaunchedEffect(full, a.tab, hasPlayer, a.pip, a.fullscreen, a.foreground, actions, personalHome) {
        a.browser.playbackElsewhere = hasPlayer
        a.shorts?.playbackElsewhere = full
        if (full || a.pip || a.tab != 0 || personalHome) a.browser.pauseMedia() else a.browser.resume()
        if (full || a.pip || a.tab != 1) a.shorts?.pauseMedia() else a.shorts?.resume()
        if (a.foreground || a.pip) p.resumeView()
        p.setPlaybackAllowed(!actions && (a.foreground || a.pip) && (a.tab != 1 || full))
        a.syncOriginalHomeVisibility()
    }
    Column(Modifier.fillMaxSize().then(if (a.pip) Modifier else Modifier.safeDrawingPadding())) {
        if (!a.pip && !full && !personalHome && !a.browser.searchActive) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.ic_weiktube), "Weiktube Logo", Modifier.size(28.dp))
                Text("Weiktube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp).weight(1f))
                IconButton(onClick = a::openSearch) { Icon(Icons.Rounded.Search, "YouTube durchsuchen") }
                IconButton(onClick = a::openAccountScreen) { Icon(Icons.Rounded.AccountCircle, "Konto und Anmeldung") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Weitere Optionen") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Video-Link öffnen") }, onClick = { menu = false; showLinkDialog = true })
                        DropdownMenuItem(text = { Text("Verlauf") }, onClick = { menu = false; a.selectTab(6) })
                        DropdownMenuItem(text = { Text("Einstellungen & Updates") }, onClick = { menu = false; a.selectTab(5) })
                        if (a.isWebTab) DropdownMenuItem(text = { Text("Seite neu laden") }, onClick = { menu = false; a.reloadCurrentPage() })
                    }
                }
            }
        }
        if (!a.pip && !full) UpdateNotice(a.updater) { a.selectTab(5) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val availableHeight = maxHeight
            // The original Gecko page and existing WebViews keep stable view identities across tabs.
            key(a.browser.view) {
                AndroidView(factory = { a.browser.view }, modifier = Modifier.fillMaxSize(), update = {
                    it.visibility = if (a.tab == 0 && !personalHome && !full && !a.pip) android.view.View.VISIBLE else android.view.View.INVISIBLE
                })
            }
            key(a.originalHome.view) {
                AndroidView(factory = { a.originalHome.view }, modifier = Modifier.fillMaxSize(), update = {
                    it.visibility = if (a.isOriginalHomeVisible) android.view.View.VISIBLE else android.view.View.INVISIBLE
                })
            }
            a.shorts?.let { shorts ->
                key(shorts.view) {
                    AndroidView(factory = { shorts.view }, modifier = Modifier.fillMaxSize(), update = {
                        it.visibility = if (a.tab == 1 && !full && !a.pip) android.view.View.VISIBLE else android.view.View.INVISIBLE
                    })
                }
            }
            if (!full && !a.pip) {
                tabStates.SaveableStateProvider(a.tab) {
                if (!personalHome) {
                when (a.tab) {
                    2 -> SettingsScreen(prefs, model, a, shieldsOnly = true)
                    3 -> SubscriptionsScreen(a.subscriptionFeed, a.youtubeAccount, a::openVideo, a::openAccountUrl)
                    4 -> AccountScreen(a.youtubeAccount, a::openAccountUrl, homeConnection = { HomeConnectionEntry(a, a.youtubeAccount) })
                    5 -> SettingsScreen(prefs, model, a)
                    6 -> HistoryScreen(prefs, model, a)
                    else -> {
                        if (a.activeBrowser.progress < 1f) LinearProgressIndicator(progress = { a.activeBrowser.progress }, modifier = Modifier.fillMaxWidth())
                        a.activeBrowser.error?.let { error ->
                            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(error)
                                    TextButton(onClick = { a.activeBrowser.reload() }) { Text("Erneut versuchen") }
                                }
                            }
                        }
                    }
                }
                }
                }
            }
            val targetHeight = if (!hasPlayer || (a.tab == 1 && !full && !a.pip)) 0.dp else if (full || a.pip) availableHeight else minOf(244.dp, availableHeight)
            Column(Modifier.align(Alignment.BottomEnd).fillMaxWidth().height(targetHeight).clipToBounds().background(Color.Black)) {
                key(p.view) {
                    AndroidView(factory = { p.view }, modifier = Modifier.fillMaxWidth().weight(1f).clipToBounds(), update = {
                        it.visibility = if (hasPlayer) android.view.View.VISIBLE else android.view.View.GONE
                        p.setPresentation(!full && !a.pip, dark)
                    })
                }
                if (hasPlayer && !a.pip) {
                    if (p.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                    p.error?.let { error ->
                        Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(error, style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = p::retry) { Text("Erneut laden") }
                            }
                        }
                    }
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().height(44.dp)
                            .pointerInput(full) {
                                var drag = 0f
                                detectVerticalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                                    if (drag > 35) p.expanded = false else if (drag < -35) p.expanded = true
                                }) { _, dy -> drag += dy }
                            }, verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { p.expanded = !p.expanded }) {
                                Icon(if (full) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, if (full) "Video minimieren" else "Video öffnen")
                            }
                            Text(p.title, Modifier.weight(1f).clickable { p.expanded = !p.expanded }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            if (!full) IconButton(onClick = p::toggle) { Icon(if (p.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p.playing) "Pause" else "Abspielen") }
                            if (full && p.isYoutube) IconButton(onClick = { p.pause(); actions = true }) { Icon(Icons.Rounded.AccountCircle, "Gefällt mir, Abos und Kommentare mit deinem Konto") }
                            if (full) IconButton(onClick = a::share) { Icon(Icons.Rounded.Share, "Video teilen") }
                            if (full && !p.isYoutube) IconButton(onClick = a::enterPip) { Icon(Icons.Rounded.PictureInPicture, "Bild-in-Bild") }
                            IconButton(onClick = a::closeVideo) { Icon(Icons.Rounded.Close, "Video schließen") }
                        }
                    }
                }
            }
        }
        if (!a.pip && !full) {
            NavigationBar(Modifier.height(60.dp), containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                listOf("Startseite" to Icons.Rounded.Home, "Shorts" to Icons.Rounded.SlowMotionVideo, "Adblock" to Icons.Rounded.Shield,
                    "Abos" to Icons.Rounded.Subscriptions, "Du" to Icons.Rounded.AccountCircle).forEachIndexed { index, item ->
                    NavigationBarItem(selected = a.tab == index, onClick = { if (index == 0) a.goHome() else a.selectTab(index) }, icon = { Icon(item.second, item.first, Modifier.size(24.dp)) }, label = { Text(item.first, maxLines = 1, style = MaterialTheme.typography.labelSmall) }, alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedIconColor = MaterialTheme.colorScheme.onSurface, selectedTextColor = MaterialTheme.colorScheme.onSurface))
                }
            }
        }
    }
    val id = p.source?.let(Links::videoId)
    if (actions && id != null) VideoActionsSheet(a.videoActions, a.youtubeAccount, id) { actions = false }
    if (showLinkDialog) AlertDialog(onDismissRequest = { showLinkDialog = false }, title = { Text("Video öffnen") }, text = {
        Column {
            OutlinedTextField(directUrl, { directUrl = it; linkError = null }, label = { Text("YouTube-Link oder HTTPS-Video") }, modifier = Modifier.fillMaxWidth())
            linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val url = directUrl.trim()
        if (Links.videoId(url) != null || Links.directVideoAllowed(url)) { a.openVideo(url); showLinkDialog = false }
        else linkError = "Bitte einen gültigen Video-Link eingeben."
    }) { Text("Öffnen") } }, dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("Abbrechen") } })
}
@Composable
private fun HistoryScreen(prefs: Preferences, model: WeiktubeModel, a: MainActivity) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Deine Mediathek", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            item { Text("Zuletzt in Weiktube geöffnet · lokal auf diesem Gerät", style = MaterialTheme.typography.bodyMedium) }
            item { Row {
                TextButton(onClick = a::openAccountScreen) { Text("Deine Playlists") }
                TextButton(onClick = model::clearHistory) { Text("Verlauf löschen") }
            } }
            if (prefs.history.isEmpty()) item { Text("Noch keine Videos im Verlauf. Öffne ein Video oder teile einen YouTube-Link mit Weiktube.") }
            items(prefs.history, key = { it.url }) { item ->
                Card(onClick = { a.openVideo(item.url) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.time)), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(prefs: Preferences, model: WeiktubeModel, a: MainActivity, shieldsOnly: Boolean = false) {
    var domains by remember(prefs.domains) { mutableStateOf(prefs.domains) }
    var exceptions by remember(prefs.exceptions) { mutableStateOf(prefs.exceptions) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (shieldsOnly) "Adblock" else "Einstellungen", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Datenschutz und Wiedergabe", style = MaterialTheme.typography.bodyLarge)
            if (shieldsOnly) {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
                Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.primary)
                Text("${a.blockedCount} blockiert", style = MaterialTheme.typography.headlineMedium)
                Text("Anfragen in dieser Sitzung · YouTube ausgenommen", style = MaterialTheme.typography.bodySmall)
            } }
            SettingSwitch("Weiktube Shields", prefs.shield) { model.boolean("shield", it) }
            SettingSwitch("Basisliste: Werbedomains", prefs.ads) { model.boolean("ads", it) }
            SettingSwitch("Basisliste: Trackingdomains", prefs.trackers) { model.boolean("trackers", it) }
            Text("Die kleinen integrierten Domainlisten filtern zusätzliche Webseiten. YouTube und dessen Player werden nicht gefiltert. First-Party- und serverseitige Werbung wird nicht zuverlässig erkannt.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(domains, { domains = it }, label = { Text("Eigene Filterliste") }, placeholder = { Text("ads.example.org\n0.0.0.0 tracker.example.org") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(exceptions, { exceptions = it }, label = { Text("Ausnahmen (Domains)") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
            Text("Eine Domain pro Zeile oder Hosts-Format. Subdomains eingeschlossen. Keine EasyList-/ABP-Regeln, keine automatischen Downloads.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                try { ShieldEngine.parse(domains); ShieldEngine.parse(exceptions)
                    model.saveFilters(domains, exceptions); error = null; a.message("Filter gespeichert.")
                } catch (e: IllegalArgumentException) { error = e.message }
            }) { Text("Listen speichern") }
            OutlinedButton(onClick = a::openShieldTest) { Text("Shields-Prüfseite öffnen") }
            OutlinedButton(onClick = { a.shield.blocked.set(0); a.blockedCount = 0 }) { Text("Sitzungszähler zurücksetzen") }
            }
            if (!shieldsOnly) {
            UpdateSettings(a.updater)
            HorizontalDivider()
            Text("Darstellung", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Hell", "Dunkel").forEach { mode -> FilterChip(selected = prefs.theme == mode, onClick = { model.string("theme", mode) }, label = { Text(mode) }) }
            }
            SettingSwitch("Lokalen Verlauf speichern", prefs.saveHistory) { model.boolean("saveHistory", it) }
            TextButton(onClick = model::clearHistory) { Text("Lokalen Videoverlauf löschen") }
            OutlinedButton(onClick = { confirmClear = true }) { Text("Cookies und Webdaten löschen") }
            HorizontalDivider()
            Text("Konten & Wiedergabe", style = MaterialTheme.typography.titleLarge)
            Text("Empfehlungen, Suchvorschläge, Shorts, Kanäle und Playlists kommen von YouTubes mobiler Website. Dort stehen auch Qualitätswahl, Kommentare und Likes bereit, soweit YouTube sie anbietet. Die Suchlupe öffnet die Suche. Ergebnisse kommen direkt von YouTube.")
            Text("Unter Du verbindest du dein Google-Konto für Abos und Playlists. Das Kontosymbol am Video öffnet Likes, Abos und Kommentare. Für Änderungen fragt Google nach deiner Freigabe. Die Website und ihre Empfehlungen behalten eine getrennte Sitzung.")
            Text("Der YouTube-Mini-Player bleibt mindestens 200 dp hoch. Keine Hintergrundwiedergabe oder YouTube-PiP. Direkte HTTPS-Videos unterstützen Android-PiP. Kein Download und keine Premium-Umgehung.")
            Text("Weiktube selbst enthält keine Analyse- oder Werbe-SDKs. Geladene Webseiten und der YouTube-Player erhalten technisch notwendige Netzwerk- und Gerätedaten. Verlauf und Einstellungen bleiben lokal; Cookies sind davon getrennt.")
            TextButton(onClick = { a.external("https://www.youtube.com/t/terms") }) { Text("YouTube-Nutzungsbedingungen") }
            TextButton(onClick = { a.external("https://policies.google.com/privacy") }) { Text("Google-Datenschutzerklärung") }
            Text("Weiktube 1.2.3 · Unabhängige App, kein Produkt von Google oder Brave.", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Webdaten löschen?") }, text = { Text("Alle Websitzungen, Cookies und der Cache werden gelöscht. Der Player wird geschlossen. Dein lokaler Verlauf bleibt erhalten.") }, confirmButton = {
        TextButton(onClick = { a.clearWebData(); confirmClear = false }) { Text("Löschen") }
    }, dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") } })
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(value, change)
    }
}
