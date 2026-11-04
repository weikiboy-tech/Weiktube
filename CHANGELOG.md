# Änderungsverlauf

## 1.3.13 / Code 27 – Bildschirmstatus nach dem Entsperren

Der Nutzer meldet den schwarzen Player nach rund fünf Minuten Ruhemodus auch
mit 1.3.12. Dessen vorherige Korrekturen sind kein erfolgreicher Gerätefix.

- Aktuellen Power-/Keyguard-Status bei Fokusgewinn, Start, Resume und
  `SCREEN_OFF`/`SCREEN_ON`/`USER_PRESENT` abgleichen, statt einen alten
  Bildschirm-Sperrstatus allein aufgrund einer Broadcast-Bezeichnung zu behalten.
- Bei zulässiger Rückkehr dieselbe gesunde native WebView fortsetzen, ohne Play,
  Reload oder View-Austausch. Manuelle Pause bleibt erhalten.
- `SCREEN_ON` weiterhin vom tatsächlichen Entsperren unterscheiden;
  verspätetes `SCREEN_OFF` blockiert ein inzwischen entsperrtes Gerät nicht
  erneut. Fokusverlust allein pausiert sichtbares PiP nicht.
- Feste `SCREEN_STATE`-Diagnose mit aktuellem und bisherigem Sperrstatus,
  ohne Seiten-, Konto- oder Zugriffsdaten.
- Gegenprobe vor dem Fix: sieben neue Fokus-/Sperrstatus-Tests, zwei
  reproduzierte Fehler und fünf erfolgreiche Kontrollen. Danach 46 gezielte
  Fälle bestanden: 15 Bildschirmstatus-, 24 native PiP- und sieben Diagnose-
  Tests. Darunter ist Entsperren → Video schließen → anderes Video öffnen
  und Play anfordern in derselben fortgesetzten WebView.
- Vollständiger Debug-/Release-Build und 322 JVM-Tests bestanden: 225 App,
  77 Home, 20 Filter; unveränderte Home-/Filter-Ergebnisse als aktuell übernommen.
  App-Lint ohne Fehler, 37 Warnungen. Release-APKs, Scope-Abgleich und native
  PiP-Quellverträge geprüft. Die unveränderten JavaScript-/Pixeltests aus
  1.3.12 wurden für 1.3.13 nicht erneut ausgeführt.

Kein aktueller Handy-/Emulatortest; die konkrete Geräteursache bleibt
unbestätigt. Die bekannte YouTube-Pause in Mini/PiP bleibt offen.
[Release 1.3.13](RELEASE-1.3.13.md).

## 1.3.12 / Code 26 – Dokumentwechsel und Hintergrunddiagnose

- Verspätete Seiten-, Fehler- und History-Meldungen eines vorherigen Videos
  in derselben WebView werden von der aktuellen Auswahl getrennt. Polling
  wartet auf das bestätigte ausgewählte Dokument und verwirft ältere Antworten.
- Ein vorübergehend fehlendes Videoelement überschreibt die letzte bekannte
  Medienposition nicht mit dem Defaultwert null.
- Eine antwortende, vollständig geladene und leere `about:blank`-Seite nach
  einem bestätigten Videodokument erlaubt höchstens einen automatischen
  Wiederherstellungsversuch je ausdrücklicher Videoauswahl. Dieselbe gesunde
  WebView bleibt erhalten; Pausenzustand, Position und neuere Auswahl werden
  berücksichtigt. Fehlende Antworten oder verstrichene Zeit reichen nicht aus.
- Diagnose ergänzt Prozessmarker, Activity-/Controller-/View-Kennungen und
  native Attachment-/Sichtbarkeitsdaten ohne Seitenadressen oder Zugangsdaten.
- Dokument-Races, isolierte Compose-Host-Zyklen und Diagnoseformat bekommen
  zusätzliche Regressionstests. Auch die erneute Auswahl desselben Videos
  während einer anstehenden Leer-Dokument-Wiederherstellung lädt in diesem
  Fall erneut, statt das unbestätigte Dokument wiederzuverwenden.
- Debug-/Release-Build und 307 JVM-Tests bestanden: 210 App, 77 Home, 20 Filter,
  darunter 61 gezielte Player-/Compose-/Diagnosefälle. App-Lint: keine Fehler,
  37 Warnungen. Scriptprüfungen und lokale Chromium-Pixeltests bestanden.

Die Ursache des schwarzen Players auf dem Handy ist nicht nachgewiesen.
Keine Geräte-/Emulatortests für 30 Sekunden, fünf oder 15 Minuten Hintergrund,
echten System-/Rendererverlust oder PiP-Wiedergabe. Die YouTube-seitige Pause
aus 1.3.11 bleibt offen. Details: [Release 1.3.12](RELEASE-1.3.12.md).

Frühere Änderungen bleiben in den bestehenden Release-Dokumenten erhalten:
[1.3.11](RELEASE-1.3.11.md), [1.3.10](RELEASE-1.3.10.md),
[1.3.9](RELEASE-1.3.9.md), [1.3.8](RELEASE-1.3.8.md) und
[Prüfprotokoll](TESTING.md).
