# SponsorBlock – 1.3.21-rc1 (lokale Testversion)

Basis: veröffentlichtes main 96432f7f458acfdb9e51d4beaa46b430fc77d25e (1.3.20).
Kein GitHub-Release, kein Updatekanal geändert. Die Geräteabnahme steht aus.

## Verhalten und Architektur

SponsorBlock ist zunächst ausgeschaltet. Unter Einstellungen → SponsorBlock erklärt ein Bestätigungsdialog den externen Dienst, Hash-Präfix, IP-Sichtbarkeit und Datenlizenz. Die Einstellungen bleiben im vorhandenen DataStore erhalten.

- Automatisch: Sponsor, Eigenwerbung, Like-/Abo-Aufruf.
- Manuell: Intro, Outro.
- Aus: Vorschau/Rückblick, musikfremde Abschnitte, Füllmaterial.
- Jede dieser acht Kategorien ist auf Automatisch/Manuell/Aus umstellbar.
- `exclusive_access` ist laut aktueller API eine Ganzvideo-Markierung, kein Skip-Segment. Sie wird nicht angefordert oder als Zeitabschnitt akzeptiert.

`SponsorBlockClient` fragt asynchron ausschließlich `GET https://sponsor.ajay.app/api/skipSegments/{prefix}` ab. Der Präfix besteht aus den ersten vier Hex-Zeichen von SHA-256(Video-ID). `actionType=skip`, Kategorienliste und `service=YouTube` werden mitgegeben. Der Parser filtert die Hash-Bucket-Antwort lokal auf die genaue Video-ID, Kategorie, Action-Type, UUID, endliche Zeitgrenzen und Dauer. Votes/locked werden übernommen. Maximal 2 MiB Antwort, 512 Segmente pro Video; keine Weiterleitungen.

Der begrenzte SharedPreferences-Cache hält maximal 64 Videos: positive Ergebnisse sechs Stunden, leere Ergebnisse/404 dreißig Minuten, Fehler fünf Minuten. Ein Mutex verhindert parallele Anfragen. Ein Mindestabstand von 1,1 Sekunden sowie persistenter 429-/Server-Backoff verhindern aggressive Abrufe. Cache-Leeren hebt Rate-Limits nicht auf. Verbindungs-/Lese-Timeout je fünf Sekunden. Ein schneller weiterer Videowechsel innerhalb des Mindestabstands kann für diesen Besuch ohne Daten bleiben; der Status zeigt dann die Nichtverfügbarkeit. Es gibt keine Wiederholungsschleife.

`SponsorBlockController` bindet Daten an WebView, Dokumentgeneration, Video-ID und einen zufälligen lokalen Besuchstoken. Alte Netzwerk- und JavaScript-Antworten werden verworfen. Dokumentwechsel, Renderer-Verlust und Schließen entfernen die Bindung. Deaktivieren stoppt die JS-Logik und weitere Anfragen; ein bereits laufender HTTP-Transport kann noch bis zum Timeout enden, sein Ergebnis wird verworfen.

`SponsorBlockScript` läuft ausschließlich im bestehenden normalen Watch-Dokument. Es verwendet Medienereignisse plus einen begrenzten Timer für die nächste Grenze, liest das vorhandene Videoelement und schreibt ausschließlich dessen `currentTime`. Keine `play()`, `pause()`, `load()`, zweite WebView, Audioquelle oder Änderungen an Sichtbarkeit/Sicherheitsprüfungen. Host-/Filterfreigabe, tatsächlicher Pausenzustand, Videodauer, Live-Zustand und seekbare Bereiche werden beachtet. Zeitplanung berücksichtigt Wiedergabegeschwindigkeit. Überlappende und unmittelbar angrenzende Auto-Segmente werden zu einem Sprung vereinigt. Ein Segment wird pro Besuch höchstens einmal automatisch versucht. Fehlende Seek-Bestätigung führt nicht zu einer Neustartschleife.

Bei API-Dauer 0 (unbekannt) erfolgt konservativ nur ein manueller Vorschlag, bei Abweichung über einer Sekunde zur tatsächlichen Dauer überhaupt kein Sprung. Live-/unendlich lange und nicht seekbare Medien werden nicht automatisch übersprungen.

