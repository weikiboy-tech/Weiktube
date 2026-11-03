# Originale YouTube-Startseite direkt im Startseite-Tab

Version `1.3.0-home-tab-test`, Versioncode 14, `com.weiktube.app`.
Ausgangspunkt ist die vom Nutzer als funktionierend bestätigte Version 1.2.9.

## Verhalten und bewusst begrenzte Änderungen

Bei einem Kaltstart oder erneutem Öffnen über das Launcher-Symbol wird **Startseite**
gewählt. Im Inhaltsbereich steht unmittelbar die bisherige originale YouTube-Seite
über Gecko. Der frühere Ersatzfeed aus Abos/Verlauf wird dort nicht mehr gerendert;
seine Quelldateien und die anderen Tabs bleiben erhalten.

Die bisherige Gecko-Implementierung wurde aus `OriginalHomeActivity` in
`OriginalHomeController` verschoben. Die Activity ist ein kleiner Kompatibilitäts-
Host für denselben Controller. Es gibt keine zweite Feed-, Konto-, Parser- oder
Player-Implementierung. MainActivity besitzt genau einen Controller. Die normale
Startseite und der beibehaltene Button unter **Du** verwenden diesen Controller;
MainActivity startet dafür keine zusätzliche Activity.

Die stabile AndroidView liegt innerhalb des vorhandenen begrenzten Inhaltsbereichs,
oberhalb der unveränderten nativen Navigation. Sie wird bei anderen Tabs, einem
großen Player, Vollbild, PiP oder einer pausierten MainActivity unsichtbar und
inaktiv. Die Anmeldung und die lebende Sitzung bleiben dabei erhalten. Die
YouTube-Seite liefert auf Home ihren eigenen Kopfbereich; der Weiktube-Header
bleibt auf den anderen Tabs bestehen. Die alten Testleisten und die permanente
Gecko-Herkunftszeile sind in Home ausgeblendet. Echte Ladefehler behalten einen
Hinweis und einen erneuten Versuch.

Die Website selbst wird nicht durch neue CSS-/DOM-Eingriffe verändert. Ihre eigene
Navigation kann deshalb innerhalb ihres Inhaltsbereichs weiterhin sichtbar sein.
Sie überdeckt die native Weiktube-Navigationsleiste nicht. Ein sichtbarer nativer
Miniplayer verwendet weiterhin die bisherige Darstellung und Größe.

## Erhalt der funktionierenden Sitzung und Übergabe

- Tabwechsel und App-Hintergrund suspendieren Fokus, Autofill, Medien und Bridge-
  Freigabe. Sie schließen weder Session noch Erweiterung und geben deren Besitz
  nicht frei. Ein erneuter Home-Aufruf aus einem anderen Tab lädt nicht neu.
- Ein erneuter ausdrücklicher Tipp auf den bereits ausgewählten Home-Tab darf zur
  Startadresse navigieren. Das ist vom bloßen Tab-Rückweg getrennt.
- Die Initialisierung darf auch nach schnellem Tabwechsel zu Ende laufen. Die
  erste Website-Navigation wartet auf einen sichtbaren Home-Tab. Eine bereits
  angeforderte normale YouTube-Seite darf im Hintergrund fertig laden; Videos,
  neue Anmeldungen, OAuth- und fremde Ziele werden dadurch nicht freigegeben.
- Die ursprüngliche Video-ID-Erkennung und JavaScript-Bridge bleiben unverändert.
  Die native Auswahl benutzt weiterhin `OriginalHomeReturn.park`,
  `acceptPlayerResult` und MainActivitys bestehende `openVideo()`-Funktion.
- Der Controller nimmt die geparkte Sitzung bei Rückkehr wieder auf. Er ersetzt
  nur den früheren ActivityResult-Transport durch einen direkten Callback mit
  derselben einmalig geprüften Rückkehr-ID. Es entsteht kein weiterer Player.
