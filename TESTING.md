# Weiktube – Prüfprotokoll

## Aktueller Stand 1.3.9: Miniplayer-/PiP-Geometrie

**Finaler Prüfstand mit Schubladenkorrektur:** Release-Build und Artefaktprüfung
bestanden, 236 JVM-Tests grün:
144 App, 72 Home, 20 Filter, keine Fehler/Skips. Unveränderte Home-/Filtertests
wurden von Gradle als aktuell übernommen. App/Home-Lint: 0 Fehler, 33/41 Warnungen.
Beide APKs haben die ursprüngliche Signatur, sind nicht debuggbar und unter 128 MiB.

16 Player- und 5 Observer-Fixtures, native PiP-Quellverträge sowie die neue lokale
Chromium-Layoutfixture mit negativen Kontrollen und zehn Zustandszyklen bestanden.
Home-Kaltstart mit 120 modellierten Zyklen, Navigation mit zehn modellierten Zyklen
und vorhandene Adblock-Scriptprüfungen ebenfalls grün. Die Scope-Prüfung erhält
die 46 ursprünglichen Quelldateien außerhalb exakt dokumentierter Änderungen.
Dies sind keine Android-Instrumentations-/Compositor-Tests.
Die aktualisierte lokale Chromium-Fixture prüft echte rote/blaue PNG-Pixel,
negative 164-/40-Pixel-Überdeckungen sowie zehn Zyklen, Benutzerpause und
Scrollposition; alle Prüfungen bestanden, ohne neue Abhängigkeit.

Belegt ist der isolierte Browservergleich mit der echten öffentlichen mobilen
YouTube-Seite: vor der Korrektur Video-Y=53 bei 196×110 im Mini-Modus und Video-Y=292
bei 384×216 nach Rückkehr. Mit der kombinierten Mini-/PiP-Videobox und begrenzter
Watch-Zentrierung lagen 31 Messpunkte aus zehn Mini-/PiP-/Rückkehrzyklen bei X=0/Y=0;
derselbe Videoknoten und `currentSrc` blieben erhalten. Dieser Chromium-Lauf ist
kein Android-WebView-, Compose- oder System-PiP-Test.
**Korrekte Bounds bedeuteten nicht korrektes Bild:** Anschließende Screenshots
und Hit-Tests fanden `.ytmDrawerLayoutContainerDrawerLayout` über dem Video.
Die geschlossene Schublade war 360 Pixel breit, fest bei `left: -100vw` positioniert,
mit `z-index: 2` und Deckkraft 1. Im 196er Mini-Viewport überdeckte sie 164 Pixel,
im 320er PiP-Viewport 40 Pixel. 164×3,5=574 Bildpunkte passen zur historischen
grauen Android-Fläche. Der CSS-Fix blendet ausschließlich diese Schublade bei
Mini-/System-PiP-Rootattributen aus; Normalansicht/gewöhnliches Querformat bleiben
unberührt. Der finale öffentliche Browserlauf enthält 32 Messpunkte aus
Normalansicht, negativer Baseline und zehn Mini-/PiP-/Rückkehrzyklen. Es blieb
stets genau ein Video mit demselben Knoten und `currentSrc`. Die Mini- und
PiP-Endscreenshots des zehnten Zyklus wurden visuell vollständig und ohne grauen Balken bestätigt.

Im finalen Browserlauf mit Schubladenfix pausierte YouTube beim zweiten Mini-
Übergang bei `currentTime=16.010453` Sekunden; es wurde keine automatische
Wiederaufnahme erzwungen. Ursache und durchgängige Wiedergabe bleiben als
separates Problem offen. Der historische
OnePlus-Test vom 03.11. zeigte bereits korrekte DOM-Maße bei gleichzeitig defektem
sichtbarem Videobild; eine gemeinsame Ursache aller Renderingfehler ist nicht
abschließend nachgewiesen.

**Keine aktuellen Handytests.** Der isolierte Android-Emulator erreichte keine
nutzbare Sitzung und wurde beendet. Auf Nutzerwunsch erfolgen keine weiteren
Geräteversuche; der Nutzer übernimmt die Prüfung der APK. Es wird kein Erfolg
von Android-Instrumentation oder echtem Compose-/WebView-Rendering behauptet.

