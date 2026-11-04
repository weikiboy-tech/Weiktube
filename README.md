# Weiktube 1.3.9

**Neu in 1.3.9:** Gezielte Korrekturen für Miniplayer und Android-Bild-in-Bild.
Beide Darstellungen verwenden dieselbe Videofläche; zurückgebliebene
YouTube-Zentrierungsabstände werden innerhalb dieser Fläche korrigiert.
Eine geschlossene YouTube-Schublade ragte trotz korrekter Videomaße als graue
Fläche über Mini- und PiP-Bild. Genau diese Schublade wird nur in diesen beiden
Darstellungen ausgeblendet; Normalansicht und gewöhnliches Querformat bleiben erhalten.
Android-Zurück verkleinert das Video ohne zusätzliches Pausieren. Im PiP-Fenster
werden keine Systemleisten-/Displayausschnitt-Insetwerte an die WebView weitergegeben;
das Compose-Padding war dort bereits null.

Der Browservergleich belegt die Überdeckung per Screenshot und Hit-Test.
Die vorherigen 31 korrekten Geometriemesspunkte waren allein **kein Bildnachweis**.
Nach dem Fix zeigen die Mini- und PiP-Endscreenshots das vollständige Bild
ohne grauen Balken. Release-Build, 236 JVM-Tests, Scriptprüfungen und lokale
Chromium-Pixeltests bestanden. Eine zusätzliche YouTube-Pause beim zweiten
Mini-Übergang bleibt offen. Kein Handy- oder erfolgreicher
Android-Emulatortest; die APK-Abnahme übernimmt der Nutzer.
Details: [Release 1.3.9](RELEASE-1.3.9.md). Die Downloadlinks zeigen bis zur
Veröffentlichung weiterhin die zuletzt veröffentlichte Version.

**Neu für 1.3.8:** Android-Bild-in-Bild für den bestehenden Weiktube-Player,
einschließlich YouTube-Watch-Seiten. Dabei bleibt dieselbe WebView erhalten.
Android 12+ übernimmt den automatischen Übergang bei geeigneter laufender
Wiedergabe; Android 8–11 verwendet den bisherigen Plattformaufruf beim Verlassen
der App. Play/Pause im Systemfenster steuert denselben Player.

**Automatisiert geprüft:** Release-Build, 234 JVM-Tests und die ergänzten
PiP-/Player-Scriptprüfungen bestanden; echte PiP-Gerätetests wurden nicht durchgeführt. YouTube kann die
Wiedergabe browserseitig stoppen. Die Umsetzung täuscht keine Sichtbarkeit vor
und umgeht keine Sicherheits-, DRM- oder Zugangsbeschränkungen. Details:
[Release 1.3.8](RELEASE-1.3.8.md).

**Neu in 1.3.7:** Korrektur des blockierten Gecko-Kaltstarts. Die bestehende Sitzung
wird zuerst leer geöffnet; YouTube lädt erst nach bestätigtem Werbeschutz.
Ein sichtbarer Ladestatus ersetzt die schwarze Wartefläche. Die gespeicherte
Website-Anmeldung bleibt erhalten. Details und Prüfgrenzen: [Release 1.3.7](RELEASE-1.3.7.md).

**Neu in 1.3.6:** Gezielte Korrektur für seitlich verschobene Player-Inhalte nach
der Rückkehr aus dem automatischen Querformat-Vollbild. Die vorhandene WebView
bekommt ihre Bildschirmabstände erneut zugestellt, ohne das Video neu zu laden.
Details und Testgrenzen: [Release 1.3.6](RELEASE-1.3.6.md).

**Neu in 1.3.5:** YouTube-Werbeschutz für den bestehenden Player,
Shorts und die originale Gecko-Startseite. Filter und bekannte Wiedergabefehler
werden im Adblock-Tab angezeigt. Umfang und Prüfgrenzen stehen in
[ADBLOCK-1.3.5.md](ADBLOCK-1.3.5.md). Die Version ist automatisiert geprüft;
die Wirkung gegen aktuelle YouTube-Werbung wurde noch nicht auf einem Handy
abgenommen. Vollständige Werbefreiheit ist nicht garantiert.

