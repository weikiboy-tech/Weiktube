# Advanced Player und Return YouTube Dislike – 1.3.20-rc1

> Veröffentlichung als **1.3.20, Code 35** nach Nutzerfreigabe. Funktionsstand entspricht rc1; siehe [Release-Hinweise](../RELEASE-1.3.20.md). Der folgende Bericht dokumentiert die Kandidatenprüfung und ihre unverändert geltenden Testgrenzen.

## Status und Ausgangsbasis

Lokaler Release-Kandidat, **nicht öffentlich veröffentlicht**. Basis: GitHub main
`161c7279ba80819228e4b1ecbdf1561ba991493f` (1.3.19), am 24.11.2026 gegen origin geprüft.
Paketname bleibt `com.weiktube.app`, Versionscode 34. Der öffentliche Updatekanal bleibt unverändert.
Dieser Bericht unterscheidet Implementierung, Simulation, Browserprobe und Geräteabnahme.
Die Android-Geräteabnahme ist noch offen: `adb devices -l` lieferte kein Gerät.

## Funktionen und Entscheidungen

### Gesten im Querformat-Vollbild

Der bestehende `PlayerGestureLayout` besitzt weiterhin genau dieselbe WebView.
`AdvancedPlayerGesture` koordiniert nur zusätzliche Ein-Finger-Gesten im erweiterten
Querformat-Player. Hochformat, Herunterziehen zum Miniplayer, die native Mini/PiP-Geometrie
und vorhandene Zweifinger-Skalierung bleiben erhalten.

- Links vertikal: Helligkeit des Activity-Fensters, 2–100 %, keine Änderung der globalen
  Systemeinstellung. Der vorherige Wert (auch automatische Systemhelligkeit `-1`) wird bei
  Fokusverlust, Verlassen der Darstellung oder Ablösen des Containers wiederhergestellt.
- Rechts vertikal: `STREAM_MUSIC` innerhalb der Android-Grenzen. Feste Lautstärke und
  Systembeschränkungen werden respektiert. Die gewählte Medienlautstärke bleibt danach erhalten.
- Doppeltipp links/rechts: konfigurierbare 5/10/15/20/30 Sekunden; weitere schnelle Tipps
  derselben Seite addieren Sprünge. Tatsächlich erreichte Sprünge werden angezeigt.
- Gedrückthalten: vorübergehende Geschwindigkeit, soweit der Player die Stufe meldet.
  Die vorherige Geschwindigkeit wird bei Loslassen, Abbruch, zweitem Finger, Fokusverlust,
  Minimieren/PiP, Rotation, Quellenwechsel und Activity-Pause wiederhergestellt.
  Pausierte Videos werden nicht gestartet. Keine neue Audioquelle und keine Play-Schleife.
- Prozentanzeige mit Sonne/Lautsprecher und animierter Fortschrittsanzeige; Geschwindigkeit
  bleibt während des Haltens sichtbar. Rückmeldung blendet nach Ende aus.

Die Trefferprüfung nutzt die tatsächliche DOM-Videofläche und `elementFromPoint` und
nimmt Buttons, Menüs, Eingaben, Timeline und Randbereiche aus. Bis zur Antwort werden
Original-Touch-Ereignisse gepuffert. Bei nicht verfügbarer Antwort werden sie spätestens
nach 120 ms an dieselbe WebView weitergereicht. Einzelne Tipps werden zur Doppeltipp-
Unterscheidung kurz verzögert. Native Seek/Hold-Gesten erreichen YouTubes eigene
Doppeltipp-/Long-Press-Erkennung nicht. Horizontale Bewegungen bleiben bei YouTube.
Zwei Finger brechen die Zusatzgeste ab und wechseln zur bestehenden Pinch-Erkennung;
ein übrig gebliebener Finger startet keinen Drag.

Seeking arbeitet nur mit endlicher Dauer und gültigen `seekable`-Intervallen des
bestehenden HTMLVideoElement. Positionen außerhalb werden auf gültige Grenzen begrenzt.
Es gibt keine Änderungen an `play()`, Audiofreigabe, Videoquelle, Filterregeln oder Stream-URLs.

### Qualität: Präferenz, keine garantierte erzwungene Auflösung

