# Website zuerst: Anmeldung und Autofill testen

Historischer Stand 1.2.6. Die anschließende Korrektur der Videoübergabe und des
Rückwegs ist in [PLAYER-HANDOFF-TEST.md](PLAYER-HANDOFF-TEST.md) dokumentiert.

Version 1.2.6-website-first-test, Android-Versioncode 10, Paket `com.weiktube.app`.
Diese Testversion setzt den Original-Home-Test aus 1.2.5 fort. Sie ersetzt weder
die bisherige Startseite noch Player, Miniplayer, Navigation oder Adblock-Engine.

## Ablauf

1. Unter **Du / Konto** **YouTube-Startseite öffnen** wählen. Es öffnet sich die echte
   mobile YouTube-Website in der vorhandenen Gecko-Ansicht mit gespeichertem Profil.
2. Ist die Website bereits angemeldet, verwendet YouTube dieses Konto. Andernfalls
   die reguläre Website-Anmeldung über **Konto** oder **Mein YouTube** öffnen.
   Eine Anmeldung in der nativen Kontokarte allein erzeugt keine Website-Sitzung.
3. Auf der echten Anmeldeseite das E-Mail- oder Passwortfeld antippen. Der ausgewählte
   Android-Passwortmanager kann Vorschläge anbieten. **Zugangsdaten vorschlagen**
   fragt sie ausdrücklich für das tatsächlich fokussierte Website-Feld an.
4. Falls keine Vorschläge erscheinen, über **Info** die optionale **Autofill-Diagnose** öffnen.
   Sie unterscheidet fehlende Android-Unterstützung, deaktiviertes Autofill,
   fehlenden Feldfokus und eine nicht verfügbare Vorschlagsoberfläche.
   Daraus folgt keine Aussage, ob ein Passwort gespeichert ist.
5. Nach der Anmeldung das aktuelle YouTube-Profil und die tatsächlich gelieferten
   Vorschläge prüfen. Der Name ist nicht fest vorgegeben. Eine leere Home-Seite ist
   allein kein Beleg für eine Abmeldung, beispielsweise bei deaktiviertem Verlauf.
6. Schließen und neu öffnen. Solange Google die gespeicherte Sitzung akzeptiert,
   wird keine erneute Website-Anmeldung benötigt. Eine separate Testbestätigung
   unter **Test** ist kein Login und wird nicht als technischer Login-Nachweis gespeichert.
7. Erst nach sichtbarer Feed-Prüfung wie bisher die Übergabe an den Originalplayer
   aktivieren und ein Video antippen. Die Videoübergabe selbst wurde nicht neu gebaut.

## Native Abos und Playlists

**Abos und Playlists freigeben** ist die nachgeordnete, unabhängige Google-API-
Freigabe. Vorhandene Freigaben werden unverändert ohne Dialog wiederhergestellt,
soweit Google das erlaubt. Neue Freigaben erfolgen ausschließlich nach Nutzeraktion
über den vorhandenen Android-AuthorizationClient, nicht über eingebettetes OAuth.

Die native Kontokarte bezeichnet ausdrücklich das Konto für Abos und Playlists.
Es kann ein anderes Konto als in der Website ausgewählt sein. Die App behauptet
keine automatische Synchronisierung und liest keine Website-Credentials aus.
Der Website-Kontowechsel erfolgt direkt bei YouTube. Eine API-Freigabe zu widerrufen
löscht das Gecko-Profil nicht; **Webdaten löschen** bleibt eine ausdrückliche Aktion.

## Autofill und Grenzen

Gecko hatte Autofill bereits standardmäßig aktiviert. Diese Version ergänzt feste
IDs für Haupt- und Popup-Ansicht, eine explizite Autofill-Eignung und eine gezielte
Anfrage über `AutofillManager.requestAutofill(view, virtualId, screenBounds)`.
Geckos eigener Delegate und die vom Browser gelieferten virtuellen Feld-IDs bleiben
maßgeblich. Keine JavaScript-Injektion in Anmeldeformulare, keine erfundenen IDs
und kein Zugriff auf Formularwerte. Eine veraltete Ansicht oder ein Hintergrund-
Dokument darf keine Vorschläge anfordern oder Diagnose-Erfolg auslösen.

Diagnoseinformationen beschränken sich auf Unterstützung/Aktivierung, aktiven
Ansichtstyp, Feldfokus und die Android-Ereignisse angezeigt/verborgen/nicht verfügbar.
Sie enthalten keine E-Mail-Adressen, Passwörter, Cookies, Tokens, vollständigen URLs,
Website-Antworten oder Formulardaten und werden nicht automatisch exportiert.