Die reguläre öffentliche Version zeigt die originale YouTube-Startseite direkt
in Weiktube. Videos öffnen im bestehenden Weiktube-Player; beim Zurückgehen bleibt
die Startseite erhalten. Der Konto-Einstieg öffnet sich einmal automatisch und ist
überspringbar. Anmeldung und Kontowechsel bleiben unter **Du** erreichbar.

**[APK für Android-Handys herunterladen](https://github.com/weikiboy-tech/Weiktube/releases/latest/download/Weiktube.apk)**

- Android 8 oder neuer, **ARM64**. Über eine vorhandene Weiktube-Version installieren,
  um die Website-Anmeldung zu behalten.
- **[x86-64-APK](https://github.com/weikiboy-tech/Weiktube/releases/latest/download/Weiktube-x86_64.apk)**
  für passende Intel-/Emulator-Geräte. Keine Unterstützung für reine 32-Bit-Geräte.
- Updates über **Einstellungen & Updates → Jetzt nach Updates suchen**. Android
  bestätigt die Installation; die App installiert Updates nicht still im Hintergrund.
- [Release 1.3.9](RELEASE-1.3.9.md), [Release 1.3.8](RELEASE-1.3.8.md), [Adblock-Prüfbericht](ADBLOCK-1.3.5.md), [Quellcode herunterladen](https://github.com/weikiboy-tech/Weiktube/releases/latest/download/Weiktube-Quellcode.zip).

Die Website verwendet das dort angemeldete Konto. Die optionale Google-API-Freigabe
für Abos/Playlists ist separat; die öffentliche APK-Veröffentlichung erweitert
keine Google-Cloud-Testnutzerfreigaben. Deren öffentlicher Freigabestatus ist nicht
verifiziert. Weiktube ist ein eigenständiges Projekt, keine offizielle YouTube-App.

### 🧪 Experimental Software – We don't give a fuck (mostly)

Weiktube ist ein unabhängiges, experimentelles Hobbyprojekt und kein fertig
ausgereiftes kommerzielles Produkt. **Production ready? Absolutely fucking not.**
Built with curiosity, questionable decisions and an unreasonable amount of debugging.

> Weiktube ist experimentelle Software. Bugs sind keine Überraschung, sondern Teil
> des Erlebnisses. Kommentare funktionieren nicht? Uns doch egal.

**Known bugs? Yes. Do we care? Depends on the bug.** Ja, wir wissen, dass Bugs
existieren. Nein, wir werden nicht jeden winzigen Schönheitsfehler bis zur Rente
wegpolieren. Solange Videos laufen und die App ihren Hauptzweck erfüllt, sind wir
erst mal glücklich. Der schiefe Pixel darf vorerst mietfrei wohnen.

- Kommentare hinzufügen funktioniert nicht? Bei dieser Komfortfunktion geben wir
  ehrlich gesagt **0 Fick** auf einen perfekten YouTube-Nachbau.
- Irgendein YouTube-Feature fehlt? Willkommen bei experimenteller Software.
  Die Vollständigkeitsabteilung besteht aus einem leeren Stuhl.
- Gestern ging's, heute nicht mehr? YouTube hat vermutlich wieder irgendwas
  geändert. Überraschung, wir stehen nicht auf deren Verteiler.

Wer eine perfekte App erwartet, ist hier eindeutig falsch. Wer eine experimentelle
YouTube-Alternative mit eingebautem AdBlock-Tab ausprobieren möchte: herzlich willkommen.
Den tatsächlichen Filterumfang findest du unter [Adblock und Datenschutz](#adblock-und-datenschutz).
**Bug reports are welcome. Whether we fix them is another story.**

**😂 Bitcoin-Miner-Easter-Egg:**

„Wir haben übrigens unser Bestes gegeben, damit der eingebaute Bitcoin-Miner
möglichst wenig RAM verbraucht. 😉“

**Das ist ein Witz. Weiktube enthält keinen Bitcoin-Miner.** Keine Mining-Funktion,
kein heimlicher Nebenjob für dein Handy. Der Miner existiert ausschließlich in
diesem schlechten Witz.

**Wo der Spaß aufhört:** Sicherheitslücken, Datenschutzfehler, Datenverlust und
schwerwiegende Defekte nehmen wir ausdrücklich ernst. Bitte melden. Das
„juckt uns nicht“ gilt für harmlose Macken und fehlende Komfortfunktionen – nicht
für deine Daten, deine Sicherheit oder eine App, die ihren Hauptzweck nicht erfüllt.

## Vorherige Entwicklungsschritte

### Website-Anmeldung aus 1.3.1

Der [YouTube-Kontoeinstieg](WEBSITE-LOGIN-TEST.md) öffnet sich einmal automatisch
und ist überspringbar. Unter **Du → Deine YouTube-Startseite → Bei YouTube anmelden /
Konto wechseln** bleibt er dauerhaft erreichbar. Die bestehende Gecko-Sitzung
wird verwendet; die Google-Freigabe für Abos und Playlists bleibt separat.
Die einmalige Einrichtung erscheint auch nach dem Update von einer älteren Version.

Die [originale YouTube-Startseite](HOME-TAB-TEST.md) ist direkt im unteren Tab
**Startseite** eingebunden. Dieselbe Gecko-Integration wird als gemeinsamer
Controller verwendet; Videos öffnen weiterhin den bestehenden Weiktube-Player.
Tabwechsel halten die Sitzung an, ohne sie neu zu laden. Die native untere
Navigation bleibt außerhalb der Website. Der bisherige Einstieg unter **Du**
bleibt bis zur Geräteabnahme erhalten und öffnet dieselbe Startseite.

## Beibehaltener Gecko-Rückkehr-Fix aus 1.2.9

Der [Gecko-Progress-Fix](GECKO-RETURN-TEST.md) behebt den in 1.2.8 noch bestehenden
Verlust der Seitenadresse und Klickverbindung nach der Player-Rückkehr. Der
geparkte Lade-Callback bleibt aktiv, damit Gecko keine künstliche `about:blank`-
Initialnavigation erzeugt. Der eigentliche Laufzeitfix betrifft eine Zuweisung
in `OriginalHomeReturn.kt`; Player, Klick-Bridge und Activity-Navigation bleiben
unverändert. Fehlernachweis und Testgrenzen stehen im neuen Bericht.

## Bisheriger Rückkehr-Fix aus 1.2.8 (allein nicht ausreichend)

Der [gezielte Rückkehr-Fix](PLAYER-RETURN-TEST.md) behandelt die Wiederaufnahme der
Klick-Bridge nach dem vorhandenen Player. Er erhält dieselbe Gecko-Sitzung und
führt keine neue Wiedergabeimplementierung oder Startseite ein. Fehlerursachen,
Regressionstests und ausdrücklich offene Gerätetests stehen im Testbericht.

## Beibehaltener Übergabeweg aus 1.2.7

Dieser [gezielte Übergabefix](PLAYER-HANDOFF-TEST.md) aktiviert die Videoüberwachung
vor dem Laden der originalen YouTube-Seite. Videoklicks benötigen keine manuelle
Feed-Bestätigung mehr. Die bestehende MainActivity öffnet über ein internes
ActivityResult weiterhin ihren vorhandenen Player. Für Zurück/Schließen wird das
Gecko-Dokument mit seiner Sitzung im Speicher gehalten statt geschlossen/neugeladen.
SPA-/History-Überwachung und Wiedergabesperren ergänzen die frühe Klickbehandlung.
Die lokale Testabdeckung und die noch offenen Gerätetests stehen im Testbericht.

## Beibehaltener Website-Einstieg aus 1.2.6

Der Kontobereich beginnt mit **YouTube-Startseite öffnen**. Die echte Website nutzt
ihr dauerhaftes Gecko-Profil; eine bereits gültige Website-Sitzung wird wiederverwendet.
Die vorhandene native Google-Freigabe für Abos und Playlists bleibt unabhängig davon
erhalten und wird nur bei Bedarf ausdrücklich angefordert. Es gibt keinen automatischen
Transfer der Google-API-Anmeldung oder der Chrome-Cookies nach Gecko.

Der [Website-first-Test](WEBSITE-FIRST-TEST.md) ergänzt eine gezielte Android-Autofill-
Anfrage für das fokussierte Anmeldefeld und einen optionalen Diagnosebereich ohne
Zugangsdaten. Verfügbare Vorschläge bestimmt der ausgewählte Passwortmanager.
Eine erfolgreiche Anmeldung mit einem echten Konto muss auf dem Gerät geprüft werden.

## Beibehaltener Original-Home-Test aus 1.2.5

Dieser Entwicklungsstand ergänzt einen isolierten
[Original-Home-Test](ORIGINAL-HOME-TEST.md) unter „Du / Konto“: sichtbare originale
YouTube-Webseite in Gecko und ein getrennter Chrome-Custom-Tab-Vergleich.
Maßgeblich ist jeweils das in YouTube aktuell ausgewählte Website-Konto, nicht
ein festgelegter Name oder die Google-API-Anmeldung. Erst nach manueller Bestätigung
der echten angemeldeten Startseite wird die Übergabe an den Originalplayer getestet.
Die bisherige Startseite, Player und Adblock bleiben unverändert. Der frühere
[Konfigurations-/Feed-Test](HOME-CONNECTION-TEST.md) ist kein Bestandteil dieses Ansatzes.

## Erhaltener Ausgangsstand 1.2.3

Der separate GeckoView-Test wurde zurückgenommen. Die Original-App und ihre APK
sind unverändert erhalten. Sicherung, Dateivergleich und die konkrete Grenze der
persönlichen Home-Empfehlungen sind in [RESTORATION.md](RESTORATION.md) dokumentiert.

Android-App mit Kotlin, Jetpack Compose und Material 3. Paket und Namespace: `com.weiktube.app`. Benötigt Android 8.0 oder neuer, einen aktuellen Android System WebView und Internetzugang.

Version 1.2.3 stellt die native Weiktube-Startseite wieder her: eigener Kontokanal, ausdrücklich gerätelokaler Verlauf und echte Uploads der eigenen Abos. Die Anmeldung verwendet Googles offiziellen Android-Konto-/Freigabedialog. Der verworfene Chrome-Custom-Tab-Versuch und dessen Browserbibliothek sind entfernt; Start und Home-Taste bleiben in Weiktube. Der aktuelle Build- und Geräteteststand steht in `TESTING.md`.

**Die verlangten originalen persönlichen YouTube-Empfehlungen innerhalb der App sind weiterhin nicht umgesetzt.** Eigene Abo-Uploads und lokaler Verlauf erfüllen diese Anforderung nicht. Die Rückkehr zur nativen Oberfläche ist keine abgeschlossene Umsetzung des gewünschten Funktionsumfangs.

Native Videokarten öffnen den vorhandenen Weiktube-Watch-Player direkt. Suche, Kanäle und Videos verwenden weiterhin originale YouTube-Webinhalte in internen WebViews. Die native Google-Freigabe erzeugt dort keine YouTube-Webanmeldung und macht deren Empfehlungen nicht automatisch persönlich.

## Bedienung

Die unteren Tabs heißen **Startseite**, **Shorts**, **Adblock**, **Abos** und **Du**. Die native Startseite zeigt den eigenen Kontokanal, „Verlauf auf diesem Gerät“ und „Videos deiner Abos“. **Mit Google anmelden** öffnet den offiziellen Android-Google-Dialog. Es gibt keinen automatischen Custom-Tab-/Browserstart und keine Browser-Symbolleiste über der Startseite.

Ein Tippen auf eine native Abo- oder Verlaufskarte öffnet deren echtes Video im vorhandenen Player. Seine Leiste bietet Minimieren/Vergrößern, Play/Pause, Teilen, Schließen und native Kontoaktionen. Video-Links, lokaler Verlauf, Einstellungen und Updates bleiben über das Appmenü erreichbar.

| Funktion | Umsetzung und Grenzen |
| --- | --- |
| Startseite | Native Ansicht mit eigenem Kontotitel/-avatar, lokal gekennzeichnetem Verlauf und echten Abo-Uploads. Abos werden mit `subscriptions.list(mine=true)` geladen; nur angeforderte Videos aus den zugehörigen Kanälen werden akzeptiert. Keine originalen persönlichen YouTube-Empfehlungen. |
| Suche und Kanäle | Originale mobile YouTube-Seiten in internen WebViews, einschließlich der von YouTube bereitgestellten Suche. Native Google-Autorisierung erzeugt darin keine Login-Cookies. |
| Videos | Native Abo-/Verlaufseinträge und geteilte Videolinks öffnen den vollständigen Watch-WebView. Direkte HTTPS-Videodateien verwenden dessen HTML5-Player. Zugangsbeschränkungen bleiben wirksam. |
| Mini-Player | Dieselbe Videoseite bleibt erhalten. Ab 1.3.3: Video im Hochformat nach unten ziehen; kompaktes 16:9-Fenster unten rechts mit Play/Pause, Schließen und Fortschritt. Ein Tipp vergrößert es wieder. |
| Shorts | Eigene dauerhaft gehaltene Browserinstanz mit YouTubes vertikaler Weboberfläche. Der Wechsel zu Shorts pausiert die normale Videowiedergabe; Audioübergänge gehören zur Geräteabnahme. |
| Kanäle und Playlists | Nach Google-Freigabe werden eigene Kanäle, eigene Playlists und deren Videoeinträge nativ angezeigt, einschließlich freigegebener privater Playlistdaten. Weitere Einträge lassen sich seitenweise laden. Kanäle öffnen den Browser, einzelne Videos die Original-Videoseite. Native Videoaktionen können nach zusätzlicher Freigabe in eine eigene Playlist speichern. Keine native Playlist-Warteschlange oder automatische Playlist-Fortsetzung. |
| Abos und Du | „Abos“ zeigt echte neue Uploads abonnierter Kanäle mit Kanalbildern, Video-Thumbnails und Dauer. Zunächst werden 12 Kanäle mit jeweils bis zu 3 Uploads verarbeitet; weitere Kanäle lassen sich laden. Ergebnisse werden innerhalb derselben Kontositzung 5 Minuten zwischengespeichert. Dies ist ein begrenzter Abo-Uploadfeed, kein vollständiger persönlicher Homefeed. „Du“ zeigt eigene Kanäle und Playlists. |
| Kommentare, Likes und Abonnieren | Die Original-Videoseite behält ihre Kommentare, Aktionen und Empfehlungen. Zusätzlich bietet ein natives Aktionsfenster echte API-Aufrufe für Gefällt mir, Abo ändern, Playlist speichern, Kommentare und Antworten. Es zeigt den tatsächlich geladenen Status; Schreiben benötigt eine zusätzliche Google-Freigabe. Diese neuen Schreibabläufe sind noch nicht auf dem Gerät abgenommen. |
| Lokaler Verlauf | Bis zu 100 im Weiktube-Player geöffnete Videos, abschaltbar und löschbar. Kein synchronisierter Google-/YouTube-Verlauf. |
| Vollbild und Rotation | WebView-Custom-View-Anbindung ohne Neuanlage des Players bei Rotation. |
| Android-PiP | Seit 1.3.8 auch für den bestehenden YouTube-Watch-Player implementiert: dieselbe WebView im Android-Systemfenster, mit Play/Pause-Systemaktionen. Automatischer Übergang nur bei geeigneter laufender Wiedergabe; manuelle PiP-Taste weiterhin vorhanden. Abnahme auf dem Gerät ausstehend; browserseitige Wiedergabebeschränkungen bleiben wirksam. |
| Qualität | Nur Auswahlmöglichkeiten des offiziellen Players bzw. der Webseite; keine erzwungene Qualität. |
| Teilen und Links | Teilen des aktiven Videos und Empfang über „Teilen → Weiktube“. Direktes Öffnen unterstützter YouTube-Links muss unter Android 12+ ggf. in den App-Einstellungen erlaubt werden. |
| Darstellung | Eigene kompakte Kopfzeile und Navigation, Play-Logo sowie System-/Hell-/Dunkelmodus für die native Oberfläche. Die Website steuert ihren eigenen Modus. |

Die Webanpassungen betreffen doppelte mobile Kopf-/Fußnavigation und die Größe der internen Videoseite. Änderungen an YouTubes Webseitenstruktur können diese Ansichten beeinflussen. Der verworfene Browserversuch ist in `AUDIT.md` getrennt vom aktuellen Stand dokumentiert.

## Native Google-Anmeldung

Seit Version 1.2 verwendet Weiktube für native Kontoansichten und Videoaktionen Googles offiziellen Android-`AuthorizationClient` aus Google Play Services. **Mit Google anmelden** auf **Startseite**, **Du** oder **Abos** startet die Google-Kontoauswahl und Freigabe. Zunächst wird nur `https://www.googleapis.com/auth/youtube.readonly` angefordert. Damit liest Weiktube Kanäle, Abos, Playlists, Videoeinträge und Videoaktionen über die YouTube Data API.

Die erste ausdrücklich ausgelöste Schreibaktion fordert zusätzlich `https://www.googleapis.com/auth/youtube.force-ssl` an, soweit noch nicht erteilt. Das gilt für Gefällt mir, Abo ändern, in Playlist speichern, Kommentar oder Antwort senden. Auch die native Kommentarabfrage benötigt eine zusätzliche Freigabe: Dafür gibt es die ausdrückliche Schaltfläche **Kommentare freigeben**, die über den auch zum Schreiben berechtigenden Scope informiert. Bloßes Öffnen des Aktionsfensters löst weder diese Freigabe noch eine Schreibanfrage aus. Erfolg wird erst nach YouTubes Antwort angezeigt; ein angenommener Kommentar kann weiterhin der Kanalmoderation unterliegen. Nach einem unklaren Netzwerkfehler werden Schreibanfragen nicht automatisch wiederholt. Der neue Kommentar-Freigabeweg und die Schreibabläufe sind noch nicht Ende zu Ende auf dem Gerät geprüft.

**Native API-Autorisierung und interne YouTube-WebViews sind getrennte Sitzungen.** Zugriffstokens und Browser-Cookies werden nicht in WebViews übertragen. Die APK enthält keine Anmeldung, Kontodaten oder Sitzung des Testgeräts. Eine frische Installation erhält dadurch keine native Google-Freigabe. Private Playlistmetadaten in der nativen Ansicht garantieren nicht das Abspielen ihrer privaten Videos im Watch-Player. Der Android-Google-Dialog allein verschafft keinen Zugriff auf den originalen persönlichen Empfehlungsfeed.

Passwörter verbleiben bei Google. Zugriffstokens und geladene Kontolisten werden nur im Arbeitsspeicher gehalten; lokal wird lediglich der Verbindungs-/Widerrufsstatus gespeichert. Nach Neustart kann eine zuvor erteilte Berechtigung wieder angefordert werden. Bei einem neuen Kontozugriff werden zuvor geladene Kontolisten geleert. Ein abgelaufener Zugriffstoken wird begrenzt ohne erneuten Dialog erneuert, sofern Google das erlaubt; erforderliche Zustimmung muss der Nutzer selbst bestätigen. Beim Trennen werden lokale Kontodaten entfernt und der Google-Zugriff widerrufen. Scheitert der Widerruf, zeigt die App das an und bietet einen erneuten Versuch; das Google-/YouTube-Konto selbst bleibt bestehen.

Die native Google-Anmeldung, Wiederherstellung der vorhandenen Kontofreigabe und echte Kontodaten wurden auf dem OnePlus geprüft. Der neue Abo-Uploadfeed lieferte Daten aus 9 der ersten 12 verarbeiteten Kanäle; ein Video-Thumbnail öffnete die Original-Videoseite. Native Bewertung und Abostatus wurden gelesen. Verwendet wurde ein ausdrücklich eingetragener persönlicher Testnutzer mit `youtube.readonly`. Playlistabnahme, neue Kommentarfreigabe und Schreibaktionen bleiben offen. Andere Signierschlüssel oder nicht freigegebene Testkonten können abgelehnt werden; den genauen Stand dokumentiert `TESTING.md`.

### Persönlicher Testbetrieb ohne Billing

Vorgesehen ist ein separates Google-Cloud-Projekt ohne verknüpftes Billing-Konto und ohne Aktivierung einer Testphase. Die App aktiviert keine kostenpflichtigen Dienste und fordert keine kostenpflichtige Kontingenterhöhung an. Die YouTube Data API besitzt ein projektbezogenes Kontingent; bei erschöpftem Kontingent zeigt Weiktube einen Fehler. Daraus folgt keine Zusage unbegrenzter API-Nutzung.

Im externen OAuth-Testmodus dürfen nur ausdrücklich eingetragene Testnutzer zustimmen; eine APK-Weitergabe an Freunde schaltet deren Konten nicht automatisch frei. Google begrenzt diesen Testmodus auf bis zu 100 eingetragene Nutzer und kann nach sieben Tagen erneut Zustimmung verlangen. Eine breitere Veröffentlichung ist gesondert zu konfigurieren und kann eine Google-Prüfung erfordern. Siehe [Google: App-Zielgruppe](https://support.google.com/cloud/answer/15549945?hl=en) und [YouTube API-Kontingente](https://developers.google.com/youtube/v3/guides/quota_and_compliance_audits).

## Adblock und Datenschutz

Der Adblock-Tab enthält Weiktube Shields: eine kleine lokale Domainfilter-Engine mit getrennten Basislisten für Werbe- und Trackingdomains, eigenen Listen, Ausnahmen und einem Sitzungszähler. Unterstützt werden reine Domains und Hosts-Dateiformat mit `#`-Kommentaren, bis 256 KiB pro Liste. Domainsegmente und Subdomains werden berücksichtigt; Ausnahmen gelten für Hauptseite oder angefragte Domain.

Ab 1.3.5 gibt es zusätzlich **YouTube-Werbefilter**: ausgewählte AdGuard-Scriptlets
entfernen bekannte Werbefelder aus anfänglichen Playerdaten sowie passenden
JSON-, Fetch- und XHR-Antworten. Eng begrenzte Netzwerkregeln und kosmetische
Filter für explizite Werberenderer ergänzen sie. Dieselbe Filterversion wird vor
dem Seitenstart in Player/Shorts-WebView und GeckoView eingebunden. Die vorhandene
Wiedergabe, Kontositzung und Videoübergabe bleiben bestehen.

Bei einem erkannten Werbedurchbruch pausiert und verdeckt der Schutz die Anzeige.
Der Player versucht einmal, das Video geschützt neu zu laden; danach erscheint
ein Fehler statt einer stillen Abschaltung des Schutzes. Eine neue Videoauswahl
oder „Erneut laden“ ist weiter möglich. Das ist **keine Garantie für dauerhaft
werbefreie oder stets erfolgreiche Wiedergabe**: unbekannte Werbevarianten,
serverseitig eingefügte Werbung und Änderungen durch YouTube können die Filter
überfordern. Sponsoring durch den Creator wird nicht übersprungen.

Der Adblock-Tab zeigt Filterversion, Fehler und getrennte Sitzungszähler. Ein
Zähler ist kein Beleg für vollständige Werbefreiheit. Filterupdates werden
höchstens einmal täglich oder manuell über HTTPS geladen, mit einem eingebauten
öffentlichen Schlüssel geprüft und nur nach erfolgreicher Prüfung übernommen.
Bei Fehlern bleibt der letzte gültige Stand erhalten. Updates enthalten nur
begrenzte Regeldaten, keinen nachgeladenen ausführbaren Code. Neue Regeln und
geänderte Einstellungen gelten beim nächsten vollständigen Seitenaufruf;
vorhandene Dokumente behalten ihre Regeln bis zum Neuladen. Globale Schalter
und Domainausnahmen sind weiterhin verfügbar. Es ist keine vollständige
AdGuard-/Brave-Engine und kein allgemeiner ABP-/EasyList-Importer.

Quellcode, GPL-3.0-Lizenz, exakte Upstream-Quellen und Generator werden mitgeliefert;
siehe [Drittanbieterhinweise](THIRD-PARTY-NOTICES.md). Sitzungs-Cookies, OAuth-Tokens,
Passwörter und vollständige authentifizierte Antworten werden vom Werbeschutz
weder protokolliert noch an den Filterupdate-Server übertragen.

Die lokale Shields-Prüfseite fordert genau eine Ressource von der reservierten Testdomain `ads.weiktube.invalid` an. Bei aktiviertem Werbefilter steigt der Sitzungszähler um eins; mit deaktiviertem Filter bleibt er unverändert.

Drittanbieter-Cookies, HTTP-Klartext, Mixed Content und lokale Datei-/Content-Zugriffe sind in den WebViews deaktiviert. Zertifikatsfehler werden nicht übergangen. Eng begrenzte, auf YouTube-Ursprünge beschränkte Web-Nachrichten dienen der Linknavigation; Google-Tokens und Passwörter werden darüber nicht übertragen. Es gibt keine Analyse- oder Werbe-SDKs. Webseiten und YouTube erhalten dennoch Netzwerk- und technische Gerätedaten; die App macht YouTube-Nutzung nicht anonym.

Einstellungen und lokaler Verlauf bleiben im privaten Appspeicher. WebView-Cookies, Cache und Webspeicher lassen sich separat löschen; native Google-Freigabe und Webdaten bleiben getrennte Zustände. Backups der Appdaten sind ausgeschlossen. Wiedergabesperren sollen unsichtbare und konkurrierende Wiedergabe verhindern; sämtliche Fehler-/Hintergrundfälle sind noch nicht abgenommen. Nach Prozessbeendigung ist eine unterbrechungsfreie Wiederherstellung nicht garantiert.

## Automatische App-Updates

Unter **Einstellungen & Updates** prüft Weiktube standardmäßig beim Öffnen bzw. Zurückkehren in den Vordergrund höchstens einmal pro Tag auf eine neuere Version. Die Prüfung lässt sich ausschalten und jederzeit manuell auslösen. Es läuft kein dauerhafter Hintergrunddienst; APKs werden nicht ungefragt heruntergeladen oder installiert.

Standardquelle:

[Update-Feed des GitHub-Projekts](https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json)

Die HTTPS-Quelle ist konfigurierbar. Damit Updates verfügbar sind, muss das Repository eine veröffentlichte Release mit passender `update.json` und APK enthalten. Eine nicht erreichbare Quelle wird als Fehler angezeigt.

Vor der Installation werden SHA-256, Android-Paketname, Versionscode und der Signierschlüssel gegen die installierte App geprüft. Downloads sind begrenzt; unsichere Weiterleitungen werden abgelehnt. Ein Update wird nur als neu angeboten, wenn sein Versionscode höher ist. Android verlangt die Freigabe „Apps aus dieser Quelle“ und anschließend eine Installationsbestätigung. Keine stille Installation und kein Austausch fremder Pakete.

Die App benötigt INTERNET und REQUEST_INSTALL_PACKAGES für diese Funktion sowie eine von AndroidX erzeugte interne Signaturberechtigung. Kamera-, Mikrofon-, Standort-, allgemeine Speicher- und Overlay-Berechtigungen werden nicht angefordert.

## Projekt und Nachweise

- `BUILD.md`: Build, Installation und Veröffentlichung eines Updates.
- `TESTING.md`: automatisierte Prüfungen und dokumentierter Stand der Gerätetests.
- `AUDIT.md`: Funktionsvergleich, Plattformgrenzen und offene Abnahmeziele.
- `app/src/test`: Tests für Links, Filter, Updates, Navigation und API-Aktionsregeln.
- `UpdateController.kt` / `UpdateUi.kt`: Updater und Oberfläche.
- `YoutubeAccountController.kt` / `AccountUi.kt`: native Google-Freigabe und schreibgeschützte YouTube-Kontoseiten.
- `VideoActionsController.kt` / `VideoActionsUi.kt`: echte native Videoaktionen mit zusätzlicher Schreibfreigabe.
- `SubscriptionFeedController.kt` / `SubscriptionsUi.kt`: echte Uploads abonnierter Kanäle mit begrenztem Sitzungs-Cache.
- `PersonalHomeUi.kt`: native Startseite mit eigenem Kontokanal, lokalem Verlauf und echtem Abo-Uploadfeed.
- `MainActivity.kt`: Android-Lifecycle, Navigation, Intents, Vollbild und PiP.
- `WeiktubeUi.kt`: Compose-Oberfläche, Tabs, Player und Einstellungen.
- `BrowserController.kt` / `PlayerController.kt`: dauerhaft gehaltene Browser- und Player-WebViews.
- `ShieldEngine.kt`, `Links.kt`, `WeiktubeModel.kt`: Filter, Quellenvalidierung und lokale Daten.

Technische Referenzen: [Google-Autorisierung unter Android](https://developer.android.com/identity/authorization), [YouTube Data API](https://developers.google.com/youtube/v3), [YouTube-Playeranforderungen](https://developers.google.com/youtube/terms/required-minimum-functionality), [Android Picture-in-Picture](https://developer.android.com/develop/ui/views/picture-in-picture), [YouTube-Nutzungsbedingungen](https://www.youtube.com/t/terms), [Google-Datenschutz](https://policies.google.com/privacy).

Weiktube ist nicht mit YouTube, Google oder Brave verbunden. Die APK ist ein Debug-Build, keine Veröffentlichung im Play Store. Technische und kontoabhängige Einschränkungen sind kein Nachweis einer 1:1-Funktionsgleichheit mit der offiziellen YouTube-App.






