> Historischer Prüfbericht vor dem nativen Viewport-Fix. Aktueller Release und dessen Testgrenzen: [1.3.14](../RELEASE-1.3.14.md).

# Verlauf, Mini/PiP und Ton – lokaler Prüfstand vom 09.11.2026

Ausgangspunkt: GitHub `main`, Commit `bf743c6f1a4ae41bb6b53daff64e227d1125da8b`,
Version 1.3.13 / Code 27. Sicherung vor Änderungen:
`validation/history-pip-audio/before.zip`. Keine Veröffentlichung, kein Git-Push,
keine Änderung des Updatekanals. Die lokalen APKs behalten vorläufig Version 1.3.13/27;
sie sind keine neue veröffentlichte Version.

## Aktueller Nachtrag: echte OnePlus-Tests und Tonkorrektur

Die folgenden Ergebnisse ersetzen die älteren Aussagen zur fehlenden
Geräteverbindung und zur ungeklärten Tonaufforderung im ursprünglichen Prüfstand.
Gerät: OnePlus CPH2769, WLAN-ADB; Bedienung mit android-use, Accessibility-Baum
und nativen Aktionen. Keine Screenshot-Schleife, kein axdroid-Gerätetest.

### Tonstart: implementiert und praktisch bestätigt

Die App erlaubte Autoplay bereits, forderte beim expliziten Öffnen jedoch keinen
hörbaren Start an. Die YouTube-Seite startete mit `muted=true` und eigener
„TON EINSCHALTEN“-Schaltfläche. `PlayerController` merkt jetzt eine einmalige
Tonanforderung für eine neue, bewusst ausgewählte Quelle vor. Erst wenn der
vorhandene Playback-/Werbeschutz den Start zulässt, wird die Anforderung an
denselben Medienknoten übergeben und beim ersten `playing` verbraucht. Der
vorhandene Player wird, falls verfügbar, über `unMute()` informiert; das Video
wird entstummt. Keine Lautstärke-Erhöhung, neuen Berechtigungen, simulierten
Klicks oder wiederholten Unmute-/Play-Schleifen. Pause verwirft die Anforderung.
Moduswechsel und Renderer-Recovery erzeugen keine neue Tonanforderung.

Auf dem OnePlus zeigt der neue YouTube-Start `PLAYING` und `VOLUME` mit
`muted=false`, `volumePercent=100`. Der Nutzer bestätigte ausdrücklich:
„hinweiß eweg ton instant da“. Beim folgenden Start des öffentlichen Videos
„Big Buck Bunny 60fps 4K“ fehlte die Ton-Schaltfläche ebenfalls.
Die frühere, zurückgenommene Rückmeldung ist hierfür **nicht** der Nachweis.

### Mini/PiP: tatsächliche YouTube-Blockade auf dem Gerät belegt

In einer nur lokal installierten Debug-Variante wurde der reale JavaScript-
Aufruf von `HTMLMediaElement.pause()` angehalten und der Aufrufpfad untersucht.
Der temporäre Diagnosewrapper ruft die Originalfunktion unverändert auf und
wird anschließend entfernt. Er verhindert keine Pause. Gespeichert wurden nur
statische Codeausschnitte und wenige Statuswerte, keine Cookies/Tokens/Antworten.

In **beiden** Modi führt der YouTube-Aufrufpfad über `pauseVideo()` zur Pause.
Der entscheidende Code prüft `this.visibility.Zj()` und
`!this.getVideoData().backgroundable`. Im Android-PiP waren nachweislich:

- `backgroundable=false`, `document.hidden=false`
- Weiktube `mini=0`, `pip=1`
- `Zj(){return this.pictureInPicture}`

Belege: lokale `validation/history-pip-audio/device-pause-stack.json` und
`device-pause-stack-pip.json`; begleitend gefilterte Playerlogs. Dies ist jetzt
ein **Gerätebefund**, nicht bloß der frühere Desktop-Chromium-Vergleich.
Die App-WebView bleibt sichtbar und verbunden; die beobachtete Pause wird
vom YouTube-Player ausgelöst. Seine Freigabeprüfung wurde entsprechend dem
Auftrag nicht manipuliert. **Beliebige YouTube-Videos funktionieren daher
weiterhin nicht zuverlässig im Mini/PiP; der Gesamtauftrag ist nicht vollständig
gelöst.** Eine weitere pauschale Änderung an `onResume()` würde diesen Pfad
nicht korrigieren.