Offene praktische Abnahme: mehrere Mini-/PiP-/Vergrößerungswechsel, Android-Zurück
ohne zusätzliche Pause, System-Play/Pause, X-Schließen, Rotation und Rückkehr zur
anklickbaren Gecko-Startseite. Änderung und Prüfgrenzen:
[RELEASE-1.3.9.md](RELEASE-1.3.9.md).

## Vorheriger Stand 1.3.8: Android-Bild-in-Bild

**Release-Build und Artefaktprüfung bestanden. Kein echter Gerätetest wurde durchgeführt.**
234 JVM-Tests grün: 142 App, 72 Home, 20 Filter. Unveränderte Home-/Filtertests
wurden durch Gradle als aktuell übernommen. App-/Home-Lint ohne Fehler,
33 beziehungsweise 41 Warnungen. Beide APKs behalten das ursprüngliche Zertifikat,
sind nicht debuggbar und liegen unter 128 MiB.

24 native PiP-Testausführungen mit Robolectric (API-Zweige 26/31/33/35) bestanden:
Parameter, echte Broadcast-Aktionen, doppelte Einstiege, veraltete Aktionen nach
Videowechsel, zehn Expand-Runden, Sperren/Entsperren ohne Autoplay und Cleanup.
Drei Decoder-Tests und fünf Script-Fixtures prüfen den begrenzten Playback-Observer.
Alle 16 Player-Script-Fixtures bestanden, einschließlich zehn PiP-Übergängen mit
erhaltener Videoidentität, Zeit, Benutzerpause und Scrollposition. PiP-Integration,
Home-Kaltstart/-Rückkehr und vorhandene Adblock-Scriptprüfungen ebenfalls grün.
Das sind keine echten YouTube-/Android-WindowManager-Tests.

Noch offene Geräteabläufe, jeweils für Android 12+ und soweit verfügbar Android
8–11:

1. Laufendes YouTube-Video über Home-Geste/Taste verlassen: genau ein Android-
   PiP-Fenster, gleiche Video-ID und Position, keine konkurrierende Wiedergabe.
2. Pausiertes Video, ladendes/fehlerhaftes Video, Gecko-Startseite ohne Video sowie
   Konto-/Einstellungsdialog verlassen: kein unerwarteter automatischer PiP-Start.
3. Manuelle PiP-Taste mit laufendem und pausiertem geeignetem Video prüfen;
   deaktivierte Systemfreigabe oder fehlende Geräteunterstützung verständlich melden.
4. Android-Play/Pause wiederholt verwenden, anschließend Video wechseln. Eine alte
   Systemaktion darf das vorherige Video nicht starten oder das neue beeinflussen.
5. PiP vergrößern und erneut verkleinern, einschließlich Hoch-/Querformat und
   internem Miniplayer: Videodokument, Position und Benutzerpause erhalten.
6. PiP schließen und App vollständig verlassen: Wiedergabe spätestens bei
   `onStop` pausiert; kein unsichtbares Audio und kein erneutes spontanes Öffnen.
7. Bildschirm ausschalten/sperren: Wiedergabe pausiert. Entsperren und Rückkehr
   ohne unerwarteten Wiedergabestart prüfen.
8. YouTube-Player und direkte HTTPS-Videodatei getrennt prüfen, einschließlich
   Adblock-Fehler, Netzverlust und Rendererabbruch. Browserseitiges Stoppen durch
   YouTube ausdrücklich als solches dokumentieren, ohne Sichtbarkeit oder
   Sicherheits-/DRM-Signale zu manipulieren.
9. Während PiP WhatsApp/Chrome öffnen; eingehenden Anruf und Audiofokusverlust
   durch andere Medien testen. Chromium verwaltet den Audiofokus; kein zweiter
   nativer Focus-Owner oder Hintergrunddienst wurde hinzugefügt.

Diese Liste ist **nicht abgearbeitet**. Weitere Grenzen und Änderungsumfang:
[RELEASE-1.3.8.md](RELEASE-1.3.8.md).

