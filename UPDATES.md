# Weiktube-Updates

**1.3.11 ist ein Diagnose-Update für den regulären In-App-Updatekanal.** Die Miniplayer-Schaltfläche
sendet eindeutige Play-/Pause-Befehle; die YouTube-seitige PiP-Pause ist noch
nicht gelöst. Die Veröffentlichung als `v1.3.11` übernimmt dieselben geprüften
APKs mit Code 25 aus der vorherigen Vorabversion; es gibt keinen neuen Build
und keine zusätzlichen App-Änderungen für den Kanalwechsel.
[Befunde und Testgrenzen](RELEASE-1.3.11.md).

Die App prüft beim Öffnen maximal einmal pro 24 Stunden auf eine neue Version. Das lässt sich unter App-Updates ausschalten. Es gibt keinen permanenten Hintergrunddienst und keine stille Installation: Nach einer verfügbaren Version startet der Nutzer den Download; Android bestätigt die Installation. Fehlgeschlagene automatische Prüfungen werden ebenfalls frühestens am nächsten Tag wiederholt. „Jetzt nach Updates suchen“ ist jederzeit möglich.

Standardquelle: `https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json`. Die Quelle kann geändert werden. Die tatsächlich angebotene Version steht im veröffentlichten Feed. Ein lokal vorbereiteter Versionsstand ist noch kein verfügbares Update. Fehlt eine Release-Datei oder ist sie nicht erreichbar, meldet die App einen Serverfehler; sie behauptet dann nicht, aktuell zu sein.

## Version 1.3.11 – Diagnose-Update im regulären Kanal

Mit der Veröffentlichung von `v1.3.11` bietet die bestehende `latest`-Quelle
Code 25 an. Nutzer von 1.3.10 können unter **Einstellungen & Updates → Jetzt
nach Updates suchen** aktualisieren. Wer die frühere 1.3.11-Vorabversion schon
installiert hat, besitzt dieselben APKs und denselben Versionscode; der Updater
muss dafür kein weiteres Update anbieten. Die Vorabveröffentlichung bleibt
historisch erhalten und wird nicht umgeschrieben.

Die reguläre Verteilung ist keine Qualitäts- oder PiP-Erfolgsgarantie. Die
270 JVM-Tests, Script-/Pixelprüfungen und der Release-Build sind bestanden;
Android-Gerätetests und die fünfminütige Live-Wiedergabeabnahme fehlen weiterhin.

## Version 1.3.10 – Wiederherstellung nach Prozessverlust

Version 1.3.10 / Code 24 ergänzt die sichere Entsorgung beendeter Player-
WebViews, höchstens einen automatischen Wiederherstellungsversuch pro expliziter
Videoauswahl und die Wiederherstellung der zuletzt bekannten Position. Manuelle
Pause, Schließen und eine neue Auswahl haben Vorrang vor wartender Recovery.
Gesunde Dokumente werden bei gewöhnlicher Hintergrundrückkehr nicht neu geladen.
Gecko behandelt auch beendete Inhalte und bereits geschlossene Sitzungen;
die gespeicherte Website-Anmeldung wird dabei nicht gelöscht.

Die Diagnose protokolliert feste Ereigniscodes ohne URLs, Video-IDs, Cookies,
Tokens oder vollständige Seitenantworten. Adblock-Regeln, Kontoanbindung und
Wiedergabeimplementierung werden nicht ersetzt. Der Release-Build, 263 JVM-Tests
(einschließlich 22 zusätzlicher Player- und fünf Home-Tests), Scriptprüfungen
und Artefaktprüfung sind bestanden. Beide APKs behalten das ursprüngliche
Signierzertifikat, sind nicht debuggbar und unter 128 MiB.
Kein Gerätetest; der Nutzer prüft die APK.
Der offene YouTube-Pause-Befund aus 1.3.9 wird nicht als behoben behauptet.
Details: [RELEASE-1.3.10.md](RELEASE-1.3.10.md).

Die Veröffentlichung bündelt ARM64-/x86-64-APKs, Quellcode, Feeds und Prüfsummen.
Die vorhandene `latest`-Quelle zeigt jeweils die öffentlich freigegebene Version.

## Version 1.3.9 – Miniplayer-/PiP-Korrektur

