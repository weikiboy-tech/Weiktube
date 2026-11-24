# Weiktube 1.3.20 – Advanced Player & Return YouTube Dislike

Gleicher Funktionsstand wie der geprüfte lokale Kandidat 1.3.20-rc1; Paketname und Signatur bleiben erhalten.
Versionscode 35 ermöglicht das Update sowohl von 1.3.19 als auch vom lokalen Kandidaten (Code 34).

- Vollbildgesten: links Helligkeit, rechts Medienlautstärke, jeweils abschaltbar.
- Doppeltipp-Spulen mit 5/10/15/20/30 Sekunden und kumulativen Folgetipps.
- Gedrückthalten beschleunigt temporär; die vorherige Geschwindigkeit wird wiederhergestellt.
- Separate WLAN-/Mobil-Qualitätspräferenzen mit Fähigkeitenprüfung und Rückfall auf Automatik.
- Optionale Return-YouTube-Dislike-Anzeige mit Zustimmung, Cache und Rate-Limit-Behandlung.
- Neuer Bereich Videoplayer unter Einstellungen & Updates.

386 JVM-Tests und die dokumentierten JavaScript-/Chromium-Prüfungen bestanden. Release-Build und Lint erfolgreich; beide APKs erneut auf Paketname, Version, ABI, Größenlimit und bisherige Signatur geprüft.
Keine echten Android-Gerätetests durchgeführt. In der mobilen YouTube-Browserprobe wurden Geschwindigkeiten bis 2× und keine Qualitätsstufen gemeldet: höhere Geschwindigkeiten und erzwungene Auflösung werden nicht vorgetäuscht.

Neue native Ein-Finger-Gesten gelten im App-Querformat-Vollbild. Bestehende Player-WebView, Shorts, PiP und AdBlock-Filter bleiben erhalten.

Details, Dateiliste und manuelle Testanleitung: [Advanced-Player-Bericht](https://github.com/weikiboy-tech/Weiktube/blob/main/docs/ADVANCED-PLAYER.md).
Dislike-Daten stammen von [Return YouTube Dislike](https://returnyoutubedislike.com/) und sind teilweise geschätzt.
