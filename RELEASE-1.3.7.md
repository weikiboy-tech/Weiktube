# Weiktube 1.3.7 – Startseite nach App-Neustart

Version 1.3.7, Versioncode 21. Gezielte Korrektur für die lange schwarze
Startseite und die irreführende Meldung, das gespeicherte Webprofil sei nicht verfügbar.

## Ursache

Die bisherige Startreihenfolge wartete zuerst auf den Adblock-Hintergrundprozess
und öffnete erst danach die erste GeckoSession. Bereits installierte Erweiterungen
startet die gebündelte Gecko-Version bei einem Prozess-Neustart jedoch erst nach
dem ersten Gecko-Fenster. Beide Seiten warteten dadurch aufeinander, bis die
20-Sekunden-Frist des Werbeschutzes ablief. Bei frisch installierten oder aktualisierten
Erweiterungen startet dieser Prozess sofort; das erklärt das wechselhafte Verhalten.

Diese Reihenfolge wurde im gebündelten Gecko-Quellcode nachvollzogen:
`ext-backgroundPage.js` wartet bei `APP_STARTUP` auf `browserStartupPromise`;
`ExtensionParent.sys.mjs` löst dieses beim Signal `extensions-late-startup` auf;
`geckoview.js` sendet das Signal aus dem ersten Fenster.

## Änderung

- Dieselbe bestehende Home-Sitzung wird zuerst leer geöffnet. Erst nach bestätigter
  Filterregistrierung darf die erste externe Seite laden. Kein zweites Hilfsfenster.
- Der vorhandene Diagnose-Einstieg verwendet dieselbe korrigierte Reihenfolge.
- Laden, Neuladen, Unterseiten und Popups dürfen die Startschutz-Prüfung nicht umgehen.
- Die Vorbereitung zeigt einen Ladestatus. Scheitert der Werbeschutz, nennt die
  Meldung diesen Schritt ausdrücklich; die Anmeldung wird nicht gelöscht.
- Ein Tabwechsel während des Starts erhält die Sitzung und den vorgemerkten
  Seitenaufruf. Veraltete Rückmeldungen nach Abbruch werden ignoriert.

Produktionsänderungen ausschließlich in `HomeEngine.kt`, `OriginalHomeController.kt`
und `HomeConnectionActivity.kt`. Player, Videoübergabe, Google-Anbindung,
Adblock-Regeln/-Engine und Website-Cookies bleiben unverändert. Der Hochformat-Fix
aus 1.3.6 bleibt enthalten.

## Prüfungen und Grenzen

Der neue Test `scripts/check-home-cold-start.mjs` prüft Quellcode-Verträge und
modelliert die bestätigte Gecko-Startbedingung: 120 Starts mit kaltem, neu
installiertem und bereits aktivem Hintergrundprozess, Schutzfehler, Abbruch und
Pause/Fortsetzen. Der alte Ablauf dient als negativer Vergleich und blockiert
im Modell wie erwartet.

Weitere ausgeführte Prüfungen:

- Release-Build für ARM64 und x86-64 erfolgreich; App-/Home-Lint ohne Fehler
  (28 bzw. 41 Warnungen, darunter vorhandene Stil-/Übersetzungshinweise).
- 207 JVM-Tests grün: 115 App, 72 Home, 20 Filter. Die unveränderten Home- und
  Filtertests wurden von Gradle als aktuell übernommen.
- Bestehende Home-Videoübergabe-Fixtures mit zehn simulierten Rückkehrzyklen,
  Gecko-Rückkehr-Lifecycle, Adblock-Erweiterungsverträge, Home-Diagnose-Bridge
  sowie alle 13 Player-JavaScript-Fixtures bestanden.
- Quellcodevergleich zu 1.3.6: genau die drei genannten Produktionsdateien
  geändert; alle Player- und Filter-Quelldateien/-Assets identisch.
- Die Startreihenfolge-Prüfung ist auch Teil des GitHub-Workflows.
- Beide APKs mit `apksigner`/`aapt` geprüft: bisheriges Signierzertifikat,
  Paket `com.weiktube.app`, Version 1.3.7/21, richtige Architektur, nicht debuggbar
  und unter dem Updaterlimit von 128 MiB. SHA-256 für beide Varianten erfasst.

Das ist ausdrücklich kein echter Gecko- oder Handytest. Geschwindigkeit,
aktuelle YouTube-Antwort und sichtbarer Start auf dem betroffenen OnePlus sind
damit noch nicht praktisch abgenommen. Die gespeicherte Anmeldung wird nicht
zur Fehlerbehebung zurückgesetzt.

## Installation

In Weiktube: **Einstellungen & Updates → Jetzt nach Updates suchen**.
Alternativ: [APK für ARM64](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.7/Weiktube.apk)
oder [x86-64](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.7/Weiktube-x86_64.apk).

Über die bestehende Installation installieren, nicht vorher deinstallieren.
Das bisherige Signierzertifikat bleibt erhalten. Der
[Quellcode](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.7/Weiktube-Quellcode.zip)
und Lizenzen sind beigefügt. Die experimentellen Werbefilter bleiben enthalten;
vollständige Werbefreiheit wird weiterhin nicht garantiert.
