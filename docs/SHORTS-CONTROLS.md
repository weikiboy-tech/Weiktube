# Shorts-Ton und interaktive Zeitleiste – 1.3.18

## Befund und Grenzen der Ursachenanalyse

Die bestehende Compose-Oberfläche enthält im Shorts-Tab nur die vorhandene
BrowserController-WebView und eine Seitenladeanzeige. Der rote Wiedergabebalken
stammt aus der YouTube-Seite; bisher gab es keine eigene Seek-Anbindung.

Nachweisbare Lücken im Audio-Fix aus 1.3.16:
- Ein Video wurde vor Bestätigung des hörbaren Medienzustands in `seen` eingetragen.
  Spätere Stummschaltung blieb dadurch ohne weitere Behandlung.
- Der globale `movie_player` konnte zu einem anderen, vorab geladenen Video gehören.
- Medienlautstärke null wurde nicht berücksichtigt.
- Reine `playing`-Ereignisse deckten SPA-/Quellenwechsel ohne erneutes `playing`
  sowie späte Änderungen der Mute-Steuerung nicht ausreichend ab.
- Die Startadresse `/shorts/` ohne ID war von der Tonfreigabe ausgeschlossen.

Diese Fälle wurden in Simulationen reproduziert. Der genaue Auslöser auf dem
OnePlus ist **nicht ermittelt**, weil kein ADB-Gerät verbunden war. Keine Aussage,
dass ein bestimmter dieser Fälle allein das gemeldete Geräteproblem erklärt.

Im lokalen Chromium-Test mit strenger Autoplay-Regel wurde dagegen konkret
beobachtet: `paused=true`, `muted=false`, `volume=1`, `readyState=4`, ohne vorherige
Nutzeraktivierung. Erst ein echter Klick auf die angebotene Tonfreigabe erlaubte
hörbare Medienwiedergabe. Das ist ein Browsernachweis, kein Android-/YouTube-Nachweis.

## Umsetzung

Eine zustandsbasierte Steuerung arbeitet ausschließlich im sichtbaren Shorts-Tab
und bei Freigabe der bestehenden AdBlock-/Playback-Sperre. Das aktive Video wird
anhand sichtbarer Fläche gewählt; versteckte/inaktive Elemente werden ausgeschlossen.
Identität umfasst Dokumentbesuch, Medienelement, Shorts-Pfad und Medienquelle.

Pro Video wird automatisch höchstens ein Tonversuch ausgelöst. Der zuständige
lokale YouTube-Player wird, falls vorhanden, über `unMute()` angesprochen; das
Medienelement wird entstummt. Nur bei Medienlautstärke null wird diese auf 1 gesetzt.
Die Android-Systemlautstärke wird niemals verändert. Nachfolgende Medienereignisse
bestätigen den Zustand oder erkennen erneute Stummschaltung/Pause. Dann erscheint
eine echte Tonfreigabe-Schaltfläche. Ihr Klick darf das bestehende Video entstummen
und fortsetzen; synthetische Klicks werden ignoriert. Keine automatische Play- oder
Unmute-Schleife, keine neue Quelle und keine zweite WebView.

Bewusste Mute-Klicks werden für den aktuellen Besuch gespeichert, einschließlich
SPA-/Dokumentwechseln innerhalb derselben origin. BrowserController vergibt dafür
pro Tab-Eintritt einen neuen Besuchswert. Es werden keine Konto-/Sitzungsdaten geloggt.
Ein passender YouTube-Unmute-Hinweis wird nur bei tatsächlich laufendem, nicht
stummem Medienzustand mit positiver Medienlautstärke ausgeblendet und bei dessen
Verlust wiederhergestellt. Physische Audioausgabe ist daraus nicht beweisbar.

Die neue Zeitleiste wird direkt in derselben WebView am unteren Rand platziert,
oberhalb der nativen App-Navigation. Drei Pixel rot/grau, 32 CSS-Pixel Touch-Fläche.
Sie verwendet das aktive HTMLVideoElement, keine zusätzliche native JS-Poll-Schleife.
Zeit-/Quellen-/Tabereignisse werden auf einen Animation-Frame zusammengefasst.

Tippen setzt die Position. Horizontales Ziehen aktualisiert zunächst nur die rote
Vorschau; beim Loslassen wird genau einmal `currentTime` gesetzt. Der gültige
`seekable`-Bereich wird erneut geprüft, Lücken werden auf die nächste gültige
Position begrenzt, das exakte Ende wird vermieden. Unbekannte Dauer oder fehlende
Seek-Bereiche sperren die Bedienung. Kein Pause-/Play-Befehl durch Scrubbing,
daher bleibt eine manuelle Pause erhalten. Identitätswechsel verwerfen eine laufende
Operation. Vertikale Gesten, Mehrfinger-Abbruch, Pointer-Cancel und Rotation
verwerfen Scrubbing ohne Seek. Kein globales Blockieren der Shorts-Wischfläche.