- Der bestätigte Fix aus 1.2.9 bleibt unverändert: Beim Parken bleibt der
  ProgressDelegate nicht-null. Kein künstliches `about:blank` beim Wiederaufnehmen.
- Die vorhandene Shields-Prüfseite bleibt über einen ausdrücklich gewählten
  Zusatzbereich der bisherigen WebView erreichbar. Die alte WebView lädt beim
  Start keine zweite generische YouTube-Homepage im Hintergrund.
- Der Button **YouTube-Startseite öffnen** unter **Du** bleibt bis zur echten
  Geräteabnahme erhalten. Er wählt dieselbe Home-Instanz, keinen zweiten Browser.

Eine neue Activity nach Prozessverlust kann die alte lebende DOM-/Scrollposition
nicht übernehmen. Sie öffnet Home im unveränderten persistenten Gecko-Profil;
gültige Website-Cookies werden weiterverwendet. Ein gespeicherter Player wird dabei
ohne Autoplay minimiert wiederhergestellt. Normales Hintergrund-/Vordergrundwechseln
im weiterlebenden Activity-Prozess erhält die vorhandene Sitzung.

## Geänderte Dateien

- `home-data/src/main/java/com/weiktube/home/OriginalHomeController.kt` —
  ausgelagerte bestehende Gecko-Ansicht, expliziter Sichtbarkeits-Lifecycle und
  Aufnahme derselben Player-Rückkehr innerhalb eines weiterlebenden Hosts.
- `home-data/src/main/java/com/weiktube/home/OriginalHomeActivity.kt` — kleiner
  Kompatibilitäts-Host für denselben Controller.
- `home-data/src/main/java/com/weiktube/home/OriginalHomeTabBackground.kt` und
  `home-data/src/test/java/com/weiktube/home/OriginalHomeTabBackgroundTest.kt` —
  begrenzte Regeln/Tests für bereits laufendes YouTube-Laden bei verborgenem Tab.
- `app/src/main/java/com/weiktube/app/MainActivity.kt` — ein Controller, Sichtbarkeit,
  direkter geprüfter Player-Callback, Home-/Back-/Tab-Anbindung, Shields-Prüfbereich.
- `app/src/main/java/com/weiktube/app/WeiktubeUi.kt` — stabile AndroidView im Home-
  Inhalt, Home ohne Ersatzfeed, nativer Header auf anderen Tabs, bestehender Player
  und untere Navigation bleiben an ihren bisherigen Stellen.
- `scripts/check-gecko-return-lifecycle.py` — der vorhandene Gecko-Regressionstest
  prüft jetzt den ausgelagerten Controller statt der alten Activity-Datei.
- `scripts/check-home-scope.py`, `scripts/home-tab-reviewed-changes.json` — exakt
  geprüfte Host-/UI-Änderungen werden vor dem bisherigen Originaldateien-Schutzcheck
  rückübersetzt. Die ursprünglichen Baseline-Hashes werden nicht ersetzt.
- Version/Verpackung/Veröffentlichung: `app/build.gradle.kts`,
  `scripts/package-source.py`, `scripts/publish-home-test.ps1`, `README.md`, dieser
  Bericht.

PlayerController, AdBlock-Engine, Google-API-Anbindung, HomeEngine/Profil,
OriginalHomeReturn, Video-URL-Policy und Klick-Bridge bleiben unverändert.

## Tatsächlich ausgeführte Prüfungen

Abschließender Build am 3. November 2026 erfolgreich:

- `:app:exportHomeConnectionApk`, `:app:testDebugUnitTest`,
  `:home-data:testDebugUnitTest`, `:app:lintDebug`, `:home-data:lintDebug`.
- **141 JVM-Tests bestanden**: 81 in app und 60 in home-data, ohne Fehler,
  fehlgeschlagene oder übersprungene Tests. Darunter vier neue Tests zur
  begrenzten Hintergrundnavigation des Home-Tabs.
