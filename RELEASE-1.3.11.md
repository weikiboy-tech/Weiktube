# Weiktube 1.3.11 – Diagnose-Vorabversion, kein fertiger PiP-Fix

Versioncode 25, Paket `com.weiktube.app`. **Die YouTube-seitige Wiedergabepause
im Miniplayer/PiP bleibt offen.** Diese Vorabversion ersetzt das stabile
Update 1.3.10 nicht. Es wurde kein Android-Gerätetest durchgeführt.

## Was tatsächlich gefunden wurde

Der öffentliche mobile YouTube-Webplayer pausiert im isolierten Chromium-Test
ab dem zweiten kleinen Viewport. Auch ein ausdrücklicher Play-Versuch wird
nach etwa einer Viertelsekunde erneut pausiert. Der Pause-Aufruf kommt aus
YouTubes Fortschritts-Handler: Die Seite hat ihren internen PiP-Zustand gesetzt
und behandelt das Video dabei als nicht hintergrundfähig. Das Dokument ist
gleichzeitig sichtbar. Das ist keine pauschale Aussage zu Premium-Konten.

Der Fehler tritt auch **ohne Weiktube-Präsentationsscript, Wiedergabe-Guard,
Adblock und Android-Lifecycle** auf. Die Gegenprobe ohne Größenwechsel läuft
bis rund 48 Sekunden weiter. Eine zusätzliche Ursache auf dem Handy bleibt
möglich. YouTubes Berechtigungs-/Sichtbarkeitssignale werden nicht gefälscht;
es gibt keine Play-Schleife als vermeintlichen Fix.

## Enthaltene Korrektur und geänderte Dateien

- `WeiktubeUi.kt`: Miniplayer-Icon und Aktion verwenden denselben gerenderten
  Zustand. Play sendet ausdrücklich Play, Pause ausdrücklich Pause; ein
  verzögerter DOM-Zustand kann den Play-Klick nicht mehr in Toggle-Pause ändern.
- `PlayerController.kt`: Diagnose-Hooks an den bestehenden Dokument-Lifecycle
  angebunden, eigene Pausebefehle markiert. Der Pause-Fallback bleibt auch bei
  fehlender/fehlerhafter Guard-Funktion wirksam; beim intakten Guard entfällt
  der zweite unnötige Pause-Aufruf.
- `MainActivity.kt`, `PlayerDiagnostics.kt`: Feste Gründe für eigene
  Pausebefehle, Gate- und Darstellungswechsel. Keine Änderung der
  Activity-/PiP-Wiedergaberegeln.
- Neu: `PlayerPlaybackDiagnostics.kt`: Begrenzte Medienereignisse und
  transparente Beobachtung von `pause()`. Keine URLs, Videokennungen,
  Zugangsdaten oder Stacktexte in den App-Logs. Unbekannte Ursachen bleiben
  ausdrücklich unbekannt; Adblock-Aufrufe werden nicht automatisch YouTube
  zugeschrieben.
- Regressionstests, CI-Scriptprüfung, exaktes Änderungsjournal,
  Versions-/Paketierungsskripte und Dokumentation ergänzt.

Das vollständige Präsentationsscript `WATCH_CHROME` ist unverändert.
Gecko-Startseite, Kontoanbindung, Adblock-Regeln, Videoübergabe und vorhandene
Player-/PiP-Implementierung bleiben erhalten. Der bewusste Wechsel zu Shorts
pausiert weiterhin den vorherigen Player, damit keine zwei Videos konkurrieren.

## Tatsächlich ausgeführte Prüfungen

- Vollständiger Android-Release-Build für ARM64 und x86-64 bestanden.
- 270 JVM-Tests ohne Fehler/Skips: 173 App, 77 Home, 20 Filter. Home-/Filter-
  Ergebnisse von Gradle als aktuell übernommen; sieben neue Tests enthalten.
- 17 Player-, acht Diagnose- und fünf Observer-JavaScript-Fixtures bestanden.
- PiP-, Home-, Navigation-, Adblock- und ursprüngliche Quellcode-Scope-Prüfungen
  bestanden. Offline-Chromium-Pixeltest mit zehn Mini-/PiP-/Rückkehrzyklen grün.
- Release-Lint ohne Fehler. Beide APKs mit dem ursprünglichen Zertifikat
  signiert, nicht debuggbar, Architektur/Version/Größe/Hash geprüft.
- Öffentlicher Chromium-Vergleich reproduziert die **weiterhin bestehende**
  YouTube-Pause; kein Android-PiP-Test und kein Nachweis fünfminütiger Wiedergabe.
- Sämtliche zehn angeforderten Geräteabläufe: **nicht durchgeführt**.

Details, Logschema, Quellen und Testgrenzen:
[Wiedergabediagnose](docs/PLAYBACK-DIAGNOSTICS.md).

## Diagnose-APK

Die Vorabversion lässt sich über die bisherige App installieren; die
gespeicherte Anmeldung wird dabei nicht gelöscht. Das höhere Versionscode-
Update kann Android nicht ohne Weiteres auf eine ältere APK zurückstufen.
Eine spätere höhere Version kann regulär darüber installiert werden.

- [ARM64-APK](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.11-preview/Weiktube.apk)
- [x86-64-APK](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.11-preview/Weiktube-x86_64.apk)

ARM64: 106595947 Bytes,
SHA-256 `7596d56c7ce3dcfb84dda00bd3dcde17e6bc3d7b6102b953fb0a738373a1171f`.
x86-64: 111603906 Bytes,
SHA-256 `688232282ae284ca357eeebb0a2d3ae0142fca3f8efd85f5326cd3d167d10389`.
