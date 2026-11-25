1.3.23 korrigiert die Startfläche der Miniplayer-Bewegung, den Android-PiP-Bildausschnitt und die Bedienung des Vollbildknopfs bei automatischer Rotation. Die visuelle Abnahme auf dem OnePlus 15R steht aus.

Die verkleinerte Karte begann bisher mit den Abmessungen der ganzen Watch-Seite. Jetzt meldet das Videoelement seine Lage relativ zur WebView. Der Host berücksichtigt das Seitenverhältnis und interpoliert die Bildränder zur Karte. Vor der ersten Messung verwendet er die Geometrie des Watch-Layouts. Beim Minimieren im Querformat bleibt die Orientierungsanforderung bis zum Ende der Kartenbewegung erhalten. Die Fingerbewegung wird in Bildschirmrichtung übertragen.

Android erhält die Schnittfläche des eingepassten Videos mit dem sichtbaren Host in Fensterkoordinaten. Steuerungsupdates im schwebenden Fenster behalten den letzten Ausschnitt des großen Fensters für die Rückkehr. Android steuert den Systemübergang. Die WebView kann von einer ganzen Watch-Seite zu einer reinen Videofläche wechseln. Ohne Hardwaremessung bleibt Seamless Resize deshalb deaktiviert.

Die Querformatansicht gehört der Activity. Der Webseitenknopf reagierte dagegen auf den HTML-Vollbildzustand. Für erkannte YouTube-Vollbildknöpfe geht der echte Klick jetzt über die vorhandene, ursprungsbeschränkte Nachrichtenbrücke an die Activity. Das Exit-Symbol richtet sich nach der angezeigten Videopräsentation. Die manuelle Orientierungswahl gilt bis zum nächsten Wechsel zwischen aufrechter und seitlicher Haltung oder dem Ende der erweiterten Ansicht. YouTube-Varianten mit anderen Bedienelementen sind auf dem Gerät zu prüfen.

Prüfungen: lokale Chromium-Pixeltests mit laufendem und pausiertem Video, Klicks auf den Website-Knopf, Compose-Test mit Zwischenwerten und Richtungsumkehr, simulierte Orientierungs-, Lifecycle-, PiP- und Gestenprüfungen. ADB meldete kein angeschlossenes Gerät. Die OnePlus-Systemanimation, reale YouTube-Controls, ausgefallene Frames und Bildkontinuität sind auf Hardware offen.

OnePlus-Test:
1. Video im Hochformat öffnen. Langsam nach unten ziehen, einmal abbrechen, dann minimieren und sofort wieder vergrößern. Mit pausiertem Video wiederholen.
2. Ins Querformat und zurück drehen. Wieder quer halten und unten rechts auf Vollbild verlassen tippen. Quer halten und prüfen, ob die normale Ansicht bleibt. Mit demselben Knopf wieder vergrößern.
3. Aus Hochformat, Querformat und interner Karte mit Home in Android-PiP wechseln. Jeweils zurückkehren und auf ersten und letzten Bewegungsframe, schwarze Flächen, Bildausschnitt und Ton achten. Ein zweites Video prüfen.

Geänderte Player-Komponenten: WeiktubeUi, PlayerGestureLayout, PlayerController, WatchNavigationScript, MainActivity und NativePipController. Tests in PlayerControllerLifecycleTest, NativePipControllerTest, check-player-layout.cjs und check-watch-navigation.cjs.
