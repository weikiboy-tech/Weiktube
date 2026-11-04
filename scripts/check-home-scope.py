"""Verify original app source, allowing only exact reviewed integration and player changes."""
from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parents[1]
adblock_review = json.loads((root / "scripts/adblock-reviewed-changes.json").read_text(encoding="utf-8"))["files"]
baseline = json.loads((root / "scripts/home-original-source-hashes.json").read_text(encoding="utf-8"))
tab_review = json.loads((root / "scripts/home-tab-reviewed-changes.json").read_text(encoding="utf-8"))["files"]
rotation_review = json.loads((root / "scripts/rotation-reviewed-changes.json").read_text(encoding="utf-8"))["files"]
mini_review = json.loads((root / "scripts/mini-player-reviewed-changes.json").read_text(encoding="utf-8"))["files"]
website_login_hook = '''    fun openWebsiteAccount() {
        // Queue before showing Home so a cold engine opens the account entry directly.
        originalHome.openWebsiteAccount()
        selectTab(0)
    }
'''
allowed = {
    "app/src/main/java/com/weiktube/app/MainActivity.kt": [
        ("        com.weiktube.home.HomeEngine.clearIfInitialized(this)\n", ""),
        ("import android.app.Activity\n", ""),
        ("import androidx.activity.result.contract.ActivityResultContracts\n", ""),
        ("import androidx.lifecycle.Lifecycle\n", ""),
        ("import com.weiktube.home.OriginalHomeActivity\n", ""),
        ("import com.weiktube.home.OriginalHomeReturn\n", ""),
        ('''    private var pendingOriginalHomeVideo: String? = null
    private var originalHomePlayer = false
    // Registered before STARTED. A result may arrive before onResume permits playback.
    private val originalHomeLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val url = OriginalHomeReturn.acceptPlayerResult(
                result.data?.getStringExtra(OriginalHomeReturn.EXTRA_RETURN_TOKEN),
                result.data?.getStringExtra(OriginalHomeReturn.EXTRA_VIDEO_URL)
            )
            if (url == null) {
                if (OriginalHomeReturn.resumeToken() == null) OriginalHomeReturn.discard()
                android.util.Log.w("WeiktubeHome", "RETURN_SESSION_UNAVAILABLE")
                message("Die Videoübergabe ist nicht mehr gültig. Öffne die YouTube-Startseite erneut und tippe das Video noch einmal an.")
            } else {
                pendingOriginalHomeVideo = url
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) openPendingOriginalHomeVideo()
            }
        }
    }

    fun openOriginalHome() {
        val resume = OriginalHomeReturn.resumeToken()
        try {
            originalHomeLauncher.launch(Intent(this, OriginalHomeActivity::class.java).apply {
                if (resume != null) putExtra(OriginalHomeReturn.EXTRA_RESUME_TOKEN, resume)
            })
            originalHomePlayer = false
        } catch (_: Exception) {
            android.util.Log.w("WeiktubeHome", "HOME_LAUNCH_FAILED")
            message("Die YouTube-Startseite konnte nicht geöffnet werden. Bitte erneut versuchen.")
        }
    }
    private fun openPendingOriginalHomeVideo() {
        if (!foreground || isFinishing || isDestroyed || !::player.isInitialized) return
        val url = pendingOriginalHomeVideo ?: return
        pendingOriginalHomeVideo = null
        openVideo(url)
        originalHomePlayer = OriginalHomeReturn.resumeToken() != null
    }
    fun closeVideo() {
        val returnHome = originalHomePlayer && OriginalHomeReturn.resumeToken() != null
        player.close()
        originalHomePlayer = false
        if (returnHome) openOriginalHome()
    }
''', ""),
        ('            originalHomePlayer = savedInstanceState.getBoolean("original_home_player", false) && OriginalHomeReturn.resumeToken() != null\n', ""),
        ('''    fun openVideo(url: String) {
        pendingOriginalHomeVideo = null
        originalHomePlayer = false
''', '    fun openVideo(url: String) {\n'),
        ('        openPendingOriginalHomeVideo()\n', ""),
        ('        outState.putBoolean("original_home_player", originalHomePlayer)\n', ""),
        ('''        player.source != null && player.expanded -> {
            player.expanded = false
            if (originalHomePlayer && OriginalHomeReturn.resumeToken() != null) {
                player.pause()
                openOriginalHome()
            }
            true
        }
''', '        player.source != null && player.expanded -> { player.expanded = false; true }\n'),
        ('        pendingOriginalHomeVideo = null; originalHomePlayer = false\n        OriginalHomeReturn.discard()\n', ""),
        ('        if (isFinishing) OriginalHomeReturn.discard()\n', "")],
    "app/src/main/java/com/weiktube/app/WeiktubeUi.kt": [
        ("AccountScreen(a.youtubeAccount, a::openAccountUrl, homeConnection = { HomeConnectionEntry(a, a.youtubeAccount) })",
         "AccountScreen(a.youtubeAccount, a::openAccountUrl)"),
        ('IconButton(onClick = a::closeVideo) { Icon(Icons.Rounded.Close, "Video schließen") }',
         'IconButton(onClick = p::close) { Icon(Icons.Rounded.Close, "Video schließen") }')],
    "app/src/main/java/com/weiktube/app/AccountUi.kt": [
        ("subscriptionsOnly: Boolean = false, homeConnection: @Composable () -> Unit = {}", "subscriptionsOnly: Boolean = false"),
        ('            if (!subscriptionsOnly) item(key = "home_connection") { homeConnection() }\n', ""),
        ('"Konto für Abos und Playlists"', '"YouTube-Konto"'),
        ('"Google-Freigabe widerrufen"', '"Google-Verbindung trennen"'),
        ('if (subscriptionsOnly) "Deine Abos" else "Abos und Playlists"', 'if (subscriptionsOnly) "Deine Abos" else "Dein YouTube"'),
        ('"Optional: Gib den Zugriff auf deine Abos und Playlists für diese Kontoansicht frei."', '"Deine Kanäle, Abos und Playlists – direkt in Weiktube."'),
        ('OutlinedButton(onClick = controller::connect, enabled = !controller.busy) { Text("Abos und Playlists freigeben") }',
         'Button(onClick = controller::connect, enabled = !controller.busy) { Text("Mit Google anmelden") }'),
        ('"Diese Google-Freigabe ist von deiner Anmeldung auf der YouTube-Startseite getrennt. Manche privaten Videos können im Player weiterhin nicht verfügbar sein."',
         '"Du meldest dich sicher bei Google an. Manche privaten Videos können im Player weiterhin nicht verfügbar sein."'),
        ('"Freigabe für Kontofunktionen widerrufen"', '"Google-Verbindung trennen"'),
        ('"Google-Freigabe widerrufen?"', '"Google-Verbindung trennen?"'),
        ('"Weiktube löscht die geladenen Daten dieser Kontoansicht und widerruft die Google-Freigabe. Dein YouTube-Konto und deine Abos bleiben bestehen. Deine Anmeldung auf der YouTube-Startseite bleibt erhalten."',
         '"Weiktube löscht die geladenen Kontodaten und widerruft den Google-Zugriff. Dein Google-/YouTube-Konto und deine Abos bleiben bestehen."'),
        ('"Freigabe widerrufen"', '"Trennen"')],
    "app/src/main/java/com/weiktube/app/YoutubeAccountController.kt": [
        ('"Für diese Kontoansicht ist eine separate Google-Freigabe nötig."', '"Melde dich mit Google an, um deine YouTube-Abos und Playlists in Weiktube zu sehen."'),
        ('"Google-Freigabe abgebrochen. Die YouTube-Startseite kannst du weiterhin öffnen."', '"Anmeldung abgebrochen. Du kannst Weiktube weiterhin ohne Konto nutzen."'),
        ('"Google-Freigabe wird vorbereitet …"', '"Google-Konto wird vorbereitet …"'),
        ('"Bitte die Freigabe für Abos und Playlists erneut bestätigen."', '"Bitte erneut mit Google anmelden, um den Kontozugriff zu bestätigen."'),
        ('"Google hat keinen Freigabedialog bereitgestellt."', '"Google hat keinen Anmeldedialog bereitgestellt."'),
        ('"Der YouTube-Kontozugriff wurde nicht freigegeben. Bitte die Freigabe erneut bestätigen."', '"Der YouTube-Kontozugriff wurde nicht freigegeben. Bitte erneut anmelden."'),
        ('"Google-Freigabe noch nicht freigeschaltet:', '"Google-Anmeldung noch nicht freigeschaltet:'),
        ('"Freigabe abgebrochen."', '"Anmeldung abgebrochen."'),
        ('"Google-Freigabe fehlgeschlagen${', '"Google-Anmeldung fehlgeschlagen${'),
        ('"Google-Freigabe aktiv. Für dieses Konto wurde kein eigener YouTube-Kanal gefunden."', '"Mit Google verbunden. Für dieses Konto wurde kein eigener YouTube-Kanal gefunden."'),
        ('"Freigabe für Kontofunktionen aktiv. Deine Kontodaten sind aktuell."', '"Mit YouTube verbunden. Deine Kontodaten sind aktuell."'),
        ('"Google-Freigabe wird erneuert …"', '"Google-Anmeldung wird erneuert …"'),
        ('"Google-Freigabe für Kontofunktionen widerrufen."', '"Google-Verbindung getrennt und Zugriff widerrufen."'),
        ('"Bitte den Zugriff auf deine Kontodaten erneut freigeben."', '"Bitte erneut mit Google anmelden, um den Kontozugriff zu bestätigen."'),
        ('"Die Google-Freigabe für Kontofunktionen ist abgelaufen. Bitte erneut bestätigen."', '"Die Anmeldung ist abgelaufen. Bitte erneut mit Google anmelden."'),
        ('"Bitte unter „Du“ die Google-Freigabe erneut bestätigen."', '"Bitte unter „Du“ die Google-Anmeldung erneut bestätigen."'),
        ('"Bitte zuerst unter „Du“ Abos und Playlists freigeben."', '"Bitte zuerst unter „Du“ mit Google anmelden."'),
        ('"Die Google-Freigabe wird gerade aktualisiert. Bitte kurz warten und erneut versuchen."', '"Die Kontoanmeldung wird gerade aktualisiert. Bitte kurz warten und erneut versuchen."'),
        ('"Bitte unter „Du“ die Google-Freigabe bestätigen."', '"Bitte unter „Du“ die Google-Anmeldung bestätigen."')],
}
# Narrow, device-reproduced same-video resume correction. Keep the original hash;
# any edit beyond these two reviewed substitutions still fails the baseline.
allowed["app/src/main/java/com/weiktube/app/PlayerController.kt"] = [
    ('''        if (source != null && rendererAlive && ((id != null && id == Links.videoId(source!!)) || url == source)) {
            expanded = true
            if (userInitiated) {
                playbackAllowed = activity.foreground || activity.pip
                // Selecting the same video again must resume the retained, possibly paused document.
                explicitStartRequested = loading
                applyPlayback(true)
            }
            return
        }
''', '''        if (source != null && rendererAlive && ((id != null && id == Links.videoId(source!!)) || url == source)) { expanded = true; return }
'''),
    ('allowed=next;if(!allowed)pending=false;else if(requestStart)pending=true;',
     'allowed=next;pending=next&&requestStart;'),
]
failures = []
for name, expected in baseline.items():
    file = root / name
    if not file.is_file():
        failures.append(name + " missing")
        continue
    content = file.read_text(encoding="utf-8").replace("\r\n", "\n")
    if name in adblock_review:
        review = adblock_review[name]
        for change in review["changes"]:
            if content.count(change["current"]) != 1:
                failures.append(name + " reviewed adblock change missing or duplicated")
            content = content.replace(change["current"], change["previous"], 1)
        if hashlib.sha256(content.encode()).hexdigest() != review["previousSha256"]:
            failures.append(name + " differs outside reviewed adblock changes")
    if name in rotation_review:
        review = rotation_review[name]
        for change in review["changes"]:
            if content.count(change["current"]) != 1:
                failures.append(name + " reviewed rotation change missing or duplicated")
            content = content.replace(change["current"], change["previous"], 1)
        if hashlib.sha256(content.encode()).hexdigest() != review["previousSha256"]:
            failures.append(name + " differs outside reviewed rotation changes")
    if name in mini_review:
        review = mini_review[name]
        for change in review["changes"]:
            if content.count(change["current"]) != 1:
                failures.append(name + " reviewed miniplayer change missing or duplicated")
            content = content.replace(change["current"], change["previous"], 1)
        if hashlib.sha256(content.encode()).hexdigest() != review["previousSha256"]:
            failures.append(name + " differs outside reviewed miniplayer changes")
    if name == "app/src/main/java/com/weiktube/app/UpdateController.kt":
        abi_hook = "UpdateManifest.parse(json, Build.SUPPORTED_ABIS.toList())"
        original_parser = 'UpdateRelease(json.getLong("versionCode"), json.getString("versionName"), json.getString("apkUrl"), json.getString("sha256"), json.optString("notes")).validate()'
        if content.count(abi_hook) != 1:
            failures.append(name + " reviewed architecture selection missing or duplicated")
        content = content.replace(abi_hook, original_parser, 1)
    if name == "app/src/main/java/com/weiktube/app/MainActivity.kt":
        if content.count(website_login_hook) != 1:
            failures.append(name + " website account hook missing or duplicated")
        content = content.replace(website_login_hook, "", 1)
    # Reverse only the reviewed embedding hooks, then apply the pre-existing original-app check.
    # A new unreviewed edit outside these exact chunks still fails the original baseline hash.
    if name in tab_review:
        review = tab_review[name]
        for change in review["changes"]:
            if content.count(change["current"]) != 1:
                failures.append(name + " reviewed Home-tab hook missing or duplicated")
            content = content.replace(change["current"], change["previous"], 1)
        if hashlib.sha256(content.encode()).hexdigest() != review["previousSha256"]:
            failures.append(name + " differs outside reviewed Home-tab changes")
    for added, original in allowed.get(name, []):
        if content.count(added) != 1:
            failures.append(name + " reviewed hook or copy missing or duplicated")
        content = content.replace(added, original, 1)
    if hashlib.sha256(content.encode()).hexdigest() != expected:
        failures.append(name + " differs outside reviewed hooks and copy")
if failures:
    raise SystemExit("FAIL original app scope:\n" + "\n".join(failures))
print(f"PASS {len(baseline)} original source files preserved; only reviewed Home, resume, miniplayer, rotation and adblock integration changes allowed.")