Gegenprobe im selben Android-Player: direkte W3C-HTTPS-MP4 im internen Mini,
nach rund 30 Sekunden `paused=false`, `ready=4`, `muted=false`, laufende
Wiedergabeposition, `connected=true`, `hidden=false`. Nutzer bestätigt ebenfalls,
dass der kleine Player mit der Vergleichsquelle funktioniert, aber mit zufälligen
YouTube-Startseitenvideos nicht. Das ist kein Erfolg für YouTube-PiP.

### Tatsächliche Validierung dieser Änderung

- 235 App-JVM-Tests: null Fehler; 19 Player-Script-Fixtures bestanden, darunter
  einmaliger Tonstart sowie Erhalt späterer Nutzer-Stummschaltung und Pause.
- Release- und Debug-Build erfolgreich. Erste Release-Paketierung scheiterte;
  Wiederholung mit Stacktrace erfolgreich. Kein Testfehler verschwiegen.
- Scope-Prüfung: 46 ursprüngliche Quelldateien mit exakt dokumentierten Ausnahmen.
- Chromium-Medienkontrolle erneut bestanden: drei Dokumente mit jeweils zehn
  laufenden und zehn pausierten/stummen Präsentationswechseln; keine Aussage
  über Android-PiP oder YouTube. Beim ersten Aufruf fehlte der Modulpfad;
  mit dem vorhandenen Playwright-Modul bestand die Prüfung.
- Gerät: YouTube-Tonstart, interner Mini, Play nach Pause, Vergrößern,
  Android-PiP und reguläre Rückkehr; direkte HTTPS-Mini-Gegenprobe.
- **Nicht erfolgreich durchgeführt:** zehn zusammenhängende Geräte-Rundläufe,
  fünfminütige Dauertests beider Modi, Nachweis hörbaren Tons in Android-PiP,
  vollständige Tests mit allen YouTube-Inhalten. Abgebrochene Batches zählen
  nicht als erfolgreiche Durchläufe.

Neue produktive Änderung ausschließlich in `PlayerController.kt`; zusätzliche
Fixtures in `scripts/check-player-scripts.cjs`, exakter Änderungsnachweis in
`scripts/history-audio-reviewed-changes.json`. Keine Geometrie-, Konto-,
AdBlock-, Gecko- oder PiP-Lifecycle-Änderung. Keine Veröffentlichung.
Abschluss: normaler, nicht debuggbarer Release-Testbuild wieder erfolgreich
installiert, temporäre ADB-Portweiterleitung entfernt, ursprüngliche
Bildschirmzeit (30 Sekunden) wiederhergestellt und android-use-Lease freigegeben.

## Ursprünglicher Prüfstand vor den Gerätetests (historisch)

### Lokaler Verlauf: implementiert

`AccountScreen` hatte keinen Verlaufseintrag. Die vorhandene lokale DataStore-Liste
wird jetzt im Tab **Du** als **Kürzlich angesehen** dargestellt: zwölf Vorschauen im
horizontalen Karussell, Titel, Thumbnail und **Alle anzeigen** für den vollständigen
Verlauf mit maximal 100 Einträgen. Die vorhandene Vorschaubildkomponente wird geteilt.
Die Auswahl verwendet `MainActivity.openVideo()` und den bestehenden Player.
Google-Freigabe ist dafür nicht erforderlich. Leere und pausierte Aufzeichnung
werden erklärt. Es gibt keine zweite Datenbank und keinen Google-Verlaufabruf.

Beim Prüfen des bisherigen Aufzeichnungswegs wurden zusätzliche Codefehler gefunden:

- Aufzeichnung erfolgte erst beim Erkennen eines neuen Titels im Player-Poll.
  Erneute Auswahl desselben geladenen Videos konnte deshalb ohne neuen Eintrag bzw.
  ohne Aktualisierung der Reihenfolge bleiben.
- Titelupdates verwendeten dieselbe Einfügefunktion wie eine Videoauswahl.
  Ein spät eintreffender Titel konnte nach einer Löschung wieder einen Eintrag anlegen.
- Der Löschbutton in der vollständigen Verlaufsansicht löschte sofort.

Jetzt zeichnet eine explizite gültige Auswahl den Öffnungsvorgang auf. Titelupdates
ändern nur vorhandene Einträge, weder deren Zeitpunkt noch Reihenfolge. Leere Titel
bei erneuter Auswahl erhalten den bekannten Titel. Löschen erfordert Bestätigung;
die Vorschau aktualisiert sich über denselben bestehenden Preferences-Flow.
Die Einstellung `saveHistory` wird weiterhin innerhalb des DataStore-Schreibvorgangs
geprüft. Prozess-Recovery wird nicht als explizite Auswahl aufgezeichnet.