Nur ein bestätigtes `seeked` am Ziel erzeugt eine Meldung und lokale Statistik. Rückgängig stellt die Position vor dem Sprung wieder her und korrigiert die Zählung. Bewusstes Hineinspulen unterdrückt Auto-Skips für diesen Abschnitt bis zum nächsten Video-Besuch; manuelles Überspringen bleibt möglich. Überlappungen zählen als ein Sprung unter der zuerst erreichten Kategorie. Gemessen werden übersprungene Videosekunden, nicht die bei erhöhter Geschwindigkeit tatsächlich verstrichene Zeit. Sofortiges Zurückspulen in den zuletzt übersprungenen Abschnitt korrigiert dessen Zählung ebenfalls.

Die native Meldung (acht Sekunden) mit Rückgängig und manuelle Buttons erscheinen nur im großen Player. Keine SponsorBlock-Overlays im Miniplayer/Android-PiP; automatische Medienereignislogik bleibt dort aktiv, sofern die bestehende Wiedergabe und Android dies zulassen. Manuelle Kategorien erfordern den großen Player. Keine Shorts-Integration.

## Vorhandene Zeitleiste

Ein ausschließlich dekorativer, `pointer-events:none`-Layer ergänzt bekannte positionierte YouTube-Zeitleisten (`.ytp-progress-bar`, `.ytm-progress-bar`, `.ytProgressBarLineHost`). Start/Breite folgen der tatsächlichen Dauer. Keine zweite Zeitleiste, keine globalen Styles, keine Verschiebung von Controls. Unbekannte oder ungeeignete DOM-Strukturen werden nicht verändert; der Status in den Einstellungen erklärt fehlende Markierungen. Die automatische Überspringung ist davon unabhängig. YouTube kann diese Strukturen ändern.

## Datenschutz und Lizenzen

Verwendet SponsorBlock-Daten von https://sponsor.ajay.app/ unter [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).
Die lokale Auswahl/Validierung der Community-Daten ist eine Bearbeitung. Die Datenlizenz beinhaltet Nichtkommerzialität und ShareAlike. Sie ist nicht mit der GPL-Lizenz des Browser-Extensionsquellcodes zu verwechseln. Für diese Implementierung wurde kein Extension-Code übernommen; die bestehende Projekt-Code-Lizenz bleibt erhalten.

Der Dienst erhält Hash-Präfix, Kategorien und übliche Verbindungsmetadaten einschließlich IP-Adresse. Das ist keine Anonymitätsgarantie. Keine Google-Cookies, Passwörter, OAuth-Tokens, Bewertungen, Einreichungen oder View-Tracking. Der HTTP-Client teilt keinen Browser-Cookiespeicher und bricht bei einem unerwarteten globalen CookieHandler sicher ab. Statistik und Cache bleiben lokal. Die Video-ID wird zur lokalen Bucket-Auswahl und im lokalen Cache verwendet.

