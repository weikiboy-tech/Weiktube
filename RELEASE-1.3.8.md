# Weiktube 1.3.8 – Android-Bild-in-Bild

Version 1.3.8, Versioncode 22, Paket `com.weiktube.app`.
**Build, automatisierte Tests und APK-Prüfung bestanden; keine Geräteabnahme.**
Eine tatsächlich laufende YouTube-Wiedergabe mit Bild und Ton im Android-PiP-Fenster
ist auf einem Handy weiterhin zu prüfen.

## Umfang

Der vorhandene Weiktube-Player soll in Androids Bild-in-Bild-Fenster weiterlaufen,
wenn ein geeignetes laufendes Video die App verlässt. Derselbe Player und dieselbe
WebView bleiben erhalten; weder eine zweite Videoquelle noch ein zusätzlicher
Webplayer werden angelegt. Adblock und bestehende Kontositzungen bleiben an den
vorhandenen Dokumenten gebunden. Der interne Miniplayer bleibt eine eigene
Darstellung innerhalb der App.

- **Android 12 / API 31 und neuer:** Die App setzt die PiP-Parameter einschließlich
  `setAutoEnterEnabled`, statt beim Verlassen einen zweiten parallelen Übergang
  anzustoßen. Automatik wird nur für eine geeignete laufende Wiedergabe aktiviert.
- **Android 8–11 / API 26–30:** Der bestehende Plattformaufruf
  `enterPictureInPictureMode` übernimmt den Übergang über `onUserLeaveHint`.
- **Manueller Einstieg:** Die PiP-Taste verwendet dieselbe Zulässigkeitsprüfung.
  Fehlende Geräteunterstützung oder abgelehnte Android-Freigabe soll verständlich
  gemeldet werden, ohne einen Ersatzplayer zu öffnen.
- **Systemaktionen:** Androids PiP-Fenster zeigt Play oder Pause als `RemoteAction`.
  Die Aktionen steuern ausschließlich das aktuelle Video im vorhandenen Player.
  Alte Aktionen nach einem Videowechsel dürfen kein neues Video starten.
- **Darstellung:** Das Systemfenster zeigt die Videofläche mit passendem
  Seitenverhältnis; Android stellt die PiP-Bedienelemente. Die ursprüngliche
  Videoseite und ihre Position sollen beim Vergrößern wieder erscheinen.

