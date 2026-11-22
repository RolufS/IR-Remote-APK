# IR-Fernbedienung für Hotel-Fernseher – Design

Stand: 22.11.2026 · Status: Entwurf zur Freigabe

## Wozu

Der Nutzer ist viel in Hotels unterwegs und schließt dort meistens seinen Fire TV Stick an den Fernseher an. Oft gibt es keine Fernbedienung, oder sie ist sehr dreckig. Alle Fernbedienungs-Apps im Play Store sind voller Werbung. Gebraucht wird eine eigene, werbefreie Android-App, die per Infrarot möglichst jeden Fernseher bedient. Sein Handy ist ein Redmi Note 13 Pro mit IR-Sender.

Typischer Ablauf im Hotel: Fernseher einschalten, auf den HDMI-Eingang mit dem Fire TV Stick umschalten, Lautstärke einstellen. Danach übernimmt die Fire-TV-Fernbedienung. Den **Eingang wechseln** ist deshalb der wichtigste Handgriff.

## Ziele

- So viele TV-Marken und -Modelle wie möglich, auf Basis öffentlich zugänglicher Code-Datenbanken.
- Ein Wizard, der die passende Fernbedienung zuverlässig und schnell findet, auch bei unbekannter Marke.
- Eingangswahl so direkt wie möglich: direkte HDMI-1–4-Tasten, wenn der Fernseher das unterstützt, sonst SOURCE.
- Funktioniert komplett **offline**, weil Hotel-WLAN oft schlecht ist oder eine Anmeldeseite hat.
- Läuft auf jedem Android-Handy mit IR-Sender, nicht nur auf Xiaomi.
- Optik wie eine echte Fernbedienung, aber nüchtern. Der ganze Bildschirm wird genutzt, die Tasten sind groß.

## Nicht-Ziele

- Steuerung über WLAN (Samsung Tizen, LG webOS, Android TV usw.). Hotel-TVs hängen praktisch nie in einem erreichbaren Netz.
- Fire TV Stick, Receiver, Soundbars oder Klimaanlagen steuern. Nur Fernseher.
- Codes von einer Original-Fernbedienung anlernen. Handys können IR nur senden, nicht empfangen.
- iPhone-Version (iPhones haben keinen IR-Sender).
- Veröffentlichung im Play Store.

## Anti-Ziele (wäre ein Fehlschlag, auch wenn es technisch läuft)

- Werbung, Tracking, Analytics, Konto-Zwang. Die App bekommt **keine Internet-Berechtigung**.
- Funktionen zum Aushebeln des Hotelmodus (Installer-/Service-Menüs, Sperren aufheben). Die App sendet nur normale Bedientasten.
- Eine verspielte Optik (Comic-Look, bunte Verläufe, Animationen um ihrer selbst willen).
- Ein Wizard, der für jeden Fehlversuch den Fernseher ausschaltet.

## Rahmenbedingungen

- Android, Kotlin, Jetpack Compose. Gesendet wird über die Standard-Schnittstelle `ConsumerIrManager`.
- Gebaut wird auf dem Windows-PC des Nutzers. Android Studio, SDK, adb und JDK 17/21 sind dort vorhanden.
- Installation als APK per USB (adb) oder als kopierte Datei.
- Bedienoberfläche auf Deutsch. Texte liegen in String-Ressourcen, damit später andere Sprachen dazukommen können.

## Lösungswege

| Weg | Vorteil | Nachteil | Entscheidung |
|---|---|---|---|
| **Native App (Kotlin/Compose), Code-Datenbank am PC aufbereitet und fest in der App** | offline, schnell, direkter Zugriff auf den IR-Sender, nur ein Werkzeugkasten | Datenbank wird nur mit einem neuen App-Build aktualisiert | **gewählt** |
| Native App, Codes zur Laufzeit aus dem Netz (z. B. irdb über jsDelivr) | kleine App, immer aktuell | ohne Netz im Hotel nutzlos, braucht die Internet-Berechtigung | verworfen |
| Plattformübergreifend (Flutter / React Native) | – | IR gibt es nur auf Android, das Plugin für den IR-Sender ist eine zusätzliche Fehlerquelle, kein Gewinn | verworfen |

Den Weg hat Claude festgelegt. Der Nutzer hat das Wie überlassen und dem Vorschlag nicht widersprochen.

## Vom Nutzer festgelegt

### Fernbedienung (Hauptbildschirm) – Variante „A2 + Eingangsleiste“

Klassischer Aufbau einer TV-Fernbedienung über den **ganzen Bildschirm**, ohne Gehäuse-Rand, mit großen Tasten und dunkler Optik. Mockup: `docs/superpowers/specs/mockups/remote.html` (rechte Variante).

Von oben nach unten:

