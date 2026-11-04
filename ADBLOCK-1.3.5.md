# Weiktube 1.3.5 – YouTube-Werbeschutz

Stand: 2026-11-04. VersionCode 19. **Automatisiert geprüft, nicht auf einem
Android-Gerät abgenommen.** Es wird keine vollständige oder dauerhafte
Werbefreiheit behauptet.

## Fehlerursache und Umsetzung

Die bisherige Domain-Engine nahm YouTube einschließlich Werbeanfragen ausdrücklich
aus. Im Player gab es keinen YouTube-Werbefilter, Gecko hatte ebenfalls keinen.
Ein zuvor werbefreier Testlauf war deshalb kein Nachweis für funktionierendes
YouTube-Adblocking.

Die vorhandenen Player-, Gecko-, Konto- und Navigationsimplementierungen bleiben
erhalten. Hinzu kommen drei Schutzschichten:

1. Gepinnte AdGuard-Scriptlets 2.5.1 vor den ersten Seitenskripten: bekannte
   Werbefelder in Initialdaten sowie passenden JSON-/Fetch-/XHR-Antworten entfernen.
2. Begrenzte Werbenetzwerkregeln; keine pauschale Sperre von `googlevideo.com`,
   YouTube-Player-API, Untertiteln oder normalen Mediendaten.
3. Explizite Werberenderer ausblenden. Erkannte laufende Werbung pausieren,
   stummschalten und abdecken. Ein geschützter Neustart im vorhandenen Player
   erhält die letzte bekannte Inhaltsposition. Ein weiterer Fehlschlag zeigt
   eine Fehlermeldung. Der Schutz wird nicht automatisch ausgeschaltet.

Bereits pausierte Videos werden durch die Werbeerkennung nicht absichtlich
gestartet. Das vor dem Pausieren erfasste Wiedergabesignal verhindert, dass eine
frühe Werbung wegen des langsameren nativen Statusabrufs den Neustart versehentlich
pausiert lässt. Späte Callback-Ergebnisse nach Schließen/Videowechsel werden verworfen.

Anmeldung und OAuth-Pfade sind vom neuen Filter ausgeschlossen. Es werden keine
Cookies, OAuth-Tokens, Passwörter oder vollständigen Antworten protokolliert.
Die Diagnose enthält nur begrenzte Zähler, feste Fehlercodes und Filterversionen.

## Geänderte Dateien

- `content-filter/`: gemeinsame Regelprüfung, RSA-Signaturprüfung, Status/Zähler,
  HTTPS-Updater, geprüfte Regeln, Runtime und AdGuard-Quellen/Lizenz.
- `WebViewAdBlock.kt`: Dokumentstart-Integration, eingeschränkte Nachrichtenquelle,
  Statusabfrage und Netzwerkfilter für die bestehenden WebViews.
- `PlayerController.kt`: Adblock-Anbindung und einmaliger geschützter Neustart;
  bestehender Player, Miniplayer und Darstellungslogik bleiben bestehen.
- `BrowserController.kt`: gleiche Anbindung für bestehende Browser-/Shorts-Ansichten.
- `ShieldEngine.kt`: YouTube-Hauptseiten sind nicht länger pauschal ausgenommen;
  Mediendomains bleiben vor pauschalen Domainblockaden geschützt.
- `MainActivity.kt`, `WeiktubeUi.kt`: Initialisierung, Einstellungen, Diagnose und
  Filterupdate-Prüfung; vorhandene Navigation erhalten.
- `home-data/.../GeckoAdBlock.kt`, `assets/youtube-adblock/`, `HomeEngine.kt`:
  separate eingebaute Erweiterung; Home lädt erst nach bestätigter Registrierung.
  Bestehende GeckoSession und Videoübergabe werden wiederverwendet.
- Modul-Gradledateien, `settings.gradle.kts`: neue Abhängigkeit, Version 1.3.5/19.
- Tests und `scripts/`: Regressionen, reproduzierbare Scriptlet-Auswahl,
  Regelsignierung, Quellcodepaket und exakt dokumentierte Integrationsänderungen.
- README, Lizenz, Drittanbieterhinweise und dieser Prüfbericht.

Eine Sicherung des vorigen Standes liegt lokal unter
`validation/adblock-before-1.3.5`. Private Signierschlüssel sind nicht im APK-
oder Quellcodepaket enthalten.

