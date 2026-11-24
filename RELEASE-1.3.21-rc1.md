# Weiktube 1.3.21-rc1 – SponsorBlock

Lokale Testversion, nicht öffentlich veröffentlicht. Kein Updatekanal geändert.
Basis main: 96432f7f458acfdb9e51d4beaa46b430fc77d25e.

## Neu

- Optionaler SponsorBlock-Abruf mit vier SHA-256-Präfixzeichen, lokaler exakter ID-Auswahl, Timeout, Cache und Backoff.
- Acht einzeln konfigurierbare Kategorien. Auto-Skips, manuelle Buttons, Rückgängig und bewusstes Zurückspulen.
- Farbige Markierungen in zuverlässig erkannten vorhandenen Zeitleisten.
- Bestätigte lokale Statistiken, Cache-/Statistik-Löschen, vorheriger Datenschutzhinweis und Attribution.
- Bestehende Player-WebView bleibt erhalten. Keine Änderungen an AdBlock, Shorts, PiP-Geometrie, Tonfix oder Gesten.

## Geprüft

403 JVM/Robolectric-Tests, 21 JS/Browser-Prüfprogramme, darunter 14 SponsorBlock-Medienzustands-Fixtures und echtes lokales HTMLVideoElement im Chromium-Test. Debug-/Release-Lint und ABI-getrennter Release-Build bestanden. Source-Scope- und gebündelter Gecko-Lifecycle-Test bestanden. Paketname/Version/ABI, Nicht-Debuggable und ursprüngliches Signierzertifikat geprüft. Lint-Warnungen sind nicht als fehlerfreie Geräteabnahme zu verstehen.

Kein angeschlossenes Android-Gerät: reale YouTube-Zeitleiste, Touch-Overlay und längere Mini-/PiP-Tests bleiben ausstehend. Keine Behauptung einer erfolgreichen Geräteprüfung. Die API lieferte beim öffentlichen read-only Smoke-Test HTTP 200; keine API-Schreibaktionen.

## Installation und Aktivierung

`dist/sponsorblock-1.3.21-rc1/Weiktube.apk` ist die ARM64-APK für das OnePlus. Als Update installieren, vorher nicht deinstallieren. Einstellungen → SponsorBlock → Aktivieren, Hinweis bestätigen. Details, Grenzen, Architektur, vollständige geänderte Komponenten und manuelle Testanleitung: [SponsorBlock-Bericht](docs/SPONSORBLOCK.md).

Die Daten stammen aus SponsorBlock (https://sponsor.ajay.app/) unter CC BY-NC-SA 4.0, keine offiziellen YouTube-Daten. `exclusive_access` ist kein Skip-Segment und wird nicht unterstützt. Unbekannte/abweichende Videodauer wird konservativ behandelt. Bei unbekannter Zeitleisten-DOM werden keine instabilen Marker erzwungen. Manuelle Buttons/Undo nur im großen Player; Auto-Skip bleibt in Mini/PiP von der bestehenden Wiedergabe abhängig.

## Verifizierte APKs

Paket: `com.weiktube.app`, Version `1.3.21-rc1`, Code `36`.
Signierzertifikat SHA-256: `f484083f7ee443d598c9216c76c3cd028d76d59ad73d6f5abc8ad6ded84eabc3`.

- Weiktube.apk (arm64-v8a), 106710683 Bytes; SHA-256 `363202574ffe8d898e202a7da49640b611d06cc1049769fa69c510482b96242a`.
- Weiktube-x86_64.apk (x86_64), 111718642 Bytes; SHA-256 `09da04c716c3d243775088df29c49c9c3a7f6185767feef2f70192fd37c0f8ee`.
