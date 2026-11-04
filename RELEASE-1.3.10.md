# Weiktube 1.3.10 – Wiederherstellung nach Prozessverlust

Stand: Version 1.3.10, Versioncode 24, Paket `com.weiktube.app`.
**Der Release-Build, 263 JVM-Tests, Scriptprüfungen und APK-Prüfung sind
bestanden.** Es wurde kein Handy- oder Android-Emulatortest
durchgeführt; der Nutzer übernimmt die praktische Prüfung der APK.

## Fehlerbild und belegter Änderungsgrund

Eine beendete Player-WebView konnte bis zum verzögerten Austausch noch über
Retry oder eine neue Auswahl angesprochen werden. Verspätete Callbacks alter
Ansichten konnten neuere Playerzustände beeinflussen. Eine renderergebundene
Vollbildfläche war außerdem keinem konkreten Besitzer zugeordnet und konnte
einen Ersatz verdecken. Die Korrektur beseitigt diese Code-Lücken; sie ist kein
Nachweis, dass jeder gemeldete schwarze oder nicht bedienbare Gerätebildschirm
genau dadurch entstanden ist.

Bei Gecko ist eine noch vorhandene Java-Sessionreferenz ebenfalls kein Beweis
für ein offenes Dokument: Ein beendeter Inhaltsprozess kann die Sitzung bereits
geschlossen haben. Diese Fälle werden nun ausdrücklich behandelt, statt eine
geschlossene Sitzung weiterhin als bedienbar anzusehen.

## Geänderte Komponenten

- `app/.../PlayerController.kt`: Rendererabbruch invalidiert alte Callback-
  Generationen sofort und entsorgt ausschließlich die betroffene Ansicht.
  Ein vollständig konfigurierter, zum Activity-Lifecycle passender Ersatz wird
  erst anschließend übernommen. Einzelne Cleanup-Fehler verhindern die übrige
  Entsorgung nicht. Quelle, zuletzt bekannte Position, Darstellungszustand und
  Wiedergabewunsch werden für höchstens einen automatischen Versuch je
  expliziter Videoauswahl vorgemerkt. Im Hintergrund wartet dieser auf eine
  zulässige Rückkehr; eine manuelle Pause widerruft den Wiedergabewunsch.
  Schließen, Zerstören und neue Auswahl verwerfen veraltete Wiederherstellung.
  Ein wiederholt abgestürztes Video verlangt eine erneute Nutzeraktion.
- `app/.../MainActivity.kt`: Vollbildflächen besitzen eine konkrete WebView-
  Zuordnung. Nach Rendererabbruch wird nur deren Fläche entfernt, ohne einen
  Callback in den toten Renderer aufzurufen. Die Rückkehr gleicht den PiP-
  Zustand mit Android ab. Gespeicherte Playerpositionen werden bei Activity-
  Wiederherstellung berücksichtigt; der Player bleibt dabei minimiert und
  pausiert, bis der Nutzer ihn startet.
- `app/.../BrowserController.kt`: Besitzerzuordnung für Browser-Vollbild und
  abgesicherte Behandlung von Cleanup-/veralteten Vollbild-Callbacks.
- `app/.../PlayerDiagnostics.kt`: Begrenztes Vokabular aus Lifecycle-, Renderer-
  und Recovery-Ereignissen mit Generation und Zustandskennung. Keine Seiten-
  URLs, Video-IDs, Cookies, OAuth-Tokens, Bridge-Payloads oder Exceptiontexte.
- `home-data/.../HomeContentRecovery.kt`, `OriginalHomeController.kt`,
  `HomeConnectionActivity.kt` und `OriginalHomeReturn.kt`: Gemeinsame,
  besitzergebundene Behandlung von `onCrash`, `onKill` und bereits geschlossenen
  Sitzungen. Alte Meldungen dürfen kein neues Dokument freigeben. Verbindungen,
  geparkte Rückgaben und die Dokumentbelegung werden bereinigt; das bestehende
  Website-Profil wird nicht gelöscht. Ein verlorenes Dokument muss neu geöffnet
  werden; seine frühere Scrollposition wird nach Prozessverlust nicht garantiert.
- `home-data/.../HomeEngine.kt` und `HomeExperimentLease.kt`: Feste
  Diagnosecodes statt freier Fehlermeldungen für Runtime-, Profil- und
  Belegungsfehler.

Gesunde WebViews und Gecko-Sitzungen bleiben bei normaler Rückkehr erhalten.
Ein nur langsamer oder vorübergehend nicht reagierender Renderer wird nicht
aufgrund eines Timers beendet oder neu geladen. Eine Responsive-Meldung hebt
den entsprechenden Zustand wieder auf. Der Fix verwendet keine Visual-Callback-
Timeouts als vermeintlichen Nachweis eines defekten Videoplayers.

