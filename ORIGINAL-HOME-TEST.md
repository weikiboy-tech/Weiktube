# Original-Home-Test: echte Website, aktuell ausgewähltes Konto

Version 1.2.5-original-home-test, Versionscode 9, Paket `com.weiktube.app`.
Diese Test-APK aktualisiert dieselbe App. Die bisherige Startseite bleibt Standard.
Keine privaten Kontodaten oder Anmeldungen werden mit der APK verteilt.

## Was dieser Ansatz prüft

Unter **Du / Konto → Originale YouTube-Startseite testen** gibt es zwei getrennte
Experimente: sichtbare mobile YouTube-Webseite in Gecko und einen Vergleich in
Chrome Custom Tabs. Das erste Ziel ist die tatsächlich angemeldete Originalseite.
Erst nach deiner ausdrücklichen visuellen Bestätigung wird die Player-Übergabe
getestet. Die Bestätigung ist eine Beobachtung des Nutzers, keine automatische
technische Kontoprüfung.

Es gibt keinen festgelegten Kontonamen oder Kanal. Das in der jeweiligen
YouTube-Websitzung ausgewählte Konto beziehungsweise Markenprofil bestimmt die
Startseite. Kontoauswahl und Wechsel bleiben bei YouTube. Die separate Google-
API-Anmeldung für Abos und Playlists ist für den Test nicht erforderlich.
Gecko, Chrome und die API-Anmeldung sind voneinander getrennt.

Die Experimente verwenden keine InnerTube-Aufrufe, keine ytcfg-Auswertung und
keinen Feed-Parser. Es werden keine Abo-, Such- oder selbst erzeugten Empfehlungen
als persönlicher Home-Feed angezeigt. Die alten Diagnosekomponenten bleiben im
Quellcode archiviert und werden von diesem Einstieg nicht verwendet.

## Gecko zuerst

1. **1. Original-Home in Weiktube testen** öffnet `https://m.youtube.com/` sichtbar.
2. Im originalen YouTube-Kontomenü prüfen, welcher Account und welches Profil
   ausgewählt sind. Eine bestehende Gecko-Anmeldung wird weiterverwendet.
3. Falls abgemeldet, YouTubes reguläre Website-Anmeldung verwenden. Authentifizierungs-
   seiten werden unverändert mit sichtbarer Herkunft angezeigt; dort laufen keine
   Weiktube-Skripte. Die Browserkennung wird nicht verändert.
4. Falls Google die eingebettete Anmeldung ablehnt oder eine explizite OAuth-
   Autorisierung nötig wird, gilt dieser Weg als blockiert. Es gibt keine
   Sicherheitsumgehung. Eine externe Anmeldung erzeugt keine Gecko-Sitzung.
5. Startseite, Profil, Scrollen und Nachladen prüfen. Test schließen und neu öffnen,
   um die Sitzung zu prüfen. Erst dann die sichtbare Home-Bestätigung und die
   Übergabeprüfung aktivieren.
6. Video antippen. Die öffentliche URL wird geprüft, die Website-Wiedergabe beendet
   und der vorhandene Weiktube-Link-Einstieg/Originalplayer geöffnet.

Die alte automatisch installierte Konfigurations-Erweiterung wird während des
Gecko-Experiments ausgesetzt. Ihr vorheriger Zustand wird wiederhergestellt; eine
unterbrochene Wiederherstellung wird vor der nächsten Engine-Nutzung nachgeholt.
Die neue Navigationserweiterung liest weder Cookies noch Konto- oder Feed-Daten.
Die erste Übergabe beendet die Testansicht. Exakte Wiederherstellung ihrer
Scrollposition nach Rückkehr ist noch kein zugesichertes Ergebnis.

## Chrome-Vergleich

1. **2. Mit Chrome vergleichen** öffnen. Chrome muss installiert sein und Custom
   Tabs anbieten; es wird kein anderer Browser unbemerkt als Chrome ausgegeben.
2. Zuerst den reinen Original-Home-Test starten. Reguläre Chrome-Sitzung, kein
   Inkognito-Modus. Aktuelles YouTube-Konto/Profil auf der Website prüfen und dort
   gegebenenfalls anmelden oder wechseln.
3. Nach Rückkehr die tatsächliche Beobachtung bestätigen. Erst danach den zweiten
   Test mit Übergabeaktionen öffnen.
4. Falls Chrome sie unterstützt: Videolink lange drücken und **In Weiktube öffnen**
   wählen. Alternativ auf einer Videoseite die Weiktube-Aktion in der Toolbar nutzen.
5. Gewöhnliche Videotipps gesondert testen: Sie dürfen nicht pauschal als direkt
   abfangbar gelten. Die Toolbar-Aktion kann erst nach Beginn einer Website-
   Wiedergabe erfolgen. Doppelten Ton und Wechsel zum Originalplayer prüfen.

