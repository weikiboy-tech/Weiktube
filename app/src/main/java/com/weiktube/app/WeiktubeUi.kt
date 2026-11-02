package com.weiktube.app

import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
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
    val storageError by model.error.collectAsStateWithLifecycle()
    val dark = when (prefs.theme) { "Dunkel" -> true; "Hell" -> false; else -> isSystemInDarkTheme() }
    val colors = if (dark) darkColorScheme(primary = mint, background = ink, surface = ink, secondary = Color(0xFFC1B6FF))
        else lightColorScheme(primary = Color(0xFF006B60), background = Color(0xFFF6F8FB), surface = Color(0xFFF6F8FB))
    LaunchedEffect(prefs) {
        activity.shield.config = ShieldConfig(prefs.shield, prefs.ads, prefs.trackers,
            runCatching { ShieldEngine.parse(prefs.domains) }.getOrDefault(emptySet()),
            runCatching { ShieldEngine.parse(prefs.exceptions) }.getOrDefault(emptySet()))
    }
    LaunchedEffect(activity.foreground, activity.pip) {
        while (activity.foreground || activity.pip) {
            activity.player.poll()
            activity.blockedCount = activity.shield.blocked.get()
            activity.keepAwake(activity.player.playing)
            delay(1000)
        }
    }
    LaunchedEffect(storageError) { storageError?.let(activity::message) }
    BackHandler(enabled = activity.fullscreen || activity.player.source != null || !activity.isWebTab || activity.browser.view.canGoBack()) { activity.back() }
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
    val webWatch = !hasPlayer && Links.videoId(a.browser.currentUrl) != null
    val full = hasPlayer && (p.expanded || LocalConfiguration.current.screenHeightDp < 450)
    val landscapePlayer = full && LocalConfiguration.current.screenHeightDp < 450
    var menu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(searchOpen) { if (searchOpen) searchFocus.requestFocus() }
    BackHandler(searchOpen) { searchOpen = false }
    var account by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    var showLinkDialog by remember { mutableStateOf(false) }
    var directUrl by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().then(if (a.pip) Modifier else Modifier.safeDrawingPadding())) {
        if (!a.pip && !landscapePlayer && !webWatch) {
            if (searchOpen) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { searchOpen = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Suche schließen") }
                    OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f).focusRequester(searchFocus), singleLine = true,
                        placeholder = { Text("Bei YouTube suchen") }, shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); a.search(query); searchOpen = false }),
                        trailingIcon = { IconButton(onClick = { focus.clearFocus(); a.search(query); searchOpen = false }) { Icon(Icons.Rounded.Search, "Suche starten") } })
                }
            } else Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!a.isWebTab) IconButton(onClick = { a.selectTab(0) }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
                Image(painterResource(R.drawable.ic_weiktube), "Weiktube Logo", Modifier.size(28.dp))
                Text("Weiktube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp).weight(1f))
                IconButton(onClick = { searchOpen = true }) { Icon(Icons.Rounded.Search, "YouTube durchsuchen") }
                IconButton(onClick = { account = true }) { Icon(Icons.Rounded.AccountCircle, "Konto und Anmeldung") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Weitere Optionen") }
                    DropdownMenu(menu, { menu = false }) {
                        if (Links.videoId(a.browser.currentUrl) != null) DropdownMenuItem(text = { Text("Im Mini-Player öffnen") }, onClick = { menu = false; a.minimizeWebVideo() })
                        DropdownMenuItem(text = { Text("Video-Link öffnen") }, onClick = { menu = false; showLinkDialog = true })
                        DropdownMenuItem(text = { Text("Lokaler Verlauf") }, onClick = { menu = false; a.selectTab(6) })
                        DropdownMenuItem(text = { Text("Einstellungen & Updates") }, onClick = { menu = false; a.selectTab(5) })
                        DropdownMenuItem(text = { Text("Seite neu laden") }, onClick = { menu = false; a.browser.view.reload() })
                    }
                }
            }
        }
        // Keep both AndroidView nodes in the same composition position for the whole Activity lifetime.
        if (!a.pip && !landscapePlayer) UpdateNotice(a.updater) { a.selectTab(5) }
        Box(Modifier.fillMaxWidth().weight(if (full || a.pip) 0.001f else 1f).clipToBounds()) {
            AndroidView(factory = { a.browser.view }, modifier = Modifier.fillMaxSize(), update = {
                it.visibility = if (a.isWebTab && !full && !a.pip) android.view.View.VISIBLE else android.view.View.INVISIBLE
            })
            if (!full && !a.pip) {
                when (a.tab) {
                    2 -> SettingsScreen(prefs, model, a, shieldsOnly = true)
                    5 -> SettingsScreen(prefs, model, a)
                    6 -> HistoryScreen(prefs, model, a)
                    else -> {
                        if (a.browser.progress < 1f) LinearProgressIndicator(progress = { a.browser.progress }, modifier = Modifier.fillMaxWidth())
                        a.browser.error?.let { error ->
                            Surface(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = MaterialTheme.colorScheme.errorContainer) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(error)
                                    TextButton(onClick = { a.browser.view.reload() }) { Text("Erneut versuchen") }
                                }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().then(if (full || a.pip) Modifier.weight(1f) else Modifier).clipToBounds().background(Color.Black)) {
            if (hasPlayer && !a.pip) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { p.expanded = !p.expanded }) { Icon(if (p.expanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess, if (p.expanded) "Mini-Player" else "Player vergrößern") }
                        Text(p.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = p::toggle) { Icon(if (p.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (p.playing) "Pause" else "Abspielen") }
                        IconButton(onClick = p::close) { Icon(Icons.Rounded.Close, "Player schließen") }
                    }
                }
            }
            AndroidView(factory = { p.view }, update = { it.visibility = if (hasPlayer) android.view.View.VISIBLE else android.view.View.GONE }, modifier = Modifier.fillMaxWidth().clipToBounds().then(
                if (full || a.pip) Modifier.weight(1f) else Modifier.height(if (hasPlayer) 200.dp else 0.dp)))
            if (hasPlayer && !a.pip && full && !landscapePlayer) {
                Surface {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        p.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = a::share) { Icon(Icons.Rounded.Share, "Video teilen") }
                            IconButton(onClick = { p.expanded = false }) { Icon(Icons.Rounded.PictureInPictureAlt, "Als Mini-Player weitersehen") }
                            IconButton(onClick = a::enterPip, enabled = !p.isYoutube) { Icon(Icons.Rounded.PictureInPicture, "Android Picture-in-Picture für HTTPS-Videos") }
                            IconButton(onClick = { p.pause(); a.external(p.source!!) }) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, "Im Browser öffnen") }
                            if (p.isYoutube) IconButton(onClick = {
                                val source = p.source!!; p.close(); a.tab = 0; a.browser.view.onResume(); a.browser.navigate(source, true)
                            }) { Icon(Icons.AutoMirrored.Rounded.Comment, "YouTube-Seite mit Kommentaren und Likes") }
                        }
                        if (p.isYoutube) Text("YouTube · PiP nicht freigeschaltet. Kommentare und Likes über die Videoseite.", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
            }
        }
        if (!a.pip && !landscapePlayer) {
            NavigationBar(Modifier.height(64.dp), containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
                listOf("Startseite" to Icons.Rounded.Home, "Shorts" to Icons.Rounded.SlowMotionVideo, "Adblock" to Icons.Rounded.Shield,
                    "Abos" to Icons.Rounded.Subscriptions, "Du" to Icons.Rounded.AccountCircle).forEachIndexed { index, item ->
                    NavigationBarItem(selected = a.tab == index, onClick = { a.selectTab(index) }, icon = { Icon(item.second, item.first, Modifier.size(24.dp)) }, label = { Text(item.first, maxLines = 1, style = MaterialTheme.typography.labelSmall) }, alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent, selectedIconColor = MaterialTheme.colorScheme.onSurface, selectedTextColor = MaterialTheme.colorScheme.onSurface))
                }
            }
        }
    }
    LaunchedEffect(full, a.tab, a.pip) {
        if (full || !a.isWebTab || a.pip) {
            a.browser.view.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause()})", null)
            a.browser.view.onPause()
        } else a.browser.view.onResume()
    }
    if (account) AlertDialog(onDismissRequest = { account = false }, title = { Text("Dein YouTube-Konto") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Dein Konto, deine Abos und Playlists öffnest du über YouTube.")
            Text("Google unterstützt die Anmeldung in eingebetteten Browsern nicht zuverlässig. Die sichere Anmeldung erfolgt deshalb extern. Eine externe Sitzung wird nicht in Weiktube übernommen.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = { account = false; a.external("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F") }) { Text("Im Browser anmelden") } },
        dismissButton = { TextButton(onClick = { account = false; a.tab = 4; a.player.expanded = false; a.browser.openAccount() }) { Text("YouTube-Kontoseite") } })
    if (showLinkDialog) AlertDialog(onDismissRequest = { showLinkDialog = false },
        title = { Text("Video öffnen") }, text = {
            Column {
                Text("YouTube-Link oder direkte HTTPS-Videodatei (z. B. MP4). PiP ist nur für direkte Videos verfügbar, die du abspielen darfst.")
                OutlinedTextField(directUrl, { directUrl = it; linkError = null }, label = { Text("https://…") }, modifier = Modifier.fillMaxWidth())
                linkError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = {
            val url = directUrl.trim()
            if (Links.videoId(url) != null || Links.directVideoAllowed(url)) {
                a.openVideo(url); showLinkDialog = false
            } else linkError = "Bitte einen gültigen YouTube-Videolink oder eine direkte HTTPS-Video-URL eingeben."
        }) { Text("Öffnen") } }, dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("Abbrechen") } })
}

