# Video-Zoom – Implementierung und Prüfbericht

Stand: 24.11.2026, Arbeitsbasis main 3248253294ef59cfc5118f58671de4283fe3c4dd (1.3.16).

## Umsetzung

Die vorhandene Player-WebView und ihr Videoelement bleiben bestehen. Androids
ScaleGestureDetector verarbeitet Zwei-Finger-Gesten im Videobereich; der Faktor
ist auf 1–8 begrenzt. Quick-Scale (Doppeltipp-Zoom) ist ausgeschaltet, damit der
vorhandene Doppeltipp weiter an den Webplayer geht. Beim Übernehmen einer
Zwei-Finger-Geste erhält die WebView ACTION_CANCEL. Die gesamte Restfolge wird
bis UP/CANCEL konsumiert, auch wenn nur ein Finger übrig bleibt. Eine neue
Ein-Finger-Folge kann danach wieder zum Minimieren verwendet werden.

PlayerController bündelt visuelle Updates pro Android-Displayframe. VideoZoomScript
transformiert ausschließlich das vorhandene Video, mit transform-origin 0 0,
Skalierung und Translation um den Gestenmittelpunkt. Aus videoWidth/videoHeight
wird die contain-Geometrie berechnet. Translation wird auf den verfügbaren
Bildinhalt begrenzt; bei ausreichend großem Faktor füllt das Video den Bereich.
Eine invers berechnete clip-path begrenzt das Bild auf seine ursprüngliche Box.
Controls, Seiteninhalt, Untertitel und die WebView selbst werden nicht gezoomt.
Keine Reloads, keine Quellenwechsel, keine Play-/Pause-/Seek-/Lautstärkebefehle.

Die native PlayerViewportTransform für kleine Fenster bleibt unverändert.
Zoom wird auf 1 zurückgesetzt bei Mini/PiP, Wechsel zwischen Portrait und
Querformat, Größenänderung, neuem Videostart und neuem Renderer/Dokument.
Die Rückkehr aus Mini/PiP startet konsistent bei 1. Vorherige Inline-Styles
werden beim Reset mit ihrer Priorität wiederhergestellt. Nach Resize während
einer aktiven Geste bleibt deren Restfolge blockiert, ohne neu zu zoomen.

