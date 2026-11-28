# Weiktube Android 1.3.28

In 1.3.27 konnte die Vorschau vier Sekunden nach dem Dokument-Commit verschwinden, obwohl der Player noch kein Bild gemeldet hatte. In 1.3.28 bleibt das Cover bis zum Bild, einem erkannten bedienbaren Webdialog/Play-Button oder dem manuellen Öffnen des Webplayers. Bleibt der Start hängen, lässt sich die Seite ausdrücklich erneut laden.

Im normalen Hochformat begrenzt das Watch-Layout jetzt auch den äußeren YouTube-Player und dessen inneren Player-Container auf die vorgesehene Höhe. Ein verbliebener vertikaler Offset im inneren Container wird dort zurückgesetzt. Die CSS-Regeln für Video-only, PiP und Miniplayer greifen weiterhin separat. Das Seitenverhältnis des Videos bleibt erhalten.

Das erste Videobild und das Eintreffen der restlichen Seite werden getrennt bewertet. Wenn die Seite unter dem Player sichtbar leer ist, zeigt Weiktube an dieser Stelle die schon verfügbaren Informationen zum ausgewählten Video. Angaben aus dem Webplayer werden anhand der Video-ID zugeordnet. Sobald die Originalseite die Fläche belegt, weicht die Zusammenfassung. Bei anhaltend fehlenden Details gibt es „Videoseite neu laden“. Die Wiedergabe wird dafür nicht automatisch unterbrochen.

## Testumfang

Die Offline-Chromium-Regression erzeugt einen 800 Pixel hohen äußeren Container mit einem um 52 Pixel versetzten Videobereich. Vor der Korrektur beginnt die Überschrift unterhalb des sichtbaren Fensters. Geprüft werden das ausgerichtete Video, die wieder sichtbare Überschrift, fehlende und verspätete Details, ein Dialog und der Wechsel in den Miniplayer und zurück. Robolectric prüft insbesondere den Zustand nach dem ersten Bild, den Wechsel zum nächsten Video und die Vorschau bei verzögertem Start.

Die genaue YouTube-DOM-Struktur des Vorfalls auf dem OnePlus konnte nicht aufgezeichnet werden. ADB meldete kein Gerät. Eine echte Android-Prüfung und eine Messung der YouTube-Netzwerkladezeit stehen aus.

Zum Nachtesten zuerst ein neues Video im Hochformat öffnen, dann ein zweites aus dem Verlauf. Auf den Bereich direkt unter dem Bild achten, auch wenn die Wiedergabe vor den Seitendetails beginnt.

Abschlusslauf: 418 JVM-/Robolectric-Tests, 23 JavaScript-/Browserprüfprogramme, Release-Build und Lint erfolgreich. Im direkten Chromium-Vergleich der beiden Produktionsskripte lag das Video bei y=52 bzw. y=0, der Titel bei y=821 bzw. y=246 (400 × 800 CSS-Pixel). Das sind Layoutmessungen einer reproduzierten Fehlerkonstellation, keine OnePlus-Ladezeiten.
