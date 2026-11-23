# Play-Store-Eintrag (Deutsch)

Texte zum Kopieren in die Play Console (Hauptsprache: Deutsch – de-DE).
Die Texte in den Codeblöcken werden 1:1 übernommen; Zeichenzahlen stehen am Ende.

Die App selbst hat 11 Sprachen (Deutsch, Englisch, Chinesisch vereinfacht, Hindi,
Spanisch, Französisch, Arabisch, Bengalisch, Portugiesisch, Russisch, Indonesisch).
Den Store-Eintrag gibt es auf Deutsch (diese Datei) und Englisch
([listing-en.md](listing-en.md)); in der Play Console unter „Store-Eintrag →
Übersetzungen verwalten“ Englisch (en-US) hinzufügen und die englischen Texte
einfügen. Die Screenshots sind die deutschen und gelten für alle Sprachen.

**Nur Handys mit Infrarot-Sender (ab Version 1.7.0):** Das Manifest verlangt
`android.hardware.consumerir` (`required="true"`). Google Play zeigt die App
deshalb nur auf Handys mit IR-Sender an; auf anderen Geräten ist sie im Store
nicht sichtbar bzw. als „nicht kompatibel“ markiert. Die APK von GitHub lässt
sich weiterhin überall installieren und zeigt ohne IR-Sender einen Hinweis.

Weitere Dateien: [release-notes.md](release-notes.md) („Neu in dieser Version“),
[console-guide.md](console-guide.md) (Schritte in der Play Console),
[production-access.md](production-access.md) (Antrag auf Produktionszugriff).

## App-Name (max. 30 Zeichen)

```
Fernbedienung für Hotel-TV
```

Der Name auf dem Startbildschirm bleibt „Fernbedienung“ (`app_name`).

## Kurzbeschreibung (max. 80 Zeichen)

```
TV-Fernbedienung per Infrarot: werbefrei, offline, findet den Code für dich.
```

## Vollständige Beschreibung (max. 4000 Zeichen)

```
Fernbedienung macht dein Handy zur Fernbedienung für den Fernseher – gedacht für Hotelzimmer, Ferienwohnungen und überall, wo die Original-Fernbedienung fehlt oder nicht funktioniert.

SO FUNKTIONIERT ES
Ein Assistent findet in wenigen Schritten den passenden Code: Marke wählen (zum Beispiel Samsung, LG oder Philips), auf MUTE tippen und angeben, ob der Fernseher reagiert hat. Ist die Marke unbekannt, sendet ein Suchlauf nacheinander Ausschalt-Befehle. Sobald der Fernseher ausgeht, tippst du auf Stopp, und die App prüft die zuletzt gesendeten Codes.

DIE FERNBEDIENUNG
• Ein/Aus, Lautstärke, Programm und Stummschaltung
• Zifferntasten, Menü, Pfeiltasten und OK
• SOURCE und – wenn der Fernseher es unterstützt – HDMI 1 bis 4 direkt
• Farbtasten und weitere Tasten des gefundenen Codes
• Lautstärke und Programm laufen weiter, solange du die Taste hältst

Mehrere Fernbedienungen lassen sich speichern und benennen, etwa „Zimmer 412“. Beim nächsten Start ist die zuletzt benutzte sofort da.

AUTOMATIONEN UND SLEEP-TIMER
• Automationen: mehrere Tasten nacheinander mit einem Tipp, z. B. Menü → Pfeil runter → OK, mit eigener Wartezeit pro Schritt
• Aufnehmen statt zusammenklicken: einfach die Tasten wie gewohnt drücken – die App merkt sich Reihenfolge und Pausen
• Vorlagen für den Sleep-Timer des Fernsehers: per Sleep-Taste oder Menüweg gängiger Marken – ungeprüft, einmal testen und bei Bedarf anpassen
• Sleep-Timer im Handy: nach 15 bis 120 Minuten (oder eigenem Wert) schaltet das Handy den Fernseher aus oder startet eine Automation – auch wenn die App geschlossen ist. Das Handy muss dafür auf den Fernseher zeigen.

SCHNELLSTEUERUNG OHNE APP
• Kacheln in den Schnelleinstellungen für Ein/Aus, leiser, lauter und Eingang (ab Android 7) – funktionieren auch auf dem Sperrbildschirm
• Widgets für den Startbildschirm mit 1 bis 6 frei wählbaren Tasten – jede Taste der Fernbedienung oder eine Automation, z. B. ein Lautstärke- und ein HDMI-Widget
• Auf Wunsch eine stille Benachrichtigung mit frei wählbaren Tasten auf dem Sperrbildschirm – herunterziehen öffnet eine Mini-Fernbedienung mit Steuerkreuz
• Optional die ganze Fernbedienung über dem Sperrbildschirm, ohne Entsperren (Tippen auf die Benachrichtigung oder Kachel „TV Fernbedienung“) – nur die Tasten, sonst nichts

WERBEFREI UND OFFLINE
• Keine Werbung, keine In-App-Käufe, kein Konto
• Keine Internet-Berechtigung – funktioniert auch im Flugmodus
• Keine Datenerhebung, keine Analyse
• Die Code-Datenbank ist in der App enthalten (Flipper-IRDB, CC0)
• Quelloffen: github.com/RolufS/IR-Remote-APK

11 SPRACHEN
Deutsch, Englisch, Chinesisch (vereinfacht), Hindi, Spanisch, Französisch, Arabisch, Bengalisch, Portugiesisch, Russisch und Indonesisch. Die Sprache wählst du beim ersten Start, später jederzeit im Menü.

WICHTIG
Benötigt ein Handy mit Infrarot-Sender (IR-Blaster) und Android 6 oder neuer. Viele Handys haben keinen IR-Sender; im Play Store wird die App deshalb nur für Handys mit IR-Sender angeboten. Fernseher, die nur per WLAN oder Bluetooth gesteuert werden, lassen sich nicht bedienen.

Markennamen gehören ihren Inhabern. Die App steht in keiner Verbindung zu den Herstellern.
```