Die gewählte API-Verwendung folgt der
[Android-Dokumentation für Bild-in-Bild](https://developer.android.com/develop/ui/views/picture-in-picture).

## Lifecycle und Grenzen

Ein sichtbares PiP-Fenster kann bereits eine pausierte Activity haben. Deshalb
pausiert Weiktube die Wiedergabe nicht allein in `onPause`. Wenn die Activity mit
`onStop` vollständig unsichtbar wird, wird die Wiedergabe pausiert. Ausschalten
oder Sperren des Bildschirms pausiert ebenfalls. Das ist keine Freigabe für
unsichtbare Audiowiedergabe und kein neuer Hintergrunddienst.

Es wird **keine** Webseiten-Sichtbarkeit gefälscht: keine Manipulation von
`document.hidden`, `visibilityState` oder Sicherheits-/DRM-Prüfungen. YouTube kann
sein Browser-Video selbst pausieren oder die Wiedergabe einschränken. Ob der
gewählte Android-PiP-Lifecycle die tatsächliche YouTube-Wiedergabe zuverlässig
erhält, ist noch nicht empirisch bestätigt. Eine Erfolgsgarantie wird nicht gegeben.

## Geänderter Bereich

- `MainActivity.kt`: PiP-Einstieg, Sichtbarkeit, Lifecycle und Bildschirmzustand.
- `NativePipController.kt`: Android-PiP-Parameter und Systemaktionen.
- `PlayerController.kt`: PiP-Bereitschaft, vorhandene Wiedergabesteuerung und
  Darstellung derselben WebView.
- `PlayerPlaybackObserver.kt`: unmittelbare Play-/Pause-/Metadatenmeldungen aus
  dem vorhandenen Videodokument; nur exakte HTTPS-Ursprünge und oberster Frame,
  begrenzte Daten und Kennung pro Ladevorgang. Keine URLs, Kontodaten oder
  Wiedergabebefehle. Auf älteren WebViews bleibt der Sekunden-Poll als Rückfall.
- `WeiktubeUi.kt`: Darstellung und Erreichbarkeit des bestehenden Players in PiP.
- Begleitende Regressionstests sowie Versions-/Release-Dokumentation.

Die Gecko-Startseite, ihre Videoübergabe, Google-Kontoanbindung und Filterregeln
werden für dieses Feature nicht ersetzt.

## Prüfstand

| Prüfung | Stand für 1.3.8 |
| --- | --- |
| Release-Build ARM64 / x86-64 | Bestanden |
| JVM-Regressionstests | 234 grün: 142 App, 72 Home, 20 Filter; unveränderte Home-/Filtertests von Gradle als aktuell übernommen |
| Native PiP-Tests | 24 Robolectric-Ausführungen, API-Zweige 26/31/33/35; echte Android-Parameter, PendingIntents und Broadcast-Zustellung, kein echter WindowManager |
| Playback-Observer | 3 Decoder-Tests und 5 JavaScript-Fixtures bestanden |
| Player-/Lifecycle-/PiP-Scriptprüfungen | 16 Player-Fixtures, darunter 10 PiP-Runden mit demselben Video; PiP-Integration, Home-Kaltstart/-Rückkehr und Adblock-Fixtures bestanden |
| Release-Lint | 0 Fehler; App 33 Warnungen, Home 41. Vier neue Feature-Warnungen betreffen zwischengespeicherte, geprüfte WebView-Feature-Guards |
| APK-Signatur, Metadaten, Größen und Prüfsummen | Beide Architekturen mit apksigner/aapt geprüft; ursprüngliches Zertifikat, nicht debuggbar, jeweils unter 128 MiB |
| Android-Home-Geste → laufendes PiP → Vergrößern | Nicht auf Gerät durchgeführt |
| System-Play/Pause, Schließen, Bildschirm sperren | Nicht auf Gerät durchgeführt |
| Echter YouTube-Videofluss und mögliche Browser-Sperren | Nicht auf Gerät durchgeführt |

Die Broadcast-Tests fanden zunächst einen fehlenden URI-Scheme-Eintrag im
PiP-Steuerungsfilter. Dieser wurde korrigiert; die vollständigen Tests und der
Build wurden danach erfolgreich wiederholt. Tests umfassen außerdem veraltete
Aktionen nach Videowechsel, zehn Vergrößerungsrunden, Sperren/Entsperren ohne
Autoplay und Receiver-Cleanup. Die konkrete Geräte-Prüfliste steht in
[TESTING.md](TESTING.md). Sie ist ausdrücklich nicht abgearbeitet.

Der Quellcodevergleich zu 1.3.7 zeigt drei geänderte Player-/Activity-/UI-Dateien
und zwei neue PiP-Helfer. Alle Gecko- und Filter-Produktionsdateien sowie der
bestehende Inset-/Gestencontainer bleiben bytegleich.

## Updateweg

In der App: **Einstellungen & Updates → Jetzt nach Updates suchen**.
Alternativ: [ARM64-APK](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.8/Weiktube.apk),
[x86-64-APK](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.8/Weiktube-x86_64.apk),
[Quellcode](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.8/Weiktube-Quellcode.zip)
und [Prüfsummen](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.8/SHA256SUMS.txt).

Über die bestehende Installation installieren, nicht vorher deinstallieren.
Android bestätigt jede Installation. Der vorhandene Updater und die getrennten
signierten Filterupdates bleiben erhalten.