Die vorhandenen Player-, Miniplayer-, PiP-, Videoübergabe-, Adblock- und
Kontoimplementierungen bleiben erhalten. Insbesondere muss auch ein Ersatz-
Player den vorhandenen Filterbereitschaftscheck durchlaufen. Es werden weder
Website-Sichtbarkeit noch Sicherheits-, DRM- oder Zugangssignale gefälscht.

## Tests und Grenzen

| Prüfung | Stand für 1.3.10 |
| --- | --- |
| 22 neue Player-Lifecycle-Tests mit Robolectric, API 35 | Bestanden; echte Controller-/WebViewClient-Callbacks mit simulierten WebViews |
| Fünf Home-Content-Recovery-Tests mit Sitzungs-/Host-Fakes | Bestanden; kein laufendes Gecko |
| Gesamte JVM-Regressionen | 263 bestanden: 166 App, 77 Home, 20 Filter; keine Fehler, Fehlschläge oder Skips; Filter-Ergebnisse von Gradle als aktuell übernommen |
| Player-/Observer-Script-Fixtures | 16 Player- und 5 Observer-Fixtures bestanden |
| Weitere Scriptprüfungen | Native PiP-Quellverträge, 120 modellierte Home-Starts, zehn modellierte Home-Bridge-Rückkehrzyklen und vorhandene Adblock-Scripts bestanden |
| Lokale Chromium-Pixel-/Layoutfixture | Erneut bestanden: negative Überdeckungskontrollen und zehn Mini-/PiP-/Rückkehrzyklen; Videoknoten, Quelle, Zeit, Pause und Scrollposition erhalten; kein Android- oder Live-YouTube-Test |
| Release-Lint | App/Home: 0 Fehler, 33/41 Warnungen |
| Release-Build ARM64 / x86-64 | Bestanden, Version 1.3.10 / Code 24 |
| APK-Version, Signatur, Architektur, Größe und SHA-256 | Beide geprüft; ursprüngliches Zertifikat, nicht debuggbar, unter 128 MiB; geprüfte Release-Dateien bytegleich zu den Build-Artefakten |
| Zwölf praktische Geräteabläufe aus TESTING.md | Alle nicht durchgeführt |

Robolectric führt den echten Player-Controller und dessen Android-Callbacks
mit simulierten WebViews aus. Die Test-Activity startet weder Gecko, Compose
noch Netzwerk-/Videowiedergabe. Lifecycle-Fixtures deaktivieren den Filter nur
im Test, weil keine Document-Start-JavaScript-Engine vorhanden ist. Ein eigener
Test prüft mit aktiviertem Filter, dass fehlende Filterbereitschaft das Laden
auch nach Rendererersatz verhindert. Die Home-Tests führen die echte
Besitzer-/Cleanup-Logik mit Fakes aus, keine Gecko-Engine.

Diese Tests prüfen die Zustandsübergänge und Race-Bedingungen, nicht echte
Rendererabstürze, Android-Compositing, sichtbare Videoframes oder Ton. Die
zwölf noch offenen Geräteabläufe stehen in [TESTING.md](TESTING.md).

**Weiterhin offen:** Im öffentlichen Chromium-Test von 1.3.9 pausierte YouTube
beim zweiten Mini-Übergang bei 16,010453 Sekunden. Für diese browserseitige
Pause gibt es hier keinen bestätigten Fix. Ebenso ist die konkrete Ursache
des vom Nutzer gemeldeten Gerätefehlers ohne Praxistest nicht abschließend
bestätigt. Die Wiederherstellung nach nachgewiesenem Prozessverlust ist keine
Garantie für unterbrechungsfreie YouTube-Wiedergabe.

## Installation und Update

Der vorhandene Updateweg bleibt erhalten. Beide geprüften APKs behalten
Paketname und ursprüngliches Signierzertifikat, damit ein Update ohne Löschen
der Website-Anmeldung möglich ist. Der SHA-256-Fingerabdruck des Zertifikats ist
`f484083f7ee443d598c9216c76c3cd028d76d59ad73d6f5abc8ad6ded84eabc3`.

| APK | Größe | SHA-256 |
| --- | ---: | --- |
| ARM64 | 106590435 Bytes | `6b542b5dd5c2492f44de1b382f4191414efd94aab51c40aaf7dba0dc66ed7a46` |
| x86-64 | 111598394 Bytes | `16d809d1cc3d0d2d8f3060520611d4fccc31c6b86921270ed4691f19463bb5ba` |

Versionsgebundene Downloads der Veröffentlichung:

- [Weiktube.apk für Android-Handys (ARM64)](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.10/Weiktube.apk)
- [Weiktube-x86_64.apk](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.10/Weiktube-x86_64.apk)
- [Bereinigter Quellcode](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.10/Weiktube-Quellcode.zip)
- [Update-Feed](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.10/update.json)

Über die vorhandene App installieren; Android bestätigt die Installation.
Es wird keine Veröffentlichung behauptet, bevor APKs, Quellcode, Feeds und
Prüfsummen tatsächlich gemeinsam hochgeladen und geprüft wurden.
