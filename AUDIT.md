# Weiktube – Funktionsaudit und Neuaufbau

Vergleichsziel des Auftrags: Bedienabläufe der offiziellen YouTube-Android-App **21.43.528**, mit eigenständigem Weiktube-Branding. Dieses Dokument beschreibt technische Unterschiede und prüfbare Abnahmekriterien. Es behauptet keine identische Oberfläche, Zugriff auf nicht öffentliche YouTube-Funktionen oder bestandene Tests, die nicht tatsächlich ausgeführt wurden.

## Ausgangslage

Version 1.2.3 stellt die native Startseite mit eigenem Kontokanal, ausdrücklich lokalem Verlauf und echten Abo-Uploads wieder her. Der Chrome-Custom-Tab-Versuch einschließlich Browserbibliothek wurde entfernt. Die Anmeldung nutzt den offiziellen Android-Google-Konto-/Freigabedialog. Native Videokarten öffnen direkt den bestehenden Watch-Player; dessen große und kleine Ansicht behalten dasselbe Dokument.

**Die originalen persönlichen YouTube-Empfehlungen innerhalb der App bleiben unerfüllt.** Eigene Abo-Uploads und gerätelokaler Verlauf sind kein Nachweis dieser Funktion. Auch der vorhandene native Google-Dialog personalisiert keine WebView-Sitzung und gewährt keinen Zugriff auf den originalen Homefeed. Die Rücknahme des Browserversuchs erfüllt den gewünschten Gesamtumfang nicht.

Die bisherigen Gerätebelege betreffen die interne Wiedergabe, native Kontodaten und Abo-Uploads. Neue Build-/Installationsnachweise für 1.2.3 werden in `TESTING.md` separat geführt. Schreibaktionen und die zusätzliche native Kommentarfreigabe bleiben unbestätigt.

## Verworfener Versuch 1.2.2

Der Chrome-Custom-Tab-Prototyp zeigte auf dem Gerät die tatsächlich angemeldete YouTube-Identität. Das Ausblenden der Browserleiste beim Scrollen wurde beobachtet und per Aufnahme festgehalten. Der Nutzer hat diesen Browserweg anschließend ausdrücklich abgelehnt; der Versuch wurde beendet und aus 1.2.3 entfernt. Eine automatische Übergabe von Video-Tipps an den Weiktube-Player wurde nicht als funktionierend getestet oder nachgewiesen. Der Versuch endete vor Änderungen an Android-Standardlinkzuordnungen. Dieser Prototyp wurde nicht als öffentliche APK-Release veröffentlicht.

## Funktionsmatrix

