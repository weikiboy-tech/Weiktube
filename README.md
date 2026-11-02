# Weiktube 1.1

Android-App mit Kotlin, Jetpack Compose und Material 3. Paket und Namespace: `com.weiktube.app`. Benötigt Android 8.0 oder neuer, einen aktuellen Android System WebView und Internetzugang.

Weiktube zeigt echte YouTube-Webinhalte in einer eigenen Android-Oberfläche. Version 1.1 entfernt die doppelte Webnavigation, ersetzt das dauerhafte zusätzliche Suchfeld durch eine Suchlupe und ergänzt einen Adblock-Tab sowie einen App-Updater. Die App bleibt ein unabhängiger WebView-Client; sie bildet nicht sämtliche Funktionen der offiziellen YouTube-App identisch nach.

## Bedienung

Die unteren Tabs heißen **Startseite**, **Shorts**, **Adblock**, **Abos** und **Du**. Die Suchlupe blendet ein einzeiliges Suchfeld anstelle der Kopfzeile ein. Nach dem Absenden erscheinen echte YouTube-Suchergebnisse; native Live-Suchvorschläge sind nicht implementiert. Das Kontosymbol öffnet die Kontoauswahl. Im Dreipunktmenü liegen **Video-Link öffnen**, **Lokaler Verlauf**, **Einstellungen & Updates** und **Seite neu laden**. Auf einer geöffneten YouTube-Videoseite wechselt ein Tippen auf **Startseite** zum Mini-Player. Die Videoseite behält ihre originale Web-Kopfzeile; die native Kopfzeile wird dort ausgeblendet.

| Funktion | Umsetzung und Grenzen |
| --- | --- |
| Startseite und Suche | Echte mobile YouTube-Seiten. Empfehlungen hängen von YouTube, Cookies, Region und dem verfügbaren Verlauf ab. Suchbegriffe werden an YouTube gesendet. Die native Eingabe bietet keine Live-Autovervollständigung. |
| Videos | YouTube-Videos öffnen standardmäßig auf der vollständigen originalen Videoseite mit Player, Aktionen, Kommentaren und Empfehlungen. Direkte HTTPS-Videodateien verwenden den separaten HTML5-Player. YouTubes Zugangsbeschränkungen bleiben wirksam; externes Öffnen ist möglich. |
| Mini-Player | Von einer geöffneten Videoseite über den Tab „Startseite“; je nach Ansicht zusätzlich über „Im Mini-Player öffnen“ im Menü. Der Webplayer wird pausiert, seine Zeitposition auf ganze Sekunden erfasst und das Video einmalig im offiziellen IFrame-Player neu geladen. Danach bleibt dieselbe Player-WebView beim Navigieren erhalten. Play/Pause, Schließen und Vergrößern; mindestens 200 dp Höhe für YouTube. Die anfängliche Übergabe ist nicht unterbrechungsfrei und kann erneutes Tippen auf Play erfordern. Nicht einbettbare Videos können dort abgelehnt werden. |
| Shorts | Originale vertikale Weboberfläche. Ein optionaler Wechsel einer erkannten Video-URL in den Mini-Player unterliegt denselben Einbettungsbeschränkungen wie andere Videos. |
| Kanäle und Playlists | Über die YouTube-Webseite erreichbar. Keine eigene native Playlistverwaltung oder automatische Playlist-Fortsetzung im separaten Player. |
| Abos und Du | YouTube-Aboseite bzw. Mediathek. Kontoabhängige Inhalte setzen eine von YouTube akzeptierte Websitzung voraus. |
| Kommentare und Likes | Auf der standardmäßig geöffneten vollständigen Videoseite verfügbar, soweit YouTube sie anbietet. Schreiben und Liken benötigen eine gültige Websitzung. Vom optionalen separaten Player führt die Kommentar-Schaltfläche zurück zur Videoseite. |
| Lokaler Verlauf | Bis zu 100 über native App-Aktionen geöffnete Videos, abschaltbar und löschbar. Dazu gehören übergebene YouTube-Links und direkte HTTPS-Videos. Keine Google-Synchronisierung; ausschließlich innerhalb der Webseite angeklickte Videos werden nicht vollständig erfasst. |
| Vollbild und Rotation | WebView-Custom-View-Anbindung; Rotation erstellt die WebViews nicht neu. |
| Android-PiP | Für erlaubte direkte HTTPS-Videodateien über eine explizite Taste. YouTube-PiP bleibt deaktiviert. |
| Qualität | Nur Auswahlmöglichkeiten des offiziellen Players bzw. der Webseite; keine erzwungene Qualität. |
| Teilen und Links | Teilen des aktiven Videos und Empfang über „Teilen → Weiktube“. Direktes Öffnen unterstützter YouTube-Links muss unter Android 12+ ggf. in den App-Einstellungen erlaubt werden. |
| Darstellung | Eigene kompakte Kopfzeile und Navigation, Play-Logo sowie System-/Hell-/Dunkelmodus für die native Oberfläche. Die Website steuert ihren eigenen Modus. |

Die Webanpassung beschränkt sich auf doppelte mobile Kopf- und Fußnavigation. Auf der vollständigen Videoseite bleiben die originale YouTube-Kopfzeile und deren Abstände erhalten. Suchfelder, Suchvorschläge, eingebettete Player, deren Bedienelemente und Werbung werden davon nicht entfernt. Änderungen an YouTubes Webseitenstruktur können eine App-Aktualisierung erfordern.

## Anmeldung

Das Kontosymbol bietet die YouTube-Seite „Du“ (`/feed/you`) und **Im Browser anmelden**. Google kann die Anmeldung in eingebetteten Browsern ablehnen. Deshalb erfolgt der sichere Anmeldeweg extern, ohne User-Agent-Täuschung oder Umgehung der Google-Anmeldevorgaben.

