# 1.3.28 – Videoseite nach dem ersten Bild

Im Hochformat wird auch der äußere Player-Container an die Videofläche angepasst. Beim ersten Bild prüft Weiktube die Videodetails separat. Ein langsamer Start blendet das Cover nicht mehr pauschal nach vier Sekunden aus. [Release und Testumfang](RELEASE-1.3.28.md)

# 1.3.27 – Vorschau und frühere Startfreigabe

Beim Videostart zeigt Weiktube das Vorschaubild des gewählten Videos. Eine verlorene Filter-Bereitmeldung wird bereits beim Dokument-Commit erneut angefordert. Bei längerem Warten lässt sich der Player manuell erneut laden. [Release und Testumfang](RELEASE-1.3.27.md)

# 1.3.26 – Start normaler Videos

Der Videoplayer richtet die Videofläche früher aus, unterscheidet Dokument und erstes Bild und übernimmt verfügbare Titel früher. Die einmalige Tonfreigabe berücksichtigt bereits laufende Videos. [Messungen und Gerätetest](RELEASE-1.3.26.md)

# 1.3.25 – App-Update oben in den Einstellungen

„Jetzt nach Updates suchen“ steht direkt oben in den Einstellungen. Ist ein Update verfügbar, erscheint darunter der Download- oder Installationsbutton.

# 1.3.24 – Rotationsstabilität

1.3.24 verwirft verspätete Messungen der Videofläche bei schnellen Drehungen und setzt ungültige Miniplayer-Transformationen zurück. Die lokale Rotationsdiagnose lässt sich nach einem Neustart teilen. Die Geräteabnahme steht aus. [Details und Gerätetest](RELEASE-1.3.24.md)

# 1.3.23 – Miniplayer, System-PiP und Vollbildknopf

1.3.23 korrigiert die Startfläche der Miniplayer-Bewegung, den Android-PiP-Bildausschnitt und die Bedienung des Vollbildknopfs bei automatischer Rotation. Die visuelle Abnahme auf dem OnePlus 15R steht aus. [Testumfang und OnePlus-Anleitung](RELEASE-1.3.23.md)

# 1.3.22 – Player-Stabilität

Der Player startet mit seiner vorgesehenen Größe. Beim Minimieren und Vergrößern teilen sich Videofenster, Rundung und Navigationsabstand einen unterbrechbaren Animationsfortschritt. In der Querformatansicht steht oben links der Minimieren-Pfeil zur Verfügung. Minimieren und Schließen geben die Vollbildfläche frei. Späte Vollbildanforderungen eines geschlossenen oder minimierten Players werden abgewiesen.

[Prüfumfang und OnePlus-Abnahme](docs/PLAYER-STABILITY.md)

# 1.3.21 – SponsorBlock

Weiktube 1.3.21 ergänzt optionales SponsorBlock mit acht Kategorien, farbigen Markierungen und Rückgängig. Aktivierung unter Einstellungen → SponsorBlock. 403 JVM-Tests und 21 JavaScript-/Browser-Prüfprogramme bestanden. Die Geräteprüfung auf dem OnePlus steht aus.

# 1.3.21-rc1 – SponsorBlock (lokal, nicht veröffentlicht)

- Optionaler Hash-Präfix-Abruf, acht konfigurierbare Skip-Kategorien, Cache/Backoff.
- Automatische und manuelle Sprünge, Rückgängig, Schutz bewusster Rücksprünge, lokale bestätigte Statistik.
- Farbige Markierungen in unterstützten vorhandenen Zeitleisten; Consent/Attribution.
- Gleicher Player, keine Shorts-/AdBlock-Änderung. Geräteabnahme ausstehend.
- [Details und Tests](docs/SPONSORBLOCK.md).

# Änderungsverlauf

## Release 1.3.20

Veröffentlichung des geprüften Advanced-Player-Kandidaten mit Versionscode 35. Keine neuen Funktionsänderungen seit rc1. [Release und Grenzen](RELEASE-1.3.20.md).

## Lokal: 1.3.20-rc1 – Advanced Player und RYD