## Formulare in der Play Console

### Datensicherheit („Data safety“)

- Erhebt oder teilt die App erforderliche Nutzerdaten? **Nein.**
  Begründung: Die App hat keine INTERNET-Berechtigung und keine Analyse-, Werbe-
  oder Absturzbibliotheken. Gespeicherte Fernbedienungen, Automationen und der
  Sleep-Timer bleiben im privaten Speicher der App auf dem Gerät; Daten, die das Gerät nie verlassen, gelten bei
  Google nicht als „erhoben“.
- Damit entfallen die Fragen zu Verschlüsselung und Löschanfragen; Ergebnis im
  Store: „Keine Daten erhoben“, „Keine Daten an Dritte weitergegeben“.

### Berechtigungen (Stand Version 1.7.0)

- `TRANSMIT_IR` – Infrarot senden.
- `SCHEDULE_EXACT_ALARM` – Sleep-Timer löst pünktlich aus (ab Android 14 nur
  nach Zustimmung des Nutzers; ohne sie ein ungenauer Alarm). Bewusst **kein**
  `USE_EXACT_ALARM`. Die App ist kein Wecker; der genaue Alarm ist eine vom
  Nutzer gestartete Zeitschaltung (Sleep-Timer). Falls die Play Console unter
  „App-Inhalte“ eine Erklärung zu „Alarme und Erinnerungen“ (exakte Alarme)
  verlangt, diesen Text verwenden:

  > Die App nutzt exakte Alarme ausschließlich für einen vom Nutzer selbst
  > gestarteten Sleep-Timer: Nach der gewählten Zeit (15 bis 120 Minuten oder
  > eigener Wert) sendet das Handy per Infrarot den Ausschalt-Befehl an den
  > Fernseher oder startet eine vom Nutzer angelegte Automation. Ein ungenauer
  > Alarm würde den Fernseher spürbar zu spät ausschalten. Ohne Zustimmung des
  > Nutzers (ab Android 14) verwendet die App einen ungenauen Alarm.

  Englisch: „The app uses exact alarms only for a sleep timer that the user
  starts: after the chosen time (15 to 120 minutes or a custom value) the phone
  sends the infrared power-off command to the TV or runs an automation the user
  created. An inexact alarm would turn the TV off noticeably late. Without the
  user's consent (Android 14+) the app falls back to an inexact alarm.“
