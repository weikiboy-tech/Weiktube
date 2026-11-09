# Weiktube 1.3.14 â€“ Mini-Player und Ton

- Bestehende Player-WebView behÃ¤lt im internen Mini und Android-PiP ihre logische LayoutgrÃ¶ÃŸe. Das sichtbare Video wird nativ proportional skaliert und zentriert, ohne neue Quelle oder neuen Player.
- Der bestÃ¤tigte Tonfix bleibt erhalten: einmaliger Tonstart bei expliziter Videoauswahl; keine Play-Schleife, keine Ã„nderung an YouTubes FreigabeprÃ¼fungen.
- Lokaler Verlauf im Du-Tab, inklusive LÃ¶schbestÃ¤tigung und stabiler Reihenfolge bei spÃ¤teren Titelmeldungen.
- Gecko-Startseite, Kontoanbindung, AdBlock und bestehende Playersteuerung bleiben erhalten.

## TatsÃ¤chlich geprÃ¼ft und Grenzen

240 App-JVM-Tests einschlieÃŸlich fÃ¼nf neuer Geometrie-/Ãœbergangstests bestanden vor der Release-Paketierung; 19 Player-Script-Fixtures und der Originalquellcode-Scopecheck bestanden.
Auf dem OnePlus wurde ein echtes Startseitenvideo im internen Mini bei unverÃ¤nderter WebView-/Dokumentinstanz, fortschreitender Wiedergabe und nicht stummgeschaltet beobachtet. Die Zentrierung wurde visuell geprÃ¼ft. Der Nutzer bestÃ¤tigte anschlieÃŸend: â€žaber es klappt jetzt!â€œ und gab diesen Release frei.

Ein automatisches ZurÃ¼ckvergrÃ¶ÃŸern wurde wÃ¤hrend der GerÃ¤tetests beobachtet; dessen Ursache ist noch nicht geklÃ¤rt. Die vollstÃ¤ndige Abnahmematrix mit fÃ¼nf Startseitenvideos, zehn Wechseln je Modus und jeweils fÃ¼nf Minuten ununterbrochener Mini-/Android-PiP-Wiedergabe ist **nicht vollstÃ¤ndig durchgefÃ¼hrt bzw. nachgewiesen**. Dieser Release behauptet daher keinen lÃ¼ckenlosen PiP-Langzeitnachweis.

Die APKs verwenden dieselbe Signatur und Paketkennung wie bisher. VersionCode 28; ARM64 fÃ¼r das OnePlus, zusÃ¤tzlich x86_64. APKs und Update-Manifeste werden vor VerÃ¶ffentlichung auf GrÃ¶ÃŸe, SHA-256 und Signatur geprÃ¼ft. Keine Kontodaten, GerÃ¤tescreenshots oder SignierschlÃ¼ssel im Quellcodepaket.
