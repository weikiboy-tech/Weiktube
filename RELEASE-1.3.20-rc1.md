# Weiktube 1.3.20-rc1 – lokaler Testbuild

Nicht auf GitHub veröffentlicht, kein Updatekanal geändert. Basis main 1.3.19.

## Enthalten

- Vollbildgesten: links Fensterhelligkeit, rechts Medienlautstärke; separate Schalter und HUD.
- Doppeltipp-Spulen mit 5/10/15/20/30 Sekunden, kumulative Folgetipps, gültige Seek-Grenzen.
- Gedrückthalten beschleunigt vorübergehend; ursprüngliche Rate wird bei Abbruch/Moduswechsel
  wiederhergestellt. Nur vom aktuellen Player gemeldete Geschwindigkeiten auswählbar.
- WLAN-/Mobil-Qualitätspräferenzen, nur gemeldete Stufen, einmalige Anfrage und Statusprüfung.
- Optionale Return-YouTube-Dislike-Anzeige mit Aktivierungsdialog, Cache und Backoff.
- Vorhandener DataStore und dieselbe Player-WebView; keine neue Wiedergabe-Engine.

## Grenzen vor der Abnahme

Keine echten Android-Gerätetests: kein Gerät per ADB verfügbar. Die Qualitätssteuerung der
mobilen Website ist nicht garantiert; in der Live-Browserprobe war die Qualitätsliste leer.
Geschwindigkeiten über 2× wurden dort ebenfalls nicht angeboten. Es wird kein erfolgreicher
Qualitätswechsel behauptet. Neue native Gesten gelten im App-Querformat-Vollbild.

## Tatsächliche Tests

- **386 JVM-Tests:** App 288, Home 78, Content-Filter 20; 0 Fehler, 0 Fehlschläge, 0 übersprungen.
- Release-Build erfolgreich (ARM64 und x86_64).
- Lint: 0 Fehler, 45 Warnungen, 1 Hinweis. Darunter bestehende API-/Accessibility-Hinweise
  und eine KTX-Stilempfehlung für den neuen SharedPreferences-Cache; nicht „warnungsfrei“.
- 13 Node-Prüfprogramme: AdBlock, Advanced Player, Browser, Home-Bridge, native PiP-Verdrahtung,
  Original-Home-Navigation, Playback-Diagnose/Observer, Qualität, Player, Shorts-Audio,
  Videozoom, Watch-Navigation erfolgreich.
- 5 Chromium-Prüfprogramme: Player-Audio, Player-Layout, Zoom-Layout, Shorts-Controls-Layout,
  neue DOM-Interaktion erfolgreich. Simulationen/lokale Testseiten, keine Android-Geräteabnahme.
- Source-Scope-Prüfung und `git diff --check` erfolgreich.
- Öffentliches YouTube-Testvideo in frischem mobilem Headless-Chrome: Player vorhanden,
  Rates bis 2×, Qualitätsliste leer. Kein Nutzerkonto verwendet.
- RYD-Live-GET: HTTP 200, passende öffentliche Testvideo-ID und numerisches Dislike-Feld.
- Beide APKs: Paketname, Versionscode, Nicht-Debuggable, ABI, Update-Größenlimit und bisherige
  Signatur geprüft. Der erste abschließende inkrementelle APK-Packlauf scheiterte ohne
  aussagekräftige Detailursache; Wiederholung und vollständiger letzter Build waren erfolgreich.

## APKs und Signatur

Paket `com.weiktube.app`, Version `1.3.20-rc1`, Code `34`.
Bestehende Signatur (SHA-256):
`f484083f7ee443d598c9216c76c3cd028d76d59ad73d6f5abc8ad6ded84eabc3`.

- `dist/advanced-player-1.3.20-rc1/Weiktube.apk` — 106674171 Bytes, SHA-256 `7a4e3abbfbe766cad0cf7ab58024b7ab30deda33b87088ad69c3e89a3d63ad6d`.
- `dist/advanced-player-1.3.20-rc1/Weiktube-x86_64.apk` — 111682130 Bytes, SHA-256 `e9c73619f5f60d37c1f3d58bfc3718c7658678a781f7b5cc36e156aad4d03779`.

Installation über die vorhandene App erhält Einstellungen und Daten. Die öffentliche
Updateversion bleibt 1.3.19; dieser Kandidat erscheint dort nicht als Download.

[Vollständige technische Entscheidungen, Dateiliste und Handy-Testanleitung](docs/ADVANCED-PLAYER.md).
[Datenschutzhinweis zur optionalen Erweiterung](PRIVACY.md).