Version 1.3.9 / Code 23 korrigiert die bestehende Videobox für Miniplayer und
System-PiP, die an die PiP-WebView gelieferten Systemleisten-/Displayausschnitt-
Insets und die Pause beim Verkleinern über Android-Zurück. Das Compose-Padding
war bereits null. Es wird keine zweite Wiedergabeimplementierung eingeführt.
Zusätzlich wird eine geschlossene YouTube-Schublade ausgeblendet, die das Video
in Mini/PiP überdeckte. Die Regel gilt nur für genau diese Schublade in diesen
beiden Darstellungen, nicht für die normale Watch-Ansicht oder Querformat.

Finaler Release-Build mit Schubladenkorrektur, 236 JVM-Tests, Scriptprüfungen
und lokale Chromium-Pixel-/Überdeckungstests bestanden. Die Mini- und
PiP-Endscreenshots des zehnten Zyklus im öffentlichen Browserlauf zeigen das Bild ohne grauen Balken.
Die Überdeckung ist im Browser nachgewiesen; die frühere reine Bounds-Prüfung
war kein sichtbarer Bildnachweis. Weiterhin nicht bestätigt ist eine
durchgängige Wiedergabe. Kein aktueller Handy-/Android-Emulatortest; der Nutzer
prüft die APK selbst. Details: [RELEASE-1.3.9.md](RELEASE-1.3.9.md).

Die `latest`-Quelle bleibt bis zum tatsächlichen Upload bei der zuletzt
veröffentlichten Version. Erst übereinstimmende APKs, Quellcode, Feed,
Versionsnummern und Prüfsummen werden gemeinsam als neue Release veröffentlicht.

## Version 1.3.8 – Android-Bild-in-Bild

Version 1.3.8 / Code 22 enthält das PiP-Update; Release-Build, 234 JVM-Tests,
Scriptprüfungen und APK-Prüfung bestanden. Die neue Integration
verwendet dieselbe Player-WebView im Android-Systemfenster. Sie ändert weder
Paketname noch den vorgesehenen Signierschlüssel, die API-Kontoanbindung oder
den getrennten signierten Filterupdate-Mechanismus.

Beide APKs, Quellcode, Feed, Versionsnummern und Prüfsummen werden gemeinsam
veröffentlicht. Die bestehende `latest`-Quelle liefert das neue Release. Für den PiP-
Gerätetest gibt es noch keinen Erfolgsnachweis; YouTube kann Wiedergabe
browserseitig blockieren. Details: [RELEASE-1.3.8.md](RELEASE-1.3.8.md).

## Release-Dateien

Ein reguläres GitHub-Release erhält `Weiktube.apk` (ARM64), `Weiktube-x86_64.apk`,
`update.json`, `update-x86_64.json`, Quellcode und Prüfsummen. Beide eigenständig
installierbaren APKs bleiben unter dem vorhandenen Limit von 128 MiB. Die bisherige
Universal-Test-APK mit beiden Architekturen war dafür zu groß.

Das Manifest behält die alten Felder für ältere ARM64-Updater und ergänzt `apks`.
Ab 1.3.2 wählt die App den ersten passenden Eintrag gemäß `Build.SUPPORTED_ABIS`.
Ein vorhandenes ungültiges oder unpassendes `apks`-Objekt löst einen Fehler aus;
es wird nicht still eine andere Architektur verwendet. Alte Feeds ohne `apks`
bleiben unterstützt. Beispiel mit Platzhaltern statt echten Prüfsummen:

```json
{
  "versionCode": 16,
  "versionName": "1.3.2",
  "apkUrl": "https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.2/Weiktube.apk",
  "sha256": "SHA256_DER_APK_MIT_64_HEX_ZEICHEN",
  "notes": "Änderungen dieser Version",
  "apks": {
    "arm64-v8a": {"apkUrl": "https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.2/Weiktube.apk", "sha256": "ARM64_SHA256"},
    "x86_64": {"apkUrl": "https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.2/Weiktube-x86_64.apk", "sha256": "X86_64_SHA256"}
  }
}
```

`versionCode` muss exakt mit der APK übereinstimmen und bei jeder Veröffentlichung steigen. Die APK muss denselben Paketnamen und dieselben aktuellen Signierzertifikate wie die installierte App haben. Schlüsselrotation wird absichtlich nicht automatisch akzeptiert. Den Debug-Signierschlüssel des ersten Builds sicher bewahren; eine mit einem anderen Rechner-Schlüssel erzeugte APK wird abgelehnt.