Vollbild-Lautstärke/Helligkeit, konfigurierbares Doppeltipp-Spulen, temporäre Geschwindigkeit,
fähigkeitsabhängige Qualitätspräferenz und optionale Dislike-Anzeige. Kein öffentlicher Release;
Geräteabnahme offen. [Details, Tests und Grenzen](docs/ADVANCED-PLAYER.md).


## Release 1.3.19

YouTube-Links aus anderen Apps und Einstieg in Androids Standard-Link-Einstellungen. [Einrichtung und Testgrenzen](RELEASE-1.3.19.md).

## Release 1.3.18

Shorts mit bestätigter Tonfreigabe und interaktiver Zeitleiste. [Details und Testgrenzen](RELEASE-1.3.18.md).

## Release 1.3.17

Zwei-Finger-Videozoom im bestehenden Player. [Release und Testgrenzen](RELEASE-1.3.17.md).

## Release 1.3.16

Gezielter Tonstart für den bestehenden Shorts-Player. Bewusstes Stummschalten bleibt während des Besuchs erhalten. Normaler Player, PiP und AdBlock unverändert. [Änderungen und tatsächliche Testgrenzen](RELEASE-1.3.16.md).

## Release 1.3.15

Shorts im Shorts-Tab, gespeicherte Wiedergabepositionen und keine Reaktion auf Kopfüberhalten. [Änderungen und Testgrenzen](RELEASE-1.3.15.md). Die älteren Abschnitte unten dokumentieren frühere Arbeitsstände.

## Release 1.3.14

Native Skalierung bei erhaltener Player-WebView-Größe, bestätigter Tonstart und lokaler Verlauf. Testergebnisse und verbleibende Grenzen: [Release-Hinweise](RELEASE-1.3.14.md). Ältere Prüfstände unten sind historische Befunde.

## Unveröffentlicht – Entwurf für die nächste Version

- Lokales „Kürzlich angesehen“-Karussell im Du-Tab, mit Vorschaubildern,
  „Alle anzeigen“, Leerzustand und Hinweis auf pausierte Aufzeichnung.
- Erneute Videoauswahl aktualisiert den Verlauf; verspätete Titelmeldungen
  ändern keine Reihenfolge und legen nach dem Löschen keine Einträge neu an.
- Bestätigung vor dem Löschen des lokalen Verlaufs.
- Begrenzte Playback-Diagnose um Stummschaltung und Medienlautstärke erweitert.
- Einmaliger Tonstart bei expliziter neuer Videoauswahl; spätere Stummschaltung
  und Pause bleiben erhalten. Auf dem OnePlus mit sofortigem Ton und ohne
  verbleibenden Hinweis vom Nutzer bestätigt.
- Neue UI-/DataStore-/Modustests und lokale Chromium-Medienkontrollen.
  YouTube-PiP bleibt offen; die quellenseitige Pause wurde auf dem Gerät
  am tatsächlichen Aufrufer nachgewiesen. Keine Umgehung der Freigabeprüfung,
  kein vollständiger Wiedergabefix behauptet, keine Veröffentlichung erfolgt.

[Prüfbericht und Geräteanleitung](docs/HISTORY-PIP-AUDIO-REVIEW.md).

## 1.3.13 / Code 27 – Bildschirmstatus nach dem Entsperren

Der Nutzer meldet den schwarzen Player nach rund fünf Minuten Ruhemodus auch
mit 1.3.12. Dessen vorherige Korrekturen sind kein erfolgreicher Gerätefix.

- Aktuellen Power-/Keyguard-Status bei Fokusgewinn, Start, Resume und
  `SCREEN_OFF`/`SCREEN_ON`/`USER_PRESENT` abgleichen, statt einen alten
  Bildschirm-Sperrstatus allein aufgrund einer Broadcast-Bezeichnung zu behalten.
- Bei zulässiger Rückkehr dieselbe gesunde native WebView fortsetzen, ohne Play,
  Reload oder View-Austausch. Manuelle Pause bleibt erhalten.