### Mini/PiP: kein neuer nachgewiesener App-Pausefehler

Geprüft wurden die bestehenden MainActivity-Lifecycle-Aufrufe, `playerMayRun`,
`pauseView`/`pauseFor`, Compose-Playback-Gates, WebView-Anbindung und die nativen
PiP-Aktionen. `onPause` hält die Player-WebView nicht an; `onStop` pausiert einen
nicht mehr sichtbaren Host. Verkleinerung ändert die Präsentation derselben View.
Die nativen Aktionen senden explizites Play bzw. Pause, keinen Toggle.
Es wurde kein neuer App-Pausefehler für diese Übergänge reproduziert und deshalb
kein spekulativer Lifecycle-Fix eingebaut. Audiofokusverlust auf dem Handy ist
ohne Gerätelog weder belegt noch ausgeschlossen; es wird kein zweiter Fokusbesitzer
und keine zusätzliche Android-Berechtigung eingeführt.

Die neuen Controller- und Chromium-Kontrollen bestehen für kontrollierte Medien.
Das ist **kein Nachweis zuverlässiger YouTube-Wiedergabe in Android-PiP**.
Die dokumentierte YouTube-interne Pause bei Verkleinerung aus 1.3.11 bleibt offen;
der damalige öffentliche Browsertest wurde in diesem Auftrag nicht wiederholt.
Siehe [bestehende Befunde](PLAYBACK-DIAGNOSTICS.md). Keine Play-Schleife, keine
gefälschte Sichtbarkeit, keine Änderung an Premium-/Zugangsprüfungen.

### Ton: Diagnose verbessert, gemeldete Tonfreigabe nicht als behoben bestätigt

`mediaPlaybackRequiresUserGesture = false` war bereits gesetzt. Die direkte
HTML5-Vorlage setzt weder `muted` noch `defaultMuted`. Die Präsentationsskripte
ändern Lautstärke und Stummschaltung nicht. Der Werbeschutz mutet erkannte Werbung
gezielt und stellt den zuvor gespeicherten Zustand wieder her; er bleibt unverändert.
Eine unbeabsichtigte Weiktube-Stummschaltung beim normalen Start wurde nicht
reproduziert. Ob der Nutzer eine YouTube-Tonfreigabe, eine gespeicherte Webseiten-
Stummschaltung oder eine Browserregel sieht, ist weiterhin offen.

Die bisherige Playback-Diagnose konnte diese Zustände nicht unterscheiden. Sie
meldet jetzt zusätzlich `VOLUME`-Ereignisse sowie strikt validierte Felder `muted`,
`defaultMuted` und `volumePercent` (0–100). Ein Script-Gegentest zeigte vor der
Erweiterung fehlende Felder; danach besteht er. Das belegt die Diagnoselücke,
**nicht** die Ursache des Tonfehlers. `volumePercent` ist die Lautstärke des
Medienelements, nicht die Android-Systemlautstärke oder ein Nachweis hörbaren Tons.
Ein `VOLUME`-Ereignis allein unterscheidet ebenfalls nicht Nutzeraktion und Website.
Die bisherige Begrenzung, Herkunfts-/Tokenprüfung und feste Logstruktur bleiben
erhalten. Keine privaten URLs, Tokens, Cookies oder Fehlermeldungstexte werden geloggt.

## Geänderte Dateien und Funktionen

| Datei | Änderung |
| --- | --- |
| `app/.../AccountUi.kt` | Zusätzlicher Slot `recentHistory` unabhängig von Kontofreigabe, nicht in der Aboliste |
| `app/.../RecentHistoryUi.kt` | Neues `RecentHistorySection` und `HistoryDeleteDialog` |
| `app/.../PersonalHomeUi.kt` | Bestehendes `HistoryPreview` intern wiederverwendbar gemacht; Darstellung unverändert |
| `app/.../WeiktubeUi.kt` | Du-Slot mit bestehendem Preferences-Flow, `openVideo` und Verlaufstab verbinden; Löschbestätigung in `HistoryScreen` |
| `app/.../WeiktubeModel.kt` | `HistoryCodec.updateTitle` / `updateHistoryTitle`, ohne Neueinfügen oder Umordnung |
| `app/.../PlayerController.kt` | Auswahl aufzeichnen, Poll-Titelupdates getrennt behandeln; keine Änderung an Wiedergabesteuerung oder Präsentation |
| `app/.../PlayerPlaybackDiagnostics.kt` | Nur lesende Volumen-/Mute-Diagnose und strenge Decoderprüfung |
| `app/build.gradle.kts` | Compose-UI-Testbibliothek, Testmanifest nur für Debug und Android-Ressourcen für JVM-Tests; Release-Abhängigkeiten unverändert |
| `HistoryCodecTest`, `HistoryStoreTest`, `RecentHistoryUiTest`, `PlayerModeContinuityTest`, `PlayerPlaybackDiagnosticsTest` | Daten-, UI-, Controller- und Diagnoseprüfungen |
| `scripts/check-player-audio.cjs`, `check-playback-diagnostics.cjs` | Kontrollierte Chromium-Medienprüfung und Diagnose-Scriptprüfung |
| `scripts/history-audio-reviewed-changes.json`, `check-home-scope.py` | Exakter nachvollziehbarer Änderungsabgleich gegen vorherigen Stand |

