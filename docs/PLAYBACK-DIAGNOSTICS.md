# Player-Diagnose: Dokumentwechsel, Hintergrundrückkehr und PiP

Dieser Bericht trennt den Bildschirmstatus-Abgleich in 1.3.13, die Dokument-/
Lifecycle-Befunde für 1.3.12 und den öffentlichen Browser-Pausepfad aus 1.3.11. **Die Ursache des
schwarzen Players nach längerem Hintergrundaufenthalt auf dem Handy ist nicht
bestätigt; die browserseitige Mini-/PiP-Pause ist nicht behoben.** Die Korrektur
von 1.3.13 wurde bisher nicht auf einem Android-Handy/-Emulator oder mit einem
angemeldeten Konto praktisch geprüft.

## 1.3.13: Sperrstatus und Fokus nach dem Ruhemodus

Der Nutzer meldet den schwarzen Player nach ungefähr fünf Minuten Ruhemodus
auch mit 1.3.12. Diese Rückmeldung ist eine praktische Gegenprobe zum bisherigen
Fix: **Der gemeldete Gerätefehler ist durch 1.3.12 nicht behoben.** Aus dem Foto
allein lassen sich Rendererzustand, WebView-Pause und Ursache nicht ablesen.

Die neue isolierte Gegenprobe umfasst sieben Fokus-/Sperrstatus-Tests: Zwei
schlugen mit dem vorherigen Code fehl, fünf Kontrollen bestanden. In den
Fehlfällen bleibt `screenOff=true`, obwohl PowerManager und Keyguard bereits
ein interaktives, entsperrtes Gerät melden. Beim Fokusgewinn wurde dieser
gespeicherte Zustand nicht erneut abgeglichen; die native WebView blieb pausiert.
Die Fixture ruft den echten `MainActivity`-Fokus-Callback auf und injiziert das
alte Flag gezielt. Sie startet weder `MainActivity.onCreate` noch Compose,
Gecko oder Chromium und beweist keine tatsächliche Ereignisfolge auf dem Handy.
Lokale Belege: `validation/screen-state-before-1.3.13/`.

Der gemeinsame Abgleich liest bei `ACTIVITY_START`, `ACTIVITY_RESUME`,
`WINDOW_FOCUS` sowie `SCREEN_OFF`, `SCREEN_ON` und `USER_PRESENT` den aktuellen
Systemzustand. Die Sperre ist aktiv, solange der Bildschirm nicht interaktiv
oder der Keyguard gesperrt ist. Eine alte `SCREEN_OFF`-Nachricht blockiert ein
inzwischen entsperrtes Gerät nicht erneut; `SCREEN_ON` allein hebt einen
gesperrten Keyguard nicht auf. Bei zulässiger Rückkehr wird dieselbe gesunde
native WebView fortgesetzt. Daraus folgt kein neuer Play-, Load-, View-Austausch-
oder Recovery-Timer-Pfad. Die bereits vorhandene Behandlung belegten Dokument-/
Rendererverlusts bleibt erhalten. Fokusverlust allein unterbricht sichtbares PiP nicht.

Beispiel des festen, rein nativen Diagnoseschemas:

```text
SCREEN_STATE signal=WINDOW_FOCUS process=00000000-0000-4000-8000-000000000000 activity=2 controller=0 view=0 interactive=true keyguard=false previouslyBlocked=true blocked=false foreground=true
```

| Feld | Interpretation |
| --- | --- |
| `signal` | Anlass der erneuten Abfrage, kein Beweis für den aktuellen Bildschirmzustand |
| `interactive`, `keyguard` | Aktuell gelesene PowerManager-/Keyguard-Werte |
| `previouslyBlocked`, `blocked` | Vorher gespeicherte bzw. neu abgeleitete App-Sperre |
| `foreground` | Activity ist nach ihrem Lifecycle im Vordergrund; separate Voraussetzung für das Fortsetzen |
| `process`, `activity` | Native Instanzen wie unten beschrieben; Controller/View sind bei dieser Hostmeldung nicht angegeben (`0`) |

`previouslyBlocked=true blocked=false` bei aktivem, entsperrtem Gerät zeigt
die Korrektur eines veralteten Flags. Erst nachfolgende View-/Gate- und
Medienbeobachtungen können weitere Zustände belegen; der Eintrag selbst beweist
weder sichtbare Frames noch Wiedergabe. Es werden keine Seiten- oder Kontodaten
erhoben.