Chrome zeigt eine echte Custom-Tab-Oberfläche, keine eingebettete Gecko-Ansicht.
Es wird ein bildschirmfüllender Tab verwendet; die Sicherheitsleiste bleibt
vorhanden und darf sich beim Scrollen ausblenden. Chrome erlaubt keine beliebige
Skriptinjektion, DOM-Auswertung oder Navigationserfassung durch Weiktube.
Die optionale Langdruck-Aktion ist experimentell und kann vom Browser weggelassen
werden. Eine Toolbar- oder Langdruck-Übergabe erfüllt nicht die Ein-Tipp-Anforderung.

## Grenzen und unveränderte Funktionen

Der vorhandene Player, Miniplayer, die App-Navigation und die Adblock-Engine bleiben
unverändert. Der bestehende Netzwerk-Adblocker kann nicht automatisch in Chrome
wirken. Auch dieses Gecko-Experiment integriert keinen neuen Adblock-Adapter.
Die bisherige Startseite wird nicht automatisch ersetzt und der stabile Updatefeed
nicht geändert. Ein Erfolg in Chrome beweist keine Gecko-Anmeldung.

Es werden keine Passwörter, Cookies, Tokens, vollständigen Website-Antworten oder
Kontoinhalte protokolliert. Der Nutzer gibt Zugangsdaten nur direkt auf der echten
Google-/YouTube-Seite ein. Der Test muss keine Zugangsdaten an Entwickler übermitteln.

## Abnahme auf deinem OnePlus

Für beide Anbieter getrennt festhalten:

- Aktives Konto/Profil auf der originalen Website korrekt; kein festgelegter Account.
- Originale persönliche Startseite sichtbar, scrollt und lädt weitere Inhalte.
- Anmeldung nach Schließen und erneutem Öffnen vorhanden.
- Abgemeldet, mehrere Konten, Profilwechsel und abgelaufene Sitzung geprüft.
- Player-Übergabe mit richtigem Video, ohne doppelten Ton.
- Normale Ein-Tipp-Übergabe separat von ausdrücklicher Toolbar-/Langdruck-Aktion bewertet.
- Bestehender Player, Miniplayer, Zurück, Vollbild und Adblock unverändert nutzbar.

Wenn die echte Startseite keine Empfehlungen liefert (beispielsweise wegen einer
Kontoeinstellung), werden keine Ersatzvideos erzeugt. Keine automatische Anbindung
an die reguläre Startseite, solange die Konto- und Bediennachweise fehlen.
Hier ist kein Smartphone verfügbar. Du testest die APK auf deinem OnePlus;
lokale Tests oder ein erfolgreicher Build ersetzen diesen Nachweis nicht.

## Technische Prüfungen

```powershell
python scripts/check-home-scope.py
node scripts/check-player-scripts.cjs
node scripts/check-browser-scripts.cjs
node scripts/check-watch-navigation.cjs
node scripts/check-original-home-navigation.cjs
.\gradlew.bat :app:exportHomeConnectionApk :app:testDebugUnitTest :home-data:testDebugUnitTest :app:lintDebug :home-data:lintDebug --no-daemon --max-workers=1
```

Der Scope-Check schützt die 46 ursprünglichen App-Quelldateien. APK und neue
Erweiterungsdateien werden vor Veröffentlichung auf Signatur und Übereinstimmung
mit dem geprüften Quellcode kontrolliert. `Weiktube.apk` bleibt die gesicherte
Originalversion; die Testversion heißt `Weiktube-Home-Test.apk`.

Stand 3. November 2026: finaler Build erfolgreich; 118 JVM-Tests bestanden,
keine Fehler oder übersprungenen Tests. Die Originalplayer-, Browser-, Watch-
Navigation- und neuen Klickbrücken-Fixtures bestanden. Lint: App 0 Fehler /
20 Warnungen, home-data 0 Fehler / 65 Warnungen. Ein Lauf auf dem OnePlus steht
weiterhin aus.
Die APK-Signatur entspricht der Original-App. Beide gepackten Erweiterungen
stimmen bytegenau mit dem geprüften Quellcode überein. Alle 46 geschützten
Originaldateien und die ursprüngliche APK bleiben erhalten.

Test-APK: 207.663.775 Bytes, SHA-256
`9027ad2bd55fab3cb659682b69b7075838868f81bc6475c8699371d5fd537b50`.

Quellen:

- [Google: unterstützte Browser und mögliche Ablehnung eingebetteter Anmeldung](https://support.google.com/accounts/answer/7675428)
- [Google OAuth: sichere Browser](https://developers.google.com/identity/protocols/oauth2/policies#browsers)
- [Chrome Custom Tabs: Sicherheitsmodell und Zugriffsbeschränkungen](https://chromium.googlesource.com/chromium/src/+/main/docs/security/custom-tabs-faq.md)
- [Android: CustomContentAction](https://developer.android.com/reference/androidx/browser/customtabs/CustomContentAction)
- [Android: CustomTabsCallback](https://developer.android.com/reference/androidx/browser/customtabs/CustomTabsCallback)
