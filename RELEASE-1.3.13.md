# Weiktube 1.3.13 – Bildschirmstatus nach dem Entsperren

Versionscode 27, Paket `com.weiktube.app`. **1.3.12 hat den gemeldeten Fehler
auf dem Handy nicht behoben:** Der Nutzer meldet nach rund fünf Minuten
Ruhemodus weiterhin den schwarzen Player. Dieses Update korrigiert eine
zusätzliche nachgewiesene Lücke beim Abgleich des Bildschirm-/Sperrstatus.
Ihre Bedeutung für den konkreten Handyfehler ist noch nicht praktisch
bestätigt; eine dauerhafte Behebung wird nicht behauptet.

## Befund und begrenzte Korrektur

In der Gegenprobe mit sieben neuen Robolectric-Tests schlugen zwei Fälle fehl:
Android meldet bereits ein interaktives, entsperrtes Gerät, doch das gespeicherte
`screenOff`-Flag bleibt beim Fokusgewinn gesetzt. Dadurch bleibt die vorhandene
WebView pausiert. Die fünf Kontrollfälle bestanden. Der zweite Fehlfall sah
zehn Fokus-Rückkehrzyklen vor und scheiterte bereits im ersten. Die Fixture injiziert
den veralteten Status gezielt; sie reproduziert keine echte Handy-Broadcastfolge.

`MainActivity` liest jetzt bei Fokusgewinn, Activity-Start/-Resume und den
Bildschirmmeldungen `SCREEN_OFF`, `SCREEN_ON` und `USER_PRESENT` den tatsächlichen
PowerManager-/Keyguard-Status neu. Eine verspätete Meldung ersetzt diesen
aktuellen Zustand nicht durch ihre bloße Bezeichnung. Ein noch gesperrtes oder
nicht interaktives Gerät bleibt blockiert. Ist die Activity im Vordergrund und
das Gerät entsperrt/aktiv, wird die vorhandene gesunde native View bei Bedarf fortgesetzt.
Das ist kein Play-Befehl: Es wird weder automatisch abgespielt noch neu geladen
oder eine weitere WebView erzeugt. Eine bewusste Benutzerpause bleibt erhalten.
Fokusverlust allein bleibt für sichtbares PiP ohne zusätzlichen Pausebefehl.

## Geänderte Dateien

- `MainActivity.kt`: gemeinsamer Abgleich des aktuellen Power-/Keyguard-Status
  und Fortsetzen der vorhandenen View bei zulässiger Rückkehr.
- `NativePipController.kt`: `SCREEN_ON` separat an den Host melden, ohne
  Bildschirm-an als erfolgreiche Entsperrung zu behandeln.
- `PlayerDiagnostics.kt`: feste `SCREEN_STATE`-Einträge mit Signal, aktuellem
  und vorherigem Sperrstatus sowie Prozess-/Activity-Kennung. Keine URLs,
  Kontodaten, Tokens oder freien Fehlermeldungen.
- Neue bzw. ergänzte Tests, Dokumentation und Versionsangabe.

`PlayerController`, UI, Gecko-Startseite, Kontoanbindung, Filter-Engine und
Präsentations-CSS bleiben unverändert. Die bisherige begrenzte Behandlung von
Dokument-/Rendererverlust bleibt erhalten. Es gibt keinen neuen Timeout-Recovery-
Mechanismus und keine Fälschung von Sichtbarkeit oder YouTube-Berechtigungen.

## Tatsächlicher Prüfstand

- Gegenprobe vor der Korrektur: sieben Tests, zwei reproduzierte Fehler und
  fünf erfolgreiche Kontrollen. Belege: `validation/screen-state-before-1.3.13/`.
- Nach der Korrektur: 46 gezielte Fälle bestanden, davon 15 Bildschirmstatus-,
  24 native PiP- und sieben Diagnose-Tests. Die neuen Fälle decken zehn
  Fokus-Rückkehrzyklen sowie die echte Receiver-Verbindung zum MainActivity-
  Abgleich und bestehenden Player unter kontrollierten Android-Zuständen ab.
  Zusätzlich geprüft: Entsperren, altes Video schließen, anderes Video öffnen,
  Dokument-Commit und erlaubte native Play-Anforderung in derselben WebView.
- Vollständiger Debug-/Release-Build und Regression erfolgreich:
  `BUILD SUCCESSFUL`, 322 JVM-Tests bestanden (225 App, 77 Home, 20 Filter).
  Unveränderte Home-/Filter-Ergebnisse von Gradle als aktuell übernommen.
  Nach dem zusätzlichen Test wurde die gesamte App-Suite erneut ausgeführt;
  Produktionscode und geprüfte APKs blieben dabei unverändert.
- App-Lint ohne Fehler, 37 Warnungen. Scope-Abgleich mit 46 ursprünglichen
  Quelldateien und native PiP-Quellverträge bestanden.
- Beide Release-APKs geprüft: Paket `com.weiktube.app`, Version 1.3.13/Code 27,
  ursprüngliches Signierzertifikat, nicht debuggbar, richtige ARM64-/x86-64-ABI
  und jeweils unter 128 MiB.
- JavaScript- und Chromium-Pixeltests sind gegenüber 1.3.12 unverändert und
  wurden für 1.3.13 nicht erneut ausgeführt. Frühere Ergebnisse sind keine
  erneute Browser-/Pixelabnahme dieser Version.
- Kein aktueller Android-Handy-/Emulatortest, kein sichtbarer Video-/Ton-Nachweis,
  keine praktischen 30-Sekunden-/5-/15-Minuten-Ruhemodus-Tests und keine echte
  System-/Rendererprozessbeendigung. Die bekannte YouTube-Pause in Mini/PiP
  bleibt ungelöst.

Die Tests verwenden den echten Activity-Fokus-Callback mit kontrollierten
Android-Zuständen und dem bestehenden Player unter Robolectric. Sie führen
nicht `MainActivity.onCreate`, die vollständige Oberfläche, Gecko oder Chromium
aus. Ein erfolgreicher Zustands-/Resume-Test ist kein Gerätetest.

Android dokumentiert verzögerbare Broadcast-Zustellung; Chromium behandelt
native Pause und Frame-Ausgabe getrennt von JavaScript. Das begründet den
gezielten Zustandsabgleich, beweist aber nicht die Ursache des Nutzerfotos.
Quellen und genaue Diagnoseinterpretation:
[Player-Diagnose](docs/PLAYBACK-DIAGNOSTICS.md), [Prüfprotokoll](TESTING.md).

## Installation und Release-Artefakte

Die folgenden Release-Artefakte gehören zu `v1.3.13`. Über eine bestehende
Installation aktualisieren, um die Website-Anmeldung zu behalten.

- [ARM64-APK](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.13/Weiktube.apk)
- [x86-64-APK](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.13/Weiktube-x86_64.apk)
- [Quellcode](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.13/Weiktube-Quellcode.zip)
- [Update-Feed](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.13/update.json)

| APK | Geprüfte Größe | SHA-256 |
| --- | ---: | --- |
| ARM64 | 106606499 Bytes | `9d07ec49d9ce92358f82133136825091e1c69965df49bef306463b99a24bbc14` |
| x86-64 | 111614458 Bytes | `8a51a7a7790db1cc788d49b1c0d377d58d0babdf677a7c20fecd1ea9d4e40782` |
