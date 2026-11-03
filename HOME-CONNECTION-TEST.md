# Homepage-Verbindungstest in der Original-App

Version: 1.2.4-home-test, Versionscode 7, Paket `com.weiktube.app`.
Diese APK aktualisiert die Original-App unter derselben Signatur. Sie ist keine
zweite Anwendung und ersetzt weder den Player noch die vorhandene Startseite.

## Stand und verpflichtende Freigabeschwelle

Dies ist die erste, vorgeschaltete Phase des vereinbarten Plans: Anmeldung und
echten Home-Datenabruf auf dem Zielgerät nachweisen. Ein erfolgreicher Build oder
Parser-Test beweist keine funktionierende Google-Anmeldung. Die native Homepage
wird erst nach erfolgreicher Prüfung dieser Sitzung an die Datenquelle gebunden.
Es gibt in dieser Phase bewusst keinen Schalter, der ein unbestätigtes Ergebnis
als fertige persönliche Startseite aktiviert.

Unter **Du / Konto → YouTube-Startseite verbinden** erscheint die eigene
Anmeldeseite der Verbindungskomponente. Sie ist ausschließlich für Anmeldung und
Verbindungsprüfung vorhanden, nicht als Feed-Oberfläche oder Videoplayer. Die
Website-Sitzung bleibt in einem eigenen lokalen Gecko-Profil; die native
Google-API-Freigabe und die bestehenden WebView-Sitzungen werden nicht ersetzt.

Der Datenabruf verwendet YouTubes private Web-Schnittstelle mit
`FEwhat_to_watch` und den von ihr gelieferten Fortsetzungen. Ein falsches oder
nicht nachweisbares aktives YouTube-Profil wird abgelehnt. Es gibt keine
Übernahme von Chrome-Cookies, fremde TV-OAuth-Clientdaten oder eine geänderte
Browserkennung. Googles Ablehnung einer eingebetteten Anmeldung wird nicht
umgangen. Kontodaten, Cookies und Tokens sind nicht in der APK enthalten.

## Test auf dem Zielgerät

1. APK als Update installieren, ohne die Original-App oder deren Daten zu löschen.
2. Im vorhandenen Kontobereich das gewünschte Google-/YouTube-Konto verbinden und
   dessen Kanaldaten laden lassen. Dann **YouTube-Startseite verbinden** antippen.
3. In der sichtbaren Google-/YouTube-Seite regulär anmelden und dasselbe
   YouTube-Profil auswählen. Anschließend **Verbindung prüfen** verwenden.
4. Die native Diagnose muss das passende Profil und tatsächlich gelieferte
   Home-Videos melden. **Weitere Ergebnisse prüfen** muss eine Folgeseite liefern.
   Die ersten drei Titel erscheinen als native Prüfvorschau. **Erstes Video im
   Originalplayer prüfen** verwendet den vorhandenen Video-Link-Einstieg der App.
   Bei falschem Profil **YouTube-Profil wechseln** verwenden.
5. Verbindung schließen, App neu starten und erneut prüfen. Es muss dieselbe
   Website-Sitzung nutzbar sein. Bei einem anderen Profil darf kein Feed als
   passend akzeptiert werden.
6. **Webverbindung trennen** prüfen: danach muss eine erneute Anmeldung nötig sein.
7. Bestehende Startseite, Suche, Videotipp, Miniplayer, Zurück und Vollbild wie
   gewohnt prüfen. Diese Wiedergabewege wurden nicht neu implementiert.

Bei einem Google-Fehler nur den sichtbaren Fehlertext zurückmelden, keine Cookies,
Tokens oder Passwörter. Erst nach der erfolgreichen Geräteprüfung folgen die
native Kartenanbindung und ein regulärer Release. Die bestehende Startseite wird
bis dahin nicht als persönlicher YouTube-Empfehlungsfeed bezeichnet.

## Technische Trennung

- `home-data` ist eine Android-Bibliothek, keine eigenständige App.
- GeckoView 159 erfordert SDK 37 und neuere Buildwerkzeuge. Dafür werden Gradle
  9.3.1, AGP 9.1.1 und Kotlin 2.4.20 verwendet. minSdk 26 und targetSdk 36 bleiben.
- Die zusätzliche Engine wird nur beim ausdrücklichen Öffnen der Verbindung
  initialisiert. Native Bibliotheken werden für den APK-Download komprimiert.
- Der Feed-Parser erhält strukturierte Antworten; er erfindet keine Einträge,
  bewahrt die Reihenfolge und verarbeitet nur unterstützte Home-Container.
- Konto-/Anmeldedaten verlassen das lokale Engine-Profil nicht über die
  Nachrichtenbrücke. Deren Antworten enthalten nur Feed-Daten und das ausgewählte
  YouTube-Profil; Fehler werden als begrenzte Codes ohne Antworttexte ausgegeben.
- Veröffentlichungen bleiben Vorabversionen ohne Änderung des stabilen Updatefeeds.

## Lokale Prüfungen

```powershell
.\gradlew.bat :app:exportHomeConnectionApk :app:testDebugUnitTest :home-data:testDebugUnitTest :app:lintDebug :home-data:lintDebug --no-daemon --max-workers=1
node scripts/check-player-scripts.cjs
node scripts/check-browser-scripts.cjs
node scripts/check-watch-navigation.cjs
node scripts/check-home-bridge.cjs
```

Die APK wird als `Weiktube-Home-Test.apk` exportiert. Die erhaltene
`Weiktube.apk` ist weiterhin die gesicherte Originalversion 1.2.3.
Die Test-APK enthält ARM64- und x86-64-Bibliotheken; 32-Bit-Geräte sind damit nicht
unterstützt.
Ein Smartphone steht für diese Entwicklung nicht zur Verfügung. Gerätestatus,
Anmeldung, Kontozuordnung und Wiedergabe dürfen nicht aus lokalen Tests abgeleitet
werden. Die frühere lokale Emulator-Umgebung scheiterte bereits beim Start.

Ergebnis am 3. November 2026: Build erfolgreich; 81 bestehende JVM-Tests und 10
neue Feed-Parser-Tests bestanden, keine Fehler oder übersprungenen Tests. Alle
vier JavaScript-Prüfprogramme bestanden. Lint: App 0 Fehler/20 Warnungen,
home-data 0 Fehler/16 Warnungen. Signatur geprüft und mit der ursprünglichen APK
identisch; gepackte Bridge-Dateien stimmen mit den getesteten Quelldateien überein.
Der Scope-Check schützt alle 46 ursprünglichen Quelldateien und erlaubt lediglich
den Kontoeinstieg und den zusätzlichen Webdaten-Löschaufruf. Die ursprüngliche
APK bleibt bytegleich erhalten.
