# Weiktube 1.3.9 – Miniplayer-/PiP-Korrektur

Stand: Version 1.3.9, Versioncode 23, Paket `com.weiktube.app`.
**Der abschließende Release-Build mit Schubladenkorrektur, 236 JVM-Tests und
Artefaktprüfung sind bestanden.** Es wurde kein
Handytest und kein erfolgreicher Android-Emulatortest durchgeführt. Der Nutzer
übernimmt die praktische Prüfung der APK. Die endgültigen Artefakte sind unten
aufgeführt; ein Geräteerfolg wird nicht behauptet.

## Änderungen

Die Korrektur betrifft drei bestehende Produktionsdateien; derselbe Player,
dieselbe WebView und dieselbe Videoquelle bleiben erhalten:

- `MainActivity.kt`: Android-Zurück verkleinert den geöffneten Player, ohne ihn
  dabei ausdrücklich zu pausieren. Die vorhandenen Pausenregeln für eine
  unsichtbare App und den ausgeschalteten/gesperrten Bildschirm bleiben bestehen.
- `WeiktubeUi.kt`: Im Android-PiP-Modus werden die an die WebView gelieferten
  Systemleisten-/Displayausschnitt-Insetwerte auf null gesetzt. Compose-Padding
  war dort bereits null; die Korrektur betrifft die weitergereichten Insets.
- `PlayerController.kt`: Miniplayer und System-PiP verwenden dieselbe Darstellung
  der reinen Videofläche. Auf die Watch-Videobox begrenztes CSS korrigiert
  zurückgebliebene Inline-Zentrierung des YouTube-Videos; dadurch soll das Bild
  innerhalb der sichtbaren Box liegen und nach dem Vergrößern wieder korrekt
  sitzen. Das Videoelement wird nicht ersetzt und die Quelle nicht neu geladen.
  Zusätzlich wird ausschließlich in Mini- und System-PiP-Darstellung die dort
  über das Video ragende geschlossene YouTube-Schublade ausgeblendet.

Es gibt keinen neuen Player, keinen zweiten PiP-Wiedergabeweg und keine Änderung
an Google-Kontoanbindung, Gecko-Sitzung oder Adblock-Regeln. Sichtbarkeit,
DRM- und Zugriffssignale werden nicht gefälscht.

## Belegter Browserbefund

Ein isolierter Desktop-Chromium-Test hat die echte öffentliche mobile
YouTube-Watch-Seite mit mobilen Viewportgrößen untersucht. Das ist ein echter
Browser-Renderingtest, **kein Android-WebView-/Compose-/WindowManager-Test**.

Vor der Korrektur lag das Video im 196×110-Mini-Viewport bei Y=53 statt Y=0.
Nach der Rückkehr in einen 384×800-Viewport lag die 384×216-Videofläche bei Y=292.
Die korrekte Breite und Höhe allein reichten deshalb nicht für ein korrekt
positioniertes Bild.

Nach der kombinierten Mini-/PiP-Videobox und Watch-Zentrierung wurden **31
Messpunkte aus zehn Mini-/PiP-/Rückkehrzyklen** aufgezeichnet: alle Videoursprünge
lagen bei X=0/Y=0, Videoelement und `currentSrc` blieben gleich.
**Diese Messung allein war kein Nachweis für ein korrekt sichtbares Bild.**
Screenshots und Hit-Tests zeigten anschließend eine separate Überdeckung:
`.ytmDrawerLayoutContainerDrawerLayout`, YouTubes geschlossene Schublade, blieb
360 Pixel breit bei `position: fixed`, `left: -100vw`, `z-index: 2` und Deckkraft 1.
Bei 196 Pixel Viewportbreite ragten damit 164 Pixel über das Video; bei 320 Pixel
PiP-Breite waren es 40 Pixel. Die Hit-Tests trafen die Schublade vor dem Video.

Dies ist die belegte Ursache der grauen Überdeckung im Browser. Die 164 Pixel
entsprechen beim historischen Gerätemaßstab 3,5 genau 574 Bildpunkten und passen
zum früheren Android-Bildstreifen. Die Übertragung des Fixes auf das Handy bleibt
ohne aktuellen Gerätetest offen.

Die zusätzliche CSS-Regel setzt nur für `data-weiktube-mini="1"` oder
`data-weiktube-system-pip="1"` genau diese Schublade auf `display: none`.
Normalansicht und gewöhnliches Querformat erhalten diese Regel nicht. Der
Miniplayer und seine Wiedergabeengine werden dafür nicht ersetzt. Der abschließende
öffentliche Browserlauf enthält 32 Messpunkte: Normalansicht, negative Baseline
und 30 Übergänge aus zehn Mini-/PiP-/Rückkehrzyklen. Es blieb stets genau ein
Videoelement mit gleicher Identität und Quelle. Die Mini- und PiP-
Screenshots des zehnten Zyklus wurden visuell geprüft: vollständiges Bild ohne den
grauen Balken. Die lokale Fixture prüft zusätzlich echte rote/blaue PNG-Pixel,
die negativen 164-/40-Pixel-Überdeckungen, zehn Zyklen, Benutzerpause und Scrollposition.

