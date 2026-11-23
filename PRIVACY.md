# Datenschutzerklärung – Fernbedienung

Stand: 23. November 2026 · App: „Fernbedienung“ (Paket `de.hotelremote`)

## Kurz gesagt

Die App erhebt keine Daten, hat keinen Internetzugang und gibt nichts weiter.

## Im Einzelnen

- **Keine Datenerhebung.** Die App erhebt, sammelt und überträgt keine
  personenbezogenen oder sonstigen Daten.
- **Kein Internet.** Die App hat keine Internet-Berechtigung und kann deshalb
  keine Verbindung zu Servern aufbauen. Die Code-Datenbank ist in der App enthalten.
- **Keine Werbung, keine Analyse.** Die App enthält keine Werbung und keine
  Bibliotheken für Analyse, Tracking oder Absturzberichte.
- **Keine Weitergabe.** Es werden keine Daten an Dritte weitergegeben.
- **Gespeicherte Fernbedienungen** (Marke, Code, optionaler Name wie
  „Zimmer 412“, Automationen) und ein laufender Sleep-Timer liegen nur lokal auf dem Gerät im privaten Speicher der App.
  Sie werden nicht gesichert oder synchronisiert und beim Deinstallieren der
  App gelöscht.
- **Berechtigungen.** Alle wirken nur auf dem Gerät:
  - „Infrarot senden“ (`android.permission.TRANSMIT_IR`), um Signale an den
    Fernseher zu senden.
  - „Wecker und Erinnerungen“ (`android.permission.SCHEDULE_EXACT_ALARM`),
    damit der Sleep-Timer pünktlich auslöst.
  - „Benachrichtigungen“ (`android.permission.POST_NOTIFICATIONS`) für die
    Restzeit des Sleep-Timers.
  - „Nach dem Start ausführen“ (`android.permission.RECEIVE_BOOT_COMPLETED`),
    um einen laufenden Sleep-Timer nach einem Neustart wieder zu planen und die
    Steuerung auf dem Sperrbildschirm (falls eingeschaltet) wieder anzuzeigen.
  - „Vibrieren“ (`android.permission.VIBRATE`) als kurze Bestätigung, wenn eine
    Kachel, das Widget oder die Sperrbildschirm-Steuerung sendet.

## Kontakt

Fragen zum Datenschutz bitte als Issue auf GitHub stellen:
https://github.com/RolufS/IR-Remote-APK/issues

---

# Privacy Policy – Fernbedienung (English)

The app collects no data, has no internet access and shares nothing.

- **No data collection.** The app does not collect or transmit any personal or other data.
- **No internet.** The app has no internet permission and cannot contact any server.
  The code database is bundled with the app.
- **No ads, no analytics.** No advertising and no analytics, tracking or crash-reporting libraries.
- **No sharing.** No data is shared with third parties.
- **Saved remotes** (brand, code, optional name, automations) and a running sleep timer are stored only locally in the app's private
  storage. They are not backed up or synced and are deleted when the app is uninstalled.
- **Permissions.** All of them act on the device only: "transmit infrared" (`TRANSMIT_IR`),
  exact alarms for the sleep timer (`SCHEDULE_EXACT_ALARM`), notifications for the time left
  (`POST_NOTIFICATIONS`), re-scheduling a running sleep timer and restoring the lock-screen controls
  after a reboot (`RECEIVE_BOOT_COMPLETED`) and a short vibration when a tile, the widget or the
  lock-screen controls send (`VIBRATE`).

Contact: GitHub issues at https://github.com/RolufS/IR-Remote-APK/issues