WLAN bevorzugt 1080p, mobile/getaktete Verbindungen 720p, unbekanntes Netz automatisch.
Android `ConnectivityManager` ermittelt den aktuellen Netzwerktyp. Änderungen gelten für
**das nächste Video**, nicht als wiederkehrende Umschaltung mitten in der Wiedergabe.

Die Integration liest `getAvailableQualityLevels` des vorhandenen mobilen Players. Nur
bekannte tatsächlich gemeldete Stufen erscheinen zusätzlich zu Automatisch in der Auswahl.
Die Standardpräferenz wird auch gespeichert, wenn sie für das aktuelle Video nicht verfügbar ist.
Es wird einmal die nächstniedrigere verfügbare Stufe über `setPlaybackQualityRange` angefragt.
Die Antwort wird mit `getPlaybackQuality` geprüft. Nach fünf Sekunden ohne Bestätigung
wird die Steuerung als unbestätigt angezeigt; keine erneute Erzwingung. Die initiale Suche
nach Fähigkeiten ist auf zwölf vorhandene Player-Poll-Zyklen begrenzt. Einstellungen-
Interaktionen und spätere Abweichungen beenden die automatische Steuerung konservativ.

Diese Methoden der mobilen Website sind **keine garantierte öffentliche API**. YouTubes
öffentliche IFrame-API hat programmatische Qualitätssteuerung entfernt. Es wird kein Embed
oder Ersatzplayer hinzugefügt. Fehlen Fähigkeiten, bleibt YouTubes automatische Auswahl
zuständig; die App zeigt ausdrücklich keine erfundene erfolgreiche 1080p-Auswahl an.

Die echte öffentliche mobile YouTube-Seite wurde in einer frischen, nicht angemeldeten
Headless-Chrome-Sitzung mit mobiler Emulation untersucht: HTTP 200, Player und Video
vorhanden, Geschwindigkeiten 0,25 bis 2 gemeldet, **Qualitätsliste leer**. Daraus folgt:
In dieser Probe waren 1,5× und 2× verfügbar, 2,5×/3× nicht; eine automatische 1080p-
Auswahl konnte nicht bestätigt werden. Das ist kein Android-WebView- oder Gerätetest.

### Return YouTube Dislike

Standardmäßig ausgeschaltet. Ein Aktivierungsdialog erklärt Datenquelle, öffentliche Video-ID,
technisch sichtbare IP-Adresse und Schätzcharakter. Keine Google-Cookies, Tokens oder
Zugangsdaten; kein Abstimmen beim externen Dienst. Nur HTTPS GET `/votes?videoId=…`.
Der separate Client nutzt weder WebView-Cookies noch den OAuth-Client. Weiterleitungen
werden abgewiesen; bei einem global installierten Java-Cookiehandler wird nicht angefragt.
Antwortgröße, Timeouts, Video-ID, Antwort-ID, Datenformat und negative Werte werden geprüft.

Asynchroner Abruf, serialisierte Anfragen, höchstens eine pro 1,1 Sekunden und 9.500 pro Tag.
Erfolgscache lokal eine Stunde, Fehler fünf Minuten, maximal 200 Einträge. `429` und
`Retry-After` (Sekunden oder HTTP-Datum) erzeugen Backoff. Gleichzeitige Abrufe derselben ID
teilen über den Cache dasselbe Ergebnis. Quellen-/Renderer-Generation und aktuelle Freigabe
verwerfen verspätete Ergebnisse. Kein erfundener Nullwert bei Fehlern.

Ein zusätzlicher Textspan sitzt im vorhandenen Dislike-Button; keine neuen Vote-Handler,
keine Veränderung seines ursprünglichen Inhalts oder Klickpfads. Ganze Zahlen werden
mit deutscher Gruppierung formatiert. Tooltip/Accessibility-Text nennt RYD und Schätzung.
Ist YouTubes Button nicht erkennbar, wird nichts an einer geratenen Position eingefügt.
Die vorhandene Poll-Routine hält die Beschriftung bei SPA-/DOM-Wechseln aktuell; es entstehen
keine zusätzlichen MutationObserver oder Event-Listener pro Poll.

## Tatsächlich ausgeführte Prüfungen

Die genauen abschließenden Summen stehen in `RELEASE-1.3.20-rc1.md`.

