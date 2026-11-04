# Weiktube 1.3.5 – YouTube-Werbeschutz

Version 1.3.5, Versioncode 19, Paket `com.weiktube.app`. Diese Veröffentlichung
ergänzt die vorhandene Wiedergabe um YouTube-Werbefilter. Die originale
personalisierte Gecko-Startseite, gespeicherte Website-Sitzung, Videoübergabe und
App-Navigation bleiben erhalten.

## Enthalten

- YouTube-Werbefilter im bestehenden Player, in Shorts und auf der Gecko-Startseite:
  ausgewählte AdGuard-Scriptlets entfernen bekannte Werbedaten; begrenzte
  Netzwerkregeln und kosmetische Filter ergänzen den Schutz.
- Bei erkannter Werbung: pausieren, stummschalten und verdecken; der Player versucht
  einmal, geschützt neu zu laden. Bleibt das Problem bestehen, erscheint ein
  Fehler. Der Schutz wird nicht still ausgeschaltet.
- Adblock-Tab mit Filterversion, begrenzten Diagnosezählern und manueller
  Updateprüfung. Signierte Filterupdates werden höchstens einmal täglich beim
  Vordergrundwechsel geprüft. Ungültige Updates ersetzen keine gültigen Regeln.
- Vorherige Bedienverbesserungen sind enthalten: im Hochformat zum kompakten
  Miniplayer herunterziehen, korrigiertes Schließen über X, automatisches
  Vollbild im Querformat und Rückkehr zur normalen Darstellung im Hochformat.
- Keine neue Player-Implementierung, keine mitgelieferte Kontositzung und kein
  Überspringen von Creator-Sponsoring.

**Die App bleibt experimentell.** Unbekannte Werbeformen und Änderungen durch
YouTube können Filter umgehen oder die Wiedergabe beeinträchtigen. Eine dauerhaft
werbefreie und immer erfolgreiche Wiedergabe wird nicht versprochen.

## Download und Installation

**[Weiktube.apk für Android-Handys (ARM64)](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.5/Weiktube.apk)**

[x86-64-APK](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.5/Weiktube-x86_64.apk)
für passende Intel-/Emulator-Geräte. Android 8 oder neuer; keine Unterstützung
für reine 32-Bit-Geräte. Beide APKs sind vollständige Einzeldateien unter dem
vorhandenen Updaterlimit von 128 MiB.

Über die vorhandene Weiktube-Version installieren, **nicht vorher deinstallieren**,
wenn die Website-Anmeldung erhalten bleiben soll. Das ursprüngliche
Signierzertifikat bleibt erhalten. Neue Installationen enthalten keine Anmeldung
eines anderen Nutzers.

Unter **Einstellungen & Updates → Jetzt nach Updates suchen** verwendet die App
den [regulären Update-Feed](https://github.com/weikiboy-tech/Weiktube/releases/latest/download/update.json).
Er enthält Versioncode 19, SHA-256-Prüfsummen und beide Architekturvarianten.
Android bestätigt die Installation; es gibt keine stille Installation.

Die getrennte [signierte Filterquelle](https://github.com/weikiboy-tech/Weiktube/releases/tag/adblock-rules-v1)
liefert ausschließlich begrenzte Regeldaten. Änderungen an ausführbarem Filtercode
benötigen eine neue APK. Bestehende Dokumente behalten ihre Filter bis zum nächsten
vollständigen Seitenaufruf, ohne die Gecko-Sitzung zu löschen.

## Tatsächlich geprüft

- **202 JVM-Tests bestanden:** 110 App, 72 Home, 20 Filter; keine Fehler oder
  übersprungenen Tests.
- JavaScript-Prüfungen für die tatsächlichen Scriptlets, JSON-/Fetch-/XHR-Filter,
  Auth-/CDN-Ausnahmen, Gecko-Registrierung, Statusmeldungen und Fehlerpfade bestanden.
- Vorhandene Navigation-, Rückkehr-, Miniplayer- und Rotation-Fixtures bestanden,
  einschließlich zehn simulierter Zustandswechsel.
- Release-Build für ARM64 und x86-64 erfolgreich; Lint: **0 Fehler, 70 Warnungen**.
- APK-Assets entsprechen dem geprüften Quellstand. Signatur und ARM64-
  Paketmetadaten wurden geprüft. Die öffentlichen Filterdateien wurden ohne
  Anmeldung heruntergeladen und bytegenau gegen das geprüfte Paket verglichen.

**Keine Gerätetests für diesen Adblock-Stand durchgeführt.** Reale Preroll-,
Midroll- und Shorts-Werbung sowie aktuelle YouTube-Anmeldung und Touch-/Rückkehr-
Abläufe wurden für 1.3.5 nicht auf einem Handy abgenommen. Automatisierte Fixtures
sind kein Nachweis vollständiger Werbefreiheit. Details: [Prüfbericht](https://github.com/weikiboy-tech/Weiktube/blob/v1.3.5/ADBLOCK-1.3.5.md).

## Quellcode und Lizenzen

[Vollständiger bereinigter Quellcode](https://github.com/weikiboy-tech/Weiktube/releases/download/v1.3.5/Weiktube-Quellcode.zip)
mit GPL-3.0-Lizenz, Herkunftsnachweisen, exakten AdGuard-Upstream-Quellen und
reproduzierbarem Generator. Siehe [Drittanbieterhinweise](https://github.com/weikiboy-tech/Weiktube/blob/v1.3.5/THIRD-PARTY-NOTICES.md).
Private Signierschlüssel, Zugangsdaten, Cookies und Gerätebilder sind ausgeschlossen.

Die optionale Google-API-Freigabe für Abos/Playlists bleibt von der Website-
Anmeldung getrennt. Diese Veröffentlichung ändert keine Google-Cloud-Freigaben.
