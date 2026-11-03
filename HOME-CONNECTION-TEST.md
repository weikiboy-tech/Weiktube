# CONFIG_UNAVAILABLE: gezielte Korrektur und Diagnose

Version 1.2.4-home-test.2, Versionscode 8, Paket `com.weiktube.app`.
Die APK aktualisiert die bisherige App mit derselben Signatur. Originalplayer,
Miniplayer, Navigation, Oberfläche, Google-API-Anmeldung und Adblock bleiben erhalten.
Die neue Datenquelle wird ohne nachgewiesenen Abruf nicht zur Startseite erklärt.

## Reproduzierte Ursache

Der Fehler entstand in `home-data/src/main/assets/home-bridge/bridge.js`,
`currentConfig()`. Bisher wurde ausschließlich `window.wrappedJSObject.ytcfg.data_`
gelesen. Fehlender Kontext, fehlender numerischer Clientname oder API-Schlüssel
wurden pauschal als `CONFIG_UNAVAILABLE` ausgegeben. Fehlendes `LOGGED_IN` wurde
zusätzlich fälschlich wie `false` behandelt.

Die Verbindung startete auf
`https://www.youtube.com/signin?action_handle_signin=true&next=%2F`.
Ein öffentlicher, nicht angemeldeter HTTP-Abruf am 3. November 2026 landete auf
`https://www.youtube.com/oops`: HTTP 200, YouTube-Fehlertitel, kein `ytcfg`,
`ytInitialData` oder InnerTube-Kontext. Eine solche Fehlerseite liefert tatsächlich
keine Konfiguration. Das reproduziert einen konkreten Fehler im Startpfad;
die finale URL auf dem Nutzergerät ist damit rückwirkend nicht bewiesen.

Die öffentlich abgerufene mobile Startadresse führte mit dem normalen Python-
HTTP-Client zur www-Startseite (HTTP 200, Titel YouTube). Dort waren die erwarteten
Felder und `LOGGED_IN=false` vorhanden. Dieser HTTP-Test führt kein JavaScript aus
und ist kein Beleg für eine angemeldete mobile Sitzung oder global geänderte Feldnamen.

Zusätzlich wird das aktuelle öffentliche HTML durch die echte Bridge im lokalen
JavaScript-Testkontext ausgewertet (`node scripts/check-home-bridge.cjs --public`):
Home ergibt korrekt `SIGNED_OUT`, die alte Anmelde-URL korrekt `PAGE_ERROR`.
Dabei wurde die zunächst zu enge JSON-Literalgrenze korrigiert: YouTubes aktueller
Konfigurationsblock ist etwa 757 KB groß. Die Suche bleibt auf 4 MB begrenzt,
und ausschließlich erlaubte Konfigurationsfelder werden begrenzt kopiert.

## Änderungen

- Start direkt auf `https://m.youtube.com/`; Klassifikation der tatsächlichen
  Zielseite. `/oops` bleibt auch bei HTTP 200 ein Seitenfehler.
- Unterstützung weiterer tatsächlich gelieferter Konfigurationsformen und
  begrenzter JSON-Literale in Initialisierungsskripten, ohne `eval` oder Aufruf
  von Seitenfunktionen. Keine erfundenen Clientversionen.
- Unbekannter Loginstatus bleibt unbekannt; keine Gleichsetzung mit `false`.
- Eigene Meldungen für Anmeldung, Consent, Fehlerseite, fehlende Konfiguration,
  falsches/nicht bestätigtes Profil und fehlende Feed-Daten.
- Optionaler Diagnosemodus mit bereinigter Zieladresse, Seitentitel-Kategorie,
  JavaScript-Verbindung, Ladezustand und fehlenden Feldnamen. HTTP-Status nur,
  wenn tatsächlich beobachtet, andernfalls ausdrücklich nicht verfügbar.
  Gecko meldet in gewöhnlichen Navigationscallbacks keinen Dokument-HTTP-Status.
- Google-Anmeldeseiten werden nicht eingebettet geöffnet. Eine ausdrückliche
  Browserprüfung öffnet den Systembrowser; die Rückkehr gilt nicht als Verbindung.

## Konkrete technische Grenze

Die vorhandene Google-API-Anmeldung liefert Kanaldaten, Abos und Playlists.
Sie erzeugt keine authentifizierte Website-Sitzung in der zusätzlichen Gecko-
Engine. Anmeldung im Systembrowser oder Chrome Custom Tab überträgt ebenfalls
keine Cookies in dieses lokale Engine-Profil.

Die offizielle YouTube Data API liefert den persönlichen Home-Empfehlungsfeed
nicht. Der eingestellte `activities.list`-Parameter `home` ist kein Ersatz.
Die private Web-Schnittstelle benötigt eine passende Website-Sitzung. Ohne sie
bleibt der Zugriff blockiert; der Konfigurationsfix erzeugt keinen fehlenden
Authentifizierungsweg. Es werden keine Abo-Videos als Personalisierung ausgegeben.