@Composable
private fun HistoryScreen(prefs: Preferences, model: WeiktubeModel, a: MainActivity) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Deine Mediathek", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
            item { Text("Zuletzt in Weiktube geöffnet · lokal auf diesem Gerät", style = MaterialTheme.typography.bodyMedium) }
            item { Row {
                TextButton(onClick = { a.tab = 0; a.browser.view.onResume(); a.browser.navigate("https://m.youtube.com/feed/library") }) { Text("YouTube-Playlists") }
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
                    model.string("domains", domains); model.string("exceptions", exceptions); error = null; a.message("Filter gespeichert.")
                } catch (e: IllegalArgumentException) { error = e.message }
            }) { Text("Listen speichern") }
            OutlinedButton(onClick = { a.tab = 0; a.player.expanded = false; a.browser.view.onResume(); a.browser.testShield() }) { Text("Shields-Prüfseite öffnen") }
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
            Text("Google kann Anmeldungen in eingebetteten Browsern ablehnen. Anmeldung wird im externen Browser geöffnet; dessen Sitzung lässt sich nicht in Weiktube übernehmen. Abos, Likes und private Playlists sind deshalb hier nicht garantiert verfügbar. Weiktube liest keine Passwörter aus.")
            Text("Der YouTube-Mini-Player bleibt mindestens 200 dp hoch. Keine Hintergrundwiedergabe oder YouTube-PiP. Direkte HTTPS-Videos unterstützen Android-PiP. Kein Download und keine Premium-Umgehung.")
            Text("Weiktube selbst enthält keine Analyse- oder Werbe-SDKs. Geladene Webseiten und der YouTube-Player erhalten technisch notwendige Netzwerk- und Gerätedaten. Verlauf und Einstellungen bleiben lokal; Cookies sind davon getrennt.")
            TextButton(onClick = { a.external("https://www.youtube.com/t/terms") }) { Text("YouTube-Nutzungsbedingungen") }
            TextButton(onClick = { a.external("https://policies.google.com/privacy") }) { Text("Google-Datenschutzerklärung") }
            Text("Weiktube 1.1 · Unabhängige App, kein Produkt von Google oder Brave.", style = MaterialTheme.typography.labelMedium)
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
