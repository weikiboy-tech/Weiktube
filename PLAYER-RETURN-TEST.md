# Gecko bleibt nach der Player-Rückkehr bedienbar

Version 1.2.8-player-return-test, Android-Versioncode 12, `com.weiktube.app`.
Gezielter Anschlussfix an 1.2.7. Kein neuer Player, Feed oder Anmeldeweg.

## Ausgangspunkt und nachgewiesene Fehlerursache

Der Nutzer hat für 1.2.7 bestätigt: Originalseite → vorhandener Player →
Android-Zurück funktioniert, danach lassen sich Videos erst nach erneutem Öffnen
der Startseite wieder auswählen. Das ist eine Nutzerbeobachtung, kein eigener
Gerätetest dieser Änderung.

Die Codeprüfung findet einen unvollständigen Lifecycle der Klick-Bridge:

1. `disable` prüfte nur die Dokument-ID. Eine verspätete Deaktivierung der alten
   Aktivierung konnte deshalb eine bereits neue Aktivierung abschalten.
2. `pagehide` deaktivierte die Bridge. Für `pageshow` beziehungsweise die Rückkehr
   in den sichtbaren Zustand gab es keinen Wiederaufnahme-Handshake.
3. Ein getrennter Native-Messaging-Port blieb endgültig getrennt. Seine vorhandenen
   Klick-Listener blockierten Videonavigation weiterhin, konnten aber nichts mehr
   an Android übergeben. Ein neues Dokument war der einzige Wiederherstellungsweg.
4. Android schickte seine Aktivierung einmalig. Es gab keine begrenzte Wiederholung
   bei ausbleibender Bestätigung und keinen gegen doppelte Bereitschaftsmeldungen
   abgesicherten Aktivierungszustand.

Die ersten beiden Sperrsequenzen wurden mit dem unveränderten 1.2.7-Script in der
Node-Fixture reproduziert. **Welche konkrete Ereignisreihenfolge auf dem OnePlus
auftrat, ist ohne Laufzeitprotokoll nicht belegt.** Ein normal und vollständig
zugestellter alter `disable`→neuer `enable`-Ablauf bestand bereits in 1.2.7; genau
deshalb reichte der bisherige Test nicht aus.

Betroffen sind konkret `enabled` und der absichtliche Startschutz `handingOff`:
Sie blieben ohne gültige erneute Aktivierung gesperrt. `openingPlayer` in der neuen
Activity wird bereits korrekt initialisiert; es wird nicht pauschal mit anderen
Zuständen zurückgesetzt.

Die Prüfung der verwendeten Gecko-159-Quellen bestätigt: `releaseSession()`
schließt die Sitzung nicht; ein Wechsel von Message-/Port-Delegates trennt einen
bestehenden Port nicht automatisch. Die neue Activity stellt die Navigation-
Delegates wieder her. Es wurde kein fortbestehendes transparentes Overlay gefunden.
GeckoView fordert beim Touch normalerweise selbst Fokus an; Fokusverlust allein
wird deshalb nicht als nachgewiesene Ursache ausgegeben.

## Gezielte Korrektur

- Eine Aktivierung ist an die bestehende Dokument-/Port-Identität gebunden.
  Suspendieren beendet diese Aktivierung; die Rückkehr erhält eine neue Generation.
  Wiederholungen desselben Handshakes reaktivieren keine bereits übergebene Auswahl.
- Alte `disable`-Nachrichten können eine neue Generation nicht deaktivieren.
- Die vorhandenen Listener fordern bei sichtbarer Rückkehr die Aktivierung erneut
  an. Ein verlorener Port kann begrenzt wiederverbunden werden, ohne neue DOM-
  Listener anzuhängen oder die Seite neu zu laden.
- Ein früher echter Tipp nach beobachteter Suspendierung wartet auf die neue
  Aktivierung. Ein alter Doppeltipp wird dabei nicht für die Rückkehr vorgemerkt.
- Android wiederholt eine unbestätigte Aktivierung begrenzt mit derselben Generation.
  Veraltete, pausierte oder inzwischen übergebene Verbindungen bleiben ausgeschlossen.
- Die wieder angehängte GeckoView erhält beim Rückweg gezielt Fokus. Sitzung,
  Dokument und Scrollposition werden weiterhin im Speicher behalten.
- Fehlerdiagnosen verwenden feste Grundcodes. Keine Cookies, Anmeldedaten,
  Tokenwerte, vollständigen URLs oder Website-Antworten werden protokolliert.

Die bestehende Erkennung der Video-ID, der ActivityResult-Übergabeweg und die
Player-Funktion bleiben erhalten. AdBlock, API-Kontoanbindung, MainActivity,
PlayerController und die Darstellung der Originalseite werden nicht geändert.

## Geänderte Dateien

- `home-data/src/main/assets/original-home-navigation/bridge.js`
- `home-data/src/main/assets/original-home-navigation/manifest.json`
- `home-data/src/main/java/com/weiktube/home/OriginalHomeActivity.kt`
- `home-data/src/main/java/com/weiktube/home/OriginalHomeReturn.kt`
- `home-data/src/main/java/com/weiktube/home/OriginalHomeBridgeActivation.kt`
- `home-data/src/test/java/com/weiktube/home/OriginalHomeBridgeActivationTest.kt`
- `scripts/check-original-home-navigation.cjs`
- Versions-/Paketangaben: `app/build.gradle.kts`, `scripts/package-source.py`,
  `scripts/publish-home-test.ps1`, `README.md`, dieser Bericht.

