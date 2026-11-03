# YouTube-Website anmelden und Konto wechseln

Version `1.3.1-website-login-test`, Versioncode 15, `com.weiktube.app`.
Ausgangspunkt ist die veröffentlichte Home-Tab-Version 1.3.0.

## Ursache und Änderung

Der eingebettete Home-Tab blendete die alte Gecko-Testleiste aus. Damit verschwand
auch deren einziger nativer Konto-Einstieg. Der Button unter Du öffnete nur Home.
Die Anmeldung selbst wurde nicht durch eine neue Kontolösung ersetzt: Der schon
vorhandene YouTube-Kontoeinstieg ist jetzt wieder erreichbar.

- Unter **Du → Deine YouTube-Startseite → Bei YouTube anmelden / Konto wechseln**
  wird die vorhandene YouTube-Kontoauswahl geöffnet. Die separate Google-API-
  Freigabe für Abos/Playlists ist dafür nicht erforderlich.
- Einmal automatisch beim ersten sichtbaren Home-Aufruf, auch einmal nach dem
  Update, öffnet derselbe Einstieg. **Später** führt zur normalen Home-Seite.
- Die vorhandene GeckoSession und ihr persistentes Profil werden weiterverwendet.
  YouTube entscheidet anhand dieser Sitzung über Anmeldung und Kontowahl.
  Konto-Cookies werden nicht gelöscht oder aus Chrome beziehungsweise API-Tokens
  importiert. Es gibt keinen festgelegten Kontonamen.
- Eine schmale Einrichtungs-/Anmeldezeile zeigt nur den tatsächlichen HTTPS-Host
  und einen Rückweg. Sie ist keine permanente Browserleiste auf der Startseite.
- Cookie-Auswahl, Anmeldung und Sicherheitsabfragen bleiben Original-Webseiten.
  Weiktube akzeptiert keine Bedingungen oder Cookies automatisch. Eine Ablehnung
  durch Google bleibt sichtbar; es gibt keine Umgehung der Anmeldesicherheit.

Gespeichert wird ausschließlich, dass die Einrichtung angezeigt wurde. Dieser
Marker ist **kein Nachweis einer Anmeldung**. Auch die Rückkehr zu Home ist kein
Anmeldenachweis. Es wird kein Login- oder Feed-Parser eingeführt. Das ausdrückliche
Löschen der Website-Daten setzt den Marker zurück; Tabwechsel, Rückkehr vom Player
und Wiederholungen nach Ladefehlern setzen ihn nicht zurück.

## Begrenzter Änderungsumfang

- `OriginalHomeController.kt`: öffentlicher, warteschlangenbasierter Kontoaufruf;
  einmalige Einrichtungsanzeige, schmaler Rückweg, Wiederholung und Back-Anbindung.
- `HomeEngine.kt`: Präsentationsmarker im vorhandenen Website-Einstellungsspeicher.
- `MainActivity.kt`: ein neuer Aufruf, der das Kontoziel vor dem Anzeigen von Home
  in denselben Controller einreiht. Vorhandene Player-Funktionen bleiben erhalten.
- `HomeConnectionEntry.kt`: dauerhaft erreichbarer Anmelde-/Kontowechsel-Button;
  vorhandener Startseiten-Button bleibt erhalten.
- `WebsiteAccountSetup.kt` und `WebsiteAccountSetupTest.kt`: reine Zustandslogik
  und zwölf JVM-Tests für Einrichtung, verzögerte Navigation, Abbruch und Neustart.
- Version, README, dieser Bericht, Quellpaket-/Veröffentlichungsskript und der
  bestehende Änderungsumfangscheck werden entsprechend ergänzt.

Insgesamt neun vorhandene Dateien geändert, drei neue Dateien, keine gelöscht;
108 portable Bestandsdateien bleiben bytegleich. `WeiktubeUi.kt`, die sonstigen
Tabs und die ursprüngliche geschützte `Weiktube.apk` bleiben unverändert.

Video-ID-Erkennung, Klick-Bridge, PlayerController, geparkte Rückkehrtransaktion,
Adblock und Google-API-Kontoanbindung bleiben unverändert. Insbesondere bleibt der
bestätigte nicht-null ProgressDelegate während der Player-Übergabe erhalten.

