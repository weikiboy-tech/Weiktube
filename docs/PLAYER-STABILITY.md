# Player-Stabilität 1.3.22-rc1

Ausgangspunkt ist main 3bdef94. Die signierte ARM64-Testversion hat Versionscode 38.

## Befunde

Beim Erststart animierte Compose von einer unsichtbaren Karte mit Höhe null zum vollen Player. Während unterschiedlicher Ist- und Zielgrößen setzte die Oberfläche den Web-Player in den kompakten Modus. Damit konnte die Seite schon bei der ersten Navigation ihre normalen Inhalte ausblenden. Der neue Player übernimmt bei Quellenwechsel sofort die ausgewählte Darstellung. Das konkrete graue Bild auf dem OnePlus konnte hier nicht reproduziert oder eindeutig einem Dialog, Gecko oder dem Website-DOM zugeordnet werden.

Der alte Minimieren-Pfad ließ eine aktive CustomView am Activity-Fenster hängen. Die Orientierung wurde nur zurückgesetzt, wenn keine CustomView vorhanden war. Verspätete Vollbildanforderungen konnten die Mini-Ansicht erneut verdecken. Die Activity entfernt die zum Player gehörende Fläche und weist Vollbild für minimierte oder beendete Sitzungen ab. Jeder neue Ladevorgang bindet eigene Fullscreen-Callbacks. Bei X werden außerdem Zoom, Gestenfeedback, RYD-Abfrage und Darstellungszustand zurückgesetzt. Die bisherige Positionsspeicherung und das Freigeben der Wiedergabe laufen weiter.

Der Sensor schrieb vorher bei jeder Winkelmeldung die Systemleisten neu. Er berücksichtigt jetzt nur einen tatsächlichen Orientierungswechsel. Die bestehende Hysterese behandelt weiterhin die Kopfüberlage. Manuelles Maximieren nutzt die aktuelle Haltung.

Breite, Höhe, Kartenradius und Abstand hatten getrennte Animationen, während der Platz für die untere Navigation sofort wechselte. Diese Werte folgen nun einer gemeinsamen Compose-Animation. Rotation, Quellenwechsel und System-PiP erhalten direkt die neue Zielgeometrie. Der Systemübergang und das Schließen der Medienquelle erfolgen unmittelbar. Die visuelle Qualität dieser Übergänge auf Hardware ist offen.

## OnePlus 15R prüfen

1. APK über die bestehende App installieren. Auf der originalen Startseite mehrere unterschiedliche Videos öffnen. Unter dem Hochformatvideo müssen Titel, Kanal und Folgeinhalte erscheinen.
2. Quer halten, oben links minimieren und das Handy quer liegen lassen. Weitere Sensorbewegungen dürfen die Karte nicht vergrößern. Auf die Karte tippen, erneut minimieren, X drücken und ein anderes Video öffnen. Im Hochformat wiederholen.
3. Beim Verkleinern sofort wieder auf die Karte tippen. Auf Sprünge am unteren Rand, flackernde Videoinhalte und hängen bleibende Bedienelemente achten.
4. Vollbild drehen, in System-PiP wechseln, zurückkehren und X drücken. Weitere Videos auswählen. Position, Ton, Zoom, Startseite und Skip-Hinweise prüfen.

Robolectric prüft je 50 Zyklen mit simulierten Orientierungszuständen und derselben WebView. Chromium verwendet lokale Videofixtures. Diese Verfahren können weder den persönlichen Feed noch ausgelassene Frames auf dem Gerät bewerten.

## Testergebnisse

100 Zyklen im Robolectric-Controller (50 je Haltung), zusammen mit Compose-Zwischenframes, Richtungswechsel, PiP-Unterbrechung und Wiedergabeposition im gezielten Testlauf bestanden. In den vollständigen JVM-Läufen traten wechselnde Fehler in Compose-Attachment-, Verlauf- und Positions-Tests auf. Im letzten vollständigen App-Lauf bestanden 305 von 307 Tests. Die isolierte Wiederholung aller betroffenen Klassen bestand. Die Ursache der Schwankungen ist offen. Alle 21 JavaScript-/Browserprogramme bestanden. Der Offline-Pixeltest prüfte zehn Mini-/PiP-/Rückkehrzyklen, der Gecko-Test das gebündelte Modul. Debug-Lint und Release-Build liefen durch. Kein Gerät über ADB erreichbar. Keine Android-Framezeiten, persönliche Startseitenvideos oder visuellen System-PiP-Übergänge gemessen.

Geändert wurden MainActivity, BrowserController, PlayerController, WeiktubeUi, PlayerControllerLifecycleTest und PlayerTransitionTest sowie Versionsnummer, Scope-Prüfung und Changelog.
