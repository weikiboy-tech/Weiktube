"""Verify original app source, allowing reviewed entry/clear hooks and website-first copy."""
from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parents[1]
baseline = json.loads((root / "scripts/home-original-source-hashes.json").read_text(encoding="utf-8"))
allowed = {
    "app/src/main/java/com/weiktube/app/MainActivity.kt": [
        ("        com.weiktube.home.HomeEngine.clearIfInitialized(this)\n", "")],
    "app/src/main/java/com/weiktube/app/WeiktubeUi.kt": [
        ("AccountScreen(a.youtubeAccount, a::openAccountUrl, homeConnection = { HomeConnectionEntry(a, a.youtubeAccount) })",
         "AccountScreen(a.youtubeAccount, a::openAccountUrl)")],
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
failures = []
for name, expected in baseline.items():
    file = root / name
    if not file.is_file():
        failures.append(name + " missing")
        continue
    content = file.read_text(encoding="utf-8").replace("\r\n", "\n")
    for added, original in allowed.get(name, []):
        if content.count(added) != 1:
            failures.append(name + " reviewed hook or copy missing or duplicated")
        content = content.replace(added, original, 1)
    if hashlib.sha256(content.encode()).hexdigest() != expected:
        failures.append(name + " differs outside reviewed hooks and copy")
if failures:
    raise SystemExit("FAIL original app scope:\n" + "\n".join(failures))
print(f"PASS {len(baseline)} original source files preserved; only reviewed entry/clear hooks and website-first copy allowed.")
