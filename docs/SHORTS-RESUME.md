> Release 1.3.15: Dieser Entwicklungsbericht beschreibt den Stand vor Veröffentlichung. Aktuelle Release-Hinweise: [1.3.15](../RELEASE-1.3.15.md).

# Shorts und lokale Wiedergabepositionen – 1.3.15

## Änderungen

- `OriginalHomePolicy.kt`, `original-home-navigation/bridge.js` und Extension-Version 2.0.2: Shorts-Pfad und reelWatchEndpoint bleiben als Shorts erhalten. Sicherheitsvalidierung, Einmal-Übergabe und Gecko-Medienblockade bleiben aktiv.
- `MainActivity.kt`: ein ausgewählter Short öffnet im bestehenden Shorts-Tab. Zurück nach einem Startseiten-Short zeigt die erhaltene Gecko-Startseite. Der normale Player pausiert bei Shorts-Auswahl.
- `PlaybackProgressStore.kt`: maximal 100 lokale Video-ID/Position/Dauer-Einträge. Keine privaten URLs oder Kontodaten. Normale YouTube-Videos werden alle fünf Sekunden Fortschrittsänderung und bei Pause/Wechsel/Schließen gesichert; beschädigte Werte, Lade-Nullwerte, Shorts und Live-Dauer ohne endliche Länge werden ignoriert.
- `PlayerController.kt`: erneute Auswahl lädt die gemerkte Position über den vorhandenen Startzeitpfad. Explizite Zeitlinks, Activity-Wiederherstellung und Von-vorne-Auswahl haben Vorrang. Kein neuer Player, keine Play-Schleife.
- `WeiktubeModel.kt`: Verlaufsaufzeichnung steuert Positionsspeicherung. Löschen entfernt auch Positionen; verspätete Meldungen der noch laufenden Auswahl dürfen sie danach nicht wieder anlegen.
- `PersonalHomeUi.kt`, `RecentHistoryUi.kt`, `WeiktubeUi.kt`: Fortschrittsbalken und Weiter-bei-Anzeige im Verlauf, Von-vorne-Aktion, Löschhinweis und mehr Platz im Shorts-Tab durch Weglassen des Zusatzheaders.

## Grenzen

Der Shorts-Tab verwendet weiterhin seine vorhandene Android-WebView. Die Gecko-Anmeldung wird nicht kopiert; persönliche Folge-Shorts im anderen Browser sind damit nicht garantiert. Geteilte Kurzlinks ohne Shorts-Kennzeichnung werden nicht anhand von Videoformat/Länge geraten.

Kein Handy per ADB erreichbar am 10.11.2026. Kein tatsächlicher Shorts-, Audio-, Scrollpositions- oder Fortsetzen-Gerätetest dieser Version. Ein erfolgreicher JVM-/Script-Test ersetzt diesen Nachweis nicht.

Noch kein GitHub-Release oder Updatekanalwechsel. Warteschlange und weitere größere Komfortfunktionen sind nicht Bestandteil dieses ersten Änderungssatzes.