## Tatsächlich ausgeführte Prüfungen

Abschließender Build am 3. November 2026 erfolgreich:

- `:app:exportHomeConnectionApk`, `:app:testDebugUnitTest`,
  `:home-data:testDebugUnitTest`, `:app:lintDebug`, `:home-data:lintDebug`.
- **153 JVM-Tests bestanden**: 81 in app und 72 in home-data, keine Fehler,
  fehlgeschlagenen oder übersprungenen Tests. Darunter zwölf neue Einrichtungstests.
- Android Lint: keine Fehler/Fatal-Meldungen, 20 Warnungen in app und 39 in
  home-data. Der zusätzliche Hinweis empfiehlt die KTX-Schreibweise für den
  neuen SharedPreferences-Zugriff. Kein Lint-Check wurde deaktiviert. Ein erster
  Lauf meldete einen internen Lint-Analysefehler; der vollständige Wiederholungslauf
  mit dem finalen unveränderten Quellstand war erfolgreich.
- `check-home-scope.py` bestanden: 46 geschützte Originaldateien; nur der exakt
  beschriebene neue Kontoaufruf wird vor dem bisherigen Baseline-Check ausgeblendet.
- `check-gecko-return-lifecycle.py` bestanden: tatsächlicher gebündelter Gecko-
  Modulcode, zehn modellierte Rückkehrzyklen ohne künstliche about:blank-Navigation.
- `check-original-home-navigation.cjs` bestanden: zehn simulierte Rückkehrzyklen,
  dasselbe Video, Shorts/Suche/Kanal, schnelle Mehrfachtipps, Bridge-Wiederverbindung
  und einmalige Listener. Das sind Fixtures, keine Android-Touchtests.
- `check-player-scripts.cjs`: neun Fixtures bestanden; `check-browser-scripts.cjs`
  und `check-watch-navigation.cjs` ebenfalls bestanden.
- APK-Signatur erfolgreich verifiziert; derselbe Signierer wie 1.3.0. Paketkennung,
  Versioncode 15 und Android 8 als Mindestversion geprüft. Vier gepackte Bridge-/
  Manifest-Dateien stimmen bytegleich mit den unveränderten Quellen überein.

APK `Weiktube-Home-Test.apk`: 207.446.259 Bytes.
SHA-256:
`a608898774f8a550a1117b0b9df347f49162cf00d2e8ba7870de9a02523344fa`.

**Keine Android-Gerätetests durchgeführt:** `adb devices -l` zeigt am
3. November 2026 kein verbundenes Gerät. Automatisierte Zustands-/JavaScript-Tests
sind kein Nachweis einer echten Google-Anmeldung oder von Touch-/Player-Verhalten.

Noch auf Android zu prüfen:

| Ablauf | Stand |
|---|---|
| Neuinstallation und Update: Konto-Einstieg einmal, danach keine Schleife | Nicht durchgeführt |
| Vorhandene Gecko-Anmeldung und Kontowechsel | Nicht durchgeführt |
| Cookie-Auswahl akzeptieren/ablehnen → Anmeldung oder Überspringen | Nicht durchgeführt |
| Google-Anmeldung, Sicherheitsabfrage und Rückkehr aus einer Authenticator-App | Nicht durchgeführt |
| Später, Android-Zurück, Offlinefehler und erneuter Versuch | Nicht durchgeführt |
| Zehn Videos mit Zurück, gleiches Video erneut, schnelle Mehrfachtipps | Nicht durchgeführt |
| Tabwechsel, Suche, Scrollposition und freie Touch-Eingaben | Nicht durchgeführt |

Die APK wird als separate Testversion mit unveränderter Paketkennung und Signatur
bereitgestellt. Über die vorhandene Version installieren, um das Website-Profil
zu behalten. Der stabile Updatekanal wird nicht umgestellt.

## Sicherung

Vor Änderungen wurden alle 117 portablen Quelldateien sowie die vorherige APK und
das Quellcodearchiv gesichert. Isolierter lokaler Backup-Branch
`backup/website-login-before-1.3.1`, Commit
`e13a6cc3728fd5ceb7b37c3aa350540d998f4462`.
Das gemischte Workspace-Repository und fremde Änderungen bleiben unberührt.