Der Passwortmanager entscheidet, welche Vorschläge er in diesem Kontext liefert.
Ein auf Android verbundenes Google-Konto bedeutet nicht, dass passende Zugangsdaten
gespeichert oder für diese Ansicht verfügbar sind. Drittanbieter-Passkeys werden
nicht durch zusätzliche Berechtigungen oder eine vorgetäuschte Browseridentität
freigeschaltet. Google kann eine eingebettete Website-Anmeldung ablehnen; die
Fehlermeldung bleibt sichtbar und gilt als Blockade, nicht als erfolgreicher Login.
Der separate Chrome-Vergleich bleibt optional und überträgt keine Cookies nach Gecko.

## Geräteabnahme

- Bestehende API-Freigabe bleibt erhalten; Website ohne API-Freigabe erreichbar.
- Frisches Gecko-Profil: reguläre Google-Anmeldung, Abbruch und sichtbare Ablehnung.
- Passwortmanager aktiviert/deaktiviert; E-Mail- und Passwortfeld; Hauptansicht und
  Popup; kein Fokus; fehlende Vorschläge; Feld-/Dokumentwechsel während einer Anfrage.
- Zwei-Faktor-Bestätigung in einer anderen App, anschließende Rückkehr zur Anmeldung.
- Website-Sitzung nach Schließen, Hintergrundwechsel, Prozessneustart und APK-Update.
- Zwei unterschiedliche Website-/API-Konten und Website-Profilwechsel.
- Neue API-Freigabe, abgelehnte Freigabe und vorhandene stille Wiederherstellung.
- Ausdrückliches Löschen der Webdaten, abgelaufene Sitzung und erneute Anmeldung.
- Echter persönlicher Home-Feed, Scrollen/Nachladen und vorhandene Player-Übergabe.
- Originalplayer, Miniplayer, Zurück, Vollbild, Navigation und Adblock unverändert.

Kein Smartphone steht in dieser Entwicklungsumgebung zur Verfügung. Lokale
Tests, Build und Lint ersetzen weder den Google-Anmeldenachweis noch den Test des
Passwortmanagers auf dem OnePlus. Der stabile Updatefeed bleibt unverändert.

## Lokale Prüfungen

Stand 3. November 2026: finaler Gradle-Build erfolgreich (58 Sekunden), 125 JVM-Tests
bestanden (81 App, 44 home-data), keine Fehler oder übersprungenen Tests. Darunter
sieben neue Tests für Autofill-Zustandswechsel. Lint: App 0 Fehler / 20 bestehende
Warnungen, home-data 0 Fehler / 65 bestehende Warnungen; keine neue Autofill-Warnung.

Die Player-, Browser-, Watch- und Original-Home-Klickbrücken-Fixtures bestanden.
Der Schutzcheck verifiziert weiterhin alle 46 ursprünglichen App-Quelldateien mit
eng begrenzten Ausnahmen für die bereits vorhandenen Einstiegshooks und die jetzt
geprüften Anmeldungstexte/Schaltflächen. Seine ursprünglichen Baseline-Hashes sind
unverändert. Der API-Controller wurde ausschließlich sprachlich angepasst.

HomeEngine, Authentifizierungsregeln, Hintergrund-2FA-Regeln, Experiment-Lease und
beide Website-Erweiterungen sind unverändert. Beide Erweiterungen in der finalen
APK stimmen bytegenau mit dem geprüften Quellcode überein. Der Signierschlüssel
entspricht der Original-App. Die erhaltene Original-APK `Weiktube.apk` bleibt
bytegenau unverändert; die neue Test-APK heißt `Weiktube-Home-Test.apk`.

Test-APK: 207.289.171 Bytes, SHA-256
`45afcf4068bdf661a3463c6b0a6963ffd9ae7fdefc3e481db2c831703d57a4b0`.

Vor Änderungen wurden 98 Quelldateien in einem separaten lokalen Backup-Repository
auf Branch `backup/website-first-before-1.2.6` gesichert (Commit
`10412a89ec2f08b7757c16ad9ca418a97a5f8939`); vorherige Test-APK und Quellcode-ZIP
liegen ebenfalls in der lokalen Sicherung. Das gemischte Workspace-Repository und
fremde Projektänderungen wurden nicht verändert. Keine Projektdatei wurde gelöscht.
Quellcodepakete enthalten nur erlaubte Projektdateien, keine Geräte- oder Kontodaten.

## Quellen

- [Google: API-Autorisierung und stille Wiederverwendung](https://developer.android.com/identity/authorization)
- [Android: AutofillManager und virtuelle Eingabefelder](https://developer.android.com/reference/android/view/autofill/AutofillManager)
- [Mozilla: GeckoView-Autofill](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/GeckoView.html#setAutofillEnabled(boolean))
- [Google: mögliche Ablehnung eingebetteter Anmeldung](https://support.google.com/accounts/answer/7675428)
- [Google: Drittanbieter-Credentials für privilegierte Apps](https://developer.android.com/identity/sign-in/privileged-apps)
