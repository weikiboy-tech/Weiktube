# Gecko → vorhandener Weiktube-Player → dieselbe Seite

Version 1.2.7-player-handoff-test, Android-Versioncode 11, `com.weiktube.app`.
Gezielter Fix für die Original-YouTube-Ansicht; kein neuer Player und kein neuer Feed.

## Gefundene Ursachen

Die bisherige Klick-Erweiterung wurde erst nach manueller Feed-Bestätigung und einer
weiteren Aktivierung installiert. Unterbrechungen schalteten die Übergabe wieder ab.
Der native Handler erkannte vollständige Videonavigationen, aber ein nachträgliches
`stop()` plus einmaliges Pausieren konnte YouTubes SPA-Wiedergabe nicht zuverlässig
verhindern. Das alte Script behandelte nur Anker-Klicks nach dem Handshake.

Die bisherige Übergabe rief außerdem `stopSessions()` auf, schloss Gecko und öffnete
die singleTask-MainActivity mit einem neuen Intent. Dadurch gingen das bestehende
Dokument und seine Scrollposition verloren. Allein das Entfernen von `finish()`
hätte diesen Fehler nicht behoben.

## Änderungen

- Navigationserweiterung installieren, aktivieren und binden, bevor die erste
  YouTube-Seite geladen wird. Videorouting ist unabhängig von der Kontobestätigung.
- Videoziele anhand gültiger öffentlicher URLs beziehungsweise begrenzter öffentlicher
  Navigationseigenschaften des angeklickten Elements bestimmen. Keine Feed-Parser,
  Empfehlungen oder API-Abfragen. Nur kanonische Video-URLs verlassen die Bridge.
- Frühe Capture-Klick-/Pointer-Tippbehandlung plus Seitenkontext-Wrapper für `pushState` und
  `replaceState`. Nichtvideo-Navigationen, Suchanfragen, Kanal- und Menülinks bleiben
  unangetastet. Erkannte fehlerhafte Videoziele werden ebenfalls blockiert.
- Früh eintreffende gültige Klicks warten auf den nativen Handshake. Verbindungsverlust,
  ungültige IDs oder doppelte Ereignisse dürfen keine Website-Wiedergabe freigeben.
- Zusätzliche Media- und native Navigationssperren verhindern die Übernahme durch
  Gecko. Normale Google-Anmeldeseiten erhalten keine Website-Script-Injektion.
- `OriginalHomeReturn` hält eine lebende Gecko-Sitzung einschließlich Port-Verbindung
  und Extension-Lease ohne Activity-/View-Referenz. Beim Parken werden die Delegates
  ersetzt und die Sitzung inaktiv gesetzt; die GeckoView wird abgetrennt, nicht
  das Dokument geschlossen. Die Rückkehr hängt dieselbe Sitzung wieder an.
- Die MainActivity empfängt ein ActivityResult mit einmaliger, geprüfter Rückkehr-ID
  und ruft ihre vorhandene `openVideo()`-Funktion auf. Kein zusätzlicher Player und
  kein öffentlicher Video-Intent für diesen Weg. Die Wiedergabe wartet auf die aktive
  MainActivity. Zurück behandelt zuerst Vollbild, anschließend den Rückweg; der
  Schließen-Button benutzt denselben bestehenden `player.close()`-Aufruf plus Rückweg.
- Die Rückkehr verwendet eine frische Bridge-Generation. Damit lassen sich weitere
  Videos öffnen, ohne die Seite neu zu laden oder frühere Übergaben zu wiederholen.
- Fehlerprotokolle enthalten nur feste Grundcodes, keine Konto-/Sitzungsdaten,
  Formularwerte oder vollständigen URLs.

## Geänderte Komponenten

Gecko: `OriginalHomeActivity.kt`, `OriginalHomePolicy.kt`, die neue
`OriginalHomeReturn.kt` mit kleiner Zustandsregel `OriginalHomeReturnPolicy.kt`
sowie `assets/original-home-navigation/bridge.js` und dessen Manifest.

App-Einstieg: `MainActivity.kt` (ActivityResult und gezielte Rückkehr),
`HomeConnectionEntry.kt` (Aufruf dieses Einstiegs), `WeiktubeUi.kt` (ausschließlich
der Schließen-Callback). Hinzu kommen Tests, eng begrenzte Erweiterungen des
Originaldateien-Schutzchecks, Versions-/Verpackungs-/Releaseangaben und Dokumentation.

`PlayerController.kt`, AdBlock-Engine, Google-API-Kontoanbindung und ursprüngliche
Wiedergabeskripte bleiben unverändert. Der bestehende Miniplayer wird nicht neu gebaut.

## Grenzen des Nachweises

Primär verhindert die frühe Klickbehandlung den Aufruf von YouTubes Router. Dadurch
kann das gleiche Feed-Dokument erhalten bleiben. Ein unbekannter Router, der vor
dem Abfangen bereits DOM-Inhalte ersetzt hat, kann durch bloßes Unterdrücken einer
History-Änderung nicht universell zurückgerollt werden. Die zusätzliche History-
Sperre unterbricht erkannte Videonavigationen; ihre alleinige Ausführung gilt nicht
als Beweis eines unveränderten Feed-DOM.

Prozessbeendigung oder Gecko-Rendererabsturz kann die lebende Seite zerstören.
Identischer DOM-/Scroll-Erhalt über solche Verluste wird nicht zugesichert.
Eine zerstörte Rückkehr-ID darf keine veraltete oder doppelte Wiedergabe starten.
Der normale Zurück-/Schließen-Rundlauf im lebenden App-Prozess soll ohne Neuladen
funktionieren und muss auf Android praktisch geprüft werden.