## Historisches Prüfprotokoll 1.2.3

## Aktueller Stand 1.2.3: Browser-Popup zurückgenommen

Am 02.11.2026 wurde der experimentelle Chrome-Custom-Tab nach ausdrücklicher Ablehnung entfernt. Automatischer Browserstart, Rückkehrseite, Browserbibliothek, Custom-Tab-Serviceabfrage und versuchsweise eingeschränkte Video-Intentfilter sind zurückgenommen. Die native Startseite und die bisherigen Video-Links sind wiederhergestellt. Der Versionscode steigt auf 6, damit die Rücknahme als normales Update ohne Löschen der Appdaten installiert werden kann.

- APK gebaut, exportiert und auf dem OnePlus installiert: Version 1.2.3, Code 6, 17.606.167 Bytes; SHA-256 `095a62e582df01e7a30f8c57af192abc1a77f0fb4bef57c87d1b409782eb04db`.
- Abschließender Build erfolgreich; 81 Tests in 15 Suiten bestanden, keine Fehler oder übersprungenen Tests. Lint: 0 Fehler, 16 Warnungen. Für diesen Lauf wurde das vorhandene separate Buildverzeichnis `app/build-home-1.2.1` wiederverwendet; seine aktuellen Berichte gehören nun zu 1.2.3.
- Normaler Launcher-Start auf dem Gerät zeigt Weiktubes native Startseite und den vorhandenen eigenen Kontokanal. Vordergrundaktivität bleibt `com.weiktube.app/.MainActivity`; kein Chrome-Popup.
- Ein Tipp auf das vorhandene Big-Buck-Bunny-Thumbnail öffnete direkt den Weiktube-Watch-Player mit Video, passendem Titel und Kommentarvorschau. Auch hier blieb Weiktube im Vordergrund. Dies prüft den vorhandenen nativen Kartenweg, nicht einen persönlichen Empfehlungsfeed.
- Die originale persönliche YouTube-Startseite innerhalb der nativen App ist weiterhin **nicht umgesetzt**. Der wiederhergestellte Abo-/Verlaufsfeed erfüllt diese Anforderung nicht.
- Im verworfenen 1.2.2-Versuch war die richtige YouTube-Sitzung auch in Chrome sichtbar; die Browserleiste ließ sich ausblenden. Der Nutzer lehnte die Custom-Tab-Oberfläche dennoch ab. Die automatische Übergabe eines gewöhnlichen Video-Tipps aus Chrome wurde nicht nachgewiesen. Der Versuch wurde vor Änderungen der Android-Linkauswahl abgebrochen; es gab keine öffentliche Veröffentlichung.

Die folgenden Abschnitte dokumentieren frühere Prüfungen und verbleibende Abnahmen. Sie ersetzen keinen Nachweis der weiterhin fehlenden Originalempfehlungen.

Stand: 2. November 2026. Automatisierte Prüfungen, frühere Gerätetests und noch offene Abnahme des aktuellen Umbaus sind getrennt aufgeführt. Ein erfolgreicher Build ist kein Nachweis vollständiger Funktionsgleichheit mit YouTube 21.43.528.

## Neuer Stand 1.2.1: native Startseite

Die Startseite zeigt den echten Titel/Avatar des eigenen Kontokanals, ausdrücklich „Verlauf auf diesem Gerät“ und „Videos deiner Abos“. Der Feed lädt `subscriptions.list(mine=true)` und nimmt nur tatsächlich angeforderte Upload-IDs aus den zugehörigen abonnierten Kanälen an. Allgemeine Webempfehlungen werden nicht als persönliche Vorschläge dargestellt. Kontowechsel leert die Feedinformationen; der lokale Verlauf bleibt als geräteeigener Verlauf getrennt.

Die Ansicht verwendet keine Custom Tabs, Browser-Symbolleiste oder zusätzliche Browserbibliothek. Suche, Kanäle und Watch-Seiten behalten ihre originalen Websitzungen. **Die angeforderte originale personalisierte YouTube-Empfehlungsseite ist weiterhin nicht umgesetzt.** Eigene Abo-Uploads und lokaler Verlauf erfüllen diese Anforderung nicht; sie bleibt offen.

