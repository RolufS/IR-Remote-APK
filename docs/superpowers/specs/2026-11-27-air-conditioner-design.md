# Klimaanlagen-Fernbedienung, Geräte-Wischen, Wetter und Klima-Automatik (Version 1.8.0)

Stand: 2026-11-27. Wunsch des Nutzers: das Gleiche wie für Fernseher auch für Klimaanlagen; zweiter Bildschirm per Wischen nach links/rechts; aktuelle Außentemperatur anzeigen; Automation „5 °C kühler als draußen, aber nicht kälter als 25 °C“. Entscheidung: in die bestehende App integriert (nicht separate App).

## 1. Datenquelle

- **SmartIR** (https://github.com/smartHomeHub/SmartIR, MIT), Ordner `codes/climate`, fest auf Commit `3dca407e3c475b57c02c08b97ae521a4ef0bbf43`. 424 Gerätedateien, 149 Marken, davon 395 im Broadlink-Base64-Format; außerdem 14 „ESPHome/Raw“, 13 „Xiaomi/Raw“, 1 „LOOKin/Raw“ (nur übernehmen, wenn das Format sicher dekodierbar ist, sonst auslassen und zählen).
- Jede Datei: `manufacturer`, `supportedModels`, `minTemperature`, `maxTemperature`, `precision`, `operationModes`, `fanModes`, optional `swingModes`, und `commands` = `off` plus verschachtelt `mode/fan/temp` bzw. `mode/fan/swing/temp`.
- Broadlink-Format: Byte 0 = 0x26 (IR), Byte 1 = Wiederholungen, Byte 2–3 = Länge (LE), danach Dauern in Einheiten von 269/8192 ms ≈ 32,84 µs, also `µs = v * 269000 / 8192`. Byte 0x00 leitet eine 2-Byte-Big-Endian-Dauer ein. Ende 0x0d 0x05. Abwechselnd an/aus, beginnend mit an. Trägerfrequenz 38 kHz.
- Etwa 200 Kommandos haben ungültiges Base64 (falsches Padding o. ä.): tolerant dekodieren, wenn eindeutig möglich, sonst Kommando weglassen; Geräte ohne gültiges `off` oder ohne gültiges Kühl-Kommando ganz weglassen. Statistik im Build-Log.
- Lizenz: MIT-Hinweis von SmartIR in README, Über-Bildschirm und `app/src/main/assets/` (z. B. `ac_codes_LICENSE.txt`) nennen.
- Flipper-IRDB-ACs werden in 1.8.0 **nicht** übernommen (zu wenige Zustände je Gerät).

## 2. Datenbank `app/src/main/assets/ac_codes.db` (eigenes Asset, getrennt von `codes.db`)

- Tabellen: `ac_brand(id, name, sort_name)`, `ac_device(id, brand_id, models TEXT, source TEXT /* SmartIR-Dateiname */, min_temp REAL, max_temp REAL, precision REAL, modes TEXT /* JSON-Liste */, fan_modes TEXT, swing_modes TEXT, commands BLOB)`, `meta(key, value)` mit Quelle und Commit.
- `commands`: ein zlib-komprimiertes Blob je Gerät. Inhalt: Symboltabelle der vorkommenden Dauern (je Gerät auf wenige Cluster ±10 % gerundet, um die Kompression zu verbessern) und je Kommando der Pfad (`off`, `cool/auto/22`, `cool/auto/off/22` …) plus Symbolfolge. Genaues Format legt Task 31 fest und dokumentiert es im Code; Kotlin- und Python-Seite haben gemeinsame Fixture-Tests.
- Lange Pausen innerhalb eines Kommandos (Wiederholungen, Mehrfach-Frames) auf höchstens 100 ms begrenzen; Wiederholungszahl aus Byte 1 anwenden; letzte Pause entfernen, sodass das Muster mit „an“ endet; Gesamtlänge eines Musters ≤ 1,5 s (sonst Gerät/Kommando auslassen und zählen).
- Doppelte Geräte (identische Kommandos) zusammenführen, Modelllisten vereinigen.
- Markennamen normalisieren (z. B. „Mitsubishi Electric“, „Mitsubishi Heavy“ bleiben getrennt; Tippfehler wie „Ggeneral Electric“ → „General Electric“; Groß-/Kleinschreibung vereinheitlichen).
- Ziel: Asset ≤ 5 MB. Größe im Build-Log ausgeben.

## 3. App-Modell

- `SavedRemote` bekommt `ac: SavedAc? = null`. Ein gespeicherter Eintrag ist damit ein **Raum** mit Fernseher und/oder Klimaanlage. Ein reiner Klima-Raum hat `keys = emptyMap()`; `brand`/`codeLabel` des Fernsehers dürfen dann leer sein, der Titel fällt auf den Klima-Namen zurück. Alte Dateien bleiben lesbar (Feld optional).
- `SavedAc(brand, deviceLabel, minTemp, maxTemp, precision, modes, fanModes, swingModes, commands: String /* Base64 des Blobs, eigene Kopie wie bei den TV-Signalen */, state: AcState)`.
- `AcState(power: Boolean, mode, temp: Double, fan, swing?)`. Die Klimaanlage meldet nichts zurück; die App merkt sich den zuletzt gesendeten Zustand je Raum und zeigt ihn an.
- `AcCodes`: dekodiert das Blob einmal (gecacht), liefert `signalFor(state): IrSignal?`. Ist der exakte Pfad nicht vorhanden, gibt es **keine** stille Ersatzwahl; die UI bietet nur Kombinationen an, die es gibt (z. B. Lüfterstufen je Modus), und rastet die Temperatur auf vorhandene Werte.
- Senden über den bestehenden `SignalSender` (serielle Warteschlange). Jede Änderung (Taste) sendet den vollständigen neuen Zustand; schnelle Folgen von +/− entprellen (letzter Zustand nach 300 ms wird gesendet).

## 4. Klima-Bildschirm

- Gleiche Optik wie die TV-Fernbedienung (dunkle Fläche, große Tasten, nicht verspielt), Vollbild.
- Oben: große Soll-Temperatur (z. B. „22 °C“), darunter Modus-Symbol + Name, Lüfter, Swing; Außentemperatur-Zeile „Draußen 31 °C · Pattaya · vor 12 min“ (nur wenn Wetter aktiviert, sonst unauffälliger Hinweis „Außentemperatur anzeigen“ der zu den Einstellungen führt).
- Tasten: Ein/Aus (wie TV-Power), große −/+ (Halten wiederholt), Modus (Durchschalten oder Auswahl), Lüfter, Swing (nur wenn vorhanden), Klima-Automatik (öffnet deren Einstellung).
- Gedrückt-Effekt wie bei den TV-Tasten (PressableKey wiederverwenden).
- Bei ausgeschaltetem Zustand sind Temperatur/Modus gedimmt; jede Änderung im ausgeschalteten Zustand schaltet nicht automatisch ein (nur merken), Ein/Aus sendet dann den gemerkten Zustand.

## 5. Wischen zwischen Geräten

- Hat ein Raum Fernseher **und** Klimaanlage, zeigt der Fernbedienungs-Bildschirm einen `HorizontalPager` mit den Seiten „TV“ und „Klima“; kleine Punkte/Beschriftung unter dem Kopf zeigen die Seite. Die zuletzt gezeigte Seite je Raum wird gemerkt.
- Hat ein Raum nur ein Gerät, kein Pager.
- Kopfzeile (Raumname, Umschalt-Menü, ☰, ⚡) bleibt für beide Seiten gleich. RTL: Seitenreihenfolge folgt der Leserichtung.
- Wischen darf nicht mit dem Halten von Lautstärke/−/+ kollidieren (Tasten verbrauchen die Geste).

## 6. Klima-Wizard

- Einstieg: im ☰-Menü/Raumliste „Klimaanlage hinzufügen“ (zum aktuellen Raum) und im Wizard-Start die Wahl „Fernseher“ oder „Klimaanlage“ (neuer Raum).
- Schritt 1 Marke wählen (Suche, häufige Marken oben: Daikin, Mitsubishi Electric, LG, Samsung, Panasonic, Gree, Midea, Fujitsu, Hitachi, Toshiba, Haier, Carrier …; „Marke unbekannt“ probiert alle).
- Schritt 2 Code-Test: für jedes Gerät der Marke „Ein · Kühlen · 24 °C · Lüfter Auto“ (bzw. nächstliegende vorhandene Kombination) senden: „Hat die Klimaanlage reagiert (Piepen oder Anlaufen)?“ Ja → Schritt 3, Nein → nächstes Gerät; „Nochmal senden“; Fortschritt „Code 3 von 28“.
- Schritt 3 Bestätigen: „Aus“ senden „Ist sie ausgegangen?“ – Nein → weiter mit dem nächsten Gerät.
- Speichern mit Namen; danach Klima-Seite zeigen.

## 7. Wetter (optional, standardmäßig aus)

- Die App erhält die Berechtigung `INTERNET`. Ohne Aktivierung baut sie **keine** Verbindung auf.
- Einstellung im Klima-Bereich: „Außentemperatur anzeigen“ an/aus + Ort (Eingabe Stadtname, Auswahl aus Treffern). Keine Standortberechtigung.
- Dienst: Open-Meteo (kein Schlüssel): Geokodierung `https://geocoding-api.open-meteo.com/v1/search?name=<Ort>&count=5&language=<Sprache>`, Wetter `https://api.open-meteo.com/v1/forecast?latitude=..&longitude=..&current=temperature_2m`. Gesendet werden nur Ortsname bzw. Koordinaten des gewählten Orts. Quellenangabe „Wetterdaten: Open-Meteo.com (CC BY 4.0)“ im Über-Bildschirm und in der Klima-Anzeige.
- `HttpURLConnection`, Zeitlimit 5 s, keine neue Bibliothek. Ergebnis mit Zeitstempel gecacht; Anzeige „vor X min“; ab 3 h alt als veraltet markiert.
- Aktualisierung: beim Öffnen des Klima-Bildschirms, wenn älter als 15 min; und durch die Klima-Automatik.
- Datenschutz: PRIVACY.md, README, Play-Datensicherheit (Entwurf in `docs/play-store/`) und Store-Texte aktualisieren: „Nur wenn du die Außentemperatur aktivierst, wird der gewählte Ort an Open-Meteo gesendet; keine Konten, keine Werbung, kein Tracking.“ Badge „No internet“ ersetzen.

## 8. Klima-Automatik („kühler als draußen“)

- Je Raum: an/aus, Abstand (Standard 5 °C, 1–10), Untergrenze (Standard 25 °C, „nicht kälter als“), optionale Obergrenze („nicht wärmer als“, Standard aus), Modus Kühlen, Lüfter (Standard Auto), Intervall 30 min.
- Ziel = clamp(rundeAufPrecision(außen − Abstand), max(Untergrenze, minTemp), min(Obergrenze, maxTemp)). Beispiel: draußen 33 °C, Abstand 5, Untergrenze 25 → 28 °C; draußen 28 → 25 °C.
- Ablauf: Start sendet sofort (Wetter holen, Ziel berechnen, Zustand Ein·Kühlen·Ziel senden). Danach AlarmManager alle 30 min (`setAndAllowWhileIdle`, wie der Sleep-Timer mit goAsync und Zeitlimit ≤ 8 s): Wetter holen → Ziel berechnen → **nur senden, wenn sich das Ziel geändert hat** (kein nächtliches Piepen). Wetter nicht erreichbar → nichts senden, Hinweis in der Benachrichtigung, nächster Versuch beim nächsten Intervall.
- Laufende Benachrichtigung „Klima-Automatik · draußen 31 °C → 26 °C“ mit „Stopp“; Neustart nach Reboot (wie Sleep-Timer).
- Hinweis in der Einstellung: Handy muss im Raum mit Sicht auf die Klimaanlage liegen; am Ladegerät zuverlässiger.
- Stoppt, wenn der Nutzer die Klimaanlage manuell ausschaltet (Aus in der App) – mit Hinweis.
- Der TV-Sleep-Timer bleibt unverändert.

## 9. Übersetzungen, Tests, Release

- Alle neuen Texte in allen 11 Sprachen (bestehender TranslationsTest).
- Tests: Python (Dekodierung, Symbolisierung, Zusammenführen, Größe), Kotlin (Blob-Lesen mit gemeinsamer Fixture, Zustand→Signal, Temperatur-Rasten, Zielberechnung, Wetter-Parser mit Beispiel-JSON, Planung der Alarme, Pager-UI, Klima-UI, Wizard-Ablauf, Migration alter remotes.json).
- ManifestTest: INTERNET jetzt erlaubt, sonst weiterhin keine neuen Berechtigungen.
- Version 1.8.0 (versionCode 13), Release-Notizen, README (Funktionen, Screenshots Klima/Pager), GitHub-Release, Installation auf dem Redmi.
