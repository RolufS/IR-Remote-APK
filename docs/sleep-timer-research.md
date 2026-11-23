# Sleep-Timer am Fernseher: Recherche und Vorlagen

Grundlage der Sleep-Timer-Vorlagen in der App (`automation/SleepTemplates.kt`).
Recherchiert im Web, **auf keinem Gerät geprüft**. Kennzeichnung je Angabe:

- **Handbuch** – Hersteller-Handbuch oder Support-Seite des Herstellers
- **Forum** – Nutzerberichte, Foren
- **abgeleitet** – aus Handbuch-Auszügen, Inhaltsverzeichnissen oder Erinnerung erschlossen

Die Zahl der Pfeil- bzw. OK-Drücke ist in allen Menüwegen **abgeleitet**; die App
markiert jede Vorlage als „ungeprüft – bitte mit ‚Testen‘ prüfen“.

## Allgemein

- Startzustand: EXIT, BACK, EXIT (mit dem, was die Fernbedienung davon hat), je 0,8 s.
  Zum Schluss EXIT (sonst BACK). – abgeleitet
- Nach Tasten, die ein Menü öffnen, 2,5 s warten; OK in ein Untermenü 1,5 s;
  Navigation 0,8 s. – abgeleitet (Recherche: 1,5–2,5 s bzw. 0,7–1 s)
- Android/Google TV merken sich oft die letzte Menüposition, Zählen ist dort fragil.
  „Überschießen“ an einen festen Rand (z. B. LEFT ×8) hilft. – Forum
- **Hotelmodus:** Menüs sind meist gesperrt; eine eigene SLEEP-Taste funktioniert dort am
  ehesten (ungeprüft). Deshalb stehen Sleep-Tasten-Vorlagen immer zuerst.
- Viele Fernseher (Sony, TCL, Samsung) setzen den Sleep-Timer beim Ausschalten zurück. – Forum

## Typ A: eigene Sleep-Taste

Code-Datenbank: 105 Code-Sätze haben eine Sleep-Taste. Erkannt wird (in dieser Priorität)
`X:Sleep`, `X:Sleep Timer`, `X:Timer`, `X:Off Timer`, `X:Sleepcall`, `X:Timer Select`,
`X:Timer Set`. `X:Picture Sleep` schaltet nur das Bild ab und wird ignoriert.

Jeder Druck stellt den nächsten Wert ein (erster Druck zeigt bei manchen Geräten nur den
aktuellen Wert). Vorlagen für ≈ 30 / 60 / 90 min, 0,8 s zwischen den Drücken, 3 s nach dem
letzten. Angenommene Werte je Marke:

| Marke | Werte nach „Aus“ (min) | Kennzeichnung |
|---|---|---|
| Vestel-Familie (Vestel, Telefunken, Finlux, JVC, Hitachi, Toshiba, Bush, Techwood, Digihome, Luxor, Polaroid), Grundig, Sharp, Hisense, Medion, unbekannte Marken | 10, 20, 30 … 120 | abgeleitet |
| LG (auch NetCast 2010–2014) | 10, 20, 30, 60, 90, 120, 180, 240 | abgeleitet (aus Erinnerung) |
| Sony (Fernbedienungen 2010–2014) | 15, 30, 45, 60, 90, 120 | Handbuch |
| Philips (ältere mit SLEEP-Taste) | 15, 30, 45, 60, 90, 120, 180, 240 | Handbuch |
| Samsung (ältere mit SLEEP-Taste) | 30, 60, 90 … 180 | abgeleitet |
| Panasonic (OFF-TIMER-Taste) | 30, 60, 90 | abgeleitet |

Drückzahl für 60 min: Vestel-Familie 6×, LG 4×, Sony 4×, Philips 4×, Samsung 2×, Panasonic 2×.

## Typ B: Menüweg je Marke

Nur angeboten, wenn die Fernbedienung alle nötigen Tasten hat. Ziel 60 min (Panasonic 30 min).
Pfad = Herkunft des Menüpfads; die Tastenzahl ist immer abgeleitet.

### Samsung

- **Tizen ≤ 2021** – Settings > General > System Manager > Time > Sleep Timer.
  Öffnen mit `X:Settings`, `X:Menu`, MENU oder HOME (Smart Hub, LEFT ×8 zum Zahnrad, OK).
  Danach DOWN ×3, OK, DOWN ×2, OK, OK, DOWN, OK, DOWN ×2 (Aus → 30 → 60), OK. – Pfad: Handbuch
- **Tizen 2022+** – Settings > All Settings > General & Privacy > System Manager > Time > Sleep Timer.
  Öffnen wie oben, dann OK (All Settings), DOWN ×3, OK, DOWN, OK, OK, DOWN, OK, DOWN ×2, OK.
  Höchstwert 180 min, Werte vermutlich Aus/30/60/90/120/150/180. – Pfad: Handbuch
  Quelle: https://www.samsung.com/ca/support/tv-audio-video/set-the-sleep-timer-on-your-samsung-tv/