Die Gecko-Ansicht bleibt eine separate Activity. Ein darüber sichtbarer nativer
Miniplayer gehört nicht zu diesem Fix. Die vorhandene Miniplayer-Implementierung
bleibt in der MainActivity erhalten. API-Anmeldung und Website-Sitzung bleiben
unverändert getrennt; dieser Fix behauptet keinen Erfolg für den Google-Login.

## Verbindliche Geräteabnahme

1. Unter **Du → YouTube-Startseite öffnen** die Originalseite öffnen, ohne eine
   manuelle Übergabe-Freigabe setzen zu müssen.
2. Herunterscrollen und ein reguläres Video antippen. Nur der vorhandene
   Weiktube-Player darf öffnen; Video-ID, sichtbare Player-Bedienung und Ton prüfen.
3. Zurück beziehungsweise Schließen: dieselbe Feed-Karte und Scrollposition müssen
   wieder sichtbar sein. Kein neuer Seitenaufruf im Netzwerk-/Navigationsprotokoll.
4. Drei unterschiedliche Videos nacheinander öffnen und zurückkehren; Doppeltipp
   darf nur einen Playerstart auslösen.
5. Shorts-Karte, Suchergebnis und Kanalvideo prüfen; Kanalavatar und Menü bleiben
   normale Navigationen, Scrollen und Nachladen funktionieren weiter.
6. Fehlende/mehrdeutige ID, unterbrochene Bridge und späte SPA-Ereignisse: sichtbarer
   Fehler beziehungsweise genau eine native Übergabe, niemals Webplayer-Fallback.
7. Vollbild-Zurück, App-Hintergrund, gewöhnliche Videos außerhalb dieser Funktion,
   Miniplayer, Adblock-Prüfseite und bestehende Kontoansichten prüfen.

## Tatsächlich durchgeführte Prüfungen

Stand 3. November 2026:

- Finaler Gradle-Build erfolgreich (1 Minute 3 Sekunden), **132 JVM-Tests bestanden**
  (81 App, 51 home-data), keine Fehler oder übersprungenen Tests. Darunter sechs neue
  Tests für einmalige Übergabe/Rückkehr und ein neuer Test für ungültige Player-Routen.
- Lint: App 0 Fehler / 20 bestehende Warnungen, home-data 0 Fehler / 65 bestehende
  Warnungen. Keine zusätzlichen Lint-Warnungen durch diesen Fix.
- Ausgeführte Bridge-Fixtures: früher Klick vor Handshake, gültige/ungültige und
  mehrdeutige Video-IDs, Ankerpriorität, begrenzte Renderer-Endpunkte, Menü-/Kanallinks,
  Pointer-Tipp gegen Scroll-/Langdruckgesten, doppelte Ereignisse, frische Rückkehr-
  Generation, Verbindungsverlust, Seitenkontext-History, unveränderte Nichtvideo-
  History-Argumente, unterbrochener synchroner Router-Stack und Media-Sperren.
- Vorhandene Player-Fixtures (9), Browser-, Watch-Navigations- und Schutzprüfung
  der 46 Originaldateien bestanden. Die ursprünglichen Baseline-Hashes wurden nicht
  ersetzt; die Ausnahmen sind auf die geprüften Einstieg-/Rückkehr-Hooks begrenzt.
- Bytevergleich zum Backup: PlayerController, ShieldEngine, YoutubeAccountController,
  HomeEngine, Autofill-Helfer, alte Konfigurations-Bridge und ursprüngliche APK
  unverändert. Beide Erweiterungen in der Test-APK stimmen mit dem geprüften
  Quellcode überein; Navigations-Erweiterung ist Version 2.0.0.
- APK-Signatur erfolgreich geprüft und identisch zur bisherigen App.

**Die verlangten Android-Geräteabläufe 1–8 sind noch nicht praktisch abgenommen.**
Ein neuer isolierter Android-35-x86_64-Emulator wurde mit Softwarebeschleunigung
versucht, zuerst mit SwiftShader und anschließend ohne GPU (Lavapipe-Fallback).
In beiden Fällen blieb `emulator-5566` offline; Android erreichte keinen nutzbaren
ADB-Zustand. Der Beschleunigungstest meldete einen fehlenden Emulator-Hypervisor-
Treiber. Die eigenen Testprozesse wurden beendet; kein physisches Gerät wurde benutzt.
Es fand daher kein echter Videostart, Gecko-Rücksprung oder Scrollpositionsvergleich
auf Android statt. Node simuliert die Firefox-Xray-Grenze und ersetzt diesen Nachweis
nicht. Die Version wird ausdrücklich als **Testversion, nicht vollständig abgenommen**
bereitgestellt.

Test-APK: 207.424.074 Bytes, SHA-256
`bc98a25636af1f458910190237eb51ac634c42bdaab68b44c017ef0b16d1023b`.

Vor den Änderungen wurden 103 Quelldateien, vorherige Test-APK und Quellcode-ZIP
gesichert. Backup-Branch `backup/player-handoff-before-1.2.7`, Commit
`5f3f3e1874b7dbe7e44f46bb48df88ee8c287183`, in einem eigenen lokalen Repository.
Das gemischte Workspace-Repository und fremde Änderungen blieben unberührt.
Keine Projektdateien wurden gelöscht und keine verbotenen Git-Operationen verwendet.

## Quellen

- [Mozilla: GeckoView WebExtensions](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/web-extensions.html)
- [Mozilla: Seitenkontext und Xray-Grenzen](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Sharing_objects_with_page_scripts)
- [Mozilla: exportFunction](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Content_scripts/exportFunction)
- [Android: Activity-Stack und singleTask](https://developer.android.com/guide/components/activities/tasks-and-back-stack)