| Bereich | Vorhandener Stand / verbleibende Grenze | Abnahmeziel |
| --- | --- | --- |
| Anmeldung | Offizieller Android-Google-Konto-/Freigabedialog für native API-Funktionen. Interne WebViews behalten ihre eigene Sitzung. Keine Cookie-/Tokenübertragung; APK enthält keine Testkonten. | Abbruch, erneute Freigabe, Kontowechsel und Trennen prüfen; native API-Anmeldung nicht als YouTube-Webanmeldung ausgeben. |
| Abos / eigenes Konto | Echte Abo-Uploads: zunächst 12 Kanäle mit je bis zu 3 Uploads, weitere Kanäle auf Knopfdruck, 5 Minuten Cache je Kontositzung. Auf dem Gerät Daten aus 9 der ersten 12 Kanäle und Öffnen eines Thumbnails bestätigt. „Du“ zeigt eigene Kanäle und Playlists. Erste Anmeldung mit `youtube.readonly`. | Kontowechsel, Pagination, Cacheablauf, private Playlists und Fehlerzustände weiter prüfen. Kein vollständiger persönlicher Homefeed. |
| Likes / Kommentare / Abonnieren / Speichern | Neues natives Aktionsfenster mit tatsächlichen API-Anfragen, inklusive Antworten und eigener Playlistauswahl. Zusätzliche Schreibfreigabe erst nach ausdrücklicher Nutzeraktion. Kein Geräte-End-to-End-Nachweis bisher. | Nur explizite Nutzeraktionen senden; keinen Erfolg vor Serverbestätigung anzeigen; Texte nach Fehlern behalten; doppelte Übermittlungen vermeiden. |
| Startseite | Native Kontokanal-/Verlaufs-/Aboansicht ohne Custom Tab und Browserleiste. Feed über `subscriptions.list(mine=true)`; nur angeforderte Videos zugehöriger Kanäle werden akzeptiert, Kontowechsel leert den Cache. | Originale persönliche YouTube-Empfehlungen bleiben ausdrücklich offen. Korrekte Kontoidentität, Abozuordnung und lokale Verlaufskennzeichnung prüfen. |
| Suche | Originale mobile YouTube-Suche in der internen WebView. Die native Google-Freigabe erzeugt keine Web-Cookies. | Tastatur, Vorschläge, Absenden, Ergebnisse und Zurück prüfen; frühere Suchtests ersetzen die aktuelle Abnahme nicht. |
| Shorts | Eigene persistente WebView mit YouTubes vertikaler Webansicht; kein identischer nativer Empfehlungsalgorithmus. | Navigation und Audioübergänge prüfen, auch bei daneben vorhandenem Mini-Player; keine konkurrierende Wiedergabe. |
| Player / Mini-Player | Native Abo-/Verlaufskarten und Videolinks öffnen den vorhandenen Watch-Player direkt. Dieser behält beim Minimieren dieselbe Original-Videoseite, ohne IFrame-Transfer. | Zeitposition, Play/Pause, Rotation, Vollbild, Rückkehr und konkurrierendes Audio prüfen. Dies belegt keinen integrierten originalen persönlichen Empfehlungsfeed. |
| PiP | Offizielle Android-PiP-API für erlaubte direkte HTTPS-Videos. YouTube-PiP bleibt deaktiviert. | Keine unsichtbare Hintergrundwiedergabe und keine Premium-Umgehung. |
| Adblock | Weiktube Shields filtert unterstützte zusätzliche Webanfragen; YouTube samt Werbung bleibt ausgenommen. Eigene Listen, Ausnahmen und Zähler. | An-/Aus-Test, Ausnahmen und echte Testanfrage prüfen. Keine vollständige Brave-/YouTube-Filterung behaupten. |
| Links | HTTPS-YouTube-Intentfilter und Teilen. YouTube-Domains gehören nicht Weiktube und können deshalb nicht als eigene App Links verifiziert werden. | Geteilte Links und Android-Zuordnung prüfen; bekannte HTTP-YouTube-Links sicher auf HTTPS normalisieren. |
| Updates | Tägliche Vordergrundprüfung, expliziter Download, Hash-/Paket-/Versions-/Signiererprüfung und Android-Installer. | Kompatibles Update vollständig installieren; falsche Signatur, manipulierte Datei, falsches Paket, Abbruch und Prozessverlust prüfen. |

## Freigaben und Kosten

Native Kontolisten, Uploadfeed, Bewertung und Abostatus verwenden `youtube.readonly`. Für native Kommentare und Schreibaktionen wird zusätzlich `youtube.force-ssl` benötigt. Die Schaltfläche „Kommentare freigeben“ erklärt die weitergehende Berechtigung; der Dialog startet nur auf diese bewusste Aktion oder eine Schreibaktion. Dieser neue Kommentarweg wurde noch nicht auf dem Gerät bestätigt. Die Freigabe erzeugt keine WebView-Login-Cookies. Private Metadaten in der nativen Ansicht garantieren keine private Wiedergabe im internen Player.

Browser-Cookies und Zugangsdaten werden nicht ausgelesen, exportiert oder in die APK eingebaut. Löschen der Weiktube-Webdaten und Widerruf der nativen API-Freigabe sind getrennte Vorgänge. Der aktuelle Startseitenablauf benötigt keine externe Browsersitzung.

Unbekannte Bewertung oder unbekannter Abostatus werden nicht als negatives Ergebnis geraten. Schreibanfragen werden nach unklarem Ausgang nicht automatisch wiederholt. Ein von YouTube angenommener Kommentar kann erst nach Kanalmoderation öffentlich erscheinen. Diese Regeln sind implementiert; echte Kontoschreibaktionen und inkrementelle Zustimmung bleiben Gegenstand der Geräteabnahme.

Das Projekt bleibt ohne kostenpflichtige Dienste oder Billing-Verknüpfung. Kontingentverbrauch ist ein API-Limit und kein in der App angezeigter Geldpreis. Die aktuell dokumentierte Standardquote umfasst 100 `search.list`-Aufrufe pro Tag in einem eigenen Bereich und 10.000 Einheiten pro Tag für die übrigen üblichen Endpunkte; Schreibaktionen wie Kommentare, Bewertungen und Abonnieren kosten jeweils 50 Einheiten. Bei Erschöpfung muss die App einen Fehler anzeigen. Es werden keine zusätzlichen Projekte zur Umgehung von Limits angelegt.

## Wiederverwendung und Plattformgrenzen