Referenzen: [ScaleGestureDetector](https://developer.android.com/reference/android/view/ScaleGestureDetector)
und [ViewGroup-Touch-Verarbeitung](https://developer.android.com/develop/ui/views/touch-and-input/gestures/viewgroup).

## Geänderte Dateien

Produktionscode:
- app/src/main/java/com/weiktube/app/PlayerGestureLayout.kt
- app/src/main/java/com/weiktube/app/PlayerController.kt
- app/src/main/java/com/weiktube/app/WeiktubeUi.kt
- app/src/main/java/com/weiktube/app/VideoZoomScript.kt (neu)

Tests und Prüfinfrastruktur:
- app/src/test/java/com/weiktube/app/PlayerVideoZoomGestureTest.kt (neu)
- app/src/test/java/com/weiktube/app/PlayerControllerLifecycleTest.kt
- scripts/check-video-zoom.cjs (neu)
- scripts/check-video-zoom-layout.cjs (neu)
- scripts/check-home-scope.py
- scripts/video-zoom-reviewed-changes.json (neu)
- .github/workflows/android.yml
- scripts/package-source.py (nimmt diesen Bericht ins Quellcodepaket auf)
- docs/VIDEO-ZOOM.md (dieser Bericht)

PlayerDragGesture, PlayerViewportTransform, NativePipController, MainActivity,
AdBlock, Kontoanbindung, Gecko-Home und Verlauf sind unverändert.

## Tatsächlich ausgeführte Tests

- `:app:testDebugUnitTest :home-data:test :content-filter:test`: **354 bestanden**
  (256 App, 78 Home, 20 Filter; 0 Fehler, 0 übersprungen).
- Sechs neue Android/Robolectric-Gestentests: Pinch-out/in, Übergabe-Cancel,
  Restfinger, neuer Ein-Finger-Drag, Pointer-Wechsel, Cancel, zehn Moduswechsel,
  Quellenwechsel, Resize sowie Antippen und Gesten außerhalb des Videobereichs.
- Neuer Controller-Test: zehn Mini/PiP/Rückkehrzyklen mit Zoom, identische
  WebView, unveränderte geladene URL und Reset bei neuer Videoauswahl.
- Neun neue JS-Fixtures: 16:9, 4:3, 9:16, 1:1, Grenzen/ungültige Werte,
  Fokuspunkt, contain/fill, Clipping, Style-Restoration, Resize/Metadaten,
  zehn Zoom-/Resetzyklen ohne Medienkommandos oder Listener-Duplikate.
- Chromium mit lokal generiertem Video: alle vier Seitenverhältnisse,
  je zehn Zoom-/Resetzyklen, weiterlaufende Frames, identisches Videoelement,
  unveränderte Controls und Seitendetails, manuelle Pause bleibt erhalten,
  Rücksetzen nach Viewport-Rotation. Screenshot-Pixelproben an fünf Rändern
  pro Format bestätigen bei Fill keine schwarzen Balken; eine Aufnahme
  zusätzlich visuell geprüft.
- Bestehende Skripte: check-player-scripts, check-native-pip-integration,
  check-browser-scripts, check-original-home-navigation, check-adblock-scripts,
  check-playback-observer, check-playback-diagnostics, check-shorts-audio.
- Bestehender Chromium-Playerlayouttest: zehn Mini/PiP/Restorezyklen bestanden.
- `check-home-scope.py`: ursprüngliche Quellen außerhalb der geprüften Diffs erhalten.
- `:app:assembleDebug :app:lintDebug`: erfolgreich; Lint 0 Fehler, 43 Warnungen,
  1 Hinweis. Dies war zunächst ein lokaler Testbuild.

Nachtrag: Auf Wunsch wird der geprüfte Zoom als 1.3.17 (VersionCode 31) für
den regulären GitHub-Updatekanal gebaut. Die vorherige Installationssignatur
bleibt erhalten. Die oben genannten Gerätetestgrenzen gelten weiterhin.

## Geräteprüfung und Grenzen

ADB meldete kein verbundenes Gerät. **Kein Test auf einem Android-Handy und kein
Live-YouTube-Test durchgeführt.** Flüssigkeit, reale Hardware-Decodierung,
Ton und Gestenkonflikte mit aktuellen YouTube-Seitenvarianten sind damit noch
nicht am Gerät abgenommen. Chromium- und Robolectric-Tests ersetzen das nicht.

Unterstützt sind der bestehende erweiterte Player (oberer Videobereich) und
sein durch Drehung aktivierter Querformatmodus. Ein von Chromium separat über
onShowCustomView erzeugtes HTML-Vollbild-Overlay liegt außerhalb dieses
Touch-Containers; dort wird kein Zoom zugesichert. Zum Testen das Handy für
Weiktubes vorhandenes Querformat drehen. Shorts sind nicht Teil dieser Änderung.

Bis zu achtfacher Zoom kann extreme Seitenverhältnisse nicht immer vollständig
füllen. Schwarze Ränder, die bereits Bestandteil des Videos selbst sind, werden
nicht automatisch erkannt. Keine eigenständige Ein-Finger-Pan-Geste: Ein Finger
bleibt für die bisherige Bedienung reserviert. Die vorhandenen Webseiten-
Selektoren können bei künftigen YouTube-Änderungen angepasst werden müssen.

## Kurzer Handytest

1. Test-APK als Update installieren. Normales Video von Home öffnen und abspielen.
2. Handy ins Querformat drehen; zwei Finger im Bild auseinanderziehen. Bild soll
   sich bis zum Füllen vergrößern, Ton und Zeit müssen weiterlaufen.
3. Zusammenziehen bis zur vollständigen Ansicht. Play/Pause, Doppeltipp und
   Zeitleiste testen. Während einer Pinch-Geste einen Finger loslassen und den
   anderen herunterziehen: darf nicht minimieren. Dann beide loslassen und mit
   einer neuen Ein-Finger-Geste im Hochformat minimieren.
4. Gezoomt nach Mini und Android-PiP wechseln: vollständiges, zentriertes Bild.
   Zurückkehren, anderes Video öffnen und erneut rotieren: jeweils Standardzoom.
5. Mindestens zehnmal wiederholen, auch mit 4:3 und Hochformatvideo. Prüfen:
   kein Neustart, kein zweiter Ton, keine schwarzen Flächen und keine bewegten Controls.
