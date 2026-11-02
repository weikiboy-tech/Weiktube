# Weiktube 1.1 – Prüfprotokoll

Stand: 2. November 2026. Build-/Logikprüfungen und Android-Gerätetests werden getrennt dokumentiert.

## Automatisierte Prüfungen

Der abschließende Build war erfolgreich. Die JUnit-Berichte enthalten **25 bestandene Tests**, keine Fehler und keine übersprungenen Tests:

- 5 Tests für YouTube-Linkformen, Hostspoofing, Zeitangaben, HTTPS und ausgeschlossene direkte YouTube-Medienadressen.
- 11 Tests für Domainfilter, Subdomaingrenzen, Schalter, Ausnahmen, YouTube-Ausnahmen, Hosts-Parser, Größenlimits und Zähler.
- 9 Tests für Updatequelle, Metadaten, Versionsprüfung, Prüfsummenformat und Zeitpunkt automatischer Prüfungen.

Der aktuelle Lint-Bericht meldet **0 Fehler und 11 Warnungen**. Versionshinweise sind keine Compilerfehler; automatisches PiP ist absichtlich nicht aktiviert. Die letzte genaue Buildausgabe steht in `build-validation.log`, aktuelle Reports in `app/build/reports/` und `app/build/test-results/testDebugUnitTest/`.

`node scripts/check-player-scripts.cjs` prüft die eingebetteten Player-Skripte mit simulierten Playerobjekten. Die JavaScript-Blöcke zur Webnavigation und Suche wurden separat auf gültige Syntax geprüft. Diese Prüfungen belegen keine reale YouTube-Wiedergabe oder funktionierende Google-Anmeldung.

## Geräteteststatus

Die Erstversion wurde auf einem verbundenen OnePlus installiert und erfolgreich gestartet. Die nachfolgende Screenshot-Rückmeldung zeigte doppelte Such-/Navigationsleisten und führte zu den Änderungen in Version 1.1.

Für **Version 1.1** sind auf dem OnePlus folgende Prüfungen bestätigt:

- APK installiert und App gestartet; kompakte Kopfzeile und einzelne untere Navigation sichtbar.
- Native Suche nach „Big Buck Bunny“ liefert echte YouTube-Ergebnisse. Die Eingabe erscheint nur bei Bedarf; keine native Live-Autovervollständigung.
- Ein Testvideo wurde auf der vollständigen originalen Videoseite sichtbar und stumm wiedergegeben. Titel, Kanal, Aktionen, Kommentare und Empfehlungen waren sichtbar. Normales Öffnen bleibt auf dieser Seite; die native Kopfzeile wird dort zugunsten der originalen Web-Kopfzeile ausgeblendet.
- „Startseite“ von der Videoseite aus öffnet den separaten Mini-Player mit einmaliger Übernahme der Zeitposition. Nach Korrektur der anfänglich zu kleinen IFrame-Fläche sind Vorschaubild und Player-Bedienelemente sichtbar. Die native Play-Taste wurde anschließend betätigt: ein verändertes Videobild und das native Pause-Symbol bestätigen den gestarteten Mini-Player. Die anfängliche Übergabe bleibt eine Neuladung, keine lückenlose Weiterwiedergabe.

Der Adblock-Tab wurde geöffnet und seine Oberfläche überprüft: Sitzungszähler „0 blockiert“, Filterlisten und Schalter waren vorhanden. Die tatsächliche Blockierung einer Testanfrage ist damit noch nicht bestätigt. Die Kontonavigation wurde auf `/feed/you` korrigiert. Vollbild, Rotation, Google-Anmeldung, persistente Einstellungen und der vollständige Update-Installationsablauf sind für diesen Stand noch nicht abschließend auf dem Gerät abgenommen. Erfolgreiches Abspielen des genannten Testvideos ersetzt diese Prüfungen nicht.

## Abnahmeliste für Version 1.1

- Installation als Update mit Erhalt der Appdaten; Paket, Version und Signatur prüfen.
- Startseite: eine native Kopfzeile und eine untere Navigation; keine dauerhaft doppelte Suchleiste. Keine verdeckten Inhalte oder Player-Bedienelemente.
- Suchlupe: einzeiliges natives Eingabefeld und Tastatur; Suchbegriff absenden, Ergebnis öffnen und Zurück-Navigation prüfen. Keine Live-Suchvorschläge erwarten.
- Alle fünf Tabs testen: Startseite, Shorts, Adblock, Abos und Du. Menüeinträge für Verlauf, Links, Einstellungen und Neuladen prüfen.
- Ein frei zugängliches YouTube-Video auf der vollständigen Videoseite öffnen: Player, Aktionen, Kommentare und Empfehlungen prüfen. Über „Startseite“ von der Videoseite in den Mini-Player wechseln: einmaliges Neuladen und auf Sekunden gerundete Startposition erwarten. Anschließend Play/Pause, Rückkehr zum großen separaten Player und Position beim Navigieren prüfen. Keine unterbrechungsfreie anfängliche Übergabe behaupten.
- Vollbild, Rotation, System-Zurück und Audioverhalten bei Appwechsel prüfen. Direkte erlaubte HTTPS-MP4 zusätzlich in Android-PiP testen.
- Konto-Dialog, externe Google-Anmeldung und Rückkehr prüfen. Keine Sitzungsübernahme in Weiktube erwarten oder als native Anmeldung ausweisen.
- Shields-Prüfseite: mit aktivem Werbefilter Zähler +1; deaktiviert bzw. mit Ausnahme unverändert. YouTube-Werbung bleibt vom Filter ausgenommen.
- Hell-/Dunkelmodus, Filtervalidierung, persistente Einstellungen, Verlauf und Löschen der Webdaten prüfen.
- Updater: gültige veröffentlichte Quelle abrufen, aktuelle Version erkennen, Netzwerk-/HTTP-Fehler anzeigen. Automatik deaktivieren/aktivieren und Tagesintervall prüfen.
- Mit einer eigens gebauten höheren Testversion APK-Download, Hash-/Paket-/Versions-/Signaturprüfung, Installationsfreigabe, Android-Bestätigung und Rückkehr testen. Manipulierte oder fremd signierte APK muss abgewiesen werden. Kein erfolgreiches Update behaupten, solange nur die Feedprüfung getestet wurde.

## Verbleibende Plattformgrenzen

YouTubes Live-Webstruktur, Consent-Dialoge, regionaler Inhalt, Einbettungsrechte, Google-WebView-Anmeldebeschränkungen und Geräte-/WebView-Versionen können Funktionen beeinflussen. Eine bestandene lokale Abnahme ist keine Garantie identischer Bedienung auf allen Geräten oder vollständiger Funktionsgleichheit mit der offiziellen YouTube-App.





