# Optionale externe Dislike-Anzeige

Return YouTube Dislike ist standardmäßig aus. Erst nach Bestätigung im Einstellungsdialog
werden öffentliche Video-IDs an `https://returnyoutubedislikeapi.com/votes` gesendet.
Wie bei jeder direkten Internetverbindung sieht der Anbieter die IP-Adresse und einen
allgemeinen App-User-Agent. Google-Zugangsdaten, Google-Cookies, OAuth-Tokens und Nutzer-
Bewertungen werden nicht mitgesendet. Es gibt keine Registrierung beim Dienst.

Zahlen sind teilweise Schätzungen, keine garantierten offiziellen YouTube-Daten.
Ergebnisse und Rate-Limit-Zähler werden lokal in privaten App-Einstellungen gespeichert.
Der Cache ist auf 200 Video-IDs begrenzt; erfolgreiche Werte gelten eine Stunde, Fehler fünf
Minuten. Ausschalten beendet weitere Abrufe; bereits gesendete Anfragen lassen sich nicht
zurücknehmen. Ein bestehender lokaler Cache enthält keine Google-Zugangsdaten.

Das bestehende Laden von YouTube-Seiten, Anmeldung, Updates, Filtern und lokalem Verlauf
ändert sich durch diese Option nicht. Weitere technische Details und Grenzen stehen im
[Advanced-Player-Bericht](docs/ADVANCED-PLAYER.md).

## SponsorBlock (optional, standardmäßig aus)

Nach ausdrücklicher Aktivierung: GET an sponsor.ajay.app mit vier SHA-256-Präfixzeichen der öffentlichen Video-ID, Kategorien und service/actionType. Der Dienst sieht die IP-Adresse; Hash-Präfixe garantieren keine Anonymität. Keine Google-Anmeldedaten, Browser-Cookies, Bewertungen, Einreichungen oder View-Tracking. Zeitbegrenzter lokaler Cache (bis 64 Videos) und lokale Skip-Statistik sind separat löschbar. Details und Attribution: [SponsorBlock](docs/SPONSORBLOCK.md).