Neu ergänzt sind Tests für Home-Routing, überlappende Sichtbarkeit von Startseite/Abos und das Ablehnen nicht angeforderter Videos bzw. unbekannter Kanäle. Die vorhandenen Kontositzungstests sichern gegen Antworten früherer Freigaben ab. Der vorliegende 1.2.1-Testlauf ist unten ausgewertet. Kontowechsel/Feedleerung und die vollständige Rückkehr aus Suche/Kanal/Video zur nativen Startseite bleiben gesondert auf dem Gerät zu prüfen.

## Automatisierte Prüfungen

Die vorhandenen JUnit-XML-Berichte für 1.2.1 in `app/build-home-1.2.1/test-results/testDebugUnitTest/` mit Zeitstempeln `2026-11-02T19:49:41` bis `19:49:42` enthalten **81 bestandene Tests in 15 Testsuiten**, keine Fehler, keine fehlgeschlagenen und keine übersprungenen Tests. Die frühere 1.2.0-Basis hatte 72 Tests.

| Bereich | Tests |
| --- | ---: |
| Lokaler Verlauf | 4 |
| YouTube-Links, HTTPS und Regressionen | 10 |
| Öffentliche Bildadressen | 3 |
| Domainfilter und Regressionen | 14 |
| Update-Policy | 9 |
| Regeln für Videoaktionen | 8 |
| Navigation aus der Videoseite | 2 |
| API-Nutzdaten für Schreibaktionen | 6 |
| Kontositzungen und veraltete Antworten | 5 |
| Abo-Uploaddaten und Zusammenführung | 7 |
| Erneutes Lesen nach Kontofreigabe | 5 |
| Routing zur nativen Startseite | 6 |
| Gemeinsame Feed-Sichtbarkeit | 2 |

Dies sind lokale Tests für Regeln und Nutzdaten. Sie führen keine echten Likes, Kommentare, Abos, Playliständerungen oder Google-Dialoge aus. Die frühere Basisprüfung bestand aus 25 Tests für Links, Filter und Updates; diese Zahl beschreibt nicht mehr den gesamten aktuellen Testsatz.

`node scripts/check-player-scripts.cjs` prüft die tatsächlich eingebetteten Player-Skripte in simulierten DOM-/Medienobjekten, unter anderem verspätete Wiedergabe, expliziten Start, Pause und Erhalt des Watch-Dokuments. Weitere Webnavigations-Skripte werden separat geprüft. Solche Tests können reale Websiteänderungen, WebView-Verhalten und Netzwerk-/Kontofehler nicht vollständig abdecken.

Die vorhandenen 1.2.1-Lintberichte unter `app/build-home-1.2.1/reports/lint-results-debug.xml` und `.txt` bestätigen **0 Fehler und 16 Warnungen**. Für diesen Eintrag wurden ausschließlich bestehende Berichte gelesen; kein neuer Build oder Testlauf wurde gestartet. Lokale Logs werden nicht mitveröffentlicht.

## APK und Gerätebefund 1.2.1

- Die Projektdatei `Weiktube.apk` hat **17.606.167 Bytes** und SHA-256 `cfc6a2f6c4b78e8b5ef0816115cd849b0e0d261a0c7502834fb25ff70d68178a`.
- Größe und Hash stimmen mit `app/build-home-1.2.1/outputs/apk/debug/app-debug.apk` überein. Die zugehörige `output-metadata.json` nennt Paket `com.weiktube.app`, Version `1.2.1`, Versionscode `4`.
- Die bereits vorliegende Geräteaufnahme `validation/personal-home-ready.png` bestätigt die native Startseite mit eigenem Kanalavatar/-titel, der Kennzeichnung „Verlauf auf diesem Gerät“ und echten Abo-Uploads. Private Kontodaten aus dieser Aufnahme werden nicht ins Protokoll übernommen; die Aufnahme gehört nicht zum Quellcodepaket.
- Dies bestätigt die Darstellung der vorhandenen Konto-/Abodaten, jedoch keine identischen personalisierten YouTube-Empfehlungen. Diese Anforderung bleibt unerfüllt.
- Der letzte Suchversuch in 1.2.1 ist **nicht bestätigt**. Suchvorschläge, Absenden, Ergebnisse und Rückkehr werden daher nicht als bestanden markiert. Frühere Suchtests mit 1.1 ersetzen diese Abnahme nicht.
- Die folgenden älteren Player-/Kontogerätetests bleiben als 1.2.0-Nachweise erhalten. Sie werden nicht automatisch als erneut bestandene 1.2.1-Tests gewertet.

