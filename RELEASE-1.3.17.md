# Weiktube 1.3.17 – Video-Zoom

- Zwei Finger vergrößern/verkleinern das Video stufenlos von 1× bis 8×.
- Nur das Video wird transformiert; Controls und Webseite bleiben unverändert.
- Mini/PiP, Rotation und neue Videos setzen den Zoom zurück.
- Bestehender Player, Shorts-Tonfix und AdBlock bleiben erhalten.

354 JVM-Tests, neun Zoom-Skriptprüfungen, Chromium-Tests für vier Formate und
bestehende Player-/PiP-/AdBlock-Prüfungen bestanden. Details und geänderte Dateien:
[Prüfbericht](docs/VIDEO-ZOOM.md).

Kein Android-Gerätetest durchgeführt. Querformat durch Handydrehung verwenden;
ein separates HTML-Vollbild-Overlay ist nicht abgedeckt. Keine Zoom-Geste im
Shorts-Tab. VersionCode 31 ermöglicht das Update aus der bisherigen App.
