# Weiktube 1.2 bauen und aktualisieren

## Voraussetzungen

JDK 17 oder 21, Android SDK Plattform 36 mit Build Tools 35.0.0 und Internet für den ersten Gradle-/Maven-Abruf. Das Zielgerät benötigt Android 8.0+ und einen aktuellen Android System WebView. Für die native Google-Kontofreigabe werden außerdem aktuelle Google Play Services benötigt.

Projekt in Android Studio öffnen oder `ANDROID_HOME` setzen. Alternativ eine eigene, nicht versionierte `local.properties` anlegen:

```properties
sdk.dir=C\:/Android/Sdk
```

Zum Kompilieren und für öffentliche Webvideos werden keine API-Schlüssel benötigt. Die native Kontofunktion benötigt eine passend eingerichtete Google-Cloud-/OAuth-Konfiguration; sie ist nicht allein durch das Bauen der APK freigeschaltet. Quellcode-ZIP und Repository enthalten keine lokalen SDK-Pfade, Caches oder privaten Signierschlüssel.

## Build und Prüfungen

Windows:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug :app:exportWeiktubeApk
```

Linux/macOS:

```sh
chmod +x gradlew
./gradlew assembleDebug testDebugUnitTest lintDebug :app:exportWeiktubeApk
```

`JAVA_HOME` muss auf das eigene JDK zeigen. Standardausgabe ist `app/build/outputs/apk/debug/app-debug.apk`; der Exporttask kopiert sie nach `Weiktube.apk`. Optional mit Node.js die eingebetteten Player-Skripte prüfen:

```sh
node scripts/check-player-scripts.cjs
```

Werkzeugversionen: Gradle 8.11.1 mit Wrapper-SHA-256, Android Gradle Plugin 8.10.1, Kotlin/Compose-Compiler 2.3.21, Compose BOM 2025.02.00 und Google Play Services Auth 22.0.0. Versionsaktualisierungen sind getrennt von einem erfolgreichen Build zu bewerten.

## Installation

APK auf das Gerät übertragen und öffnen oder per ADB installieren:

```sh
adb install -r Weiktube.apk
adb shell am start -n com.weiktube.app/.MainActivity
```

Android kann eine Freigabe für „Unbekannte Apps installieren“ verlangen. `-r` erhält vorhandene Appdaten bei kompatibler Signatur. Bei einem Signaturkonflikt nicht automatisch deinstallieren: Die Deinstallation würde lokale Appdaten löschen.

YouTube-Links lassen sich über „Teilen → Weiktube“ übergeben. Für direktes Öffnen ggf. Android-Einstellungen → Apps → Weiktube → Standardmäßig öffnen konfigurieren.

## Google-Autorisierung für den persönlichen Testbetrieb

1. Ein separates Google-Cloud-Projekt verwenden. Kein Billing-Konto verknüpfen, keine kostenlose Testphase oder kostenpflichtigen Dienste aktivieren.
2. Nur die benötigte YouTube Data API v3 aktivieren und die Google-Auth-App mit zutreffendem Namen, Kontaktangaben und externer Zielgruppe konfigurieren.
3. Im Status „Testing“ die tatsächlich berechtigten Google-Konten als Testnutzer hinzufügen. Installation der APK allein reicht für Freunde nicht; zusätzliche Konten müssen einzeln freigegeben werden.
4. Einen OAuth-Client vom Typ Android für `com.weiktube.app` und den SHA-1-Fingerabdruck des Signierzertifikats der installierten APK registrieren. Der Fingerabdruck ist beispielsweise über `gradlew signingReport` oder `apksigner verify --print-certs Weiktube.apk` prüfbar. Ein anderer Signierschlüssel benötigt eine passende zusätzliche Clientkonfiguration.
5. Die Scopes `https://www.googleapis.com/auth/youtube.readonly` und `https://www.googleapis.com/auth/youtube.force-ssl` im Datenzugriff konfigurieren. Die erste Anmeldung fordert nur Lesezugriff an. Die weitergehende Freigabe wird erst bei einer bewussten Schreibaktion oder über „Kommentare freigeben“ angefordert; das Betrachten einer Seite genügt dafür nicht.
6. Auf einem Gerät mit Google Play Services unter „Du → Mit Google anmelden“ Kontoauswahl, Zustimmung und echte API-Daten prüfen. Verbindungsfehler und fehlende Cloud-Konfiguration dürfen nicht als erfolgreiche Anmeldung behandelt werden.
7. Die zusätzliche Freigabe getrennt prüfen: Nutzer startet selbst Gefällt mir, Abo ändern, Playlist speichern oder Kommentar/Antwort senden. Zustimmung und Abbruch, API-Erfolg und Fehlerzustände dokumentieren. Öffentliche Kommentare oder Kontoänderungen nicht ungefragt als Testdaten erzeugen.

