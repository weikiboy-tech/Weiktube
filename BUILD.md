# Weiktube bauen und aktualisieren

## Voraussetzungen

JDK 17 oder 21, Android SDK Plattform 36 mit Build Tools 35.0.0 und Internet für den ersten Gradle-/Maven-Abruf. Das Zielgerät benötigt Android 8.0+ und einen aktuellen Android System WebView.

Projekt in Android Studio öffnen oder `ANDROID_HOME` setzen. Alternativ eine eigene, nicht versionierte `local.properties` anlegen:

```properties
sdk.dir=C\:/Android/Sdk
```

Es werden keine API-Schlüssel benötigt. Quellcode-ZIP und Repository enthalten keine lokalen SDK-Pfade, Caches oder privaten Signierschlüssel.

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

Werkzeugversionen: Gradle 8.11.1 mit Wrapper-SHA-256, Android Gradle Plugin 8.10.1, Kotlin/Compose-Compiler 2.3.21, Compose BOM 2025.02.00. Versionsaktualisierungen sind getrennt von einem erfolgreichen Build zu bewerten.

## Installation

APK auf das Gerät übertragen und öffnen oder per ADB installieren:

```sh
adb install -r Weiktube.apk
adb shell am start -n com.weiktube.app/.MainActivity
```

Android kann eine Freigabe für „Unbekannte Apps installieren“ verlangen. `-r` erhält vorhandene Appdaten bei kompatibler Signatur. Bei einem Signaturkonflikt nicht automatisch deinstallieren: Die Deinstallation würde lokale Appdaten löschen.

YouTube-Links lassen sich über „Teilen → Weiktube“ übergeben. Für direktes Öffnen ggf. Android-Einstellungen → Apps → Weiktube → Standardmäßig öffnen konfigurieren.

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

Automatische Prüfung bedeutet: höchstens täglich bei Vordergrundnutzung. Download und Android-Installation bleiben ausdrückliche Benutzeraktionen. Das laufende Gerät muss dem App-Installer die Berechtigung erteilen; Android bestätigt die Installation selbst.
