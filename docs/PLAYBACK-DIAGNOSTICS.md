# Wiedergabepause bei Miniplayer und PiP: Diagnose und Grenzen

Dieser Bericht beschreibt einen reproduzierten öffentlichen Browsertest und
die begrenzte Diagnose im vorhandenen Weiktube-Player. **Die browserseitige
Mini-/PiP-Pause ist damit noch nicht behoben.** Es wurde kein Android-Handy-,
Emulator- oder angemeldeter Kontotest durchgeführt. Der Prüfstand ist
unabhängig davon, ob dieselben Diagnose-APKs als Vorabversion oder über den
regulären Updatekanal verteilt werden.

## Belegter Browserbefund

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
APP_PAUSE reason=HOST_STOP generation=4
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

## Teststand und noch offene Geräteabnahme

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