- `POST_NOTIFICATIONS` – laufende Benachrichtigung des Sleep-Timers mit Restzeit
  und „Abbrechen“ sowie die (optionale, standardmäßig aus) Steuerung auf dem
  Sperrbildschirm.
- `RECEIVE_BOOT_COMPLETED` – einen laufenden Sleep-Timer nach einem Neustart
  wieder planen und die Sperrbildschirm-Steuerung (falls eingeschaltet) wieder anzeigen.
- `VIBRATE` (ab Version 1.4) – kurzes Vibrieren als Bestätigung, wenn eine Kachel,
  das Widget oder die Sperrbildschirm-Steuerung sendet (normale Berechtigung, keine Rückfrage).
- Keine Internet-, Standort-, Kontakt- oder Vordergrunddienst-Berechtigung.

### Einstufung des Inhalts (IARC-Fragebogen)

- E-Mail-Adresse für die Einstufung: Entwickler-E-Mail.
- Kategorie: **Dienstprogramm, Produktivität, Kommunikation oder Sonstiges**
  („Dienstprogramm“).
- Alle Fragen mit **Nein** beantworten (keine Gewalt, keine sexuellen Inhalte,
  keine derbe Sprache, keine Drogen, kein Glücksspiel, keine Interaktion oder
  Inhalte von Nutzern, kein Teilen des Standorts, keine digitalen Käufe).
- Erwartetes Ergebnis: niedrigste Altersstufe (USK 0 / PEGI 3 / IARC 3+).

### Zielgruppe und Inhalte

- Empfehlung: Zielgruppe **18 Jahre und älter**; Frage „Könnte die App
  unbeabsichtigt Kinder ansprechen?“ → **Nein**.
- Begründung: Die App ist ein Werkzeug für Reisende im Hotelzimmer und richtet
  sich nicht an Kinder. Eine Zielgruppe unter 13 würde die Familienrichtlinien
  (Programm „Für Familien“) mit zusätzlichen Pflichten und Prüfungen auslösen,
  ohne Nutzen für die App. Die Altersfreigabe (IARC 3+) bleibt davon unberührt;
  die App kann trotzdem von allen installiert werden.

### Werbung

- „Enthält deine App Werbung?“ → **Nein, meine App enthält keine Werbung.**
- Werbe-ID („Advertising ID“): Die App verwendet **keine** Werbe-ID.

### App-Zugriff

- **Alle Funktionen sind ohne besondere Zugriffsrechte verfügbar.** Keine
  Anmeldung, kein Konto, keine Zugangsdaten nötig.

### Weitere Erklärungen

- Regierungs-App: Nein. Finanzfunktionen: keine. Gesundheit: keine.
  Nachrichten-App: Nein.
- Datenschutzerklärung (URL): https://github.com/RolufS/IR-Remote-APK/blob/main/PRIVACY.md

### Store-Einstellungen

- App oder Spiel: **App**
- Kategorie: **Tools**
- Kostenlos: **ja** (kann später nicht mehr auf kostenpflichtig umgestellt werden)
- Website (optional): https://github.com/RolufS/IR-Remote-APK

### Geräte

- Durch `android.hardware.consumerir` (erforderlich) filtert Play alle Geräte
  ohne IR-Sender aus. Im Gerätekatalog der Play Console erscheinen die meisten
  Geräte deshalb als „nicht unterstützt“ – das ist so gewollt, nichts ausschließen
  oder hinzufügen.

## Grafiken

