# 1.2.9: Gecko-Rückkehr ohne künstlichen Seitenwechsel

Version `1.2.9-gecko-return-test`, Versioncode 13, `com.weiktube.app`.

## Warum 1.2.8 den gemeldeten Fehler nicht beseitigte

Die vorherigen Tests prüften die JavaScript-Bridge und ihre Aktivierungszustände,
aber nicht den Start des nativen Gecko-Progress-Moduls. Die Korrekturen dieser
Bridge reichten deshalb nicht aus. Der neue Screenshot zeigt die reale YouTube-
Suchseite, während die native Herkunftsanzeige nur „Seite wird geladen …“ meldet
und die Player-Verbindung dauerhaft auf ihre Wiederaufnahme wartet.

Die konkrete Ursache ist jetzt in der **tatsächlich ausgelieferten Gecko-Version**
nachgewiesen. `assets/omni.ja` im APK enthält:

- `chrome/geckoview/content/geckoview.js`: Der Modul-Setter `enabled` führt bei
  einem Wechsel von `false` nach `true` den jeweiligen `onEnable()`-Callback aus.
- `modules/GeckoViewProgress.sys.mjs`: `onEnable()` ruft `_fireInitialLoad()` auf.
  Diese Methode meldet synthetisch `PageStart("about:blank")`,
  `LocationChange("about:blank")` mit `canGoBack=false` und `PageStop(true)`.

`OriginalHomeReturn.park()` setzte bisher `session.progressDelegate = null`.
`OriginalHomeActivity.configureSession()` setzte nach der Rückkehr wieder einen
Delegate. Damit wurde das Modul aus- und wieder eingeschaltet. Unsere echten
Activity-Callbacks behandeln `PageStart` als neuen Seitenaufruf: Sie trennen den
behaltenen Port, erhöhen die Dokumentnummer und ersetzen die gespeicherte URL.
Das anschließende `LocationChange` bestätigt die falsche `about:blank`-Adresse.

Die sichtbare YouTube-Seite wird dabei nicht neu geladen. Genau deshalb bleibt
sie sichtbar, während ihre Klickverbindung verloren geht. Die Herkunftsanzeige
und die ausbleibende Aktivierung auf dem Screenshot passen zu dieser Ereigniskette.

## Minimale Laufzeitänderung

In `OriginalHomeReturn.kt` bleibt während des Parkens ein **nicht-null,
Activity-freier ProgressDelegate** installiert. Er verarbeitet keine UI-Ereignisse
und hält keine Referenz auf die alte Activity. Der Wechsel des Delegates bei der
Rückkehr ist nun `enabled=true → true`. Der echte Gecko-Modul-Setter tut bei einem
unveränderten Wert nichts: Es entsteht keine künstliche Initialnavigation.

Das ist die einzige geänderte Anweisung im eigentlichen App-Laufzeitcode.
Die Kommentare erläutern den Grund. Das endgültige Schließen einer Sitzung darf
ihren Delegate weiterhin entfernen. Es gibt keine pauschale Ausnahme für
`about:blank`; echte neue Dokumente müssen weiterhin die alte Bridge invalidieren.

**Unverändert:** `OriginalHomeActivity`, `MainActivity`, PlayerController,
WeiktubeUi, JavaScript-Klick-Bridge, Aktivierungs-/Rückkehrregeln, AdBlock,
Google-API-Anbindung, Gecko-Version und die originale YouTube-Seite.

## Regressionstest mit dem ausgelieferten Gecko-Code

`scripts/check-gecko-return-lifecycle.py` liest die oben genannten Methoden aus
dem angegebenen APK und führt die relevanten unveränderten JavaScript-Methoden
in einer isolierten Node-Umgebung aus. Er bildet den Delegate-Wechsel ab und
modelliert die beschriebenen Activity-Seiteneffekte. Der Vertrag mit den
Produktionsdateien wird separat geprüft, damit eine Rückkehr zu `null` nicht
unbemerkt den Test bestehen kann.

Wichtig: Das ist ein Test des gebündelten Modulcodes und des Übergangsvertrags,
**kein ausgeführter Android-/GeckoView-Gerätetest und keine Ausführung der Kotlin-
Activity in Node**. Er deckt die konkrete, bisher fehlende Fehlerquelle ab.