Kein Chrome-Cookie-Import, keine fremden TV-OAuth-Zugangsdaten, keine geänderte
Browserkennung. OAuth bleibt bei der vorhandenen Google-Android-Autorisierung.
Die eingebettete Engine führt keinen neuen Google-OAuth-Flow aus.

Quellen:

- [YouTube Data API: activities.list / home](https://developers.google.com/youtube/data/reference/rest/v3/activities/list)
- [Google OAuth-Richtlinien](https://developers.google.com/identity/protocols/oauth2/policies)
- [Android-Autorisierung](https://developer.android.com/identity/authorization)
- [Chrome Custom Tabs und Browsersitzungen](https://developer.chrome.com/docs/android/custom-tabs)
- [YouTube.js: Authentifizierung](https://ytjs.dev/guide/authentication)
- [Mozilla: Content-Script-Fetch-Kontext](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Content_scripts#xhr_and_fetch)

## Datenschutz und Feed

Diagnosen werden nur lokal angezeigt, nicht in Logcat, Dateien oder einen Server
geschrieben. Keine Passwörter, Cookies, Tokens, API-Schlüssel, vollständigen
Antworten oder unbereinigten URLs/Seitentitel. Queryparameter und Fragmente werden
entfernt; unbekannte Pfade werden kategorisiert. Konto- und Video-Prüfvorschauen
sind getrennte lokale Nutzdaten, keine Diagnose.

Erfolgreicher Abruf setzt ein bestätigtes, mit dem vorhandenen Konto identisches
Website-Profil voraus. Prüfung vor und nach `FEwhat_to_watch`. Nur echte Home-
Einträge in YouTubes Reihenfolge; Werbung und nicht unterstützte Container werden
übersprungen. Fortsetzungen müssen aus der vorherigen Antwort stammen. Keine
Abo-, Such- oder selbst berechneten Ersatzempfehlungen. Die native Prüfvorschau
öffnet das erste echte Ergebnis über den vorhandenen Link-Einstieg des Originalplayers.

## Test auf dem Zielgerät

1. APK als Update installieren, keine Appdaten löschen.
2. **Du / Konto → YouTube-Startseite verbinden → Diagnosemodus** öffnen.
3. **Verbindung prüfen**. Diagnose zeigt tatsächliche Zielseite und Prüfschritt.
4. Fehlende interne Website-Sitzung muss als Blockade erscheinen. Alleinige
   Anmeldung im externen Browser darf keinen erfolgreichen Home-Abruf melden.
5. Falls eine gültige Engine-Sitzung besteht: passendes Profil, echte Home-Videos
   und **Weitere Ergebnisse prüfen** testen. Falsches Profil darf keinen Feed liefern.
6. Bei echten Ergebnissen erstes Video im Originalplayer öffnen; App schließen
   und neu öffnen, Sitzung erneut prüfen.
7. Vorhandene Suche, Videoöffnung, Miniplayer, Zurück, Vollbild und Adblock prüfen.

Nur bereinigte Diagnose oder Fehlercode weitergeben, keine Zugangsdaten.
Hier steht kein Smartphone zur Verfügung. Lokale Tests beweisen keine erfolgreiche
Anmeldung oder Feed-Abfrage mit dem Nutzerkonto. Der lokale Emulator scheiterte
bereits beim Start; es wird keine Geräteprüfung behauptet.

## Lokale Prüfungen

```powershell
.\gradlew.bat :app:exportHomeConnectionApk :app:testDebugUnitTest :home-data:testDebugUnitTest :app:lintDebug :home-data:lintDebug --no-daemon --max-workers=1
python scripts/check-home-scope.py
node scripts/check-player-scripts.cjs
node scripts/check-browser-scripts.cjs
node scripts/check-watch-navigation.cjs
node scripts/check-home-bridge.cjs
```

Der Scope-Check schützt alle 46 ursprünglichen Quelldateien und erlaubt nur die
bereits vorhandenen drei Verbindungseinstiege. `Weiktube.apk` bleibt die originale
Version 1.2.3; die neue APK heißt `Weiktube-Home-Test.apk`. ARM64 und x86-64 ab Android8.
Veröffentlichung als Vorabversion; der stabile Updatefeed bleibt erhalten.

Ergebnis am 3. November 2026: finaler Build erfolgreich; 101 JVM-Tests ohne Fehler
oder übersprungene Tests. Alle vier JavaScript-Prüfprogramme und der zusätzliche
öffentliche HTML-Test bestanden. Lint: jeweils 0 Fehler / 20 Warnungen für App
und home-data. Originaldatei-Scope geprüft, ursprüngliche APK bytegleich.
Signatur der neuen APK stimmt mit der Original-APK überein; die gepackten Bridge-
Dateien stimmen bytegenau mit dem getesteten Quellcode überein.

APK SHA-256:
`6158f149030c49420d25e2fae48ae0fb09218e1b86aea810251a9c8fe2c55251`
