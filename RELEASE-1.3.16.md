# Weiktube 1.3.16 – Shorts mit Ton

## Gezielte Änderung

Der vorhandene Shorts-Tab hatte bisher eine eigene WebView mit verpflichtender
zusätzlicher Mediengeste. Der bereits vorhandene Tonstart des normalen Players
war dort nicht eingebunden.

Diese Version erlaubt den Tonstart in der dedizierten Shorts-WebView und gibt
dem sichtbaren, bereits laufenden Short einmalig Ton. Auch das nächste Short
kann hörbar starten. Eine bewusste Stummschaltung über die Webseitensteuerung
bleibt für den laufenden Shorts-Besuch erhalten; erst ein erneuter Tab-Eintritt
fordert wieder Ton an. Die Medienlautstärke wird nicht hochgesetzt.

Kein neuer Player, keine Play-Schleife, keine zusätzliche Audioquelle. Der
normale Watch-Player, PiP, AdBlock, Gecko-Anmeldung, Shorts-Routing und die
Orientierungslogik wurden nicht verändert.

## Geänderte Komponenten

- `BrowserController`: Tonfreigabe nur für die dedizierte Shorts-WebView;
  Freigabe bleibt an Sichtbarkeit und bestehende Filterbereitschaft gebunden.
- `MainActivity`: einmalige Tonanforderung beim Eintritt in den Shorts-Tab.
- `ShortsAudioScript`: idempotente Ereignisbehandlung, aktives Video,
  bewusste Stummschaltung und verspätete Medien-/Filterbereitschaft.
- Version, Tests, CI, Erhaltungsprüfung und Release-Dokumentation aktualisiert.

## Testgrenzen

15 neue JavaScript-Verhaltenstests und ein nativer Verdrahtungsvertrag prüfen Tonstart, Folgevideos, erneut verwendete
Medienelemente, manuelles Stummschalten, Hintergrund/Offscreen, Filterbereitschaft,
Origin-/Frame-Grenzen und doppelte Listener. Bestehende Browser-, Player-, PiP-,
Navigations-, Diagnose-, Home- und AdBlock-Skriptprüfungen bestehen.

Kein Gerät war per ADB verbunden. Reale YouTube-Shorts-Wiedergabe und hörbarer Ton
auf einem Android-Gerät wurden für diese Version **nicht getestet**. Die Tests
simulieren Zustände; sie beweisen keine Kompatibilität mit jeder YouTube-Seitenvariante.
Systemlautstärke und eine bewusste Stummschaltung werden nicht übersteuert.

347 JVM-Tests bestanden (249 App, 78 Home, 20 Filter; keine Fehler/übersprungenen Tests).
ARM64- und x86_64-Release-APKs gebaut, Release-Lint bestanden. Signatur, Paketname,
Version 1.3.16/30, nicht debuggbarer Build und APK-Größen unter dem Updater-Limit
von 128 MiB geprüft. Beide APKs verwenden die bisherige Installationssignatur.