## Tatsächlich ausgeführte Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| `:app:testDebugUnitTest` | 110 bestanden |
| `:home-data:testDebugUnitTest` | 72 bestanden |
| `:content-filter:testDebugUnitTest` | 20 bestanden |
| Release-Build für ARM64 und x86-64 | Bestanden |
| Lint für alle drei Module | 0 Fehler, 70 Warnungen |
| `check-adblock-scripts.cjs` | Gepinnte Original-Scriptlets ausgeführt; Initialdaten, echte JSON-/Fetch-/XHR-Hooks, Metadatenerhalt, Auth-/CDN-/DRM-Ausnahmen, zehn simulierte SPA-/Werbezustandswechsel, laufende/pausierte Videos und Fehlerpfade bestanden |
| `vendor-adblock.mjs --check` | Upstream-Hashes und reproduzierbarer Generator bestanden |
| `test-gecko-adblock.mjs` | Dokumentstart-Registrierung, Update-Lücke, gepinnte Dokumentregeln, Fehler/Retry, Ursprungsschutz und begrenzte Zähler bestanden |
| `check-player-scripts.cjs` | 13 Fixtures inklusive Miniplayer, zehn Wiederöffnungen und zehn Hoch-/Querformatwechseln bestanden |
| Browser-, Watch-, Home- und Original-Home-Navigationstests | Bestanden; einschließlich zehn simulierten Rückkehrzyklen |
| Gecko-Rückkehr-Lifecycle-Modell | Bestanden |
| `check-home-scope.py` | 46 ursprüngliche Quelldateien gegen exakt dokumentierte Änderungen geprüft |
| APK-Prüfung | ARM64-Version 1.3.5/19, Android 8+, bestehendes Signierzertifikat bestätigt; beide APKs unter 128 MiB; Filter-/Gecko-Assets bytegleich zum geprüften Quellstand |

Ein ursprünglicher Regressionstest verlangte noch die alte YouTube-Ausnahme und
schlug deshalb zunächst fehl. Er prüft jetzt die ausdrücklich gewünschte
Werbeblockierung bei weiterhin erlaubten Mediendomains. Der vollständige letzte
Build und alle 202 JVM-Tests waren anschließend erfolgreich.

Die zusätzlichen Lint-Hinweise betreffen zwischengespeicherte WebView-Featurechecks,
den ausschließlich gehaltenen Application-Context sowie Abhängigkeitsupdates.
Es wurden keine nicht zugehörigen Bibliotheks-Upgrades vorgenommen.

**Nicht durchgeführt:** reale YouTube-Werbeauslieferung auf dem Handy, Anmeldung,
Touch-/Tab-/Rückkehrtests auf Android, echte Preroll-/Midroll-/Shorts-Wiedergabe,
Gerätetest des Filterupdates. Fixtures belegen die definierten Codepfade, nicht
die Wirkung gegen jede aktuelle Werbevariante.

## Filterupdates und Grenzen

Die signierte Quelle liegt als separate, nicht als „latest“ markierte
[GitHub-Release](https://github.com/weikiboy-tech/Weiktube/releases/tag/adblock-rules-v1)
vor. Diese erste Veröffentlichung der Filterdaten ließ die damalige reguläre
APK-Release v1.3.2 unverändert. Mit der [APK-Release v1.3.5](RELEASE-1.3.5.md)
werden auch beide APK-Varianten, der vollständige bereinigte Quellcode und die
Update-Dateien für den vorhandenen App-Updater bereitgestellt.
Beide öffentlichen Filterdateien wurden ohne Anmeldung heruntergeladen und
bytegenau gegen das geprüfte Paket verglichen.
Updates werden täglich beim Vordergrundwechsel oder manuell geprüft; kein
Hintergrunddienst. Signatur, Schema, erlaubte Datenfelder, Größe und Version
werden geprüft. Bei Problemen bleibt der letzte gültige Stand aktiv.

Aktive Dokumente behalten ihre Filter bis zum nächsten vollständigen Seitenaufruf.
Die Gecko-Sitzung wird für Updates nicht gelöscht oder zwangsweise neu geladen.
Downloads enthalten ausschließlich begrenzte Regeldaten; neuer ausführbarer
Filtercode erfordert eine neue APK. Unbekannte serverseitige Werbung kann neue
Regeln oder Code benötigen. Creator-Sponsoring ist ausdrücklich nicht enthalten.

ARM64-APK SHA-256:
`d9f9478ef1c827bdd13697a8bda5b6508a03d24a11dfa79130b7bf46f62be08c`

Für Installation über die bisherige Version bleibt die Signatur identisch.
Nicht deinstallieren, wenn die vorhandene Website-Anmeldung erhalten bleiben soll.
