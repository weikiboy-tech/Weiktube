# Externe YouTube-Links und Android-Standardzuordnung – 1.3.19

Basis: main 33f633e9ef97583f8714700335af29e08b4d2128.

## Befunde und Umsetzung

Das bestehende Manifest ist bereits passend und bleibt unverändert: HTTPS für
`youtube.com`, `www.youtube.com`, `m.youtube.com`, `youtu.be`, BROWSABLE/DEFAULT,
exportierte MainActivity mit `singleTask`, ausdrücklich `autoVerify=false`.
Es gibt keine zweite Activity und keine behauptete Verifizierung fremder Domains.

Reparierte Lücken:
- acceptIntent wertete zuvor beliebige Aktionen und Text auch bei VIEW aus.
  Nun werden ausschließlich ACTION_VIEW mit data oder ACTION_SEND/text/plain mit
  EXTRA_TEXT ausgewertet. Fremde Extras/Schemes werden nicht als Navigation ausgeführt.
- Externe URLs werden separat streng geprüft, ohne interne URL-/Player-Kompatibilität
  zu verändern: HTTPS, exakte Hosts, kein Userinfo, nur Standard-/443-Port,
  keine kodierten Pfadtrenner/Steuerzeichen, Dot-Segmente oder Redirect-Wrapper.
- Normale Videos, youtu.be und /live nutzen den vorhandenen Player. Shorts gehen
  über openVideo in den vorhandenen Shorts-Tab. Playlists, Kanäle/Handles/Profile,
  deren Unterseiten und Suchergebnisse gehen in die bestehende Gecko-Webansicht.
