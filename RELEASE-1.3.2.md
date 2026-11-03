# Weiktube 1.3.2 – reguläre öffentliche Version

Die bestätigte Startseite und Anmeldung aus 1.3.1 werden regulär veröffentlicht.
Paket `com.weiktube.app`, Versioncode 16. Das Release ist kein GitHub-Prerelease
und wird über den bestehenden Standard-Updater angeboten.

## Enthalten

- Originale YouTube-Startseite direkt im Home-Tab, mit der gespeicherten
  Website-Anmeldung und originalen Empfehlungen dieses Kontos.
- Einmaliger, überspringbarer Konto-Einstieg; dauerhaft erreichbar unter
  **Du → Bei YouTube anmelden / Konto wechseln**.
- Bestehender Weiktube-Player, Videoübergabe, Rückkehr, Adblock und andere Tabs.
  Diese Implementierungen werden für die Veröffentlichung nicht verändert.
- Architekturwahl für App-Updates; bestehende HTTPS-, Größen-, Prüfsummen-,
  Paket- und Signaturprüfungen bleiben erhalten.

## Download und Updates

**[Weiktube.apk für Android-Handys (ARM64)](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.2/Weiktube.apk)**

[Weiktube-x86_64.apk](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.2/Weiktube-x86_64.apk)
für passende Intel-/Emulator-Geräte. Android 8 oder neuer; keine Unterstützung für
reine 32-Bit-Geräte. Beide APKs sind vollständige Einzeldateien. Über die vorhandene
Version installieren, damit die Website-Sitzung erhalten bleibt.

Die alte Universal-Test-APK überschritt das vorhandene Updaterlimit von 128 MiB.
Deshalb werden dieselben Funktionen in zwei kleineren APKs ausgeliefert. Die
Standarddatei und die bisherigen Manifestfelder verweisen auf ARM64. Ab 1.3.2
wählt der Updater anhand der Gerätearchitektur den passenden `apks`-Eintrag.

Ältere ARM64-Versionen verwenden weiterhin die Standardquelle. Ältere
x86-64-Installationen benötigen einmal das manuelle x86-64-Update oder die Quelle
`https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update-x86_64.json`.
Android bestätigt jede Installation; es gibt keine stille Installation.

Die Website-Anmeldung ist unabhängig von der optionalen Google-API-Freigabe für
Abos und Playlists. Die öffentliche APK-Veröffentlichung ändert keine Cloud-
Testnutzer oder OAuth-Verifikation; deren öffentlicher Status wurde nicht geprüft.
Es werden keine Kontositzungen, Zugangsdaten, privaten Schlüssel oder Gerätebilder
veröffentlicht.

## Änderungen gegenüber 1.3.1

- `app/build.gradle.kts`: Version 1.3.2/code16; nicht debuggbarer Release-Build,
  gleiche ursprüngliche Signatur und eigenständige APKs für die zwei Architekturen.
- `UpdateManifest.kt`, `UpdateManifestTest.kt` und ein Parser-Aufruf in
  `UpdateController.kt`: abwärtskompatibles Architekturfeld mit zwölf neuen Tests.
- Veröffentlichungsskript: vollständiges bereinigtes Quellpaket einschließlich
  home-data, geprüfte Release-Dateien, erhaltene Git-Historie und normale
  Fast-Forward-Aktualisierung von main. Keine Remote-Historie wird überschrieben.
- README, Build-/Update-Anleitung, Bericht und Source-/Scope-Prüfung aktualisiert.

Die geschützte ursprüngliche `Weiktube.apk` im Projektstamm und die vorherige
Test-APK bleiben erhalten. Neue reguläre Dateien liegen unter `dist/stable/`.

## Prüfstand

Release-Build erfolgreich mit `:app:exportStableApks`, den vorhandenen
`:app:testDebugUnitTest`-/`:home-data:testDebugUnitTest`-Tasks und Release-Lint
beider Module. Separate Release-Unit-Test-Tasks sind in diesem Projekt nicht
registriert; die gemeinsamen JVM-Tests wurden über die vorhandenen Tasks ausgeführt.

- **165 JVM-Tests bestanden**: 93 in app, 72 in home-data; keine Fehler oder
  übersprungenen Tests. Zwölf neue Tests prüfen Architekturwahl, alte Feeds,
  ungültige Einträge und unveränderte URL-/Hash-Validierung.
- Release-Lint: keine Fehler/Fatal-Meldungen; 20 Warnungen in app und 39 in home-data.
- Originalquellen-Schutzcheck, beide APK-Gecko-Lifecycle-Fixtures und sämtliche
  Klick-Bridge-, Player-, Browser- und Watch-Navigations-Fixtures bestanden.
  Zehn Rückkehrzyklen wurden simuliert, nicht auf Android ausgeführt.
- Beide APKs: Signatur verifiziert, gleiches Zertifikat wie 1.3.1 und die erneut
  heruntergeladene bisherige öffentliche Version 1.1.0, Paketkennung
  `com.weiktube.app`, Code 16, Name 1.3.2, nicht debuggbar und korrekte einzelne ABI.
  Die gepackten Bridge-Dateien entsprechen den unveränderten Quellen.
- Alle bestehenden Laufzeitdateien der App und von home-data sind gegenüber
  1.3.1 bytegleich, außer dem einen Parser-Aufruf im Updater. Neue Parser-/Testdateien
  sind separat. Keine Datei wurde gelöscht.

| Datei | Bytes | SHA-256 |
|---|---:|---|
| Weiktube.apk (ARM64) | 106480435 | `7d06299ba01b9656ae0f3e3bde74252106b0a9315808bf57d331d8c650b26eae` |
| Weiktube-x86_64.apk | 111488394 | `1647d5448a1f432c29f0e8ad80051a4a8717be8ceee90140c979a9c2e6db7104` |

Der Nutzer hat den Anmeldeablauf aus 1.3.1 als gewünschtes Verhalten bestätigt.
Eigene Android-Geräteprüfungen wurden in dieser Umgebung nicht durchgeführt:
Es war kein Gerät verbunden. Insbesondere der komplette In-App-Update-/Installer-
Ablauf, Google-Anmeldung und zehn echte Player-Rückkehrzyklen sind hier nicht als
Gerätetest abgenommen. Automatisierte Fixtures ersetzen diese Tests nicht.

Vor Änderungen wurden die 120 portablen Quellen und alle vorherigen APK-/ZIP-
Artefakte gesichert. Backup-Commit `4916c5823c51f638efbead66f56cd8f011d6aee6`.
