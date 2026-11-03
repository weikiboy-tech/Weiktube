# Wiederherstellung und Startseitenprüfung — 3. November 2026

## Wiederhergestellter Stand

Die Original-App ist `com.weiktube.app`, Version 1.2.3, Versionscode 6.
Die erhaltene APK hat SHA-256
`095a62e582df01e7a30f8c57af192abc1a77f0fb4bef57c87d1b409782eb04db`.
Sie wurde beim GeckoView-Versuch nicht überschrieben. Der neue Test war eine
andere Anwendung (`com.weiktube.probe`), kein Update dieser Original-App.

Vor der Wiederherstellung wurden Quellcode, Konfiguration, beide APKs und die
Git-Historie lokal gesichert und die Sicherung anhand der Datei-Prüfsummen geprüft.
Build-Caches, bestehende Validierungsdaten und andere Projekte bleiben unangetastet.
Der gesamte separate Test einschließlich seiner Builddateien, seiner APK,
Veröffentlichungsskripte und Dokumentation wurde reversibel ins private Backup
verschoben. Er ist kein aktives Projektmodul mehr. Die Quellcode-Verpackung enthält
wieder ausschließlich die Original-App.

Die verfügbare Git-Historie enthält `8006faa` (1.1.0) und `c35fc05` (Testzweig).
Der Test-Commit enthält zusätzlich die vorher bereits lokal vorhandenen
1.2.3-Änderungen; es gibt keinen separaten historischen 1.2.3-Commit davor.
Ein pauschaler Reset auf 1.1.0 würde unter anderem die bestehende native
Google-Anmeldung entfernen. Deshalb wurde der erhaltene 1.2.3-Stand verwendet,
nicht der ältere 1.1.0-Stand.

Alle Dateien unter `app/src`, die App-Buildkonfiguration und die ursprüngliche APK
sind gegenüber dem gesicherten Stand vor der Rücknahme bytegleich. Insbesondere
wurden `PlayerController`, `BrowserController`, `WeiktubeUi`, `MainActivity`,
`YoutubeAccountController`, `ShieldEngine`, die Miniplayer-Logik und die
Videoaktionen nicht verändert. Es wurden keine Produktions-Codeänderungen an
der Startseite vorgenommen, weil die erforderliche Datenquelle fehlt.

## Ergebnis der gezielten Homepage-Untersuchung

`YoutubeAccountController` verwendet Googles regulären AuthorizationClient,
den Scope `youtube.readonly` und die YouTube Data API v3 unter
`https://www.googleapis.com/youtube/v3/`. Schreibrechte werden gesondert für
entsprechende Nutzeraktionen angefordert. Es gibt keine weitere bereits
authentifizierte Home-Datenquelle in diesem Projekt.

Die vorhandene `SubscriptionFeedController`-Kette liest Abos, deren
Uploadplaylists und Video-Metadaten. `PersonalHomeUi` beschriftet die Ergebnisse
als „Videos deiner Abos“ und den Verlauf als „Verlauf auf diesem Gerät“.
Diese vorhandenen Bereiche bleiben erhalten, sind aber ausdrücklich **keine
persönlichen YouTube-Home-Empfehlungen**. Sie erfüllen die angeforderte Funktion
nicht. Es wurde kein weiterer Ersatzfeed ergänzt.

Der aktuelle offizielle [`activities.list`-Vertrag](https://developers.google.com/youtube/data/reference/rest/v3/activities/list)
markiert `home` als veraltet und beschreibt dort keine persönlichen Empfehlungen.
`mine=true` liefert eigene Aktivitäten. Die
[API-Änderungshistorie](https://developers.google.com/youtube/data/revision_history)
dokumentiert die Einstellung des früheren persönlichen Home-Abrufs zum
12. September 2016. Unterschiedliche Beschreibungen des alten Rückgabeverhaltens
in diesen Dokumenten ändern nichts daran: Beide bieten keinen aktuellen
personalisierten Home-Endpunkt.

[`videos.list`](https://developers.google.com/youtube/data/reference/rest/v3/videos/list)
kann konkrete Videos oder regionale/Kategorie-Popularität liefern; das wäre kein
persönlicher Homefeed. Zusätzliche OAuth-Schreibrechte schaffen diesen Endpunkt
nicht. Ein Loginfehler als Ursache wurde nicht nachgewiesen. Die Anmeldung zu
ersetzen würde die fehlende API-Funktion nicht beheben.

**Ergebnis:** Echte persönliche Empfehlungen sind mit der vorhandenen Anbindung
nicht verfügbar. Die angeforderte Homepage ist daher nicht implementiert.
Keine WebView-Homepage, Browserüberlagerung, erfundene API, Trending-Liste oder
selbst erzeugte Empfehlung wurde als Lösung hinzugefügt.

Die vorhandenen nativen Videokarten verwenden bereits `a::openVideo` und den
Originalplayer. Diese Übergabe und sämtliche anderen Navigationswege bleiben
unverändert.

## Regressionen und Grenzen der Prüfung

Ergebnis des frischen Laufs vom 3. November: 81 JVM-Tests, 0 Fehler, 0 übersprungen;
Lint 0 Fehler und 16 bestehende Warnungen. Alle drei vorhandenen
JavaScript-Prüfprogramme bestanden (darunter 9 Player-Szenarien). Die v2-Signatur
der Original-APK ist gültig. Der Hashvergleich von 51 geschützten Dateien ergab
keine Änderungen. Es handelt sich um die ursprüngliche APK, nicht um einen
neu gebauten oder umsignierten Player.

- Die bestehenden 81 JVM-Tests prüfen unter anderem Links, Home-Routing,
  Kontositzungswechsel, Feed-Sichtbarkeit, Videoaktionen, Updates und Shield-Regeln.
- Die vorhandenen JavaScript-Prüfungen decken Player-, Browser- und
  Watch-Navigation ab. Es werden keine neuen Player-Skripte eingebaut.
- Quellcode- und APK-Identität sind zusätzlich über Datei-Prüfsummen geprüft.
- Es steht kein Handy zur Verfügung. Frühere Gerätetests stehen in `TESTING.md`;
  sie werden nicht als neuer Wiedergabe- oder Anmeldetest ausgegeben.
- Der bestehende Shield-Code bleibt unverändert. Er nimmt YouTube-Domains
  ausdrücklich aus. Dass YouTube-Werbung damit blockiert würde, wäre eine falsche
  Behauptung; dieses bestehende Verhalten wurde nicht außerhalb des Auftrags geändert.

Zum Original zurückkehren: **Weiktube** (`com.weiktube.app`) öffnen, nicht
**Weiktube Test**. Die getrennte Test-App kann über Android deinstalliert werden;
die Original-App und deren Daten müssen dafür nicht gelöscht werden. Jede
weitergegebene APK enthält keine persönliche Anmeldung des Erstellers.