## Kontointegration: früherer initialer Gerätetest

In einem früheren 1.2-Arbeitsstand wurden Cloud-/OAuth-Konfiguration und initiales Laden echter Kontodaten auf dem OnePlus erfolgreich geprüft:

- Separates Google-Cloud-Projekt und passender Android-OAuth-Client eingerichtet, YouTube Data API aktiviert. Keine Billing-Verknüpfung oder Aktivierung einer Testphase.
- Freigabe von `https://www.googleapis.com/auth/youtube.readonly` durch einen ausdrücklich eingetragenen persönlichen Testnutzer. Private Kontonamen und E-Mail-Adressen werden hier nicht veröffentlicht.
- Anmeldung über Googles offiziellen Dialog erfolgreich abgeschlossen.
- Eigener YouTube-Kanal und abonnierte Kanäle erschienen in den nativen Ansichten. Status: „Mit YouTube verbunden. Deine Kontodaten sind aktuell.“

Dieser initiale Lesetest erzeugt keine WebView-Anmeldung und belegt keinen Zugriff beliebiger weiterer Google-Konten. Die später ergänzte Berechtigung `youtube.force-ssl` sowie native Likes, Aboänderungen, Playlist-Speichern, Kommentare und Antworten wurden **noch nicht Ende zu Ende auf dem Gerät getestet**. Es wurden für diese Abnahme keine ungefragten öffentlichen Kommentare oder anderen Kontoschreibaktionen ausgelöst.

## Frühere Gerätetests: Version 1.1

Version 1.1 wurde auf dem OnePlus installiert und gestartet. Bestätigt wurden die kompakte Kopfzeile, eine einzelne untere Navigation und echte Suchergebnisse für „Big Buck Bunny“. Ein öffentliches Testvideo wurde auf der vollständigen Original-Videoseite stumm abgespielt; Titel, Kanal, Aktionen, Kommentare und Empfehlungen waren sichtbar.

Der damalige Mini-Player lud das Video einmalig im IFrame-Player neu. Nach Korrektur der Größe waren Vorschaubild und Bedienelemente sichtbar; Betätigen von Play führte zu verändertem Videobild und Pause-Symbol. **Diese Übergabe wurde im aktuellen Umbau entfernt.** Der frühere Test ist kein Beleg für den neuen persistenten Original-Watch-Player.

Der Adblock-Tab zeigte Filter, Schalter und den Zähler „0 blockiert“. Die tatsächliche Blockierung einer Testanfrage wurde damit nicht belegt. Die öffentlichen 1.1.0-Release-Dateien wurden heruntergeladen und die APK-Prüfsumme verifiziert. Das war kein vollständiger In-App-Update-Installationstest.

## Bestätigte Geräteprüfung des Umbaus 1.2.0

Auf dem verbundenen OnePlus wurden folgende Abläufe tatsächlich geprüft:

