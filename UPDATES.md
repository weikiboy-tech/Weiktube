# Weiktube-Updates

Die App prüft beim Öffnen maximal einmal pro 24 Stunden auf eine neue Version. Das lässt sich unter App-Updates ausschalten. Es gibt keinen permanenten Hintergrunddienst und keine stille Installation: Nach einer verfügbaren Version startet der Nutzer den Download; Android bestätigt die Installation. Fehlgeschlagene automatische Prüfungen werden ebenfalls frühestens am nächsten Tag wiederholt. „Jetzt nach Updates suchen“ ist jederzeit möglich.

Standardquelle: `https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json`. Die Quelle kann geändert werden. Version 1.1.0 wurde unter diesem Repository veröffentlicht; der lokale Arbeitsstand 1.2.0 ist damit nicht automatisch veröffentlicht. Fehlt eine Release-Datei oder ist sie nicht erreichbar, meldet die App einen Serverfehler; sie behauptet dann nicht, aktuell zu sein.

## Release-Dateien

Ein neues GitHub-Release erhält mindestens zwei Assets: `Weiktube.apk` und `update.json`. Das folgende Schema ist ein Beispiel für den Arbeitsstand 1.2.0 und keine Behauptung einer bereits verfügbaren Release:

```json
{
  "versionCode": 3,
  "versionName": "1.2.0",
  "apkUrl": "https://github.com/weikiboy-tech/Weiktube/releases/download/v1.2.0/Weiktube.apk",
  "sha256": "SHA256_DER_APK_MIT_64_HEX_ZEICHEN",
  "notes": "Änderungen dieser Version"
}
```

`versionCode` muss exakt mit der APK übereinstimmen und bei jeder Veröffentlichung steigen. Die APK muss denselben Paketnamen und dieselben aktuellen Signierzertifikate wie die installierte App haben. Schlüsselrotation wird absichtlich nicht automatisch akzeptiert. Den Debug-Signierschlüssel des ersten Builds sicher bewahren; eine mit einem anderen Rechner-Schlüssel erzeugte APK wird abgelehnt.

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

Öffentlicher Feed und APK der Version 1.1.0 wurden heruntergeladen und ihre Prüfsumme verifiziert. Der vollständige Ablauf innerhalb der App – höhere Version erkennen, herunterladen, prüfen, Android-Freigabe, Installation und Rückkehr – ist damit noch nicht abgenommen. Abbruch, Prozessverlust und erneuter Versuch bleiben Teil der Prüfliste in `TESTING.md`.

Die neue Playerarchitektur und native Kontoaktionen ändern weder Paketname noch erforderliche Signieridentität. Veröffentlichungen erfolgen erst mit zusammenpassender APK, Feed und bereinigtem Quellcode. `scripts/publish-release.ps1` nimmt `AUDIT.md` in die optionalen Projektdokumente auf; lokale Buildlogs, SDK-Pfade, Gerätebilder und Signierschlüssel gehören nicht in das Release.

Referenzen: [Android PackageManager und Installationsfreigabe](https://developer.android.com/reference/android/content/pm/PackageManager), [Android SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo).