`MainActivity`, `NativePipController`, Playergeometrie, AdBlock, Gecko, Anmeldung,
Signierkonfiguration und Updatecode wurden nicht verändert. Der Scope-Test prüft
weiterhin 46 ursprüngliche Dateien nach Rücknahme der explizit dokumentierten Diffs.

## Tatsächlich ausgeführte Tests

| Prüfung | Ergebnis / Aussagegrenze |
| --- | --- |
| Gradle: App/Home/Filter-JVM-Tests, `assembleDebug`, `lintDebug`, `:app:assembleRelease` mit ABI-Splits | **BUILD SUCCESSFUL**; Log `validation/history-pip-audio/full-build.log` |
| JVM | **332 bestanden**, 235 App + 77 Home + 20 Filter; keine Fehler/Skips. Unveränderte Home-/Filter-Tasks waren UP-TO-DATE |
| Neue Regressionen | Zehn zusätzliche Fälle: zwei Codec-, ein DataStore-, fünf Compose-UI- und zwei Player-Modusfälle |
| DataStore | Tatsächliche Speicherung, deaktivierte Aufzeichnung, Reihenfolge, neue ViewModel-Instanz, Löschen und verspäteter Titel geprüft. Keine echte Android-Prozessbeendigung |
| Compose | Tatsächliche Semantik/Klicks: Video-Callback, Alle anzeigen, leer/pausiert, zwölfte Karte per Scroll, Vorschau nach State-Löschung, Bestätigen/Abbrechen. Kein Handy-Pixeltest |
| Player-Modus | Je YouTube-/HTTPS-Controllerfixture zehn normale/Mini/PiP-Präsentationswechsel mit laufendem Zustand, zehn pausierte Wechsel, identische View/Position und keine zusätzlichen Lade-/Pause-/Startbefehle. Kein Chromium in Robolectric |
| Chromium-Medien | Drei lokale Dokumente, jeweils zehn laufende Mini/PiP-Präsentations-/Normalzyklen plus zehn pausierte/stumme Zyklen. Echte Canvas-Videospur und synthetische Audiospur bleiben live; AudioContext läuft. Audio-Testquelle hat absichtlich Gain 0: **kein hörbarer Ton nachgewiesen** |
| Chromium-Geometrie | Vorhandene Pixelfixture inklusive Negativkontrollen und zehn Mini/PiP/Rückkehrzyklen bestanden |
| Scripts | Player 17, Diagnose 9 und Observer 5 Fixtures; Browser-, Adblock-, Watch-, Home-Bridge-, Kaltstart-, Navigation-, Gecko- und native PiP-Prüfungen bestanden |
| Lint | Keine Fehler, 37 vorhandene Warnungen |
| Scope | 46 ursprüngliche Dateien gegen exakte freigelegte Änderungen geprüft, bestanden |
| Echtes Android, hörbarer Ton, echte System-PiP-Ausgabe, Live-YouTube, fünf Minuten je Modus | **NICHT DURCHGEFÜHRT** |

Die ersten neuen Testläufe scheiterten an Testumgebung/Fixture (Compose-Testressourcen,
direktem internem ViewModelStore-Aufruf), nicht an einem belegten Gerätefehler. Diese
Fixtureprobleme wurden korrigiert; das oben genannte Ergebnis ist der abschließende Lauf.

## Die ausdrücklich gewünschten Testwerkzeuge