**Separater offener Wiedergabebefund:** Im finalen öffentlichen Browserlauf
pausierte YouTube beim zweiten Mini-Übergang bei `currentTime=16.010453` Sekunden.
Es wurde keine automatische Wiederaufnahme erzwungen. Diese Pause bleibt offen;
der Schubladenfix ist ein Bildfix, kein belegter Fix der gesamten Wiedergabesteuerung.
Korrekte Geometrie und erhaltene Videoidentität sind kein Nachweis für
ununterbrochene Wiedergabe. Ebenso ist nicht belegt, dass der historische
Android-Bildfehler ausschließlich dieselbe Ursache hatte.

## Prüfstand

| Prüfung | Stand für 1.3.9 |
| --- | --- |
| Öffentliche mobile YouTube-Seite in isoliertem Chromium | Überdeckung per Screenshot/Hit-Test nachgewiesen; final 32 Messpunkte inklusive negativer Baseline und zehn Übergangszyklen, stets ein Video mit gleicher Identität/Quelle; Mini-/PiP-Endbilder visuell ohne grauen Balken |
| Durchgängige Browser-Wiedergabe über alle Übergänge | Nicht bestätigt; Ursache der beobachteten Pause bleibt offen |
| Finaler Release-Build ARM64 / x86-64 | Mit Schubladenkorrektur bestanden |
| JVM-Regressionstests | 236 bestanden (144 App, 72 Home, 20 Filter), keine Fehler/Skips; unveränderte Home/Filter-Ergebnisse von Gradle als aktuell übernommen |
| Player-/Observer-Script-Fixtures | 16 Player- und 5 Observer-Fixtures bestanden |
| Lokale Chromium-Layoutfixture | Echte PNG-Pixel, negative 164-/40-Pixel-Überdeckungen, zehn Zyklen, Benutzerpause und Scrollposition bestanden; ohne neue Abhängigkeit |
| Weitere Regressionen | Native PiP-Quellverträge, 120 modellierte Home-Kaltstartzyklen, zehn modellierte Navigationszyklen und vorhandene Adblock-Scriptprüfungen bestanden |
| Scope-Prüfung | 46 ursprüngliche Quelldateien gegen exakt dokumentierte Änderungen geprüft |
| Release-Lint | App/Home 0 Fehler, 33/41 Warnungen |
| APK-Signatur, Version, Architektur, Größe und SHA-256 | Finaler Neubuild geprüft, ursprüngliches Signierzertifikat, nicht debuggbar, beide APKs unter 128 MiB |
| Physisches Android-Handy | Nicht durchgeführt; Nutzer übernimmt APK-Abnahme |
| Android-Emulator mit echter WebView/Compose/PiP | Nicht durchgeführt; isolierter Start erreichte keine nutzbare Android-Sitzung |

Die lokale Emulatorprüfung scheiterte an fehlender Hardwarebeschleunigung und
einem erfolglosen Softwarestart. Der Emulator erhöhte zudem die vorgegebene
Speichergrenze. Er wurde beendet; es erfolgen auf Nutzerwunsch keine weiteren
Handy-/Emulatorversuche. Frühere Tests aus 1.3.8 gelten nicht automatisch für diese
Änderung. Weitere Prüfgrenzen stehen in [TESTING.md](TESTING.md).

## Installation und Update

Die Veröffentlichung verwendet den vorhandenen Updateweg: ARM64- und
x86-64-APK, gemeinsame Versionsnummer 1.3.9/code23, passende SHA-256-Prüfsummen,
Update-Feed und bereinigter Quellcode. Das bisherige Signierzertifikat bleibt
Voraussetzung für beide APKs. Die folgenden versionsgebundenen Links sind für die
Veröffentlichung vorgesehen und werden durch den Upload verfügbar:

- [Weiktube.apk für Android-Handys (ARM64)](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.9/Weiktube.apk)
- [Weiktube-x86_64.apk](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.9/Weiktube-x86_64.apk)
- [Vollständiger bereinigter Quellcode](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.9/Weiktube-Quellcode.zip)
- [Update-Feed](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.9/update.json)

| APK | Größe | SHA-256 |
| --- | ---: | --- |
| ARM64 | 106581959 Bytes | `e9a8b048da48fb3d149a6c9c92f8baa96e821336d9b1b43b5298b78d5c0af004` |
| x86-64 | 111589918 Bytes | `c7dc57c07a24e6b1cbdc0973020b9fc9ee6c5cc09d2eef37507c796056642ee6` |

Über die vorhandene App installieren, wenn die Website-Anmeldung erhalten
bleiben soll. Android bestätigt die Installation. Die Änderung garantiert
weder fehlerfreie Darstellung auf jedem Gerät noch durchgängige YouTube-
Wiedergabe unter allen Browser- und Plattformbedingungen.