| Datei | Größe | Verwendung |
|---|---|---|
| `icon-512.png` | 512 × 512, 32-Bit-PNG | App-Symbol |
| `feature-graphic.png` | 1024 × 500 | Vorstellungsgrafik |
| `screenshot-1-remote.png` | 1080 × 1920 | Telefon-Screenshot: Fernbedienung |
| `screenshot-2-wizard-brand.png` | 1080 × 1920 | Telefon-Screenshot: Markenwahl |
| `screenshot-3-wizard-test.png` | 1080 × 1920 | Telefon-Screenshot: Code-Test |
| `screenshot-4-inputs.png` | 1080 × 1920 | Telefon-Screenshot: Eingangs-Test |
| `screenshot-5-automations.png` | 1080 × 1920 | Telefon-Screenshot: Automationen mit Vorlagen |
| `screenshot-6-recording.png` | 1080 × 1920 | Telefon-Screenshot: Aufnahme-Modus |
| `screenshot-7-widget-setup.png` | 1080 × 1920 | Telefon-Screenshot: Widget einrichten |
| `screenshot-8-lockscreen.png` | 1080 × 1920 | Telefon-Screenshot: Mini-Fernbedienung auf dem Sperrbildschirm (Darstellung) |

Icon und Vorstellungsgrafik erzeugt `tools/make_graphics.py`, die Screenshots
der Robolectric-Test `StoreScreenshots` (siehe README, Abschnitt „Play Store“).
Screenshots im Format 9:16, weil Google höchstens das Seitenverhältnis 2:1 zulässt;
Play erlaubt bis zu 8 Telefon-Screenshots, alle 8 in der Reihenfolge 1 bis 8
hochladen. Bild 8 ist eine Darstellung: die echten Layouts der Benachrichtigung
in einem nachgebildeten Sperrbildschirm (im Bild vermerkt).

## Checkliste: Schritte, die nur du machen kannst

Ausführliche Reihenfolge mit Dateien: [console-guide.md](console-guide.md).

1. Entwicklerkonto anlegen (https://play.google.com/console): einmalig 25 US$,
   Identitätsprüfung mit Ausweis; bei privaten Konten auch Bestätigung eines
   Android-Geräts und der Telefonnummer.
2. Kontakt-E-Mail für den Store-Eintrag angeben (Pflicht, öffentlich sichtbar).
3. App anlegen: Name „Fernbedienung für Hotel-TV“, Sprache Deutsch, App, kostenlos;
   Erklärungen bestätigen.
4. Store-Eintrag ausfüllen: Texte und Grafiken aus diesem Ordner hochladen.
5. Formulare ausfüllen: Datenschutzerklärung, App-Zugriff, Werbung, Einstufung
   des Inhalts, Zielgruppe, Datensicherheit, Werbe-ID, Regierungs-App,
   Finanz- und Gesundheitsfunktionen (Antworten oben).
6. Vorher das Repo öffentlich auf GitHub stellen, damit die URL der
   Datenschutzerklärung erreichbar ist.
7. Geschlossener Test (Pflicht für neue private Konten): Track „Geschlossener
   Test“ anlegen, `dist/Fernbedienung-1.7.0.aab` hochladen (vorher den
   App-Signaturschlüssel festlegen – empfohlen: eigenen Schlüssel
   `~/.android/hotelremote.jks` hochladen, siehe
   [console-guide.md, Schritt 5.4](console-guide.md#5-geschlossener-test)), mindestens
   **12 Tester** per E-Mail-Liste einladen, die **14 Tage ohne Unterbrechung**
   dabeibleiben. Tester brauchen ein Handy mit IR-Sender – ohne sehen sie die
   App im Play Store nicht.
8. Danach in der Play Console den Produktionszugriff beantragen (vorbereitete
   Antworten: [production-access.md](production-access.md)), dann Release in
   „Produktion“ erstellen und zur Prüfung einreichen.
9. Für jedes spätere Update `versionCode` erhöhen und neu signiert hochladen.

## Zeichenzahlen

| Text | Zeichen | Grenze |
|---|---|---|
| App-Name | 26 | 30 |
| Kurzbeschreibung | 76 | 80 |
| Vollständige Beschreibung | 3169 | 4000 |

Gezählt als Unicode-Zeichen (Python `len`) des Codeblock-Inhalts, Zeilenumbrüche mitgezählt.