- [android-use](https://github.com/leeguooooo/android-use), Windows v0.3.0 lokal unter
  `validation/tools`, Download gegen GitHubs SHA-256 geprüft.
  `android-use doctor` und `adb devices -l`: **keine Geräte**. Kein Bildschirmzugriff
  oder Gerätetest möglich. Keine Begleit-App installiert oder Dienste aktiviert.
- [axdroid aus axuse](https://github.com/ctoldigital/axuse/tree/main/phone-use/android),
  Commit `c7f2fa4c5ef04fc899d0bdf91cf90f3ade8d8f52`, isolierter Checkout und Python-venv.
  `axdroid status --url http://127.0.0.1:8360`: **BRIDGE_UNREACHABLE**, Verbindung
  abgelehnt. Upstream-Offline-/Simulatortests mit pytest: **48 bestanden, 1 übersprungen**
  (optionale Bildanalyse-Abhängigkeit fehlt). Kein Weiktube-Gerätebeleg.
- Beide bleiben externe QA-Werkzeuge. Keine neue App-Abhängigkeit, kein globaler
  MCP-Umbau, kein Cloud-Relay und keine Weitergabe von Sitzungsdaten.

## Geräteabnahme – noch offen

1. **Du**, ohne Google-Freigabe: Video A, B und erneut A öffnen. A muss zuerst stehen;
   Thumbnail und Titel antippen, vollständigen Verlauf über Alle anzeigen öffnen.
   App neu starten und Liste vergleichen. Bei deaktiviertem Verlauf C öffnen:
   C darf nicht dazukommen. Löschen erst abbrechen, dann bestätigen; Vorschau muss leer werden.
2. Direkte erlaubte HTTPS-Mediendatei mit Audio öffnen: mindestens fünf Minuten
   normale Wiedergabe, interner Mini und Android-PiP; jeweils zehn Wechsel.
   Pause vor Wechsel muss Pause bleiben. Play/Pause über PiP-Aktion prüfen.
3. Dieselben Schritte mit YouTube wiederholen. Bei sofortiger Pause die festen
   `WeiktubePlayer`-/`WeiktubePlayback`-/`WeiktubePiP`-Ereignisse zeitlich vergleichen:
   `APP_PAUSE`, Gate, `PAUSE_CALL`/`PAUSE`, danach `VOLUME`-Zustände.
   Fehlende App-Pause allein beweist noch keine Plattformbeschränkung.
4. Ton: erstes, zweites, erneut erstes Video; Mini, PiP und zurück. Absichtlich
   stummschalten und prüfen, dass es stumm bleibt. Falls „Ton einschalten“ erscheint,
   genauen Button/Quelle und Diagnosezustand festhalten. Keine Kontodaten mitsenden.
5. Rotation, Sperren/Entsperren, Appwechsel, neues Video, Gecko-Startseite und
   Adblock prüfen. Android-Systemlautstärke/Audioausgabe getrennt vom Web-Mute prüfen.

## Changelog-Entwurf für die nächste Version

- Neu: Lokales „Kürzlich angesehen“-Karussell im Du-Tab mit vollständigem Verlauf.
- Behoben: Erneute Videoauswahl aktualisiert den Verlauf; verspätete Metadaten
  erzeugen nach dem Löschen keine Einträge mehr. Löschen mit Bestätigung.
- Diagnose: Lautstärke- und Stummschaltungszustand bei Wiedergabeereignissen.
- Tests: Compose-Verlaufsbedienung, DataStore, wiederholte Mini/PiP-Präsentation
  und Erhalt bewusster Pause/Stummschaltung.
- Offen: YouTube-seitige Mini/PiP-Pause und wiederholte Tonfreigabe auf echten
  Geräten weiterhin nicht als behoben bestätigt. Keine Veröffentlichung erfolgt.

### Korrektur der laufenden Gerätetests (09.11.2026)

Der Nutzer hat seine vorherige Audio-Rückmeldung ausdrücklich korrigiert: Die Aufforderung „Ton einschalten“ erscheint weiterhin. Die Aussage „Ton ist hörbar“ ist kein Nachweis dafür, dass die Aufforderung entfällt. Der Tonfehler bleibt offen.

Der geplante Batch mit zehn Mini-Rundläufen wurde nach genau einer angewendeten Aktion beim Warten auf „Pausieren“ abgebrochen (condition_timeout; completed=1, applied_actions=1). Zehn erfolgreiche Durchläufe sind NICHT nachgewiesen. Die aufgezeichnete Player-Generation war inzwischen 11 statt der ursprünglichen direkten Testquelle (Generation 5); dieser Batch darf nicht als Test der ursprünglichen MP4-Quelle gewertet werden. Die Logs zeigen muted=true sowie PAUSE_CALL/JS_UNATTRIBUTED nach PRESENTATION_MINI. Das weist noch keine konkrete aufrufende Komponente nach.

Aktueller Status: YouTube-Tonaufforderung und Mini-/PiP-Pause sind nicht behoben. Kein erfolgreicher Dauertest behauptet.