- JVM/Robolectric: Gestenrichtungen, deaktivierte Seiten, Grenzen, Helligkeits-Restore,
  Einzel-/Doppeltipp, kumulative Sprünge, Long-Press-Abbruch, verspätete/fehlende Trefferprüfung,
  Pinch-Vorrang im echten Gestencontainer mit PlayerController, veraltete Dokumentantworten,
  zehn Moduswechsel mit gleicher WebView, Preferences-Codec, Netzwerkauswahl,
  RYD-Erfolg/Nullwert/Fehler/TTL/Parallelität/Backoff/Cache-Verdrängung und bestehende Regressionen.
  Robolectric führt **keine echte Chromium-Wiedergabe** aus.
- JavaScript-VM: Seek-Grenzen und getrennte seekable-Bereiche, Play/Pause unverändert,
  Rückkehr auf 1,25×, abgelehnte Geschwindigkeiten, Sichtbarkeitsabbruch, Qualität-Fallback,
  wirkungslose Setter und manuelle Auswahl; vorhandene Player-/AdBlock-/Shorts-/PiP-Skripttests.
- Lokales Chromium mit Testseiten: echte DOM-Trefferprüfung, ursprünglicher Dislike-Button
  weiterhin bedienbar, idempotente Beschriftung, veraltete ID/Opt-out/fehlender Button;
  bestehende Audio-, Shorts-Scrubbing-, Player-Layout- und Zoomtests.
- Live-Netzprobe: RYD lieferte HTTP 200, passende ID und numerisches Dislike-Feld für das
  öffentliche Testvideo `M7lc1UVf-VE`. Kein Nutzerkonto und keine Bewertung verwendet.
- Android Release-Build, Lint, Source-Scope-Prüfung und APK-Signaturprüfung.

**Nicht durchgeführt:** Touch-/Audio-/PiP-/Rotationsprüfung auf einem echten Handy,
langfristige API-Nutzung, angemeldete YouTube-Sitzung und Qualitätswechsel im Android-WebView.
Keine Aussage über erfolgreiches 1080p/2160p-Erzwingen oder zuverlässiges 3× auf jedem Video.

## Bekannte Grenzen

- Neue native Ein-Finger-Gesten gelten im App-Querformat-Vollbild. Hochformat, Shorts,
  Mini/PiP und eventuell separat von YouTube erzeugte HTML-Custom-View-Flächen nutzen die
  vorhandene Bedienung. Keine neuen Gesten auf Menüs oder Zeitleisten.
- Bei langsamer WebView (>120 ms Trefferantwort) wird die Eingabe an YouTube weitergegeben,
  statt Bedienelemente zu blockieren. Dann greift dessen vorhandene Gestenbedienung.
- Langdruck-Geschwindigkeiten sind nur auswählbar, wenn das aktuell geöffnete Video sie meldet.
  Ein Player kann eine zunächst akzeptierte Rate später selbst ändern; keine Zwangsschleife.
- Qualitätspräferenzen gelten einmal pro Video, Netzwerk-/Einstellungsänderungen beim nächsten.
  Die Live-Probe lieferte keine Qualitätsstufen; hier ist nur automatische Qualität nachgewiesen.
- YouTube kann DOM-Selektoren und interne Qualitätsmethoden ändern. RYD kann ausfallen,
  ratenbegrenzen oder keine Daten haben. Dann fehlt die Anzeige, die Wiedergabe bleibt unabhängig.
- Authentifizierte Empfehlungen, Filter, Kontoanbindung, Shorts und native PiP-Implementierung
  wurden nicht umgebaut. Die neue Integration ersetzt keine bestehende Wiedergabequelle.

## Manuelle Abnahme am Handy

1. APK über die bestehende Installation installieren. Einstellungen & Updates → Videoplayer.
2. Normales Video öffnen, quer drehen. Links/rechts hoch und runter wischen. Grenzen und
   Overlay prüfen; Player verlassen und Helligkeit mit dem vorherigen Zustand vergleichen.
3. Sprungweite auf 15 Sekunden: doppelt links/rechts und mehrfach rechts tippen. Position
   beobachten; Start-/Endgrenzen sowie ursprüngliche Pause prüfen. Einzeltipp und Timeline testen.