1. Schmale Kopfzeile: ☰ (gespeicherte Fernbedienungen / Wizard) · aktive Fernbedienung „Samsung · Code 3 ▾“ (antippen zum Wechseln) · ⋮ (Menü)
2. SOURCE (blau abgesetzt) · Ein/Aus (rund, rot) · MUTE
3. **HDMI 1 · HDMI 2 · HDMI 3 · HDMI 4**. Diese Reihe erscheint **nur**, wenn die Fernbedienung direkte Eingangs-Codes hat und der Nutzer sie im Wizard bestätigt hat. Fehlt sie, bekommen die Zahlentasten den Platz.
4. Zahlenblock 1–9, darunter LIST · 0 · PRE-CH
5. Lautstärke-Wippe · MENU/HOME übereinander · Programm-Wippe
6. Großes Steuerkreuz mit OK
7. BACK · INFO · EXIT
8. Farbtasten Rot/Grün/Gelb/Blau

### Wizard – „Fernbedienung finden“

Mockup: `docs/superpowers/specs/mockups/wizard.html`

1. **Marke wählen:** Suchfeld, darunter „Häufig in Hotels“ und dann alle Marken alphabetisch. Unten: „Marke unbekannt → Suchlauf“.
2. **Code testen:** „Richte das Handy auf den Fernseher und tippe auf MUTE. Wurde der Ton stumm geschaltet?“ Ja / Nein (nächster Code). Fortschritt „Code 3 von 48“. Bei „Ja“ folgt ein Bestätigungstest mit VOL +. Ist der Fernseher aus, kann der Nutzer stattdessen mit Ein/Aus testen.
3. **Eingänge prüfen:** „Tippe auf HDMI 1. Wechselt der Fernseher direkt auf diesen Eingang?“ Bei „Ja“ bekommt die Fernbedienung die HDMI-Reihe. Hat der Code keine direkten Eingänge, wird der Schritt übersprungen.
4. **Speichern:** Zusammenfassung (was funktioniert), Name optional (z. B. „Hotel Adlon, Zi. 412“), darunter die Liste der bisher gespeicherten Fernbedienungen, dann „Zur Fernbedienung“.

**Suchlauf bei unbekannter Marke:** Die App sendet nacheinander das Ausschalt-Signal aller Code-Sätze, die häufigsten zuerst, und zeigt dabei „127 von 410 · Philips“. Der Nutzer tippt auf „Stopp – hat reagiert!“, sobald der Fernseher ausgeht. Danach testet die App die letzten Codes vor dem Stopp einzeln mit ihm (MUTE, sobald der Fernseher wieder an ist) und macht mit Schritt 3 weiter.

## Von Claude festgelegt (Vorschläge, korrigierbar)

### Code-Daten

- **Quellen:**
  - **Flipper-IRDB** (github.com/Lucaslhm/Flipper-IRDB, CC0, Ordner `TVs/`, über 100 Marken) als Grundstock. Die `.ir`-Dateien enthalten benannte Tasten, meist als `parsed` (Protokoll, Adresse, Befehl), teils als `raw` (Zeiten).
  - **irdb** (github.com/probonopd/irdb, Ordner `codes/<Marke>/TV/`, CSV mit Protokoll/Gerät/Untergerät/Funktion) für zusätzliche Marken und Code-Sätze.
  - Beide Quellen werden auf einen festen Commit gepinnt.
  - LIRC bleibt außen vor (uneinheitliches Format, viele Doppelungen).
- **Aufbereitung am PC:** Ein Python-Skript (`tools/build_codes.py`) liest beide Quellen und vereinheitlicht die Tastennamen auf eine feste Liste. Diese Liste umfasst POWER, POWER_ON, POWER_OFF, MUTE, VOL_UP, VOL_DOWN, CH_UP, CH_DOWN, 0–9, SOURCE, HDMI1–HDMI4, MENU, HOME, OK, UP/DOWN/LEFT/RIGHT, BACK, EXIT, INFO, LIST, PRE_CH, RED/GREEN/YELLOW/BLUE und Extras. Das Skript fasst gleiche Code-Sätze zusammen und schreibt eine SQLite-Datei nach `app/src/main/assets/`.
- **Code-Satz = „eine Fernbedienung“ in der App:**
  - Dazu gehören Marke, Protokoll mit Geräte-Adresse und die Tasten.
  - Zwei Quelldateien gelten als derselbe Code-Satz, wenn Protokoll, Adresse sowie die Befehle für POWER und MUTE gleich sind. Ihre übrigen Tasten werden vereinigt.
  - Widersprechen sich zwei Quellen bei einer Taste, gewinnt die Mehrheit. Bei Gleichstand gewinnt Flipper-IRDB.