## Tatsächliche Prüfungen

Stand 3. November 2026:

- Abschließender Gradle-Build erfolgreich in einer Minute. **137 JVM-Tests
  bestanden** (81 App, 56 home-data), 0 Fehler und 0 übersprungene Tests.
  Fünf neue Tests prüfen die Aktivierung über zehn Rückkehrzyklen, wiederholte
  Bereitschaftsmeldungen, suspendierte Berechtigungen, verspätete Bestätigungen
  und einen ersetzten Port.
- Die neue Bridge-Fixture scheitert mit dem gesicherten 1.2.7-Script an
  `A retired disable must not strand the second video`: eine statt zwei
  Übergaben. Mit dem korrigierten tatsächlichen Script besteht sie.
- Node simuliert zehn aufeinanderfolgende Übergaben/Rückkehrvorgänge im selben
  Dokument, denselben Videolink erneut, Shorts, Such-/Kanallinks, weiter unten
  liegende Scrollpositionen, frühe Rückkehr-Tipps, schnelle Doppeltipps, verspätete
  Nachrichten, verlorene Bestätigungen, verlorene Ports und alte Port-Callbacks.
  Listenerzahl und Dokument-ID bleiben konstant; das Script lädt nicht neu,
  setzt die simulierte Scrollposition nicht zurück und startet kein Webmedium.
- Ein neuer echter Pointer-Tipp auf dasselbe Video funktioniert auch innerhalb
  des 800-ms-Schutzfensters. Der nachlaufende Kompatibilitätsklick des vorherigen
  Tipps bleibt gesperrt. Ein früher Rückkehr-Tipp wartet auf die neue Bestätigung,
  ohne einen zweiten Tipp zu benötigen; ein alter Doppeltipp wird nicht nachgeholt.
- JavaScript-Syntaxprüfung sowie vorhandene Player-Fixtures (9), Browser-,
  Watch-Navigations- und Schutzprüfung der 46 ursprünglichen Dateien bestanden.
- Lint: 0 Fehler. App weiterhin 20 Warnungen, home-data 66 statt zuvor 65.
  Die zusätzliche `SetTextI18n`-Warnung betrifft den neuen deutschsprachigen
  Timeout-Hinweis als Textliteral; sie ist kein Laufzeit-/Lifecycle-Fehler.
- Bytevergleich zum Backup: **MainActivity, WeiktubeUi, PlayerController,
  ShieldEngine, YoutubeAccountController, HomeEngine, Video-URL-Policy,
  bisherige Player-Scripts und ursprüngliche APK unverändert**. Insgesamt
  9 Dateien geändert, 3 hinzugefügt, keine gelöscht.
- APK-Signatur erfolgreich geprüft, identisch zur bisherigen App. Paket
  `com.weiktube.app`, Versioncode 12, ARM64 und x86-64, Android 8+.
  Beide Erweiterungen im APK stimmen bytegenau mit dem geprüften Quellcode
  überein; die Klick-Erweiterung trägt Version 2.0.1.

Test-APK: 207.305.771 Bytes, SHA-256
`fd8b59638893750361ca723d6ba243329d179b342fbd6cd6cb7c503d8e5fb352`.

Lokale Nachweise liegen unter `validation/player-return-before-regression.log`,
`player-return-bridge-tests.log`, `player-return-build-final.log`,
`player-return-verification.json` und `player-return-change-inventory.json`.

**Android-Gerätetests sind nicht durchgeführt.** `adb devices -l` meldet am
3. November 2026 kein erreichbares Gerät. Der zuvor versuchte Emulator konnte
ohne verfügbaren Hypervisor-Treiber nicht nutzbar gestartet werden. Deshalb wird
aus simulierten Bridge-Ereignissen kein erfolgreicher Praxistest abgeleitet.

| Verlangter Ablauf | Android-Gerätetest |
|---|---|
| Video 1 → Zurück → Video 2 | Nicht durchgeführt |
| Video 1 → Zurück → Video 2 → Zurück → Video 3 | Nicht durchgeführt |
| Zehn Videoaufrufe mit Android-Zurück dazwischen | Nicht durchgeführt |
| Dasselbe Video erneut öffnen | Nicht durchgeführt |
| Scrollen und weiter unten ein Video öffnen | Nicht durchgeführt |
| Suche, Navigation und andere Elemente nach Rückkehr bedienen | Nicht durchgeführt |
| Schnelles Mehrfachtippen ohne mehrfachen Playerstart | Nicht durchgeführt |
| Tatsächliche Scrollposition nach Rückkehr vergleichen | Nicht durchgeführt |

Die Testversion ist erst nach diesen Geräteabläufen praktisch abgenommen.
Prozess- oder Rendererabbruch kann die lebende Seite weiterhin verlieren.

## Sicherung und Quellen

Vor Änderungen wurden alle 107 freigegebenen Quelldateien, die bisherige Test-APK
und das Quellcodearchiv gesichert. Lokaler isolierter Backup-Branch
`backup/player-return-before-1.2.8`, Commit
`66452fb4e0de63629570e35f5fd6a4a597a03e4d`.
Das gemischte Workspace-Repository und fremde Änderungen bleiben unberührt.

- [Mozilla: Native Messaging mit GeckoView](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)
- [Mozilla: Laufzeit und Trennung eines Messaging-Ports](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/API/runtime/Port)
- Lokaler Quellstand der verwendeten GeckoView-Version 159: `GeckoView.java`,
  `WebExtension.java` (View-/Session-Übergabe, Fokus und Port-Delegate-Verhalten).
