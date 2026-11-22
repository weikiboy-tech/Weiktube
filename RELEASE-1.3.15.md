# Weiktube 1.3.15 – Shorts, Weiterschauen und Drehung

- Shorts-Links und Shorts-Menüpunkte öffnen im vorhandenen Shorts-Tab. Die Startseiten-Brücke und Links aus der Videoansicht erhalten die Shorts-Kennzeichnung.
- Kopfüberhalten wird ignoriert: Es löst weder eine Drehung ins umgekehrte Hochformat noch das Verlassen von Vollbild aus. Beide Querformate bleiben möglich; aufrechtes Halten führt zurück ins Hochformat.
- Lokale Wiedergabepositionen für normale YouTube-Videos: Weiter-bei-Anzeige, Fortschrittsbalken im Verlauf und Von-vorne-Aktion. Explizite Zeitlinks haben Vorrang. Verlauf löschen entfernt auch gespeicherte Positionen.
- Bestehende Videooberfläche, Tonfix, PiP-Darstellung und AdBlock-Algorithmen bleiben erhalten. Kein neuer Player.

## Prüfung und Grenzen

347 JVM-Tests (249 App, 78 Home, 20 Filter) ohne Fehler. Zusätzlich Home-/Browser-/Watch-Navigationsfixtures, 19 Player-Script-Fixtures, PiP-Verträge, Adblock-Scripttests und Quellcode-Scopecheck bestanden. Release-Build erfolgreich; Lint beim Gesamtbuild: 0 Fehler, 43 Warnungen. Nach der abschließenden 180°-Korrektur App-Tests und Release-Build erneut erfolgreich.

Die endgültige APK wurde auf dem OnePlus installiert. Das ist noch kein vollständiger Praxistest: physische Drehung, Shorts-Wiedergabe, Scrollposition und Fortsetzen sind für diesen Stand nicht durch den Assistenten am Gerät bestätigt. Gecko-Startseite und WebView-Shorts teilen ihre Anmeldung nicht automatisch.

VersionCode 29; unveränderte Paketkennung und Signatur. ARM64- und x86_64-APKs sind nicht debuggable und passen zum bisherigen Updater. Die veröffentlichte ARM64-APK entspricht der zuletzt installierten Korrektur.

Details: [Shorts und Drehung](https://github.com/weikiboy-tech/Weiktube/blob/v1.3.15/docs/SHORTS-ORIENTATION.md), [Wiedergabepositionen](https://github.com/weikiboy-tech/Weiktube/blob/v1.3.15/docs/SHORTS-RESUME.md).
