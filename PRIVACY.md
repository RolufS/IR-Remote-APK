# Datenschutzerklärung – Fernbedienung

Stand: 27. November 2026 · App: „Fernbedienung“ (Paket `de.hotelremote`)

## Kurz gesagt

Der Entwickler erhebt keine Daten, und die App gibt nichts an ihn weiter. Ins Internet
geht sie nur, wenn du die optionale Außentemperatur einschaltest (Standard aus):
dann gehen der gesuchte Ortsname (mit der App-Sprache) bzw. die Koordinaten des
gewählten Orts an Open-Meteo. Keine Konten, keine Werbung,
kein Tracking.

## Im Einzelnen

- **Keine Datenerhebung durch den Entwickler.** Die App sammelt keine
  personenbezogenen oder sonstigen Daten für den Entwickler und überträgt nichts
  an ihn. Nur wenn du die Außentemperatur einschaltest, gehen an Open-Meteo: bei
  der Ortssuche der eingetippte Ortsname mit der App-Sprache, beim Abruf der
  Temperatur die Koordinaten des gewählten Orts (siehe nächster Punkt). Sonst
  verlässt nichts das Gerät.
- **Internet nur für die Außentemperatur (optional, Standard aus).** Die App hat
  ab Version 1.8.0 die Internet-Berechtigung, nutzt sie aber nur, wenn du im
  Klima-Bereich „Außentemperatur anzeigen“ einschaltest. Solange das aus ist, baut
  sie keine Verbindung auf. Eingeschaltet sendet sie an den Wetterdienst
  [Open-Meteo](https://open-meteo.com) (Open-Meteo.com, Wetterdaten CC BY 4.0):
  - bei der Ortssuche den Ortsnamen, den du eintippst, und die App-Sprache
    (Geokodierung `geocoding-api.open-meteo.com`),
  - zum Abruf der Temperatur nur die Koordinaten des gewählten Orts
    (`api.open-meteo.com`), beim Öffnen des Klima-Bildschirms (höchstens alle
    15 Minuten), beim Antippen der Anzeige und bei laufender Klima-Automatik
    alle 30 Minuten.

  Es gibt keine Standortberechtigung; die App kennt deinen Standort nicht, nur
  den Ort, den du selbst auswählst. Es wird kein Konto, keine Kennung und keine
  Werbe-ID mitgesendet. Wie jeder Webserver sieht Open-Meteo dabei technisch
  bedingt die IP-Adresse der Anfrage; zur Verarbeitung dort siehe die
  Datenschutzerklärung von Open-Meteo (https://open-meteo.com/en/terms). Beim
  Entwickler kommen keine Daten an. Ausschalten beendet alle Abrufe.
- Die Code-Datenbanken für Fernseher und Klimaanlagen sind in der App enthalten.
- **Keine Werbung, keine Analyse.** Die App enthält keine Werbung und keine
  Bibliotheken für Analyse, Tracking oder Absturzberichte.
- **Keine Weitergabe.** Außer dem oben beschriebenen, von dir eingeschalteten
  Wetterabruf werden keine Daten an Dritte weitergegeben.
- **Gespeicherte Fernbedienungen** (Marke, Code, optionaler Name wie
  „Zimmer 412“, Automationen, Klimaanlage mit zuletzt gesendetem Zustand und
  Einstellungen der Klima-Automatik), ein laufender Sleep-Timer bzw. eine laufende
  Klima-Automatik sowie der gewählte Wetter-Ort mit dem letzten Temperaturwert
  liegen nur lokal auf dem Gerät im privaten Speicher der App.
  Sie werden nicht gesichert oder synchronisiert und beim Deinstallieren der
  App gelöscht.
- **Sperrbildschirm.** Optional (Standard aus) lässt sich die Fernbedienung über
  dem Sperrbildschirm öffnen. Dort zeigt die App nur den Namen der zuletzt
  benutzten Fernbedienung und ihre Tasten; nichts ist änderbar, es werden keine
  weiteren Daten angezeigt und keine Daten erhoben.
- **Berechtigungen:**
  - „Infrarot senden“ (`android.permission.TRANSMIT_IR`), um Signale an den
    Fernseher bzw. die Klimaanlage zu senden.
  - „Internetzugriff“ (`android.permission.INTERNET`) – nur für die optionale
    Außentemperatur (siehe oben); ohne Einschalten unbenutzt.
  - „Wecker und Erinnerungen“ (`android.permission.SCHEDULE_EXACT_ALARM`),
    damit der Sleep-Timer pünktlich auslöst.
  - „Benachrichtigungen“ (`android.permission.POST_NOTIFICATIONS`) für die
    Restzeit des Sleep-Timers, die laufende Klima-Automatik und die (optionale)
    Steuerung auf dem Sperrbildschirm.
  - „Nach dem Start ausführen“ (`android.permission.RECEIVE_BOOT_COMPLETED`),
    um einen laufenden Sleep-Timer bzw. eine laufende Klima-Automatik nach einem
    Neustart wieder zu planen und die
    Steuerung auf dem Sperrbildschirm (falls eingeschaltet) wieder anzuzeigen.
  - „Vibrieren“ (`android.permission.VIBRATE`) als kurze Bestätigung, wenn eine
    Kachel, das Widget oder die Sperrbildschirm-Steuerung sendet.

  Bis auf „Internetzugriff“ (nur bei eingeschalteter Außentemperatur) wirken alle
  Berechtigungen nur auf dem Gerät.

## Kontakt

Fragen zum Datenschutz bitte als Issue auf GitHub stellen:
https://github.com/RolufS/IR-Remote-APK/issues

---

# Privacy Policy – Fernbedienung (English)

The developer collects no data, and the app sends nothing to the developer. It only goes online if you turn on the optional
outdoor temperature (off by default): then the place name you search for (with the app language) or the coordinates
of the chosen place are sent to Open-Meteo. No accounts, no ads, no tracking.

- **No collection by the developer.** The app collects no personal or other data for the developer and sends
  nothing to them. Only if you turn on the outdoor temperature, Open-Meteo receives the place name you type when
  searching (with the app language) and the coordinates of the chosen place when fetching the temperature (see next
  point). Otherwise nothing leaves the device.
- **Internet only for the outdoor temperature (optional, off by default).** Since version 1.8.0 the app has the
  internet permission but uses it only after you turn on "Show outdoor temperature" in the air-conditioner section.
  While it is off, the app makes no connection. When on, it sends to the weather service
  [Open-Meteo](https://open-meteo.com) (Open-Meteo.com, weather data CC BY 4.0) only the place name you type when
  searching (plus the app language) and the coordinates of the place you chose when fetching the temperature (when
  the AC screen opens, at most every 15 minutes; when you tap the display; every 30 minutes while the AC automation
  runs). There is no location permission, no account, no identifier and no advertising ID. As with any web server,
  Open-Meteo technically sees the IP address of the request; see Open-Meteo's terms and privacy policy
  (https://open-meteo.com/en/terms). Nothing reaches the developer. Turning it off ends all requests.
- The code databases for TVs and air conditioners are bundled with the app.
- **No ads, no analytics.** No advertising and no analytics, tracking or crash-reporting libraries.
- **No sharing.** Apart from the weather request described above, which you turn on yourself, no data is shared
  with third parties.
- **Saved remotes** (brand, code, optional name, automations, air conditioner with its last sent state and
  automation settings), a running sleep timer or AC automation and the chosen weather place with the last
  temperature are stored only locally in the app's private storage. They are not backed up or synced and are deleted when the app is uninstalled.
- **Lock screen.** Optionally (off by default) the remote can be opened over the lock screen.
  It then shows only the name of the last used remote and its keys; nothing can be changed,
  no other data is shown and no data is collected.
- **Permissions.** "Internet" (`INTERNET`) only for the optional outdoor temperature; all others act on the device
  only: "transmit infrared" (`TRANSMIT_IR`),
  exact alarms for the sleep timer (`SCHEDULE_EXACT_ALARM`), notifications for the time left and the running AC
  automation (`POST_NOTIFICATIONS`), re-scheduling a running sleep timer or AC automation and restoring the lock-screen controls
  after a reboot (`RECEIVE_BOOT_COMPLETED`) and a short vibration when a tile, the widget or the
  lock-screen controls send (`VIBRATE`).

Contact: GitHub issues at https://github.com/RolufS/IR-Remote-APK/issues