Quellen, geprüft am 24.11.2026:
- [API](https://wiki.sponsor.ajay.app/w/API_Docs)
- [Kategorien/Action-Types](https://wiki.sponsor.ajay.app/w/Types)
- [Datenlizenz und Attribution](https://github.com/ajayyy/SponsorBlock/wiki/Database-and-API-License)
- [Attribution-Vorlage](https://gist.github.com/ajayyy/4b27dfc66e33941a45aeaadccb51de71)
- [Projekt](https://github.com/ajayyy/SponsorBlock)

## Tatsächlich durchgeführte Prüfungen

- JVM/Robolectric: 403 Tests bestanden (App 305, home-data 78, content-filter 20), keine Fehler/übersprungenen Tests. 17 neue SponsorBlock-Tests für API/Parser, Cache/Backoff, Preferences/Statistik, Controller-Token, alte Callbacks, Deaktivierung, Statistikreset und unveränderte Playback-Befehle.
- 14 deterministische JS-Medienzustands-Fixtures: Auto/Manuell/Aus, Überlappungen, Undo, bewusstes Spulen, Dauer/Live/seekable, Filterfreigabe, falsches Video, fehlende Bestätigung, Wiedergaberate und wiederholte Konfiguration.
- Chromium mit lokal erzeugtem 12-Sekunden-MP4: wirklicher HTMLVideoElement-Sprung und `seeked`, Undo mit weiterlaufendem Video, manuelles Seeking bei pausiertem Video, Marker-Geometrie, durchgehende Klicks, Resize, idempotente Listener/DOM-Schicht, gleicher Video-Knoten/Quelle, Opt-out und SPA-ID-Wechsel. Keine echten YouTube-Inhalte und kein Android-Hardwaretest. Die Test-Autoplay-Option existiert nur im Testbrowser.
- Alle 21 `check-*.cjs`/`check-*.mjs`-Prüfprogramme bestanden, einschließlich bestehender Audio-, Shorts-, AdBlock-, Zoom-, PiP-, Navigation- und Advanced-Player-Regressionen.
- Debug-Lint bestanden (keine Errors; bestehende API-/Dependency-/Accessibility-Hinweise und Style-Warnungen bleiben sichtbar).
- Ein öffentlicher read-only API-Smoke-Test lieferte HTTP 200 und einen gültigen Hash-Bucket. Die ausgewählte öffentliche Testvideo-ID hatte in der angefragten Kategorieauswahl keine Segmente. Keine Einreichungen, kein Tracking, kein Konto verwendet.

Release-Build für arm64-v8a und x86_64 sowie Release-Lint bestanden. APKs: com.weiktube.app, 1.3.21-rc1/code36, nicht debuggable, ursprüngliches Signierzertifikat unverändert. Source-Scope-Prüfung bestätigt 46 geschützte Originaldateien mit ausschließlich ausdrücklich erfassten Änderungen. Der Gecko-Lifecycle-Test gegen das gebaute APK bestätigt zehn modellierte Rückkehrzyklen; kein Gerätetest. Details und Hashes: Releasebericht.

**Keine Android-Gerätetests durchgeführt:** ADB meldete kein Gerät. Reale YouTube-DOM-Kompatibilität, manuelle Touch-Bedienung, längere Mini-/PiP-Wiedergabe und sichtbare Marker auf dem OnePlus sind noch nicht abgenommen. Simulationen ersetzen diese Abnahme nicht.

## Manuelle Abnahme auf Android

1. APK als Update installieren (nicht vorher deinstallieren), normal ein Video öffnen. SponsorBlock muss zunächst aus sein; Audio, AdBlock und Player wie bisher.
2. Einstellungen → SponsorBlock aktivieren, Hinweis lesen und bestätigen. Ein Video mit bekannten aktuellen SponsorBlock-Segmenten öffnen; Status und farbige Markierungen prüfen.
3. Vor einen Sponsorabschnitt spulen und normal hineinlaufen lassen. Ein Sprung, passende Zeitmeldung; Rückgängig bringt die ursprüngliche Stelle zurück, ohne erneuten Auto-Skip. Statistik vorher/nachher vergleichen.
4. Bewusst direkt in den Abschnitt spulen: Er bleibt anschaubar. Intro auf Manuell stellen und Button prüfen, auch bei pausiertem Video. Pausiert bleibt pausiert.
5. Mehrere Videos schnell öffnen, fehlende Daten/Offline testen: keine Sprünge aus vorherigen Videos, normale Wiedergabe bleibt möglich.
6. Mini und Android-PiP: laufendes Video erreicht Auto-Segment ohne Zusatzoverlay. Zurück, Vollbildrotation, Pinch/Doppeltipp/Lautstärke/Helligkeit und manuelle Geschwindigkeit prüfen.
7. Markierungen/Hinweise deaktivieren, Kategorien umstellen; SponsorBlock vollständig ausschalten: keine weiteren Auto-Skips. Cache/Statistik getrennt löschen und App neu starten; Einstellungen bleiben erhalten.
8. Shorts-Audio/Seekbar, Gecko-Anmeldung, Verlauf, externe Links und neues Video nach Player-Schließen auf Regressionen prüfen.

## Geänderte Komponenten

Neue Produktionsdateien: SponsorBlockSegment.kt, SponsorBlockPreferences.kt, SponsorBlockClient.kt, SponsorBlockStatistics.kt, SponsorBlockScript.kt, SponsorBlockController.kt, SponsorBlockUi.kt.
Integration: PlayerController.kt (Lifecycle/Filtergate/Poll), WeiktubeModel.kt (vorhandener DataStore), WeiktubeUi.kt (Settings/Overlay), app/build.gradle.kts (lokale RC-Version).
Tests: SponsorBlockClientTest.kt, SponsorBlockStatisticsTest.kt, SponsorBlockControllerTest.kt, check-sponsorblock.cjs, check-sponsorblock-dom.cjs.
Dokumentation/Verpackung: dieser Bericht, RELEASE-1.3.21-rc1.md, CHANGELOG.md, TESTING.md, README.md, PRIVACY.md, THIRD-PARTY-NOTICES.md, scripts/package-source.py, scripts/check-home-scope.py und sponsorblock-reviewed-changes.json.
Unverändert: Shorts-Code, Filter/AdBlock-Engine, PlayerGestureLayout/ViewportTransform, NativePipController, MainActivity, Links, PlayerPlaybackObserver, bestehende Advanced-Player-/RYD-Skripte und Veröffentlichungs-/Updater-Konfiguration.
