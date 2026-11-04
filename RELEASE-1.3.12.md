# Weiktube 1.3.12 – Dokumentwechsel und Hintergrunddiagnose

Versionscode 26, Paket `com.weiktube.app`. Die Änderung korrigiert belegte
Fehler bei aufeinanderfolgenden Dokumenten in derselben Player-WebView und
ergänzt eine begrenzte Wiederherstellung bei positiv erkanntem Dokumentverlust.
**Die konkrete Ursache des nach 5/15 Minuten schwarzen Players auf dem Handy
ist weiterhin nicht nachgewiesen.** Kein Gerätetest belegt eine dauerhafte
Behebung. Die YouTube-seitige Mini-/PiP-Pause aus 1.3.11 bleibt offen.

## Belegte Fehler und gezielte Korrektur

Ein Vergleichslauf mit dem bisherigen 1.3.11-Player führte 13 Tests aus:
acht Fehler wurden reproduziert, fünf Kontrollen bestanden. Eine noch lebende
WebView konnte verspätete `onPageStarted`-, History-, Abschluss- oder
Fehlermeldungen ihres vorherigen Dokuments liefern. Die neue Auswahl wurde
dadurch mit der alten Videoidentität überschrieben oder vorzeitig als fertig
bzw. fehlgeschlagen behandelt. Ein nach der neuen Auswahl, aber vor deren
Dokumentbestätigung gestarteter Poll konnte noch Daten des alten Videos lesen.
Die Gegenprobe liegt lokal unter `validation/document-race-before-1.3.12/`.

Die Zuordnung prüft jetzt sowohl die WebView als auch das ausgewählte Dokument.
Polling und Wiedergabefreigabe warten auf dessen Commit; veraltete Antworten
werden verworfen. Reguläre Navigation und belegte SPA-Wechsel bleiben möglich.
Ein fehlendes Videoelement überschreibt eine vorhandene Position nicht mehr
mit null. Ein zusätzlicher Test reproduzierte die erneute Auswahl desselben
Videos während einer anstehenden Leer-Dokument-Wiederherstellung: Das noch
unbestätigte Dokument wurde wiederverwendet. Die Wiederverwendungsprüfung
berücksichtigt jetzt auch diesen Zustand; der Test besteht nach der Korrektur.
Diese Codefehler sind reproduziert; sie sind kein Beleg dafür,
dass alle schwarzen Bildschirme denselben Ursprung haben.

Eine zusätzliche Wiederherstellung reagiert ausschließlich auf eine gültige
JavaScript-Antwort, die nach dem bereits bestätigten Videodokument ein vollständig
geladenes, leeres `about:blank` ohne Video meldet. Pro ausdrücklicher Auswahl
ist höchstens ein automatischer Versuch erlaubt. Er verwendet die vorhandene
gesunde WebView, berücksichtigt Position und Pause und wird durch eine neue
Auswahl bzw. Schließen ungültig. Beim erneuten Verlust folgt eine sichtbare
Fehlermeldung statt einer Reload-Schleife. Ein langsamer Renderer, fehlende
Antworten oder bloßer Zeitablauf lösen diesen Pfad nicht aus. Der vorhandene
Ersatzpfad für tatsächlich beendete Renderer bleibt bestehen.

## Geänderte Komponenten

- `PlayerController.kt`: Dokumentzuordnung, Commit-/Polling-Grenzen,
  Positionserhalt und begrenzte Wiederherstellung eines verlorenen Dokuments.
  Native Instanzkennungen und Attachment-Beobachtung ergänzen die Diagnose.
- `PlayerDiagnostics.kt`: zufälliger Prozessmarker sowie getrennte Activity-,
  Controller- und View-Zähler; ausschließlich feste Ereignisse und typisierte
  Geometrie-/Sichtbarkeitswerte. Keine URLs, Video-IDs, Tokens oder Fehlermeldungstexte.
- `MainActivity.kt`: Prozessmarker im Saved-State-Bundle und Zuordnung aller
  vorhandenen Lifecycle-Diagnosen zur Activity. Fehlende alte Marker bleiben
  ausdrücklich unbekannt. Keine Änderung der Anmeldung oder PiP-Lifecycle-Regeln.