## Geänderte Dateien

- app/src/main/java/com/weiktube/app/ShortsAudioScript.kt
- app/src/main/java/com/weiktube/app/BrowserController.kt (Besuchskennung)
- app/build.gradle.kts (1.3.18, VersionCode 32)
- scripts/check-shorts-audio.cjs
- scripts/check-shorts-controls-layout.cjs (neu)
- .github/workflows/android.yml
- scripts/check-home-scope.py
- scripts/shorts-controls-reviewed-changes.json (neu)
- scripts/package-source.py
- scripts/publish-release.ps1
- README.md, CHANGELOG.md, TESTING.md
- RELEASE-1.3.18.md und docs/SHORTS-CONTROLS.md

Normaler Player, PiP, Zoom, AdBlock-Engine, Gecko-Home, MainActivity-Navigation,
Google-Anmeldung und Verlauf bleiben unverändert.

## Ausgeführte Prüfungen

- 19 JS-Simulationen: erster Tonstart, Lautstärke null, nächstes Short, Wiederbetreten,
  Nutzer-Mute, verspätete Filter-/Medienbereitschaft, spätes YouTube-Remute ohne
  Dauerschleife, synthetischer Klick, Seek 25/50/75 %, beide Ziehrichtungen,
  Pause-Erhalt, Quellenwechsel während Seek, vertikale Richtung, Cancel, Resize,
  unbekannte Dauer, Bereichslücken, versteckte Medien und Listener-Idempotenz.
- Chromium mit lokal erzeugter MP4 inklusive Audiospur und HTTP-Range-Unterstützung:
  echte Medien-Seeks bei 25/50/75 %, Pointer-Drag in beide Richtungen, Pause-Erhalt,
  Quellenwechsel, bewusstes Mute, neuer Besuch und echter Tonfreigabe-Klick bestanden.
- Separater Chromium-Lauf unter strenger Autoplay-Regel: Blockade ohne User-Activation
  über CDP ohne Aktivierung ausgelesen; echter Klick setzt Wiedergabe fort.
- Vorhandene Browser-, Player-, PiP-, Zoom-, Home-, AdBlock-, Playback-Observer-
  und Diagnose-Skriptprüfungen bestanden. Quellen-Erhaltungsprüfung bestanden.
- Gradle: App-/Home-/Filter-JVM-Tests, beide Release-APKs und Release-Lint.
  Finale Zahlen stehen in den Release-Hinweisen.

## Nicht geprüft / verbleibende Einschränkungen

Kein Android-Gerät verbunden: keine Geräteabnahme, kein Test hörbaren Tons auf
Lautsprechern/Kopfhörern und kein Live-Test der aktuellen YouTube-Shorts-Seite.
Die Definition of Done ist damit noch nicht auf dem Nutzergerät bestätigt.
YouTube kann eigene Mute-/Autoplay-Regeln anwenden; ein vollautomatischer Tonstart
wird nicht zugesichert. Die einmalige echte Freigabe ist dafür die erlaubte Alternative.

Die Erkennung von YouTube-Mute-Buttons und Mediencontainern hängt an deren DOM
und deutschen/englischen Beschriftungen. Unbekannte Varianten können weitere
Anpassungen benötigen. Nicht seekbare Streams bleiben absichtlich nicht bedienbar.
Das bestehende YouTube-Seitenelement wird nicht global entfernt; je nach Website-
Variante kann dessen dünner Balken hinter der Weiktube-Zeitleiste sichtbar bleiben.

## Test auf dem Handy

1. Auf 1.3.18 aktualisieren; Shorts öffnen. Erstes Video und mehrere weitere auf Ton
   prüfen. Falls die Plattform blockiert: einmal auf die echte Tonfreigabe tippen.
2. Bewusst stummschalten, weiterwischen: stumm muss erhalten bleiben. Tab verlassen
   und erneut betreten: neue Toninitialisierung.
3. Am unteren roten Balken 25/50/75 % antippen; in beide Richtungen ziehen. Rote
   Vorschau muss folgen, Position muss beim Loslassen übernommen werden.
4. Video pausieren und spulen: muss pausiert bleiben. Während einer Geste Short
   wechseln oder Tab verlassen: keine Positionsänderung im nächsten Video.
5. Vertikal im Video wischen, anschließend normalen Player, Mini/PiP und Zoom testen.