- Original-Watch-Seite mit korrekter 16:9-Videofläche sichtbar.
- Vorhandene native Kontofreigabe wiederhergestellt und echte Kontodaten geladen.
- Neuer Abo-Uploadfeed: erste 12 Kanäle verarbeitet, Daten aus 9 verfügbaren Kanälen angezeigt; pro Kanal werden bis zu 3 Uploads geladen. Ein tatsächliches Video-Thumbnail öffnete die Original-Videoseite.
- Mini-Player, Wechsel über „Du“ und „Abos“ sowie Rückkehr zum großen Video behielten dieselbe Watch-Sitzung. Position lief von 18 auf 39 Sekunden weiter; keine IFrame-Übergabe oder Neuladung bei diesem Ablauf.
- Vollbild im sensorabhängigen Querformat und Rückkehr ins Hochformat.
- Doppeltes Tippen spulte insgesamt 20 Sekunden vor.
- Das originale Qualitätsmenü bot Optionen bis 2160p; 1080p wurde ausgewählt. Dies ist ein Nachweis der Auswahl, keine Messung der tatsächlich übertragenen Streamauflösung.
- Native Bewertung und Abostatus wurden mit vorhandenem Lesezugriff geladen.

Die originale Kommentaransicht wurde geöffnet und zeigte einen Ladezustand; vollständig geladene Webkommentare sind damit nicht belegt. Für die native Kommentarabfrage wurde die zusätzliche Google-Berechtigung erkannt und eine ausdrückliche Schaltfläche „Kommentare freigeben“ ergänzt. Dieser neue Freigabeweg wurde noch nicht auf dem Gerät getestet. Likes, Aboänderungen, Playlist-Speichern, Kommentare und Antworten wurden nicht als echte Schreibaktionen abgenommen. YouTube samt Werbung bleibt vom Domainfilter ausgenommen.

## Weitere Geräteabnahme des aktuellen Umbaus

Die oben belegten Abläufe decken nicht sämtliche Fehler- und Randfälle ab. Die folgende Regression-/Abnahmeliste ergänzt sie für die getrennten WebViews und nativen Videoaktionen.

- Installation als Update: Paket, höhere Version, gleiche Signatur und Erhalt vorhandener Appdaten prüfen.
- Startseite 1.2.1: nativer eigener Kontokanal, klarer lokaler Verlauf und echte Abo-Uploads; nur eine Appnavigation, keine Browser-Symbolleiste. Scrollen, Video öffnen und minimieren. Die Startseitenposition soll erhalten bleiben; Websuche/Kanalseiten behalten ihr eigenes Browserdokument. Erneutes Öffnen/Schließen darf keine versteckten konkurrierenden Player hinterlassen.
- Suche: originale YouTube-Suche, Live-Vorschläge soweit von YouTube angeboten, Tastatur, Absenden, Ergebnis öffnen, Zurück und erneute Suche prüfen. Es gibt keine separat erfundene native Vorschlagsliste.
- Original-Videoseite: Wiedergabe, Titel, Kanal, Kommentare und Empfehlungen prüfen. Groß → Mini → Groß soll dasselbe Watch-Dokument und dieselbe Wiedergabe fortsetzen, ohne absichtliche IFrame-Übergabe oder Neuladung. Zeitposition, Play/Pause, Schließen und Navigation zur nächsten Empfehlung messen.
- Shorts: eigene Browserinstanz und vertikales Wischen prüfen. Wechsel zwischen Shorts, normalem Video und anderen Tabs darf keine doppelte Audiowiedergabe erzeugen. Mini-Player-Bedienung während Shorts gesondert prüfen.
- Vollbild, Rotation, System-Zurück und Appwechsel prüfen; auch direkt nach Videowahl vor Abschluss des Ladens. Nach Rückkehr/Prozessverlust darf kein unerwarteter Hintergrundstart entstehen. Rendererabbruch soll einen verständlichen Wiederholungsweg bieten.
- Direkte erlaubte HTTPS-Videodatei gesondert in Android-PiP testen, einschließlich Beenden von PiP. Im damaligen Stand blieb YouTube-PiP deaktiviert; die neue Vorbereitung 1.3.8 ist oben getrennt dokumentiert.
- Alle fünf Tabs und Menüeinträge für Verlauf, Links, Einstellungen und Neuladen prüfen. Kanal-/Suchlinks aus dem Player sollen den Browser öffnen; native Videoaktionen müssen stets zum aktuell sichtbaren Video gehören.

### Konto- und Aktionsabnahme

