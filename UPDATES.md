# Weiktube-Updates

Die App prüft beim Öffnen maximal einmal pro 24 Stunden auf eine neue Version. Das lässt sich unter App-Updates ausschalten. Es gibt keinen permanenten Hintergrunddienst und keine stille Installation: Nach einer verfügbaren Version startet der Nutzer den Download; Android bestätigt die Installation. Fehlgeschlagene automatische Prüfungen werden ebenfalls frühestens am nächsten Tag wiederholt. „Jetzt nach Updates suchen“ ist jederzeit möglich.

Standardquelle: `https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json`. Die Quelle kann geändert werden. Ist noch keine öffentliche Release-Datei unter dieser Adresse vorhanden, meldet die App einen verständlichen Serverfehler; sie behauptet dann nicht, aktuell zu sein.

## Release-Dateien

Ein neues GitHub-Release erhält mindestens zwei Assets: `Weiktube.apk` und `update.json`.

```json
{
  "versionCode": 2,
  "versionName": "1.1.0",
  "apkUrl": "https://github.com/weikiboy-tech/Weiktube/releases/download/v1.1.0/Weiktube.apk",
  "sha256": "SHA256_DER_APK_MIT_64_HEX_ZEICHEN",
  "notes": "Änderungen dieser Version"
}
```

`versionCode` muss exakt mit der APK übereinstimmen und bei jeder Veröffentlichung steigen. Die APK muss denselben Paketnamen und dieselben aktuellen Signierzertifikate wie die installierte App haben. Schlüsselrotation wird absichtlich nicht automatisch akzeptiert. Den Debug-Signierschlüssel des ersten Builds sicher bewahren; eine mit einem anderen Rechner-Schlüssel erzeugte APK wird abgelehnt.

## Downloadprüfung

- Nur HTTPS-Quellen und HTTPS-Weiterleitungen, höchstens fünf Weiterleitungen.
- Keine eingebetteten URL-Zugangsdaten, kein Klartext-Fallback und kein Übergehen von Zertifikatsfehlern.
- Feed höchstens 64 KiB, APK höchstens 128 MiB, Verbindungs-/Lesezeitlimit und Gesamtdauergrenze.
- SHA-256-Abgleich vor dem Öffnen der APK und nochmals unmittelbar vor der Installation.
- Prüfung von Paketname, APK-Version und Signierzertifikaten über Android PackageManager. Android prüft die vollständige Installierbarkeit und APK-Signatur nochmals bei der eigentlichen Installation.
- APK liegt nur im privaten Appcache. Der nicht exportierte FileProvider gibt ausschließlich den Unterordner `updates/` temporär für den Android-Installer frei.

Erforderlich ist die Android-Berechtigung `REQUEST_INSTALL_PACKAGES`. Die Android-Freigabe „Aus dieser Quelle installieren“ wird erst bei ausdrücklichem Installationswunsch geöffnet; nach der Freigabe erneut „Update installieren“ drücken. Es werden weder andere Apps ersetzt noch Downgrades angeboten.

Referenzen: [Android PackageManager und Installationsfreigabe](https://developer.android.com/reference/android/content/pm/PackageManager), [Android SigningInfo](https://developer.android.com/reference/android/content/pm/SigningInfo).