Es wird kein OAuth-Client-Secret in die Android-App eingebaut. Die Identität dieser Android-Autorisierung beruht auf Paketname und Signierzertifikat. Zugriffstokens bleiben aus WebViews und Updater ausgeschlossen. Für den vorliegenden signierten Build wurden Cloud-Projekt, YouTube Data API, Android-OAuth-Client und ein persönlicher Testnutzer eingerichtet. Die erste Anmeldung mit Lesezugriff und Laden des eigenen Kanals sowie der Abonnements wurde auf dem OnePlus erfolgreich geprüft. Diese Freischaltung überträgt sich nicht automatisch auf einen anders signierten eigenen Build oder weitere Testkonten. Die zusätzliche Schreibfreigabe, Schreibaktionen und vollständige Lifecycle-/Playlistabnahme stehen noch aus; der frühere Lesetest belegt diese neuen Abläufe nicht.

Der externe Testmodus hat Teilnehmer- und Zustimmungsgrenzen; zum Weitergeben an eine offene Nutzergruppe ist die Veröffentlichung bzw. gegebenenfalls Verifizierung gesondert zu prüfen. Die App fordert keine bezahlte API-Kontingenterhöhung an. Ein erschöpftes Kontingent führt zu einer Fehlermeldung.

Referenzen: [Android-Autorisierung](https://developer.android.com/identity/authorization), [Google-Testnutzer](https://support.google.com/cloud/answer/15549945?hl=en), [YouTube Data API](https://developers.google.com/youtube/v3/getting-started).

## Architektur und Abnahme

Startseite/Suche, Shorts und Original-Videoseite besitzen getrennte, dauerhaft gehaltene WebViews. Minimieren und Vergrößern verändern die Darstellung desselben Watch-Dokuments; es gibt keine Übergabe an einen IFrame-Player. Die große Videoseite behält Kommentare und Empfehlungen. Native Kontoaktionen verwenden ausschließlich die Data API und erhalten keine WebView-Sitzung.

Ein erfolgreicher Build prüft diese Live-Abläufe nicht. Vor einer Veröffentlichung die Abnahmeliste in `TESTING.md` durchführen, insbesondere Feed-/Scroll-Erhalt, Suchvorschläge, Zeitposition beim Minimieren, Audioübergang zu Shorts, Appwechsel, Rotation, Google-Freigaben und den vollständigen Update-Installer. Bekannte Plattformgrenzen stehen in `AUDIT.md`.

## Update veröffentlichen

Die Standardquelle liegt im Repository [weikiboy-tech/Weiktube](https://github.com/weikiboy-tech/Weiktube). Die App liest:

```text
https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json
```

Für jedes Update:

1. `versionCode` erhöhen und `versionName` anpassen.
2. Mit demselben Signierschlüssel wie die installierte APK bauen und Prüfungen durchführen.
3. SHA-256 der fertigen APK berechnen.
4. APK und `update.json` als Assets einer veröffentlichten GitHub-Release hochladen. Die Feedangaben müssen exakt zu dieser APK passen.

Beispielschema mit zu ersetzenden Werten:

```json
{
  "versionCode": 3,
  "versionName": "1.2.0",
  "apkUrl": "https://github.com/weikiboy-tech/Weiktube/releases/download/v1.2.0/Weiktube.apk",
  "sha256": "<64-stelliger SHA-256-Hash der APK>",
  "notes": "Änderungen dieser Version"
}
```

Kein Geheimnis gehört in diese JSON-Datei. Den Signierschlüssel geschützt sichern und niemals ins Repository hochladen. Ein Debug-Build verwendet normalerweise den lokalen Android-Debugschlüssel; ein anderer Rechner oder ein frischer CI-Runner kann einen anderen Schlüssel erzeugen und damit kein kompatibles Update liefern. Der Updater lehnt solche APKs ab. Für eine dauerhaft veröffentlichte App ist eine stabile, geschützt verwaltete Signieridentität erforderlich.

`scripts/publish-release.ps1` kann aus einer fertig geprüften `Weiktube.apk` bereinigten Quellcode und den Feed lokal vorbereiten. Ohne `-Publish` erfolgen keine Remoteänderungen. Die explizite Dateiauswahl nimmt unter anderem `README.md`, `BUILD.md`, `TESTING.md`, `UPDATES.md` und `AUDIT.md` mit; SDK-Pfade, Buildberichte, Geräteaufnahmen und private Schlüssel gehören nicht dazu. Eine vorhandene Vorbereitung wird nicht überschrieben. `-PublishPrepared` prüft APK-Hash, Version und Zielrepository gegen den vorbereiteten Feed und veröffentlicht die geprüfte Vorbereitung. Vor dieser Veröffentlichung Quellcode, APK-Signatur, Versionsangaben und konkrete Release-Notizen kontrollieren. Ein CI-Debug-Build mit frischem Schlüssel ersetzt die bestehende signierte Update-APK nicht.

Automatische Prüfung bedeutet: höchstens täglich bei Vordergrundnutzung. Download und Android-Installation bleiben ausdrückliche Benutzeraktionen. Das laufende Gerät muss dem App-Installer die Berechtigung erteilen; Android bestätigt die Installation selbst.