- App-Neuerstellung/Prozessneustart, erneute Freigabe, Kontowechsel und begrenzte stille Token-Erneuerung bei HTTP 401 prüfen. Daten verschiedener Konten dürfen nicht vermischt werden.
- Eigene Playlists und Videoeinträge, einschließlich freigegebener privater Metadaten, prüfen. Pagination, leere Listen, Aktualisieren, Wechsel zwischen „Du“ und „Abos“ sowie Kontingentfehler testen. Sichtbare private Metadaten garantieren keine private Videowiedergabe in der WebView.
- Abo-Uploadfeed: weitere Kanalgruppen, 5-Minuten-Cacheablauf, Aktualisieren, nicht verfügbare Kanäle und sofortiges Leeren beim Kontowechsel prüfen. Es werden bis zu 3 aktuelle Uploads pro geladenem Kanal gezeigt; kein vollständiger persönlicher Homefeed.
- Im nativen Aktionsfenster tatsächlichen Videotitel, Kanal, Bewertung, Abostatus, Kommentare und Antworten prüfen. Unbekannter Status darf nicht als „nicht abonniert“ oder „nicht gelikt“ ausgegeben werden.
- „Kommentare freigeben“ und erste Schreibaktion: zusätzlicher Google-Dialog für `youtube.force-ssl`; Zustimmung, Ablehnen, Abbruch und erneutes Laden prüfen. Bloßes Öffnen des Fensters darf keine weitergehende Freigabe oder Schreibanfrage auslösen.
- Nutzer führt gewünschte Testaktionen selbst aus: Gefällt mir setzen/entfernen, Abo ändern, in eine eigene Playlist speichern, Kommentar/Antwort senden. Erfolg erst nach Serverantwort; Moderationshinweis bei angenommenen Kommentaren beachten. Doppelte Playlist-Einträge vermeiden, Entwurf bei Fehler behalten und keine automatische Wiederholung einer unklar ausgegangenen Schreibanfrage.
- Netzverlust, deaktivierte Kommentare, fehlende Berechtigung, falsche Cloud-Konfiguration und Kontingentfehler verständlich anzeigen. Kommentare/Replies und weitere Seiten ohne doppelte Einträge laden.
- Trennen: lokale Kontolisten entfernen und Google-Zugriff widerrufen. Fehlgeschlagenen Widerruf sichtbar halten. Weitere Konten ausschließlich nach ausdrücklicher Testnutzerfreigabe prüfen.

### Filter, Daten und Updates

- Shields-Prüfseite: aktivierter Werbefilter erhöht den Sitzungszähler um eins; deaktiviert oder mit passender Ausnahme bleibt er unverändert. YouTube einschließlich Werbung bleibt ausgenommen.
- Hell-/Dunkelmodus, Filtervalidierung, persistente Einstellungen, Verlauf und Löschen der Webdaten prüfen. Webdaten und native Google-Freigabe sind getrennte Zustände.
- Updater: gültigen veröffentlichten Feed abrufen, aktuelle Version erkennen, Netzwerk-/HTTP-Fehler anzeigen, Automatik deaktivieren/aktivieren und Tagesintervall prüfen.
- Mit einer passenden höheren Testversion APK-Download, Hash-/Paket-/Versions-/Signaturprüfung, Installationsfreigabe, Android-Bestätigung und Rückkehr testen. Manipulierte oder fremd signierte APK muss abgewiesen werden. Abbruch, Prozessverlust und erneuten Versuch prüfen. Feedabruf allein gilt nicht als erfolgreicher Update-Installationstest.

## Verbleibende Plattformgrenzen

YouTubes Live-Webstruktur, Consent-Dialoge, regionale Inhalte, Kontobeschränkungen, Google-WebView-Anmeldegrenzen und WebView-/Geräteversionen können Funktionen beeinflussen. Die native Google-Autorisierung überträgt weder Tokens noch Cookies an den Webplayer und personalisiert den Web-Home-Feed nicht automatisch. Der Testbetrieb bleibt ohne Billing; ein erschöpftes API-Kontingent ist als Fehler zu behandeln. Weitere Grenzen und Primärquellen stehen in `AUDIT.md`.

## Quellcodepaket