- `SCREEN_ON` weiterhin vom tatsächlichen Entsperren unterscheiden;
  verspätetes `SCREEN_OFF` blockiert ein inzwischen entsperrtes Gerät nicht
  erneut. Fokusverlust allein pausiert sichtbares PiP nicht.
- Feste `SCREEN_STATE`-Diagnose mit aktuellem und bisherigem Sperrstatus,
  ohne Seiten-, Konto- oder Zugriffsdaten.
- Gegenprobe vor dem Fix: sieben neue Fokus-/Sperrstatus-Tests, zwei
  reproduzierte Fehler und fünf erfolgreiche Kontrollen. Danach 46 gezielte
  Fälle bestanden: 15 Bildschirmstatus-, 24 native PiP- und sieben Diagnose-
  Tests. Darunter ist Entsperren → Video schließen → anderes Video öffnen
  und Play anfordern in derselben fortgesetzten WebView.
- Vollständiger Debug-/Release-Build und 322 JVM-Tests bestanden: 225 App,
  77 Home, 20 Filter; unveränderte Home-/Filter-Ergebnisse als aktuell übernommen.
  App-Lint ohne Fehler, 37 Warnungen. Release-APKs, Scope-Abgleich und native
  PiP-Quellverträge geprüft. Die unveränderten JavaScript-/Pixeltests aus
  1.3.12 wurden für 1.3.13 nicht erneut ausgeführt.

Kein aktueller Handy-/Emulatortest; die konkrete Geräteursache bleibt
unbestätigt. Die bekannte YouTube-Pause in Mini/PiP bleibt offen.
[Release 1.3.13](RELEASE-1.3.13.md).

## 1.3.12 / Code 26 – Dokumentwechsel und Hintergrunddiagnose

- Verspätete Seiten-, Fehler- und History-Meldungen eines vorherigen Videos
  in derselben WebView werden von der aktuellen Auswahl getrennt. Polling
  wartet auf das bestätigte ausgewählte Dokument und verwirft ältere Antworten.
- Ein vorübergehend fehlendes Videoelement überschreibt die letzte bekannte
  Medienposition nicht mit dem Defaultwert null.
- Eine antwortende, vollständig geladene und leere `about:blank`-Seite nach
  einem bestätigten Videodokument erlaubt höchstens einen automatischen
  Wiederherstellungsversuch je ausdrücklicher Videoauswahl. Dieselbe gesunde
  WebView bleibt erhalten; Pausenzustand, Position und neuere Auswahl werden
  berücksichtigt. Fehlende Antworten oder verstrichene Zeit reichen nicht aus.
- Diagnose ergänzt Prozessmarker, Activity-/Controller-/View-Kennungen und
  native Attachment-/Sichtbarkeitsdaten ohne Seitenadressen oder Zugangsdaten.
- Dokument-Races, isolierte Compose-Host-Zyklen und Diagnoseformat bekommen
  zusätzliche Regressionstests. Auch die erneute Auswahl desselben Videos
  während einer anstehenden Leer-Dokument-Wiederherstellung lädt in diesem
  Fall erneut, statt das unbestätigte Dokument wiederzuverwenden.
- Debug-/Release-Build und 307 JVM-Tests bestanden: 210 App, 77 Home, 20 Filter,
  darunter 61 gezielte Player-/Compose-/Diagnosefälle. App-Lint: keine Fehler,
  37 Warnungen. Scriptprüfungen und lokale Chromium-Pixeltests bestanden.

Die Ursache des schwarzen Players auf dem Handy ist nicht nachgewiesen.
Keine Geräte-/Emulatortests für 30 Sekunden, fünf oder 15 Minuten Hintergrund,
echten System-/Rendererverlust oder PiP-Wiedergabe. Die YouTube-seitige Pause
aus 1.3.11 bleibt offen. Details: [Release 1.3.12](RELEASE-1.3.12.md).

Frühere Änderungen bleiben in den bestehenden Release-Dokumenten erhalten:
[1.3.11](RELEASE-1.3.11.md), [1.3.10](RELEASE-1.3.10.md),
[1.3.9](RELEASE-1.3.9.md), [1.3.8](RELEASE-1.3.8.md) und
[Prüfprotokoll](TESTING.md).