- Normale Videozeitstempel (t/start, Sekunden oder h/m/s, auch #t) und öffentlicher
  Playlist-Kontext bleiben erhalten; explizites t=0 bleibt explizit. Trackingparameter
  werden entfernt. HTTP wird am externen Eingang nicht akzeptiert; intern bleibt die
  bisherige HTTP-Upgrade-Unterstützung unverändert.
- Eine bereits behandelte externe URL wird im Activity-Saved-State markiert.
  Bei Wiederherstellung wird sie nicht erneut über die gespeicherte Wiedergabe gelegt.
  Ein abweichender neuer Link hat Vorrang vor der alten Player-Wiederherstellung.
  onNewIntent setzt weiterhin den aktuellen Intent und verarbeitet jede Zustellung
  sofort, auch wiederholte gleiche Links. Es gibt keine eigene asynchrone Intent-Queue.
  Eine noch vorgemerkte alte Home-Videoübergabe wird beim externen Link verworfen.
- Ein bestehendes HTML-Vollbild wird vor der externen Navigation über die vorhandene
  Funktion geschlossen; vorhandene Player-/WebView-Instanzen werden weiterverwendet.

Unter Einstellungen & Updates steht jetzt:
**YouTube-Links standardmäßig mit Weiktube öffnen**.
Der Button öffnet ab API 31 ACTION_APP_OPEN_BY_DEFAULT_SETTINGS für Weiktube.
Android 8–11 und OEM-Fallbacks verwenden die App-Infoseite, bei Bedarf die allgemeine
Standard-App-Seite. ActivityNotFoundException und SecurityException werden abgefangen.
Es werden weder andere Apps noch Systemeinstellungen automatisch verändert.

## Geänderte Dateien

- app/src/main/java/com/weiktube/app/Links.kt
- app/src/main/java/com/weiktube/app/MainActivity.kt
- app/src/main/java/com/weiktube/app/WeiktubeUi.kt
- app/src/main/java/com/weiktube/app/ExternalYoutubeIntent.kt (neu)
- app/src/main/java/com/weiktube/app/YoutubeLinkSettings.kt (neu)
- app/src/test/java/com/weiktube/app/ExternalYoutubeIntentTest.kt (neu)
- app/src/test/java/com/weiktube/app/YoutubeManifestTest.kt (neu)
- app/build.gradle.kts (1.3.19 / VersionCode 33)
- scripts/check-home-scope.py, scripts/external-links-reviewed-changes.json
- scripts/package-source.py, scripts/publish-release.ps1
- README.md, CHANGELOG.md, TESTING.md, RELEASE-1.3.19.md, docs/EXTERNAL-LINKS.md

PlayerController, Player-Gesten/Zoom, PiP, ShortsAudioScript/Seekbar,
BrowserController, AdBlock, Google-Anmeldung und Verlauf sind unverändert.

## Automatisiert geprüft

Zehn neue JVM-/Robolectric-Tests:
- Vier Domains, Kurzlinks, normale Videos, Shorts und /live.
- Zeitstempel inklusive expliziter Null sowie Playlist-Kontext.
- Playlist-, Kanal-, Profil- und Suchseiten über den bestehenden Seiten-Einstieg.
- Host-/Scheme-Spoofing, Userinfo, Ports, Pfadtricks, ungültige IDs und Redirect-Wrapper.
- Plaintext-Teilen mit Begleittext und korrekter Zeichensetzung; unerlaubte Aktionen/Typen.
- Neue versus bereits konsumierte Start-Intents bei vorhandenem Saved-State.
- Tatsächliches MainActivity.onNewIntent im JVM-Test: zehn schnelle Linkpaare,
  letzte Zustellung gewinnt, identische Player-WebView; zwei Shorts nutzen dieselbe
  Shorts-WebView; alter vorgemerkter Home-Link wird gelöscht; Back minimiert den
  bestehenden Player. Keine Activity.onCreate/Gecko-Initialisierung in dieser Fixture.
- Android-Einstellungs-Intents für SDK 26/28/30/31/35/36 und fehlende/gesperrte OEM-Seiten.
- Zusammengeführtes Manifest: exported/singleTask und Intent-Auflösung aller vier
  HTTPS-Domains, kein Treffer für einen täuschend ähnlichen Fremdhost.

Der laufende Activity-Test verwendet Robolectric API 28, da dessen JVM-WebView
nicht die Algorithmic-Darkening-Implementierung der echten API-33+-WebView hat.
Die übrigen Intent-/Manifest-Tests verwenden API 35; SDK-abhängige Settings-Auswahl
wird zusätzlich über explizite SDK-Werte getestet. Das sind keine Gerätetests.

Vorhandene Skriptprüfungen für Shorts-Ton/Seekbar, Browser, Player, PiP, Zoom,
Original Home und AdBlock sowie die Quellen-Erhaltungsprüfung bestehen.
Vollständiger JVM-Lauf, Release-Build und Lint: Ergebnisse in RELEASE-1.3.19.md.

## Android-Grenzen und nicht durchgeführte Tests

Kein Handy per ADB verbunden. Nicht tatsächlich getestet: WhatsApp/Discord/Telegram,
Chrome, OEM-Einstellungsoberfläche, implizite Linkauflösung mit echter Domainauswahl,
Kaltstart/Prozesswiederherstellung auf einem Android-Gerät, hörbare Wiedergabe und
Android-Task-/Zurückverhalten in diesen Apps. Die Definition of Done ist auf dem
Nutzergerät noch zu bestätigen. Robolectric prüft keine echte Domain-Verifizierung.

Android 8–11 kann nach dem Löschen bisheriger Standards eine App-Auswahl anbieten.
Ab Android 12 werden nicht bestätigte Weblinks normalerweise im Browser geöffnet;
für fremde Domains muss der Nutzer Weiktube ausdrücklich zuordnen. Eine bestehende
YouTube-Zuordnung kann dafür zuerst deaktiviert werden müssen. Domains können nicht
verlässlich gleichzeitig mehreren Apps als bevorzugtem Handler zugeordnet werden.

In-App-Browser, ausdrücklich auf YouTube gerichtete Intents und in Chromes Adressleiste
geöffnete Seiten müssen den Android-Link-Resolver nicht nutzen. Dann hilft, sofern
die Quell-App es anbietet, **In anderer App öffnen** oder **Teilen → Weiktube**.
Redirect-/Attribution-Wrapper werden absichtlich nicht aufgelöst: stattdessen den
normalen direkten YouTube-Link teilen. Keine assetlinks.json auf fremden Domains,
keine gefälschte Verifizierung und keine automatische Änderung fremder Standards.

Offizielle Grundlagen:
- https://developer.android.com/training/app-links/verify-applinks
- https://developer.android.com/reference/android/provider/Settings#ACTION_APP_OPEN_BY_DEFAULT_SETTINGS

## Anleitung fürs OnePlus

1. Auf 1.3.19 aktualisieren. Weiktube → Menü → Einstellungen & Updates öffnen.
2. **YouTube-Links standardmäßig mit Weiktube öffnen** antippen.
3. In Android **Unterstützte Links öffnen** aktivieren. Falls eine Domainauswahl oder
   **Links hinzufügen** angezeigt wird, alle vier YouTube-Domains auswählen.
4. Falls weiterhin YouTube aufgeht: YouTube-App-Symbol lange drücken → App-Info →
   Standardmäßig öffnen → unterstützte Links deaktivieren bzw. Standards löschen.
   Anschließend die Weiktube-Zuordnung prüfen. Namen variieren mit OxygenOS-Version.
5. In WhatsApp/Discord einen echten normalen YouTube-Link und einen youtu.be-Link
   antippen. Beide müssen den vorhandenen Weiktube-Player öffnen.
6. Shorts teilen: muss im Shorts-Tab landen. Ein Video mit `?t=30` bzw. `&t=30`
   testen; Playlist und Kanal müssen in der vorhandenen Webansicht erscheinen.
7. Während Weiktube läuft mehrere unterschiedliche Links nacheinander öffnen.
   Dann Back verwenden: bestehendes Minimieren/Zurückverhalten prüfen.
8. App aus den letzten Apps schließen und einen weiteren Link antippen. Zusätzlich
   bei späterer Rückkehr nach Hintergrundzeit prüfen, dass kein alter Link den neuen
   Inhalt überschreibt. Bei internen Browsern die externe Öffnen-/Teilen-Funktion nutzen.

Ein ADB-Test mit explizitem Paket prüft nur die App-Verarbeitung. Erst ein impliziter
ACTION_VIEW-Aufruf ohne Paket/Komponente prüft Androids tatsächliche Standardzuordnung.
