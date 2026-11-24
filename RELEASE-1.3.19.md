# Weiktube 1.3.19 – YouTube-Links aus anderen Apps

- Neuer Eintrag in Einstellungen & Updates öffnet Androids Standard-Link-Einstellungen.
- Strikte externe URL-/Intent-Prüfung und bestehendes Routing für Video, Shorts und Seiten.
- Neue Links bei Activity-Wiederherstellung erhalten Vorrang vor altem Playerzustand;
  bereits behandelte Start-Intents werden nicht erneut abgespielt.
- Bestehende Activity, Player und Shorts-WebView bleiben erhalten.
- Player, PiP, Zoom, Shorts-Audio/Seekbar und AdBlock unverändert.

**Einmal manuell einrichten:** In Weiktube → Einstellungen & Updates →
YouTube-Links standardmäßig mit Weiktube öffnen. Unterstützte Links aktivieren und
YouTube-Domains auswählen; gegebenenfalls die Zuordnung der offiziellen YouTube-App
vorher deaktivieren. Fremde Domains können wir nicht selbst verifizieren.

Keine echte Android-Geräteabnahme; Quell-Apps mit eigenem Browser oder festem
YouTube-Aufruf können Androids Standardzuordnung umgehen.
[Dateien, Prüfungen, Einschränkungen und OnePlus-Anleitung](docs/EXTERNAL-LINKS.md).

364 JVM-Tests bestanden (266 App, 78 Home, 20 Filter), keine Fehler oder übersprungenen Tests.
Release-Build, Lint, bestehende Skriptprüfungen und Quellen-Erhaltungsprüfung bestanden.
ARM64/x86_64 mit bisheriger Installationssignatur und VersionCode 33.
