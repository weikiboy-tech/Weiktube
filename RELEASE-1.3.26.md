# Weiktube Android 1.3.26 – Start normaler Videos

## Was sich beim Öffnen ändert

Das mobile Watch-Dokument konnte das Präsentationsskript erhalten, bevor sein HTML-Wurzelelement existierte. Die erste Anwendung der Videogeometrie fiel dann aus. Weiktube installiert die Darstellung jetzt für die ausgewählte Wiedergabe, übernimmt den nativen Querformat-/PiP-Zustand und wendet das Layout beim Erscheinen der Wurzel an. Beim Dokument-Commit gleicht die App die Darstellung erneut ab. Gespeicherte Zieh-Offsets und ihre Animation gehören zum jeweiligen Video.

Vor dem Commit belegt ein Ladegerüst die Playerfläche. Anschließend ist die Originalseite bedienbar. Ein dezenter Fortschrittsindikator begleitet den Übergang zum Bild. Der vorhandene Medienbeobachter meldet den ersten an den Browser-Compositor übergebenen Frame. Bei pausiertem Video oder eingeschränkter WebView dient ein dekodierbares Bild mit gültigen Dimensionen als Ersatzsignal. Auf dem physischen Display sichtbare Pixel kann die WebView damit nicht garantieren.

Ein bekannter Titel aus dem lokalen Verlauf erscheint bei übereinstimmender Video-ID. Androids Titel-Callback und der Watch-Seitentitel können die Anzeige anschließend aktualisieren, auch wenn das Videoelement noch fehlt. Die Gecko-Startseite übergibt im bestehenden Vertrag eine URL. Bei bisher unbekannten Videos stammt der Titel daher weiterhin vom Watch-Dokument. Kanalbild, Bewertungen, Beschreibung, Kommentare und Empfehlungen erscheinen entsprechend der Datenlieferung durch YouTube. Drittanbieterabfragen sind keine Startvoraussetzung. Bei ausgeschalteter Dislike-Funktion entfallen wiederholte Skriptaufrufe. Lange Dislike-Zahlen erscheinen kompakt; der zugängliche Text enthält den vollständigen Schätzwert.

Die einmalige Tonanforderung nutzt auch ein bereits spielendes Video. Ausstehende Play-Aufrufe werden zusammengefasst. Der bestehende AdBlock-Gate und die Android-Systemlautstärke bleiben maßgeblich. Der Hinweis zur gespeicherten Position liegt über der Seite und verändert die Höhe der WebView nicht. „Von vorne“ bleibt erreichbar.

## Messung und Grenzen

Lokales Desktop-Chromium, identische Offline-Seite und Fenstergröße, fünf neue Dokumente pro Version. Die Fixture fügt das Wurzelelement nach dem Startskript ein und simuliert den späteren Präsentationsaufruf mit 350 ms Verzögerung. Gemessen wurde die Zeit ab Einfügen der Wurzel bis zum ersten geprüften Animationsframe mit einer 400 × 225 Pixel großen Videofläche.

| Version | Einzelmessungen in ms | Median |
| --- | --- | --- |
| 1.3.25 | 368, 371, 374, 354, 374 | 371 ms |
| 1.3.26 | 13, 10, 10, 10, 10 | 10 ms |

Für echte YouTube-Ladezeiten, Bildausgabe, Lautsprecher, Kaltstart, längere Inaktivität und WLAN/Mobilfunk-Vergleich liegen keine OnePlus-Messwerte vor. ADB meldete kein angeschlossenes Gerät. Die reale Detailansicht hängt vom YouTube-Layout, Konto, Cache und Netzwerk ab.

## Automatisierte Prüfung

415 JVM-/Robolectric-Tests im finalen Gesamtlauf bestanden. 22 JavaScript-/Chromium-Prüfprogramme bestanden, einschließlich Audio, verspätetem Frame, Quellwechsel, Geometrie und 320-Pixel-Aktionsleiste. Release-Lint meldete null Fehler, 42 Warnungen und einen Hinweis. Ein bestehender DataStore-Verlaufstest erreichte in einem Zwischenlauf sein Zeitlimit; der vollständige Abschlusslauf bestand.

## Test auf dem OnePlus 15R

1. Weiktube frisch öffnen. Ein neues Video antippen und auf Position des ersten Bildes, Ton und Titel achten.
2. Ein Video aus dem Verlauf mit gespeicherter Position öffnen. „Von vorne“ testen, anschließend bewusst stummschalten.
3. Nach Miniplayer, System-PiP und zwei Drehungen ein anderes Video öffnen. Auch zwei schnell hintereinander gewählte Videos und einen externen Watch-Link testen.
4. Bei Bedarf denselben Ablauf mit WLAN und mobilen Daten wiederholen. Für Zeitmessungen enthält der lokale Logcat-Tag `WeiktubeStart` einmalige Meilensteine ab Auswahl. Er enthält weder Video-ID noch URL, Kontodaten oder Token. `watch-details` erfasst das Vorhandensein von Titel- und Kanal-DOM; dynamische Empfehlungen und Kommentare können später nachladen.

## Geänderte Komponenten

PlayerController.kt, PlayerPlaybackObserver.kt, WeiktubeUi.kt und ReturnDislikeScript.kt. Die Tests betreffen PlayerControllerLifecycleTest.kt, PlayerPlaybackObserverTest.kt und die Browserprüfungen check-playback-observer.cjs, check-player-scripts.cjs, check-advanced-player-dom.cjs und check-watch-start.cjs. Versionsangaben, Release-Packager, Quellcodeprüfung, README und Changelog wurden für den Updatekanal angepasst.