Android garantiert keine feste Broadcast-Lieferzeit und nennt ab Android 14
insbesondere zurückgestellte `ACTION_SCREEN_ON`-Meldungen bei gecachten Apps.
Das spricht für eine aktuelle Statusabfrage statt Annahmen über die Reihenfolge.
Es beweist nicht, dass genau diese Verzögerung auf dem Nutzergerät stattfand.
[Android-Broadcasts](https://developer.android.com/develop/background-work/background-tasks/broadcasts)

Chromiums `BrowserViewRenderer` setzt mit `SetIsPaused` den internen Pausenzustand;
`IsClientVisible` berücksichtigt ihn und `UpdateBeginFrameSource` trennt bei
fehlender Client-Sichtbarkeit die Frame-Quelle. Window-/Attachment-Zustände
werden separat behandelt. Daher ist antwortendes JavaScript allein kein Beweis
für fortlaufende native Frame-Ausgabe. Eine Ressourcenneuvergabe nach Unsichtbarkeit
ist eine mögliche zusätzliche Erklärung, **kein nachgewiesener Handybefund**.
[Chromium BrowserViewRenderer](https://github.com/chromium/chromium/blob/main/android_webview/browser/gfx/browser_view_renderer.cc),
[WebView.onPause](https://developer.android.com/reference/android/webkit/WebView#onPause())

Nach der Korrektur bestanden 46 gezielte Tests: 15 Bildschirmstatus-, 24 native
PiP- und sieben Diagnosefälle. Die erweiterten Bildschirmtests führen auch den
echten Receiver-Aufruf über den MainActivity-Abgleich bis zum vorhandenen Player
aus; Android-Zustände sind kontrolliert, keine realen Handybroadcasts. Ein
zusätzlicher Fall prüft nach Entsperren das Schließen und Öffnen eines anderen
Videos mit Dokument-Commit und erlaubter nativer Play-Anforderung in derselben
WebView. Anschließend bestand die erneut ausgeführte vollständige App-Suite;
Produktionscode und APKs wurden dafür nicht geändert.
Der abschließende Debug-/Release-Build war erfolgreich. 322 JVM-Tests bestanden:
225 App, 77 Home, 20 Filter; unveränderte Home-/Filter-Ergebnisse wurden von
Gradle als aktuell übernommen. App-Lint meldet keine Fehler und 37 Warnungen.
Scope-Abgleich mit 46 ursprünglichen Quelldateien und native PiP-Quellverträge
bestanden. Beide Release-APKs sind auf Paket/Version/Code 27, ursprüngliches
Signierzertifikat, richtige ABI, Größe und SHA-256 geprüft; nicht debuggbar und
jeweils unter 128 MiB. Prüfsummen stehen in [Release 1.3.13](../RELEASE-1.3.13.md).
Die unveränderten JavaScript-/Chromium-Pixeltests aus 1.3.12 wurden für 1.3.13
nicht erneut ausgeführt; daraus wird kein neuer Renderingnachweis abgeleitet.
Neue Handy-/Emulatortests, praktische 30-Sekunden-/5-/15-Minuten-Ruhemodus-Abläufe
und sichtbare Mini-/PiP-Wiedergabe wurden nicht geprüft. Die nachfolgenden
früheren Teststände sind kein Ersatz dafür. [Release 1.3.13](../RELEASE-1.3.13.md).

## 1.3.12: Belegte Dokument-Races und begrenzte Wiederherstellung

Die Gegenprobe mit dem bisherigen 1.3.11-Controller umfasst 13 ausgeführte
Robolectric-Tests: acht reproduzierte Fehler und fünf erfolgreiche Kontrollen.
Verspätete Start-/History-/Abschluss-/Fehlermeldungen eines früheren Videos
konnten in derselben weiterhin lebenden WebView die neuere Auswahl oder ihren
Ladezustand verändern. Ein Poll vor Bestätigung des neu ausgewählten Dokuments
konnte noch Daten des vorherigen Videos liefern. Belege liegen lokal in
`validation/document-race-before-1.3.12/BASELINE.txt` und der zugehörigen JUnit-XML.

Der Controller ordnet solche Meldungen jetzt zusätzlich dem ausgewählten
Dokument zu. Polling und Wiedergabefreigabe warten auf dessen Commit; ältere
Antworten bleiben wirkungslos. Das Fehlen eines Videoelements während eines
SPA-Übergangs setzt die letzte gültige Medienposition nicht mehr auf null.
Diese Lücken sind im Code reproduziert, **nicht als Ursache des konkreten
Handyfehlers bewiesen**.

Der neue automatische Wiederherstellungspfad verlangt einen positiven Beleg:
Ein bereits bestätigtes Videodokument liefert über JavaScript nun eine
vollständig geladene, leere `about:blank`-Seite ohne Video. Dann ist einmal je
ausdrücklicher Videoauswahl ein Wiederherstellungsversuch in derselben gesunden
WebView möglich. Quelle, Position und Benutzerpause werden berücksichtigt;
eine neue Auswahl oder Schließen entwertet den alten Auftrag. Ein zweiter
Verlust zeigt einen Fehler. Fehlende JS-Antworten, Ladeverzögerungen, fehlender
Fokus und Zeitablauf erlauben keinen Reload. Die bestehende Behandlung eines
wirklich beendeten Renderers bleibt ein anderer Pfad mit View-Ersatz.

Ein zusätzlicher Test reproduzierte zunächst eine weitere Lücke: Dieselbe
Videoauswahl während einer anstehenden Leer-Dokument-Recovery verwendete das
noch unbestätigte Dokument wieder. Die Auswahlprüfung berücksichtigt jetzt
auch die Dokumentgültigkeit; die negative Gegenprobe besteht nach dem Fix.

Der abschließende Gradle-Lauf mit App-/Home-/Filtertests, Debug-/Release-Build
und Debug-Lint war erfolgreich. 307 JVM-Tests bestanden: 210 App, 77 Home,
20 Filter, ohne Fehler oder Fehlschläge; unveränderte Home-/Filter-Ergebnisse
wurden als aktuell übernommen. Darunter sind 61 gezielte Fälle: 27 Dokument-,
drei Compose-Host-, sieben Diagnose- und 24 bestehende Lifecycle-Tests.
App-Lint meldet keine Fehler und 37 Warnungen. 17 Player-, fünf Observer-, acht
Diagnose-Fixtures, Home-/Filter-/Watch-Scriptprüfungen, der Scope-Abgleich mit
46 ursprünglichen Quelldateien und zehn lokale Chromium-Pixelzyklen bestanden.
Die Release-APKs wurden auf Paket/Version 1.3.12/Code 26, ursprüngliches
Signierzertifikat, ABI, Größe und SHA-256 geprüft; beide sind nicht debuggbar
und unter 128 MiB. Die Prüfsummen stehen in [Release 1.3.12](../RELEASE-1.3.12.md).

Die Compose-Fixture startet eine isolierte `ComponentActivity` mit echtem `AndroidView`/
`key`, aber weder die komplette `MainActivity` noch Chromium. Bundle-/Parcel-
Roundtrip, neue Controller und native Hostmaße beweisen weder echten Prozessverlust
noch Videopixel. [Prüfprotokoll](../TESTING.md), [Release 1.3.12](../RELEASE-1.3.12.md).

## Native Kennungen: Prozess, Activity, Controller, View und Dokument

Native Player-Ereignisse enthalten ab 1.3.12 zusätzlich diese Felder:

```text
ACTIVITY_RESTORE generation=0 state=ACTIVITY_RECREATION process=00000000-0000-4000-8000-000000000000 activity=2 controller=0 view=0
VIEW_HOST_UPDATE generation=4 state=PAUSED process=00000000-0000-4000-8000-000000000000 activity=2 controller=3 view=5 attached=true shown=true hasParent=true parentMatches=UNKNOWN visibility=VISIBLE windowVisibility=VISIBLE windowFocus=false width=320 height=180
```

Die Werte sind Beispiele. `process` ist eine zufällige lokale Kennung je
App-Prozess, keine PID, Konto-/Gerätekennung oder Zugriffsbefugnis. Sie wird nur
zur Wiederherstellung im Activity-Bundle gespeichert, nicht als dauerhafte
Nutzerkennung in Einstellungen. Der gespeicherte alte Wert wird nicht ausgegeben.
`activity`, `controller` und `view` zählen native Instanzen innerhalb dieses
Prozesses; `0` bedeutet nicht angegeben. `generation` bezeichnet den lokalen
Controller-/Dokumentzustand und darf nicht über verschiedene Controller hinweg
als dieselbe Sitzung interpretiert werden.

| Schritt/Signal | Was daraus folgt | Was daraus nicht folgt |
| --- | --- | --- |
| `ACTIVITY_CREATE state=COLD_START` | Die Activity erhielt kein Saved-State-Bundle | Kein Beweis für einen erstmals gestarteten App-Prozess |
| `ACTIVITY_RESTORE state=ACTIVITY_RECREATION` | Gespeicherter und aktueller Prozessmarker stimmen überein; neue Activity im selben Prozess | Kein Prozessverlust; kein Nachweis sichtbarer Videoframes |
| `ACTIVITY_RESTORE state=PROCESS_RESTORE` | Das Bundle stammt von einer anderen Prozessinstanz | Keine Aussage, weshalb der vorige Prozess endete oder wie lange er im Hintergrund war |
| `ACTIVITY_RESTORE state=RESTORE_UNKNOWN` | Bundle vorhanden, aber gültiger Marker fehlt, etwa aus einer älteren Version | Prozessverlust darf nicht daraus geraten werden |
| `ACTIVITY_SAVE_STATE` / `ACTIVITY_DESTROY` | Speichern bzw. beobachtetes Zerstören dieser Activity; `CONFIGURATION_CHANGE` oder `FINISHING` soweit gemeldet | Fehlendes `ACTIVITY_DESTROY` beweist keinen weiterhin lebenden Prozess |
| `RENDERER_CRASHED` / `RENDERER_KILLED` | Native Meldung über Ende des Renderers der benannten View | Nicht automatisch Ende der App oder aller WebViews |
| `RENDERER_UNRESPONSIVE` | Native Meldung über fehlende Reaktion | Kein Beweis für Prozessende; kein automatischer Kill-/Reload-Grund |
| `STALE_DOCUMENT_IGNORED` | Meldung gehört nicht zum aktuell angenommenen Dokument | Kein Nachweis eines Rendererabsturzes |
| `DOCUMENT_LOST` | Der eng begrenzte positive Leer-Dokument-Befund wurde angenommen | Keine pauschale Diagnose für jeden schwarzen Bildschirm |
| `DOCUMENT_COMMITTED` / `RECOVERY_DOCUMENT_READY` | Das gewählte bzw. wiederhergestellte Dokument wurde gemeldet | Keine bestätigte Videowiedergabe, kein dekodierter Frame oder Ton |
| `VIEW_ATTACHED`, `VIEW_DETACHED`, `VIEW_HOST_UPDATE` | Native Anbindung oder geänderter Snapshot der konkreten View | `attached=true` bzw. positive Maße allein garantieren kein sichtbares Video |

`shown`, `visibility`, `windowVisibility`, `windowFocus`, `hasParent` und
`attached` beschreiben ausschließlich die native View. Maße werden beim
Formatieren auf 0–65535 begrenzt. `parentMatches` ist nur dann wahr/falsch,
wenn der erwartete Parent übergeben wurde; ansonsten `UNKNOWN`. Fehlender
Window-Fokus ist beispielsweise in PiP erwartbar und kein eigenständiger
Pause- oder Recovery-Grund. Snapshots enthalten keine View-Objekte,
Klassennamen, Titel, URLs oder freien Fehlertexte. Unbekannte Statusstrings
werden als `UNKNOWN` ausgegeben.

Für den schwarzen Player zuerst Prozess-/Activity-Marker vergleichen, danach
Renderer- und View-Identität, Dokument-Commit/-Verlust und native Anbindung.
Erst anschließend Pause-/Gate-Ereignisse zuordnen. Eine neue View-ID bei
demselben Controller ist etwas anderes als eine neue Activity oder ein neuer
Prozess. Fehlende oder abgeschnittene Logs bleiben eine Evidenzlücke.

Android garantiert beim Beenden eines Hintergrundprozesses kein `onDestroy()`.
`ActivityScenario.recreate()` führt Speichern/Zerstören/Neuerstellen der Activity
aus, beendet aber keinen Linux-Prozess. `AndroidView`-Factory, Update und
Release haben ebenfalls getrennte Lebenszyklen. Ein erfolgreicher
`postVisualStateCallback` schließt den Zustand von `<video>` ausdrücklich aus.
[Prozess-Lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle),
[ActivityScenario](https://developer.android.com/reference/androidx/test/core/app/ActivityScenario#recreate()),
[AndroidView](https://developer.android.com/reference/kotlin/androidx/compose/ui/viewinterop/AndroidView.composable),
[WebView](https://developer.android.com/reference/android/webkit/WebView#postVisualStateCallback(long,%20android.webkit.WebView.VisualStateCallback)).

Die praktischen Rückkehrtests nach 30 Sekunden, fünf und 15 Minuten,
vollständige MainActivity-Neuerstellung, echter System-/Rendererverlust sowie
Mini-/PiP-Wiedergabe sind **nicht durchgeführt**. Die folgenden älteren
Browserbefunde ersetzen diese Tests nicht.

## Belegter Browserbefund aus 1.3.11

Ein isolierter Desktop-Chrome-Kontext lud die öffentliche mobile YouTube-
Watch-Seite ohne Übernahme einer Benutzeranmeldung. Im wiederholten Ablauf
Normalansicht → 196×110-Mini → 320×180-PiP-Größe → Normalansicht begann die
Wiedergabe zunächst normal. Ab dem zweiten Mini-Übergang pausierte die Seite.
Danach wurde auch eine ausdrücklich im Test ausgelöste Wiedergabe über das
Mediaelement oder die Seiten-Playerfunktion jeweils nach ungefähr einer
Viertelsekunde wieder pausiert. Es wurde keine Wiederaufnahme-Schleife gebaut.

Der zusätzliche Debugger-Test hielt am tatsächlichen seiteninternen
Pause-Aufruf an. Der aufrufende Fortschritts-Handler prüfte unter anderem:

- eine interne YouTube-Bedingung für Picture-in-Picture war wahr;
- die internen Bedingungen für diesen Pause-Zweig waren erfüllt;
- das Video wurde in diesem Zweig nicht als für Hintergrundwiedergabe geeignet
  behandelt;
- anschließend rief die Seite ihre eigene Pause-Funktion auf.

Die beobachtete interne Sichtbarkeitsfunktion lieferte den Wert eines
YouTube-eigenen Picture-in-Picture-Zustands. Gleichzeitig war
`document.hidden=false`. Dieser interne Zustand ist weder Androids
`isInPictureInPictureMode` noch ein Nachweis, dass ein echtes System-PiP-Fenster
existierte. Die Aussage zur Hintergrundfähigkeit folgt aus dem beobachteten
Bedingungszweig; es wurden keine Konto-, Zahlungs- oder Berechtigungsdaten
ausgelesen. Daraus folgt **keine allgemeine Aussage, dass ein bestimmtes Abo
den Weiktube-Player repariert**, und keine Garantie für andere Videos oder
YouTube-Versionen.

Der kontrollierte Vergleich trennt diese Seite von unserem Präsentations-CSS:

| Variante | Tatsächlich beobachtet |
| --- | --- |
| Unveränderte Seite, keine Weiktube-Scripts und keine Viewportwechsel | Wiedergabe von ungefähr 5,7 bis 47,942882 Sekunden, kein erfasster Pause-Aufruf |
| Nur Viewportwechsel, ohne `WATCH_CHROME`, Wiedergabe-Guard oder Adblock-Script | Erste Pause beim zweiten 196×110-Übergang bei 20,338213 Sekunden; erneute ausdrückliche Play-Versuche wurden ebenfalls pausiert, insgesamt neun `pause`-Ereignisse und 18 erfasste JS-Pause-Aufrufe, einschließlich Aufrufen auf bereits pausierten Elementen |
| Viewportwechsel mit bestehendem Präsentationsscript | Gleiches Grundmuster: Pause ab dem zweiten Mini-Übergang und nach weiteren ausdrücklichen Play-Versuchen |

Damit sind unser CSS und der Android-Lifecycle **keine notwendige Ursache für
diese Browser-Reproduktion**. Eine zusätzliche Android-spezifische Ursache
auf dem Handy ist dadurch nicht ausgeschlossen. Der Test benennt einen
konkreten seiteninternen Pause-Zweig; er legt weder alle YouTube-Regeln offen
noch ersetzt er einen Android-WebView-/Audiofokus-Test.

Lokale Belege, die nicht automatisch Teil einer Veröffentlichung sind:

- `validation/mini-playback-audit/report.json`: Wiedergabe-/Pause-Ereignisse und
  ausdrücklich ausgelöste Play-Versuche.
- `validation/mini-playback-stack/report.json`: isolierte Debugger-Untersuchung
  des tatsächlich erreichten seiteninternen Pause-Zweigs. Rohdaten daraus
  werden nicht in die App-Diagnose übernommen.
- `validation/mini-playback-control/report.json`: unveränderte Gegenprobe.
- `validation/mini-playback-viewport-only/report.json`: Viewport-Gegenprobe
  ohne die genannten Weiktube-Scripts.

Der ältere Befund bei 16,010453 Sekunden aus
`validation/mini-public-layout-drawer-fix/report.json` enthielt nur die
Resize-/Pause-Korrelation, keine Aufrufattribution. Seine ruhige Gegenprobe
endete bereits bei 14,141814 Sekunden. Die neuen längeren Gegenproben und der
Debugger-Beleg erweitern diese Evidenz; die frühere Geometriekorrektur allein
war kein Beweis für ununterbrochene Wiedergabe.

## Kleine Bedienkorrektur und Diagnosekomponenten

- `WeiktubeUi.kt`: Das sichtbare Play-Symbol ruft ausdrücklich `play()` auf,
  das sichtbare Pause-Symbol ausdrücklich `pause()`. Der Klick wird an den
  beim Rendern angezeigten Zustand gebunden. Ein inzwischen veränderter
  JavaScript-Zustand soll einen gewünschten Play-Klick nicht in einen
  Toggle-Pause-Klick verwandeln. Diese Korrektur überwindet keine Pause-Regel
  der YouTube-Seite.
- `PlayerDiagnostics.kt` und Aufrufstellen: Eigene Pausebefehle erhalten feste
  Gründe; Darstellung, Wiedergabefreigabe und Lifecycle werden als feste
  Ereignisse protokolliert.
- `PlayerPlaybackDiagnostics.kt`: Ein einmal je Dokument eingerichteter
  Beobachter erfasst begrenzte Medien-/Sichtbarkeitsereignisse. Die
  JavaScript-`pause()`-Methode wird transparent beobachtet; die originale
  Funktion wird weiterhin ausgeführt, einschließlich ihres Rückgabewerts
  und möglicher Exceptions. Es gibt keinen erzwungenen Play-/Reload-Aufruf.
- `PlayerController.kt`: Eigene Pausebefehle und der bestehende Wiedergabe-
  Guard werden beim Aufruf kenntlich gemacht. Diagnose-Hooks werden zusammen
  mit der jeweiligen WebView eingerichtet, vor Dokumentwechsel erneuert und
  beim Entsorgen entfernt. Player, Quelle und bestehender Adblock bleiben
  die vorhandenen Implementierungen. Der ausdrückliche Pausebefehl führt seine
  direkte Medienpause in `finally` aus, auch wenn der Guard-Aufruf fehlschlägt.
  Das bestehende Präsentationsscript `WATCH_CHROME` ist exakt unverändert.

## Logschema und richtige Interpretation

Die beiden Log-Tags sind `WeiktubePlayer` und `WeiktubePlayback`. Die Diagnose
zeichnet keine URLs, Medien-IDs, Titel, Medienzeit, Cookie-/Tokenwerte,
Stacknamen, Seitenantworten oder freien Exceptiontexte auf.

Ein eigener Pausebefehl hat dieses Schema:

```text
APP_PAUSE reason=HOST_STOP generation=4 process=00000000-0000-4000-8000-000000000000 activity=2 controller=3 view=5
```

`generation` ist ein lokaler Controller-Zähler, keine Video-ID. Die festen
Pausegründe bedeuten:

| Grund | Bedeutung |
| --- | --- |
| `USER` | Ausdrücklicher Pausebefehl aus der Appbedienung |
| `HOST_STOP` | Activity/Player wird nicht mehr sichtbar betrieben |
| `SCREEN_LOCK` | Bildschirm aus oder gesperrt |
| `SHORTS_SELECTED` | Wechsel zu Shorts unterbricht den bisherigen Player |
| `ACCOUNT_ACTION` | Konto-/Interaktionsansicht wurde geöffnet |
| `FILTER_FAILURE` | Vorhandener Filter meldet einen Fehler |
| `FILTER_RECOVERY` | Vorhandene begrenzte Wiederherstellung nach Werbeerkennung |
| `CLOSE` | Player wird geschlossen |

Weitere native Ereignisse enthalten feste Kennungen, beispielsweise
`PRESENTATION_NORMAL`, `PRESENTATION_MINI`, `PRESENTATION_PIP`, `USER_PLAY`,
`ACTIVITY_PAUSE`, `ACTIVITY_STOP`, `VIEW_PAUSED` oder `RENDERER_KILLED`.
`GATE_ALLOWED`, `GATE_HOST_BLOCKED`, `GATE_POLICY_BLOCKED` und
`GATE_FILTER_BLOCKED` beschreiben **Weiktubes eigene Wiedergabefreigabe**.
Insbesondere bedeutet `GATE_POLICY_BLOCKED` keine festgestellte
YouTube-/Premium-Beschränkung.

Der Medienbeobachter liefert dieses Schema; die Werte sind nur ein Beispiel:

```text
event=PAUSE reason=JS_UNATTRIBUTED seq=12 elapsedMs=22000 video=true paused=true connected=true hidden=false ready=4 mini=true pip=false width=196 height=110
```

| Feld | Bedeutung und Grenze |
| --- | --- |
| `event` | `INITIAL`, `PLAY`, `PLAYING`, `PAUSE_CALL`, `PAUSE`, `ENDED`, `VISIBILITY`, `PAGE_HIDE`, `BLUR`, `METADATA`, `EMPTIED` oder `RESIZE` |
| `reason` | Begrenzte Zuordnung eines Pause-Aufrufs; siehe Tabelle unten |
| `seq` | Monotoner Dokumentzähler; beginnt für ein neues Dokument erneut, keine globale Ereignis-ID |
| `elapsedMs` | Zeit seit Einrichtung des Dokumentbeobachters, keine Medienzeit; nicht unmittelbar mit Androids monotonic clock gleichsetzen |
| `video` | Beobachtetes Ziel ist ein Video; `false` kann Audio oder ein fehlendes Ziel bedeuten |
| `paused` | Gelesener Medienzustand; kein Nachweis des Urhebers |
| `connected` | Mediaelement ist im DOM verbunden; kein Beweis für eine an Androids Window angehängte WebView |
| `hidden` | Unveränderter Wert von `document.hidden`; `false` beweist keine unverdeckten Pixel |
| `ready` | `readyState` im Bereich 0–4; selbst 4 garantiert weder hörbaren Ton noch fortlaufende Wiedergabe |
| `mini`, `pip` | Weiktubes Präsentationsattribute am Dokument; keine Bestätigung eines Android-Systemfensters oder von YouTubes internem PiP-Zustand |
| `width`, `height` | Viewportgröße, nicht notwendigerweise Größe des sichtbaren Videobildes |

| `reason` | Zulässige Schlussfolgerung |
| --- | --- |
| `NONE` | Ereignis ohne Pause-Zuordnung |
| `APP_COMMAND` | Der erfasste JS-Aufruf erfolgte innerhalb eines markierten App-Pausebefehls; konkreten nativen `APP_PAUSE`-Grund heranziehen |
| `APP_GUARD` | Der vorhandene App-Guard hat diesen JS-Pause-Aufruf ausgelöst |
| `JS_UNATTRIBUTED` | Ein JS-Pause-Aufruf außerhalb dieser markierten Bereiche wurde gesehen; ohne weitere Evidenz kein Beweis für eine konkrete YouTube-Regel oder Nutzeraktion |
| `ENGINE_OR_UNOBSERVED` | Für das Pause-Ereignis fehlt ein zeitlich passender erfasster JS-Aufruf; Browser, früher gespeicherte Methodenreferenz oder eine Beobachtungslücke bleiben möglich |

Die Zuordnung von `PAUSE` verwendet den zuletzt erfassten passenden Aufruf
am selben Mediaelement innerhalb einer Sekunde. Das ist eine begrenzte
Korrelation, keine vollständige Stackanalyse. Ein `PAUSE_CALL` allein beweist
auch keinen Wechsel von laufend zu pausiert: Das Element kann bereits
pausiert gewesen sein. `BLUR` allein ist ebenfalls kein Pause-Grund.

JavaScript und native Seite begrenzen auf jeweils höchstens 120 Ereignisse
pro 60 Sekunden. Nach Erreichen der Grenze können Einträge fehlen. Bridge-
Nachrichten sind auf 768 Zeichen und ein festes Schema begrenzt; Herkunft,
Hauptframe, WebView-Identität, Dokumentkennung und Sequenz werden geprüft.
Die Dokumentkennung wird nicht geloggt. Diese Prüfungen begrenzen veraltete
oder unerwartete Meldungen; Seiten-JavaScript bleibt kein manipulationssicherer
Beweissensor. Ohne unterstützte Bridge-/Document-Start-Funktion kann die
Beobachtung unvollständig oder verspätet beginnen.

Für die Auswertung zuerst eigene `APP_PAUSE`-/Gate-/Lifecycle-Ereignisse prüfen,
dann die Medienereignisse und die Reihenfolge der Darstellungswechsel.
Fehlende Einträge nicht als Beweis für fehlende Aufrufe behandeln. Es werden
keine zusätzlichen Audiofokus-Anforderungen gestellt, nur um einen Fokusverlust
zu vermuten oder zu provozieren. Ohne belegtes Signal bleibt die Ursache offen.

## Plattformregeln und nicht unterstützte Schlussfolgerungen

Android führt beim PiP-Eintritt `onPause` aus, obwohl die Activity sichtbar
bleibt. Die Plattform empfiehlt für laufende Videowiedergabe die Trennung
über `onStop`/`onStart`. PiP hat gewöhnlich keinen Eingabefokus. Ein fehlender
Fokus darf daher nicht pauschal als Aufforderung zum Pausieren behandelt werden.
[Android-PiP-Dokumentation](https://developer.android.com/develop/ui/views/picture-in-picture)

`WebView.onPause()` ist kein vollständiger JavaScript-Stopp. `pauseTimers()`
wirkt hingegen global auf alle WebViews. Chromium verarbeitet Pause,
Window-/View-Sichtbarkeit und Anbindung gesondert. Diese Zustände sollen
beobachtet und korrekt verwaltet, nicht gegenüber der Website gefälscht werden.
[WebView-Referenz](https://developer.android.com/reference/android/webkit/WebView#onPause()),
[Chromium AwContents](https://chromium.googlesource.com/chromium/src/+/main/android_webview/java/src/org/chromium/android_webview/AwContents.java)

Audiofokusverlust ist eine mögliche Android-Ursache. Chromium besitzt dafür
bereits einen eigenen AudioFocusDelegate; ein zusätzlicher konkurrierender
Focus-Owner kann das Ergebnis verändern. Ein allgemeines Plattformverhalten
beweist keinen konkreten Audiofokusverlust in diesem Test.
[Android-Audiofokus](https://developer.android.com/media/optimize/audio-focus),
[Chromium AudioFocusDelegate](https://chromium.googlesource.com/chromium/src/+/refs/heads/main/content/public/android/java/src/org/chromium/content/browser/AudioFocusDelegate.java)

YouTube beschreibt Miniplayer und mobiles PiP für seine eigene App und nennt
je nach Funktion Inhalts-/Hintergrundwiedergabegrenzen. Daraus entsteht keine
Garantie für eine verkleinerte mobile Website in einer fremden WebView. Die
beobachtete seiteninterne Bedingung wird deshalb nicht in eine allgemeine
Premium-Diagnose übersetzt.
[YouTube-Miniplayer](https://support.google.com/youtube/answer/9162927?co=GENIE.Platform%3DAndroid&hl=en),
[YouTube-PiP](https://support.google.com/youtube/answer/7552722?co=GENIE.Platform%3DAndroid&hl=en)

Ein `pause`-Ereignis kann auch aus internen Browserabläufen stammen, etwa wenn
ein Mediaelement aus dem Dokument entfernt wird. Ein JS-Wrapper allein deckt
deshalb nicht sämtliche Pauseursachen ab.
[HTML-Medienstandard](https://html.spec.whatwg.org/multipage/media.html#internal-pause-steps)

Es werden keine Sichtbarkeits-, Hintergrundberechtigungs-, DRM- oder
Zugangssignale verändert. Der Diagnosebericht ist kein Vorschlag, die
beobachtete Bedingung zu umgehen.

## Abgeschlossener Teststand 1.3.11 und weiterhin offene Geräteabnahme

Der öffentliche Browservergleich und die beschriebenen A/B-Befunde sind
durchgeführt. Der abschließende Build des Diagnose-Updates 1.3.11 / Code 25
ist erfolgreich; das lokale Protokoll liegt unter
`validation/pause-1.3.11-build.log`. Die folgenden Prüfungen wurden für diesen
Stand abgeschlossen, statt nur frühere Ergebnisse als Nachweis zu übernehmen:

| Prüfung | Ergebnis |
| --- | --- |
| Vollständiger Release-Build ARM64 / x86-64 | Bestanden |
| JVM-Tests | 270 bestanden: 173 App, 77 Home, 20 Filter; keine Fehler, Fehlschläge oder Skips; unveränderte Home-/Filter-Ergebnisse von Gradle als aktuell übernommen |
| Neue Tests dieses Diagnosesatzes | Zwei zusätzliche Player-Lifecycle-Tests, damit 24 in dieser Suite, und fünf Diagnose-Decoder-Tests bestanden |
| Player-/Diagnose-/Observer-Scripts | 17 Player-, acht Diagnose- und fünf Observer-Fixtures bestanden; die zusätzliche Player-Fixture prüft den echten extrahierten Pausebefehl bei intaktem, fehlendem und werfendem Guard |
| Weitere Regressionen | Native PiP-Quellverträge, zehn modellierte Original-Home-Bridge-Rückkehrzyklen, 120 modellierte Home-Kaltstarts sowie Adblock-JS-/Gecko-/Watch-Ereignisprüfungen bestanden |
| Scope-Prüfung | 46 ursprüngliche Quelldateien gegen dokumentierte Änderungen geprüft; `WATCH_CHROME` exakt unverändert |
| Release-Lint App/Home | Bestanden, keine Fehler |
| Offline-Chromium-Pixel-/Layoutfixture | Echte Pixelprüfung mit negativen Überdeckungskontrollen und zehn Mini-/PiP-/Rückkehrzyklen bestanden |
| Android-Gerät, Android-Emulator und fünfminütige Live-YouTube-Abnahme | Nicht durchgeführt |

Die APKs tragen das ursprüngliche Signierzertifikat. Für die Veröffentlichung
im regulären Updatekanal werden dieselben geprüften Artefakte der vorherigen
Vorabversion verwendet; ein Kanalwechsel ändert weder ihre Prüfsummen noch
die oben beschriebenen Testgrenzen. Angaben zur Verteilung stehen in
[Release 1.3.11](../RELEASE-1.3.11.md).

| APK | Größe | SHA-256 |
| --- | ---: | --- |
| ARM64 | 106595947 Bytes | `7596d56c7ce3dcfb84dda00bd3dcde17e6bc3d7b6102b953fb0a738373a1171f` |
| x86-64 | 111603906 Bytes | `688232282ae284ca357eeebb0a2d3ae0142fca3f8efd85f5326cd3d167d10389` |

Die JVM-/Script-Tests prüfen Controllerzustände, Zuordnung und Begrenzung.
Die Offline-Pixelfixture prüft eine kontrollierte lokale Seite. Beides
bestätigt weder die aktuelle YouTube-Regel für andere Inhalte/Konten noch
Androids PiP-/Audioausgabe. Die kurze öffentliche Browser-Reproduktion ist
kein fünfminütiger Wiedergabe-Dauertest.

Diese zehn Geräteabläufe sind **nicht durchgeführt**:

| Geräteablauf | Stand |
| --- | --- |
| 1. Normale Videowiedergabe mindestens 60 Sekunden ohne Darstellungswechsel | Nicht durchgeführt |
| 2. Zehn Mini-/Vergrößerungswechsel mit laufendem Video und Diagnoseereignissen | Nicht durchgeführt |
| 3. Sichtbares Play-Symbol nach browserseitiger Pause antippen und tatsächlichen App-Befehl prüfen | Nicht durchgeführt |
| 4. Manuell pausieren, Mini/PiP wechseln und unerwartetes Autoplay ausschließen | Nicht durchgeführt |
| 5. Android-System-PiP öffnen, Play/Pause benutzen, vergrößern und schließen | Nicht durchgeführt |
| 6. Hoch-/Querformat und Vollbild wiederholt wechseln, auf View-/Window-/Medienzustände achten | Nicht durchgeführt |
| 7. App vollständig verbergen sowie Bildschirm sperren/entsperren; richtige Pausegründe prüfen | Nicht durchgeführt |
| 8. Andere Medien-App, Audiofokuswechsel und Anruf auf dem Gerät prüfen | Nicht durchgeführt |
| 9. Zur ursprünglichen Gecko-Startseite zurückkehren, mehrere Videos und die übrigen Tabs bedienen | Nicht durchgeführt |
| 10. Werbeschutz, Fehlerpfade und Diagnosebegrenzung prüfen; keine Zugangsdaten in Logs und keine Wiederaufnahme-/Reload-Schleife | Nicht durchgeführt |

Die bestehende Miniplayer-/PiP-Wiedergabe gilt erst dann als praktisch
funktionierend, wenn der betreffende Android-Ablauf tatsächlich geprüft ist.
Die vorliegende Diagnose erklärt den reproduzierten Browser-Pausepfad und
korrigiert einen begrenzten Bedienfehler; sie behauptet keinen fertigen
PiP-Fix.
# Ergänzung vom 09.11.2026 – Medienlautstärke, unveröffentlicht

Die begrenzte `WeiktubePlayback`-Diagnose meldet zusätzlich `VOLUME` bei
`volumechange` und enthält `muted`, `defaultMuted` sowie `volumePercent` (0–100).
Der Decoder akzeptiert ausschließlich das feste Schema mit 17 Feldern; Herkunft,
Dokumentkennung, Sequenz und Rate-Limit bleiben geprüft. Die zusätzlichen Werte
sind rein lesend und werden bei sämtlichen Diagnoseereignissen mitgeführt.
Sie erfassen weder Android-Systemlautstärke noch tatsächliche Lautsprecherausgabe.
Ein Volume-Ereignis identifiziert nicht automatisch den Verursacher.

Das dient zur Trennung von Pausieren, Medienstummschaltung und Lautstärke null.
Die gemeldete wiederholte Tonfreigabe und YouTube-Mini/PiP sind weiterhin nicht
als behoben bestätigt. [Aktueller Testbericht und Geräteabläufe](HISTORY-PIP-AUDIO-REVIEW.md).