**Die externe Browsersitzung wird nicht nach Weiktube übernommen.** Eine externe Anmeldung garantiert daher keine Abos, Likes oder privaten Playlists innerhalb der App. Diese Aktionen können im externen Browser durchgeführt werden. Weiktube besitzt keine native Google-OAuth-/YouTube-Data-API-Kontointegration und liest oder speichert keine Google-Passwörter.

## Adblock und Datenschutz

Der Adblock-Tab enthält Weiktube Shields: eine kleine lokale Domainfilter-Engine mit getrennten Basislisten für Werbe- und Trackingdomains, eigenen Listen, Ausnahmen und einem Sitzungszähler. Unterstützt werden reine Domains und Hosts-Dateiformat mit `#`-Kommentaren, bis 256 KiB pro Liste. Domainsegmente und Subdomains werden berücksichtigt; Ausnahmen gelten für Hauptseite oder angefragte Domain.

**YouTube und dessen Player sind vom Blocking ausgenommen, einschließlich der dort geladenen Werbeanfragen.** Weiktube ist kein YouTube-Werbeblocker. Es gibt keine ABP-/EasyList-Regeln, kosmetischen Werbefilter, automatischen Filterlisten-Downloads oder vollständige Brave-Engine. First-Party-/serverseitige Werbung, Service-Worker-, Cache-, WebSocket-, Blob-/Data- und bestimmte Weiterleitungszugriffe sind nicht vollständig erfassbar.

Die lokale Shields-Prüfseite fordert genau eine Ressource von der reservierten Testdomain `ads.weiktube.invalid` an. Bei aktiviertem Werbefilter steigt der Sitzungszähler um eins; mit deaktiviertem Filter bleibt er unverändert.

Drittanbieter-Cookies, HTTP-Klartext, Mixed Content und lokale Datei-/Content-Zugriffe sind in den WebViews deaktiviert. Zertifikatsfehler werden nicht übergangen. Es gibt keine JavaScript-Native-Bridge, Analyse-SDKs oder Werbe-SDKs. Webseiten und YouTube erhalten dennoch Netzwerk- und technische Gerätedaten; die App macht YouTube-Nutzung nicht anonym.

Einstellungen und Verlauf bleiben im privaten Appspeicher. Cookies, Cache und Webspeicher lassen sich separat löschen. Backups der Appdaten sind ausgeschlossen. Ohne sichtbare App bzw. erlaubtes direktes PiP wird der separate Player pausiert. Rotation und Navigation behalten dessen Instanz; nach Prozessbeendigung ist eine unterbrechungsfreie Wiederherstellung nicht garantiert.

## Automatische App-Updates

Unter **Einstellungen & Updates** prüft Weiktube standardmäßig beim Öffnen bzw. Zurückkehren in den Vordergrund höchstens einmal pro Tag auf eine neuere Version. Die Prüfung lässt sich ausschalten und jederzeit manuell auslösen. Es läuft kein dauerhafter Hintergrunddienst; APKs werden nicht ungefragt heruntergeladen oder installiert.

Standardquelle:

[Update-Feed des GitHub-Projekts](https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json)

Die HTTPS-Quelle ist konfigurierbar. Damit Updates verfügbar sind, muss das Repository eine veröffentlichte Release mit passender `update.json` und APK enthalten. Eine nicht erreichbare Quelle wird als Fehler angezeigt.

Vor der Installation werden SHA-256, Android-Paketname, Versionscode und der Signierschlüssel gegen die installierte App geprüft. Downloads sind begrenzt; unsichere Weiterleitungen werden abgelehnt. Ein Update wird nur als neu angeboten, wenn sein Versionscode höher ist. Android verlangt die Freigabe „Apps aus dieser Quelle“ und anschließend eine Installationsbestätigung. Keine stille Installation und kein Austausch fremder Pakete.

Die App benötigt INTERNET und REQUEST_INSTALL_PACKAGES für diese Funktion sowie eine von AndroidX erzeugte interne Signaturberechtigung. Kamera-, Mikrofon-, Standort-, allgemeine Speicher- und Overlay-Berechtigungen werden nicht angefordert.

## Projekt und Nachweise

- `BUILD.md`: Build, Installation und Veröffentlichung eines Updates.
- `TESTING.md`: automatisierte Prüfungen und dokumentierter Stand der Gerätetests.
- `app/src/test`: Tests für Links, Filter und Updatevalidierung.
- `UpdateController.kt` / `UpdateUi.kt`: Updater und Oberfläche.
- `MainActivity.kt`: Android-Lifecycle, Navigation, Intents, Vollbild und PiP.
- `WeiktubeUi.kt`: Compose-Oberfläche, Tabs, Player und Einstellungen.
- `BrowserController.kt` / `PlayerController.kt`: dauerhaft gehaltene Browser- und Player-WebViews.
- `ShieldEngine.kt`, `Links.kt`, `WeiktubeModel.kt`: Filter, Quellenvalidierung und lokale Daten.

Technische Referenzen: [YouTube IFrame API](https://developers.google.com/youtube/iframe_api_reference), [YouTube-Playeranforderungen](https://developers.google.com/youtube/terms/required-minimum-functionality), [Android Picture-in-Picture](https://developer.android.com/develop/ui/views/picture-in-picture), [YouTube-Nutzungsbedingungen](https://www.youtube.com/t/terms), [Google-Datenschutz](https://policies.google.com/privacy).

Weiktube ist nicht mit YouTube, Google oder Brave verbunden. Die APK ist ein Debug-Build, keine Veröffentlichung im Play Store. Technische und kontoabhängige Einschränkungen sind kein Nachweis einer 1:1-Funktionsgleichheit mit der offiziellen YouTube-App.



