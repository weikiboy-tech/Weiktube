# Weiktube 1.3.18 – Shorts-Ton und interaktives Spulen

- Zustandsbasierter Tonstart statt unbestätigter Einmal-Markierung.
- Ton beim ersten/nächsten Short und neuem Tab-Besuch anfordern; bewusstes Mute bleibt erhalten.
- Bei Autoplay-Blockade echte einmalige Tonfreigabe statt Wiedergabe-Schleife.
- Rote Shorts-Zeitleiste antippen oder horizontal ziehen; gültige Seek-Bereiche,
  Quellenwechsel und pausierte Wiedergabe werden berücksichtigt.
- Normaler Player, PiP, Zoom, Gecko-Startseite und AdBlock unverändert.

19 JavaScript-Simulationen und Chromium-Tests mit echter lokaler MP4 bestanden,
auch unter strenger Autoplay-Regel. Kein Android-/Live-YouTube-Gerätetest:
automatischer Ton bleibt von Plattformregeln abhängig; bei Blockade einmal tippen.

[Ursachen, Dateiliste, Tests, Einschränkungen und Handytest](docs/SHORTS-CONTROLS.md).

354 JVM-Tests bestanden (256 App, 78 Home, 20 Filter), keine Fehler/übersprungenen Tests.
Release-Build und Lint erfolgreich. ARM64/x86_64 mit bestehender Signatur, VersionCode 32.