- **Reihenfolge:** Innerhalb einer Marke werden die Code-Sätze nach der Zahl der Quelldateien sortiert, die sie stützen (häufigste zuerst). Für den Suchlauf gilt dieselbe Reihenfolge über alle Marken hinweg.
- **Direkte Eingänge:** Ein Code-Satz hat „direkte Eingänge“, wenn er mindestens HDMI1 und HDMI2 enthält. Erkannt wird das an Tastennamen wie `HDMI1`, `HDMI_1`, `HDMI 1`, `Input_HDMI1` u. Ä. Flipper-IRDB hat dafür eigene `*_Discrete.ir`-Dateien (z. B. bei Samsung). Es werden **nur Codes aus den öffentlichen Daten** übernommen, nichts geraten oder erfunden.
- **Marken-Zuordnung:** Einige Hotelmarken werden vom selben Hersteller gebaut und nutzen dessen Codes. Ein Beispiel ist Vestel mit Telefunken, Finlux, JVC (Europa) und Hitachi (Europa). Es gibt eine kleine handgepflegte Tabelle, damit unter „Telefunken“ auch die Vestel-Codes auftauchen. Diese Code-Sätze stehen hinter den markeneigenen.
- **„Häufig in Hotels“** ist eine handgepflegte Liste: Samsung, LG, Philips, Sony, Panasonic, Hisense, TCL, Telefunken, Grundig, Toshiba, Sharp, Thomson, Loewe, Medion, Vestel.
- **Quellenangabe:** Unter ⋮ → „Über“ stehen beide Datenbanken mit ihren Lizenzen.

### Senden

- Die App setzt die Protokolle selbst in Pulsfolgen (Mikrosekunden) plus Trägerfrequenz um und übergibt sie an `ConsumerIrManager.transmit`.
- Unterstützt werden mindestens die Protokolle, die in den TV-Daten vorkommen:
  - NEC / NECext / NEC42
  - Samsung32
  - RC5 / RC5X / RC6
  - SIRC 12/15/20
  - Kaseikyo (Panasonic)
  - RCA, JVC, Sharp
  - Rohdaten (raw)
- Das Skript meldet, wie viele Code-Sätze an fehlenden Protokollen scheitern. Ziel: mindestens 95 % der TV-Code-Sätze sind sendbar. Code-Sätze mit nicht unterstütztem Protokoll werden weggelassen, nicht ausgegraut angezeigt.
- **Gedrückt halten** wiederholt bei VOL, CH und dem Steuerkreuz, so wie das Protokoll es vorsieht (z. B. NEC-Wiederholcode, RC5/RC6 mit gleichem Toggle-Bit).
- Jeder Tastendruck gibt ein kurzes haptisches Feedback.

### Gespeicherte Fernbedienungen

- Gespeichert werden Name, Marke, Code-Satz-ID und „direkte Eingänge bestätigt“ (ja/nein), lokal auf dem Gerät.
- Beim Start öffnet die App die zuletzt benutzte Fernbedienung. Gibt es noch keine, startet der Wizard.
- Umbenennen und Löschen gehen über ☰. Ein Wizard-Neustart ist jederzeit möglich.

### Bedienoberfläche – Details

- Nur Hochformat, nur dunkle Optik.
- Tasten, die der Code-Satz nicht kennt, werden ausgegraut angezeigt. Der Aufbau bleibt gleich, damit die Tasten nicht „springen“.
- Zusatztasten aus den Daten, die nicht im Layout vorkommen (z. B. Netflix, Untertitel, Sleep), liegen unter ⋮ → „Weitere Tasten“ als Liste.
- Hat das Handy keinen IR-Sender, erscheint ein deutlicher Hinweis. Die App bleibt bedienbar (zum Anschauen), sendet aber nichts.
- minSdk 21 (Android 5), damit auch ältere Handys mit IR-Sender laufen (Galaxy S4–S6, LG G3–G5, HTC One, ältere Xiaomi).

## Dem Bauenden überlassen

- Paket- und Modulstruktur, Wahl von Room/SQLite-Zugriff und DataStore, Navigation.
- Genaue Pausen beim Suchlauf (Vorschlag: ca. 1,2 s je Code) und wie viele Codes nach „Stopp“ zurückgeprüft werden (Vorschlag: die letzten 5).
- Was der Wizard testet, wenn ein Code-Satz kein MUTE hat (z. B. VOL – statt MUTE).
- Exakte Abstände, Schriftgrößen und Farben im Rahmen der Mockups.
- Tests: Unit-Tests der Protokoll-Encoder gegen bekannte Referenz-Zeiten, Tests für das Daten-Skript (Zusammenführung, Tastennamen, Erkennung direkter Eingänge).
- Signieren der APK (eigener Schlüssel, liegt nicht im Repo).