- Erneute Prüfung am 02.11.2026: Die [offizielle Data API](https://developers.google.com/youtube/data/reference/rest/v3/activities/list) liefert laut `homeParameterDeprecated` keinen persönlichen Homefeed. `mine=true` bezeichnet eigene Kanalaktivitäten und gewährt keinen Zugriff auf Empfehlungen. Die vorhandene Android-Autorisierung mit `youtube.readonly` behebt diese Grenze nicht.
- [YouTube.js](https://ytjs.dev/guide/) hat einen Home-Aufruf über YouTubes interne Schnittstelle. Laut seiner [aktuellen Authentifizierungsdokumentation](https://ytjs.dev/guide/authentication) funktioniert OAuth dort nur für den TV-Client; andere Clienttypen verwenden Cookies. Das ist kein Nachweis, dass unser eigener Android-OAuth-Client den persönlichen Homefeed abrufen kann. Es wurden bei dieser Untersuchung keine Kontotokens an interne Endpunkte gesendet, keine fremden Clientzugänge verwendet und keine Sitzungscookies ausgelesen. Ein funktionierender integrierter Weg für die verlangten Originalempfehlungen ist weiterhin nicht belegt; der danach erprobte Browserweg wurde vom Nutzer verworfen.
- `android-youtube-player` ist ein MIT-lizenzierter Wrapper um den offiziellen IFrame-Player. Er kann eine IFrame-Anbindung verbessern, gewährt aber keine zusätzlichen Google-Konto-, Werbe- oder Wiedergaberechte. Der aktuelle Umbau verwendet stattdessen die Original-Watch-Seite; dieser Wrapper wurde nicht eingebaut.
- NewPipe, NewPipeExtractor und LibreTube veröffentlichen Code unter GPL-3.0-or-later. Eine Übernahme erfordert Beachtung der jeweiligen Lizenz und Herkunft. Diese Projekte lösen die native offizielle Google-Kontointegration nicht automatisch; LibreTube verwendet optional Piped-Konten, NewPipe beschreibt Website-/interne API-Extraktion.
- Die Lizenz eines Drittprojekts betrifft dessen Quellcode. Sie ist keine Freigabe für geschützte YouTube-Schnittstellen, Google-Zugangsdaten oder Premium-Funktionen.
- Es werden keine proprietären APK-Bestandteile oder YouTube-Markenassets kopiert. Eine ähnliche Bedienlogik wird eigenständig umgesetzt.

## Verlässlichkeit vor einer neuen Veröffentlichung

Paketname `com.weiktube.app` und bestehende Signieridentität müssen erhalten bleiben. Eine andere Signatur würde das Update vorhandener Installationen verhindern und die registrierte Android-OAuth-Konfiguration ändern. Versionscodes müssen monoton steigen. Signierschlüssel und OAuth-Geheimnisse gehören nicht in das Repository oder in Release-Dateien.

Vorhandene Unit-Tests prüfen Filter, Links, Updates, Verlauf, Navigation, Bildadressen, Videoaktionen, Kontositzungen und Abo-Uploads. Alte Kontositzungen dürfen keine Antworten mehr einspielen; der native Feed wird beim Sitzungswechsel geleert. Für 1.2.3 sind Build, Entfernung des Browserversuchs und erneute Installation getrennt zu bestätigen. Erfolgreiche bestehende Tests lösen die noch fehlenden originalen persönlichen Empfehlungen nicht. `TESTING.md` dokumentiert bestätigte Berichte und neue Abnahme; dieses Audit bleibt die Funktions- und Blockermatrix.

## Primärquellen

- [Google-Autorisierung auf Android](https://developer.android.com/identity/authorization)
- [YouTube-Bewertungen](https://developers.google.com/youtube/data/reference/rest/v3/videos/rate), [Kommentare schreiben](https://developers.google.com/youtube/data/reference/rest/v3/commentThreads/insert), [Abonnieren](https://developers.google.com/youtube/data/reference/rest/v3/subscriptions/insert)
- [Aktuelle API-Kontingente](https://developers.google.com/youtube/data/determine_quota_cost), [nicht mehr unterstützter Home-Feed](https://developers.google.com/youtube/data/reference/rest/v3/activities/list)
- [YouTube-Playeranforderungen](https://developers.google.com/youtube/terms/required-minimum-functionality)
- [android-youtube-player und MIT-Lizenz](https://github.com/PierfrancescoSoffritti/android-youtube-player), [NewPipe](https://github.com/TeamNewPipe/NewPipe), [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor), [LibreTube](https://github.com/libre-tube/LibreTube)