**Umstieg alter x86-64-Installationen:** Einmal die x86-64-APK manuell als Update
installieren oder als Updatequelle
`https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update-x86_64.json`
eintragen. Alte Updater können das neue Architekturfeld noch nicht auswerten.
ARM64-Installationen können die bisherige Standardquelle weiterverwenden.
Die Gecko-Version unterstützt ARM64/x86-64, nicht reine 32-Bit-Geräte.

## Kontofunktion ab Version 1.2

Der APK-Updater und die native Google-Autorisierung sind getrennt. Ein App-Update überträgt keine Google-Tokens, erzeugt keine WebView-Cookies und erteilt keine YouTube-Berechtigung. Der Nutzer gibt zunächst Lesezugriff (`youtube.readonly`) im offiziellen Google-Dialog frei. Native Schreibaktionen oder die Schaltfläche „Kommentare freigeben“ fordern erst bei ausdrücklicher Betätigung zusätzlich `youtube.force-ssl` an, soweit noch nicht erteilt. Das Update selbst startet keine Likes, Abos, Kommentare oder Playliständerungen.

Cloud-Projekt, Android-OAuth-Client und gegebenenfalls OAuth-Testnutzer müssen passend konfiguriert sein. Eine APK-Weitergabe gewährt weiteren Konten nicht automatisch Zugang. Auch für OAuth muss die registrierte Signieridentität zum neuen Build passen. In einem früheren 1.2-Arbeitsstand wurden die erste Anmeldung und das Laden eigener Kanaldaten sowie Abos mit einem registrierten Testkonto auf dem OnePlus bestätigt. Die neue Schreibfreigabe und die Schreibaktionen wurden noch nicht auf dem Gerät abgenommen. Das ist auch kein Nachweis einer öffentlichen Freigabe für beliebige Google-Konten oder eines durchlaufenen APK-Updates.

## Downloadprüfung

- Nur HTTPS-Quellen und HTTPS-Weiterleitungen, höchstens fünf Weiterleitungen.
- Keine eingebetteten URL-Zugangsdaten, kein Klartext-Fallback und kein Übergehen von Zertifikatsfehlern.
- Feed höchstens 64 KiB, APK höchstens 128 MiB, Verbindungs-/Lesezeitlimit und Gesamtdauergrenze.
- SHA-256-Abgleich vor dem Öffnen der APK und nochmals unmittelbar vor der Installation.
- Prüfung von Paketname, APK-Version und Signierzertifikaten über Android PackageManager. Android prüft die vollständige Installierbarkeit und APK-Signatur nochmals bei der eigentlichen Installation.
- APK liegt nur im privaten Appcache. Der nicht exportierte FileProvider gibt ausschließlich den Unterordner `updates/` temporär für den Android-Installer frei.

Erforderlich ist die Android-Berechtigung `REQUEST_INSTALL_PACKAGES`. Die Android-Freigabe „Aus dieser Quelle installieren“ wird erst bei ausdrücklichem Installationswunsch geöffnet; nach der Freigabe erneut „Update installieren“ drücken. Es werden weder andere Apps ersetzt noch Downgrades angeboten.

## Freigabestand

Der historische Prüfstand der Veröffentlichung 1.3.2 steht in `RELEASE-1.3.2.md`;
der aktuelle Prüfstand und die offenen Gerätetests für 1.3.8 stehen in `RELEASE-1.3.8.md`.
Der vollständige Ablauf innerhalb der App – höhere Version erkennen, herunterladen,
prüfen, Android-Freigabe, Installation und Rückkehr – wurde hier nicht auf einem
Gerät durchgespielt. Abbruch, Prozessverlust und erneuter Versuch bleiben Teil der
Prüfliste in `TESTING.md`.

Die neue Playerarchitektur und native Kontoaktionen ändern weder Paketname noch erforderliche Signieridentität. Veröffentlichungen erfolgen erst mit zusammenpassender APK, Feed und bereinigtem Quellcode. `scripts/publish-release.ps1` nimmt `AUDIT.md` in die optionalen Projektdokumente auf; lokale Buildlogs, SDK-Pfade, Gerätebilder und Signierschlüssel gehören nicht in das Release.

Referenzen: [Android PackageManager und Installationsfreigabe](https://developer.android.com/reference/android/content/pm/PackageManager), [Android SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo).