4. In YouTube 1,25× wählen, freie Videofläche halten: 2× bis zum Loslassen, dann 1,25×.
   Bei pausiertem Video darf Halten nicht starten. Halten → zweiter Finger, Rotation, Home/PiP,
   Unterbrechung und anderer Videostart dürfen keine temporäre Geschwindigkeit zurücklassen.
5. Pinch hinein/heraus, ins Hochformat, zum Mini ziehen; Android-PiP öffnen und zurück.
   Mehrere Videos nacheinander testen, Ton und Position dürfen nicht neu starten.
6. WLAN-/Mobilpräferenz ändern, jeweils nächstes Video öffnen und Qualitätsstatus prüfen.
   Nur gemeldete Stufen erwarten. Im YouTube-Menü manuell ändern: kein Zurückschalten.
7. RYD aktivieren und Hinweis bestätigen. Zahl neben Dislike ansehen, normales Dislike-Verhalten
   nur bei Bedarf selbst prüfen. Video schnell wechseln, offline testen, RYD abschalten.
   Bei fehlenden Daten darf keine falsche Null erscheinen.
8. App neu starten: Einstellungen bleiben; Shorts-Ton/Seekbar, externe Links, Verlauf,
   persönliche Startseite, Rotation, Miniplayer/PiP und AdBlock zusätzlich prüfen.

## Geänderte Dateien

Bestehende Laufzeitdateien:
- `app/src/main/AndroidManifest.xml` – normale Netzwerkstatus-/Audioeinstellungsberechtigungen.
- `MainActivity.kt` – temporäre Rate bei Activity-Pause zurücksetzen.
- `PlayerController.kt` – dokumentgebundene Gestenbefehle, HUD, Fähigkeiten, RYD-Koordination.
- `PlayerGestureLayout.kt` – zusätzliche Gestenkoordination, Fokus-/Detach-Bereinigung.
- `WeiktubeModel.kt` – neue Schlüssel im vorhandenen DataStore.
- `WeiktubeUi.kt` – Gestenanschluss, HUD, vorhandener Einstellungsbereich.

Neue Laufzeitdateien (alle unter `app/src/main/java/com/weiktube/app/`):
`AdvancedPlayerGesture.kt`, `AdvancedPlayerScript.kt`, `PlayerWindowControls.kt`,
`PlayerGestureFeedback.kt`, `PlayerPreferences.kt`, `PlayerQuality.kt`, `PlayerSettings.kt`,
`ReturnDislikeRepository.kt`, `ReturnDislikeScript.kt`.

Tests: neue `AdvancedPlayerGestureTest.kt`, `AdvancedPlayerIntegrationTest.kt`,
`PlayerPreferencesTest.kt`, `ReturnDislikeRepositoryTest.kt`; Ergänzung in
`PlayerControllerLifecycleTest.kt`. Neue Scripts `check-advanced-player.cjs`,
`check-player-quality.cjs`, `check-advanced-player-dom.cjs`.

Build/Dokumentation: `app/build.gradle.kts`, `scripts/check-home-scope.py`,
`scripts/advanced-player-reviewed-changes.json`, `scripts/package-source.py`, `README.md`,
`CHANGELOG.md`, `TESTING.md`, `THIRD-PARTY-NOTICES.md`, `PRIVACY.md`,
`RELEASE-1.3.20-rc1.md`, dieser Bericht.

`NativePipController`, `PlayerDragGesture`, `PlayerViewportTransform`, `PlayerPlaybackObserver`,
Shorts-Skripte, externe Links, Gecko-/Kontoanbindung sowie `content-filter` bleiben unverändert.

## Quellen

- [RYD-Dokumentation](https://returnyoutubedislike.com/docs/)
- [RYD-Nutzungsrechte und Rate-Limits](https://www.returnyoutubedislike.com/docs/usage-rights)
- [RYD-Endpunkte](https://returnyoutubedislike.com/docs/endpoints)
- [RYD-Projekt](https://github.com/Anarios/return-youtube-dislike)
- [YouTube IFrame-API, Änderungen zur Qualitätssteuerung](https://developers.google.com/youtube/iframe_api_reference)
- [Android AudioManager](https://developer.android.com/reference/android/media/AudioManager)