- **Vor Tizen (bis ~2015)** – TOOLS > Sleep Timer > Wert: `X:Tools`, DOWN ×3, RIGHT ×2, OK. – Pfad: Handbuch
  Quelle: https://www.samsung.com/in/support/tv-audio-video/how-to-adjust-sleep-timer-in-samsung-tv

### LG

- **webOS** – Settings-/Q.Menu-Taste (`X:Settings`, `X:Q Settings`, `X:Q.Settings`, `X:Q.Menu`,
  `X:Q Menu`) öffnet die Quick Settings mit Sleep-Timer-Symbol: DOWN ×3, OK ×4
  (Aus → 10 → 20 → 30 → 60). Position des Symbols je Version verschieden. Voller Pfad:
  All Settings > General > Time & Timer > Timers > Sleep Timer. – Pfad: Handbuch
  Quelle: https://www.lg.com/us/support/help-library/webos-tv-quick-smart-settings--20154712822304
- **NetCast (2010–2014)** – nur über die SLEEP-Taste (Typ A).

### Sony

- **Ältere Bravia (2012–2016)** – HOME > Settings > System Settings > Clock/Timers > Sleep Timer;
  Werte 15/30/45/60/90/120. HOME, DOWN ×8 (Überschießen bis Settings), OK ×4, DOWN ×4, OK. – Pfad: Handbuch
  Quelle: https://helpguide.sony.net/gbmig/C5221001/v1/eng/c_clock_sleep.html
- **Android/Google TV** – Quick-Settings-Taste (`X:Quick Settings`, `X:Settings`) > Sleep-Timer-Kachel:
  RIGHT ×3, OK, DOWN ×4, OK. Sonst Settings > System > Timers & clock > Sleep timer. – Pfad: Forum
  Quelle: https://pro-bravia.sony.net/resources/display/settings/android_8/timers/index.html

### Philips

- **Saphi (2015+)** – Settings > (TV settings/All settings >) General settings > Sleep timer,
  Schieberegler in 5-min-Schritten bis 180. `X:Settings`/`X:Setup`, DOWN ×7, OK, RIGHT ×12 (60 min), OK.
  – Pfad: Handbuch (nur Auszüge)
  Quelle: https://manualslib.com/manual/2706583/Philips-6100-Series.html?page=32
- Ältere mit SLEEP-Taste: Typ A. Quelle: https://www.manualsdir.com/manuals/543581/philips-51pp9303h-17b.html?page=29

### Panasonic

- **Ältere Viera** – MENU, DOWN bis Timer, OK, OK (Off Timer), UP bis 30 min, OK, EXIT; das
  Handbuch nennt nur 30 min. MENU, DOWN ×3, OK, OK, UP ×2, OK. – Pfad: Handbuch
  Quelle: https://help.na.panasonic.com/answers/how-to-turn-on-the-sleep-timer-television/
- OFF-TIMER-Taste, je Druck +30 min bis 90: Typ A (ungeprüft).

### Hisense

- **VIDAA** – Settings > System > Power & Energy > Power > Sleep timer, Aus bis 120 in
  10-min-Schritten. `X:Settings`/`X:Nav Setting`/MENU, DOWN ×5, OK, DOWN ×3, OK, OK, DOWN, OK,
  DOWN ×6, OK. – Pfad: Handbuch (Auszug)
  Quelle: https://www.manualowl.com/m/Hisense/50U6G1/Manual/674919?page=40

### TCL

- **Google TV** – Zahnrad > System (> Power & Energy) > Sleep timer > Wert.
  `X:Settings`/MENU, DOWN ×6, OK, DOWN ×3, OK, DOWN, OK, DOWN ×6, OK. – Pfad: Handbuch (Support-Seite)
  Android TV: Settings > Device Preferences > Screensaver > Sleep Timer bzw. System > Power & Energy > Sleep Timer.
  Quelle: https://support.tcl.com/en_US/sleep-timer-tcl-google-tv-

### Toshiba, Sharp, Grundig, Telefunken, Vestel-Familie

- Meist eigene SLEEP-Taste, Werte 10 … 120 in 10-min-Schritten (Typ A). – abgeleitet
- Menü-Ausweichweg MENU > Settings/Timers > Sleep timer (aus Inhaltsverzeichnissen); als Vorlage
  nicht enthalten, weil die Menüs je Modell stark abweichen.

## Rückmeldungen

Wer eine Vorlage an einem echten Gerät ausprobiert hat (klappt / klappt mit anderen Zahlen /
klappt nicht), bitte als Issue melden: Marke, Modell oder Baujahr, Vorlage und was geändert werden musste.
