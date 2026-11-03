# Weiktube-Updates

Die App prüft beim Öffnen maximal einmal pro 24 Stunden auf eine neue Version. Das lässt sich unter App-Updates ausschalten. Es gibt keinen permanenten Hintergrunddienst und keine stille Installation: Nach einer verfügbaren Version startet der Nutzer den Download; Android bestätigt die Installation. Fehlgeschlagene automatische Prüfungen werden ebenfalls frühestens am nächsten Tag wiederholt. „Jetzt nach Updates suchen“ ist jederzeit möglich.

Standardquelle: `https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json`. Die Quelle kann geändert werden. Reguläre Version ist 1.3.2 (Versioncode 16). Fehlt eine Release-Datei oder ist sie nicht erreichbar, meldet die App einen Serverfehler; sie behauptet dann nicht, aktuell zu sein.

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

Prüfstand und Artefaktprüfung der regulären Version stehen in `RELEASE-1.3.2.md`.
Der vollständige Ablauf innerhalb der App – höhere Version erkennen, herunterladen,
prüfen, Android-Freigabe, Installation und Rückkehr – wurde hier nicht auf einem
Gerät durchgespielt. Abbruch, Prozessverlust und erneuter Versuch bleiben Teil der
Prüfliste in `TESTING.md`.

Die neue Playerarchitektur und native Kontoaktionen ändern weder Paketname noch erforderliche Signieridentität. Veröffentlichungen erfolgen erst mit zusammenpassender APK, Feed und bereinigtem Quellcode. `scripts/publish-release.ps1` nimmt `AUDIT.md` in die optionalen Projektdokumente auf; lokale Buildlogs, SDK-Pfade, Gerätebilder und Signierschlüssel gehören nicht in das Release.

Referenzen: [Android PackageManager und Installationsfreigabe](https://developer.android.com/reference/android/content/pm/PackageManager), [Android SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo).