`scripts/package-source.py` verwendet eine explizite Auswahl der Projektdateien und Quellordner entsprechend dem Release-Skript. Eine zusätzliche Pfadsperre schließt Validierungsbilder, Screenshots, lokale SDK-Konfigurationen, Buildlogs, Schlüssel und private Verzeichnisse aus. Nach dem Packen werden ZIP-Integrität, tatsächliche Dateiliste, erlaubte Pfade und erforderliche Builddateien geprüft. Geräteaufnahmen und private Kontoinformationen gehören nicht zum Quellcodepaket.

## Vorheriger APK-Stand 1.2.0 (02.11.2026)

- Exportierte Datei: Weiktube.apk, 17.768.793 Bytes.
- SHA-256: 29e1ae317f14b234ac0f7a7c6c88cf6849d08eda40ffcbf58d3fb1122e427072.
- Abschließende JVM- und Lint-Prüfung in frischem Gradle-Prozess erfolgreich. Der erste langlebige Gradle-Prozess war nach dem erfolgreichen APK-Export abgestürzt; deshalb wurden die Prüfungen wiederholt.
- Genau diese APK wurde auf dem OnePlus installiert. Das native Videoaktionsfenster lädt jetzt beim ersten Öffnen ohne manuellen Sitzungs-Refresh Titel, Bewertung und Abostatus. Fehlende Kommentarbefugnis erscheint getrennt mit einer ausdrücklichen Google-Freigabeschaltfläche.
- Keine Like-, Abo-, Playlist- oder Kommentar-Schreibaktion wurde während der Abnahme abgesendet. Die zusätzliche Kommentarfreigabe wurde nicht erteilt/getestet.
- Die APK enthält keine Google-Anmeldung, Cookies oder privaten Kontodaten des Testgeräts. Eine neue Installation startet abgemeldet. Die Google-App ist weiterhin im Testmodus; andere Google-Konten benötigen eine Testnutzer-Freischaltung. Öffentliches Ansehen benötigt keine Google-Anmeldung.
- Teständerungen der Bildschirmzeit und Rotationssperre wurden zurückgesetzt. Der unveränderte Domainfilter hat automatisierte Regressionstests; eine vollständige neue An/Aus-Geräteprüfung wurde nicht abgeschlossen. YouTube ist wie zuvor vom Filter ausgenommen.
- Keine Behauptung vollständiger 1:1-Parität: Web- und native Kontositzung bleiben getrennt, eigene/native Übergänge und Mini-Player weichen vom Original ab; YouTube-PiP/Hintergrundwiedergabe bleiben deaktiviert. Mehr Details siehe AUDIT.md.
# YouTube-Werbeschutz 1.3.5

Vorheriger Stand: [Gecko-Kaltstart-Fix 1.3.7](RELEASE-1.3.7.md). Die zyklische
Abhängigkeit zwischen erstem Fenster und Adblock-Hintergrundprozess wurde im
gebündelten Gecko-Quellcode nachvollzogen. Neuer Quellcode-/Modelltest mit
120 Startzyklen und negativem Vergleich des alten Ablaufs bestanden.
Release-Build und App-/Home-Lint ohne Fehler; 207 JVM-Tests grün (unveränderte
Home-/Filtertests von Gradle als aktuell übernommen). Vorhandene Rückkehr-,
Adblock-, Diagnose- und Player-Scriptprüfungen bestanden. Keine Handytests
und kein echter Gecko-Integrationstest für diese Version.

Weiter enthalten: [Vollbild-Rückkehr-Fix 1.3.6](RELEASE-1.3.6.md) mit fünf zusätzlichen
Android-Inset-Regressiontests. Insgesamt 207 JVM-Tests grün; kein Handytest.

Der aktuelle [Prüfbericht](ADBLOCK-1.3.5.md) dokumentiert 202 erfolgreiche
JVM-Tests, Scriptlet-/Gecko-Fixtures und den APK-Build. Keine Gerätetests für
diese Version. Historische Aussagen weiter oben, wonach YouTube vom Filter
ausgenommen ist, beschreiben den damaligen Stand vor 1.3.5.

