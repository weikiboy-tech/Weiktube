# Weiktube 1.3.6 – Hochformat nach Vollbild

Version 1.3.6, Versioncode 20. Gezielte Korrektur für die seitlich verschobene
und rechts abgeschnittene Ansicht nach dem automatischen Querformat-Vollbild.

## Ursache und Änderung

Das äußere Compose-Layout berücksichtigt im Hochformat die Systemleisten und
Display-Aussparungen. Im Querformat erhält die WebView diese Abstände selbst.
Beim Wechsel zurück konsumiert die verwendete Compose-Version die aktualisierten
Insets, bevor sie die eingebettete WebView erreichen. Dadurch kann der seitliche
Querformat-Abstand in der Webseite stehen bleiben, obwohl der native Footer
bereits korrekt ausgerichtet ist.

Die bestehende Player-WebView erhält jetzt nach Moduswechsel, Konfigurationswechsel
und geändertem Layout ausdrücklich aktualisierte Insets. Im nativ gepolsterten
Hochformat/Miniplayer werden Systemleisten- und Aussparungswerte auf null gesetzt;
im Vollbild werden die tatsächlichen Werte weitergegeben. Die Zustellung wird
zusammengefasst und beim Entfernen der View abgebrochen.

Das folgt dem dokumentierten
[Android-Verhalten bei hängenbleibenden WebView-Insets](https://developer.android.com/develop/ui/views/layout/webapps/understand-window-insets#avoid-ghost-padding-by-zeroing-insets).
Es wird dieselbe WebView weiterverwendet. Kein Seitenneuladen, kein Austausch des
Players, keine Änderung von Videozeit, Adblock, Google-Anmeldung oder Gecko-Startseite.

## Geänderte Bereiche

- `PlayerGestureLayout.kt`: direkte, begrenzte Insets-Zustellung an den vorhandenen Player.
- `WeiktubeUi.kt`: teilt dem Container mit, ob das äußere Layout Systemabstände berücksichtigt.
- `PlayerViewportInsetsTest.kt`: Android-Regressiontests für den Fehler und wiederholte Wechsel.
- Versions-, Release- und Prüfdateien für die Veröffentlichung als 1.3.6/20.

## Prüfungen

- Release-Build für ARM64 und x86-64 erfolgreich; App-Lint: 0 Fehler, 28 Warnungen.
- 207 JVM-Tests grün: 115 App, 72 Home, 20 Filter. Unveränderte Home-/Filtertests
  wurden durch Gradle als aktuell übernommen.
- Fünf neue Robolectric-Tests mit Android-35-Insetobjekten: alter Abstand bei
  konsumierendem Vorfahren reproduziert; direkte Null-Zustellung korrigiert ihn.
  Zehn Wechsel mit linker/rechter Aussparung, dieselbe WebView, erhaltene
  Tastatur-/Gesten-Insets und gelöschte versteckte Systemleistenabstände geprüft.
- 13 bestehende Player-JavaScript-Fixtures bestanden, einschließlich wiederholtem
  Hoch-/Querformatwechsel und Erhalt von Videoidentität, Zeit und Scrollposition.
- Gegen den veröffentlichten Stand 1.3.5 wurden in allen drei Modulen exakt zwei
  Produktionsquelldateien geändert: PlayerGestureLayout und WeiktubeUi.
- Beide APKs: Paket/Version/Architektur, ursprüngliches Zertifikat, deaktiviertes
  Debugging und Größe unter dem Updaterlimit von 128 MiB geprüft.

Keine Handytests für diese Version; die Prüfung des sichtbaren Ergebnisses auf
dem betroffenen OnePlus steht noch aus. Robolectric prüft Android-Zustellung,
nicht den echten Chromium-Renderer oder den aktuellen YouTube-DOM auf dem Handy.

## Installation

In Weiktube: **Einstellungen & Updates → Jetzt nach Updates suchen**.
Alternativ: [APK für ARM64](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.6/Weiktube.apk)
oder [x86-64](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.6/Weiktube-x86_64.apk).

Über die bestehende Installation installieren; nicht vorher deinstallieren.
Das bisherige Signierzertifikat bleibt erhalten. Der
[Quellcode](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.6/Weiktube-Quellcode.zip)
und Lizenzen sind beigefügt. Die experimentellen Werbefilter aus 1.3.5 bleiben
enthalten; vollständige Werbefreiheit wird weiterhin nicht garantiert.