## Tatsächlich ausgeführte Prüfungen

Stand 3. November 2026:

- Build erfolgreich in 1 Minute 12 Sekunden; **137 JVM-Tests bestanden**
  (81 App, 56 home-data), keine Fehler oder übersprungenen Tests.
- Der neue Test wurde erneut gegen die fertige APK 1.2.9 ausgeführt. Mit dem
  alten Park-Übergang entstehen drei synthetische Ereignisse; die Erwartung
  „keine neue Initialnavigation“ scheitert mit **3 statt 0**. Mit der korrigierten
  Produktionszuweisung bestehen zehn Park-/Rückkehrzyklen. Die modellierten
  Seitenadresse, Portverbindung, Dokumentnummer, History und Scrollwerte bleiben
  erhalten. Der echte erste Modulstart erzeugt weiterhin seine Initialereignisse.
  Eine echte nachfolgende Navigation invalidiert das modellierte alte Dokument.
- Bestehende Bridge-Fixtures für zehn simulierte Übergaben, dasselbe Video,
  Such-/Kanallinks, frühe Rückkehr-Tipps, doppelte Ereignisse und Wiederverbindung
  bestanden. Ebenso die neun bestehenden Player-Fixtures, Browser- und
  Watch-Navigationstests sowie der Schutzcheck der 46 ursprünglichen Dateien.
- Lint: 0 Fehler, unverändert 20 App- und 66 home-data-Warnungen.
- APK-Signatur geprüft und identisch zu 1.2.8. Versioncode 13, ARM64/x86-64,
  Android 8+. Die Extension-Dateien im APK stimmen mit dem unveränderten
  Quellcode überein.
- Bytevergleich zur Sicherung: Alle Dateien außerhalb der oben dokumentierten
  Änderungsliste unverändert, darunter Activity, Player, AdBlock, Kontoanbindung
  und Klick-Bridge. 5 Dateien geändert, 2 hinzugefügt, keine gelöscht.

APK: 207.305.767 Bytes; SHA-256
`09f32066d3c6e2315f973e2ed400e286fc0be9cc067c1136202b2dd1eb239512`.

Lokale Nachweise: `validation/gecko-return-before-regression.log`,
`gecko-return-lifecycle-tests.log`, `gecko-return-build-final.log`,
`gecko-return-verification.json` und `gecko-return-change-inventory.json`.

**Gerätetests nicht durchgeführt:** `adb devices -l` meldet am 3. November 2026
kein erreichbares Gerät. Daher wurden mit 1.2.9 keine echten Videostarts,
Android-Zurück-Tastendrücke, Touch-Ereignisse oder Scrollpositionen geprüft.

Die praktische Abnahme bleibt: drei und anschließend zehn Videoaufrufe mit
Zurück dazwischen, dasselbe Video erneut, Scrollen zu einem unteren Video,
Suche/Kanalnavigation nach Rückkehr und schnelle Mehrfachtipps. Dabei müssen
Seitenadresse, Bedienbarkeit und Scrollposition erhalten bleiben; nur der
bestehende Weiktube-Player darf die Videos öffnen.

## Geänderte Dateien und Sicherung

- Laufzeitfix: `home-data/src/main/java/com/weiktube/home/OriginalHomeReturn.kt`.
- Neuer Regressionstest: `scripts/check-gecko-return-lifecycle.py`.
- Version/Veröffentlichung: `app/build.gradle.kts`, `scripts/package-source.py`,
  `scripts/publish-home-test.ps1` (einschließlich expliziter UTF-8-Kodierung der
  Releasebeschreibung).
- Dokumentation: `README.md` und dieser Bericht.

Vor Änderungen wurden alle 110 portablen Quelldateien, die APK 1.2.8 und ihr
Quellcodearchiv gesichert. Isolierter lokaler Backup-Branch
`backup/gecko-progress-before-1.2.9`, Commit
`47f1c0ef73bcfa14c44251211b0c8fe52cd51da8`. Keine Projektdateien gelöscht und
keine fremden Änderungen zurückgesetzt.

Die Ursache wurde anhand der lokalen Gecko-159-Java-Quellen und der genannten
Originalmodule im APK ermittelt. Die Herkunft ist Mozillas gebündelter Runtime-
Code, nicht eine angenommene Eigenschaft einer anderen Gecko-Version.