- `PlayerDocumentLifecycleTest.kt`, `PlayerComposeAttachmentTest.kt` und
  `PlayerDiagnosticsTest.kt`: neue Dokument-, Compose-Host- und Diagnoseprüfungen;
  bestehende Lifecycle-Fixtures berücksichtigen den Dokument-Commit.

Produktionsänderungen betreffen ausschließlich `PlayerController.kt`,
`PlayerDiagnostics.kt`, `MainActivity.kt` und die Versionsangabe. UI,
Gecko-Implementierung, Filter-Engine und `NativePipController` bleiben unverändert.
Es werden keine Sichtbarkeits-, DRM- oder YouTube-Berechtigungssignale gefälscht.

## Prüfstand und Grenzen

- Gegenprobe vor der Korrektur: 13 Tests, acht reproduzierte Fehler und fünf
  erfolgreiche Kontrollen.
- Zusätzliche Gegenprobe zur erneuten Auswahl desselben Videos während einer
  anstehenden Recovery: vor der Korrektur fehlgeschlagen, danach bestanden.
- Vollständiger Gradle-Lauf mit App-/Home-/Filtertests, Debug-Build, Debug-Lint
  und `:app:assembleRelease -PweiktubeStableApks=true`: `BUILD SUCCESSFUL`.
- 307 JVM-Tests bestanden: 210 App, 77 Home, 20 Filter; keine Fehler oder
  Fehlschläge. Unveränderte Home-/Filter-Ergebnisse von Gradle als aktuell
  übernommen. Enthalten sind 61 gezielte Fälle: 27 Dokument-, 24 bestehende
  Lifecycle-, drei Compose-Host- und sieben native Diagnose-Tests.
- App-Lint: keine Fehler, 37 Warnungen.
- Release-APKs geprüft: Paket/Version/Code und ARM64-/x86-64-ABI korrekt,
  ursprüngliches Signierzertifikat, nicht debuggbar und jeweils unter 128 MiB.
- 17 Player-, fünf Observer-, acht Diagnose-Fixtures sowie Home-/Filter-/Watch-
  Scriptprüfungen bestanden. Scope-Abgleich mit 46 ursprünglichen Quelldateien
  bestanden. Offline-Chromium-Pixeltest mit zehn Mini-/PiP-/Rückkehrzyklen bestanden.
- Keine Android-Handy-/Emulatortests, keine Live-YouTube-Langzeitabnahme,
  keine praktischen 30-Sekunden-/5-/15-Minuten-Rückkehrtests, kein echter
  Prozess-/Rendererabbruch und keine nachgewiesene PiP-Wiedergabe.

Die Compose-Prüfung verwendet eine isolierte echte `ComponentActivity` und
Compose-`AndroidView` mit dem bestehenden Wrapper/Controller unter Robolectric.
Sie prüft zehn Ersatz- und zehn Hintergrundzyklen sowie ein serialisiertes Bundle.
Das ist weder `MainActivity.onCreate` mit der kompletten App noch ein Chromium-
Pixeltest oder ein tatsächlicher Android-Prozessverlust. Die Controller-Fixtures
führen kein Seiten-JavaScript aus. Ein Dokument-Commit beweist keinen dekodierten
Videoframe. Weitere Details: [TESTING.md](TESTING.md),
[Player-Diagnose](docs/PLAYBACK-DIAGNOSTICS.md).

## Installation und Release-Artefakte

Die folgenden Release-Artefakte gehören zu `v1.3.12`. Über eine bestehende
Installation aktualisieren, um die gespeicherte Website-Anmeldung zu erhalten.

- [ARM64-APK](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.12/Weiktube.apk)
- [x86-64-APK](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.12/Weiktube-x86_64.apk)
- [Quellcode](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.12/Weiktube-Quellcode.zip)
- [Update-Feed](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.12/update.json)

| APK | Geprüfte Größe | SHA-256 |
| --- | ---: | --- |
| ARM64 | 106604887 Bytes | `536907edc83d7c168ef46d4656e922048e597c08d59e8c2663a19492cafd6447` |
| x86-64 | 111612850 Bytes | `c97b3decd95c7432327efe6798c47a446444e20c98ae92bea4ce03445f276c5e` |