- Android Lint: keine Fehler/Fatal-Meldungen; 20 Warnungen in app und 38 in
  home-data. Die Warnungen wurden nicht durch sachfremde Änderungen bereinigt.
- `check-home-scope.py`: 46 geschützte Originaldateien bestehen den Hash-/
  Änderungsumfangscheck. Abweichungen sind nur die explizit geprüften Home-Hooks.
- `check-gecko-return-lifecycle.py`: Der tatsächliche gebündelte Gecko-Modulcode
  reproduziert den früheren Null-Delegate-Fehler. Zehn simulierte Rückkehrzyklen
  mit dem erhaltenen nicht-null Delegate bestehen ohne künstliches about:blank.
  Activity-/Port-Auswirkungen sind modelliert, keine Android-Ausführung.
- `check-original-home-navigation.cjs`: zehn simulierte Rückkehrzyklen,
  Wiederöffnung desselben Videos, Shorts/Suche/Kanal, doppelte Klicks,
  Bridge-Wiederverbindung und Listener-Idempotenz bestanden.
- `check-player-scripts.cjs`: neun Fixtures bestanden;
  `check-browser-scripts.cjs` und `check-watch-navigation.cjs` ebenfalls bestanden.
- APK-Signatur erfolgreich verifiziert, unverändert zur vorherigen Testversion.
  Paket `com.weiktube.app`, Versioncode 14, Android 8 oder neuer.
  Die vier gepackten Bridge-/Manifest-Dateien entsprechen den geprüften Quellen.

APK: `Weiktube-Home-Test.apk`, 207.310.931 Bytes.
SHA-256:
`4bf79deafba44a1c0564c1335a41cf6d40e500f15c57adaefd05087b59396fe1`.

**Gerätetests nicht durchgeführt:** Am 3. November 2026 zeigt `adb devices -l`
kein erreichbares Android-Gerät. Quellprüfung, JVM-Tests und isolierte Gecko-/
JavaScript-Fixtures ersetzen weder einen echten App-Start noch Touch-/Wiedergabe-
oder Scrolltests auf Android. Die Nutzerbestätigung für 1.2.9 gilt als Ausgangspunkt,
nicht als Geräteabnahme dieser neuen Einbindung.

| Verbindlicher Ablauf | Android-Gerätetest für 1.3.0 |
|---|---|
| Start → originale Seite direkt im Home-Tab | Nicht durchgeführt |
| Video → vorhandener Weiktube-Player | Nicht durchgeführt |
| Zurück → Seite weiterhin anklickbar | Nicht durchgeführt |
| Zehn verschiedene Videos nacheinander mit Zurück | Nicht durchgeführt |
| Scrollen → anderer Tab → gleiche Scrollposition | Nicht durchgeführt |
| App verlassen/öffnen → Home und gültige Anmeldung | Nicht durchgeführt |
| Shorts, Adblock, Abos und Du | Nicht durchgeführt |
| Laufzeitprüfung auf zusätzliche Sessions/Player | Nicht durchgeführt |

Die normale Tab-Rückkehr und zehn reale Player-Rundläufe müssen auf einem Gerät
abgenommen werden, bevor der alte Du-Einstieg entfernt wird. Auch die native
Suchaktion aus anderen Tabs (Weiterleitung zur echten mobilen YouTube-Suchseite)
und die genaue Tastatur-/Layoutdarstellung sind noch nicht auf Android geprüft.

## Sicherung

Vor Änderungen wurden alle 112 portablen Quelldateien, die bestätigte APK 1.2.9
und das Quellcodearchiv gesichert. Isolierter lokaler Backup-Branch
`backup/home-tab-before-1.3.0`, Commit
`fac29d4dcf148238a499a6ff99b3bca64884164f`.
Das gemischte Workspace-Repository und fremde Änderungen bleiben unberührt.
