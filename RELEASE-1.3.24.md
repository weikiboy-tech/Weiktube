# Weiktube Android 1.3.24

Wiederholte Drehungen konnten eine verspätete Messung der Videofläche aus einer früheren Hochformatansicht übernehmen. Jede neue Orientierungsanforderung, Konfigurationsänderung, geänderte WebView-Größe und Präsentationsänderung entwertet jetzt ausstehende Messungen. Beim Empfang überprüft der Player zusätzlich das Seitenverhältnis des Web-Viewports. Das auslaufende Ende einer Miniplayer-Animation gehört jeweils zu ihrem eigenen Übergang. Eine vorübergehend auf null geschrumpfte Zielfläche gibt ihre alte Transformation frei.

Beim verspäteten Vollbild-Callback prüft die Activity zusätzlich, ob die Oberfläche bereits einen Parent hat. Der alte addView-Aufruf wirft in diesem Fall eine IllegalStateException. Ein synchroner Rückruf beim Schließen erhält den neuen Orientierungswunsch.

Die bestehenden Animationen und die Wiedergabe laufen in derselben WebView weiter. Die genaue Ursache des auf dem OnePlus beobachteten Absturzes bleibt offen. Für den Vorfall liegt kein Stacktrace vor.

## Prüfung auf dem OnePlus 15R

1. Video öffnen und während der Wiedergabe rasch zwischen Hoch- und Querformat wechseln. Auch vor Ende der Animation zurückdrehen.
2. Mit pausiertem und vergrößertem Bild wiederholen. Während einer Drehung minimieren, anschließend vergrößern und System-PiP öffnen und schließen.
3. Während der Drehung den Vollbildknopf benutzen. Bildschirm sperren, entsperren und ein weiteres Video öffnen.
4. Bei erneutem Versatz oder Absturz Uhrzeit merken. Nach dem Öffnen unter Einstellungen „Lokale Rotationsdiagnose teilen“ wählen. Das lokale Protokoll enthält bis zu 64 Ereignisse sowie auf Android 11 und neuer die letzten vom System erfassten Prozess-Endgründe. Es enthält keine Video-URLs oder Kontodaten.

Eine reale OnePlus-Prüfung, Langzeitwiedergabe und Messung der Bildzeiten stehen aus.

## Automatisierte Prüfung

app: 314 Tests, home-data: 78 Tests, content-filter: 20 Tests. Die abschließende Gradle-Prüfung war erfolgreich. 21 JavaScript-/Browserprüfungen bestanden. Release-Lint meldet 0 Fehler, 41 Warnungen und einen Hinweis.

Robolectric simuliert 100 Layout-Rundreisen mit verspäteten DOM-Antworten, 100 Wechsel mit unterbrochenen und kurzzeitig nullgroßen Layouts sowie 100 unterbrochene Compose-Animationen mit Zwischenframes. Weitere Regressionstests prüfen veraltete Miniplayer-Abschlüsse und bereits angehängte Vollbildflächen. Die Messungen erfassen simulierte Android-Layouts und Animationswerte; die Bildausgabe auf OxygenOS bleibt offen. In einem Zwischenlauf lief ein bestehender Verlaufstest in ein Timeout; der abschließende Gesamtlauf bestand.
