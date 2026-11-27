# Play-Store-Eintrag (Englisch)

Englische Übersetzung des Store-Eintrags aus [listing-de.md](listing-de.md), für
die Play Console unter „Store-Eintrag → Übersetzungen verwalten → Englisch
(Vereinigte Staaten) – en-US“. Gleiche Längenregeln; die Texte in den
Codeblöcken werden 1:1 übernommen, Zeichenzahlen stehen am Ende. Formulare,
Grafiken und Checkliste stehen nur in `listing-de.md` und gelten für alle
Sprachen.

Ab Version 1.7.0 bietet Google Play die App nur Handys mit Infrarot-Sender an
(`android.hardware.consumerir` erforderlich); Details in `listing-de.md`.

## App name (max. 30 characters)

```
TV Remote for Hotels
```

## Short description (max. 80 characters)

```
Infrared remote for TV and air conditioner: ad-free, finds the right code.
```

## Full description (max. 4000 characters)

```
TV Remote turns your phone into a remote control for the TV and the air conditioner – made for hotel rooms, holiday flats and anywhere the original remote is missing or broken.

HOW IT WORKS
A short wizard finds the right code: pick the brand (for example Samsung, LG or Philips), tap MUTE and say whether the TV responded. If the brand is unknown, a scan sends power-off commands one after another. As soon as the TV turns off, tap Stop and the app checks the codes it sent last.

THE REMOTE
• Power, volume, channels and mute
• Number keys, menu, arrow keys and OK
• SOURCE and – if the TV supports it – HDMI 1 to 4 directly
• Color keys and further keys of the code that was found
• Volume and channel keep going while you hold the key

Save and name several remotes, such as “Room 412”. Next time, the one you used last is ready right away.

AIR CONDITIONER
• Its own wizard: pick the brand, the app sends “On · Cool · 24 °C” and you say whether the unit responded
• Large target temperature, −/+, mode, fan and swing; swipe between the TV and the AC of a room
• Optionally the outdoor temperature, e.g. “Outside 31 °C · Pattaya” (off by default, place by search, no location permission)
• AC automation: e.g. 5 °C cooler than outside, but not colder than 25 °C – checks every 30 minutes and only sends when the target changes. The phone has to stay in the room.

AUTOMATIONS AND SLEEP TIMER
• Automations: several keys in a row with one tap, e.g. Menu → Arrow down → OK, with its own wait time per step
• Record instead of building by hand: just press the keys as usual – the app remembers the order and the pauses
• Templates for the TV's own sleep timer: via the sleep key or the menu path of common brands – untested, try once and adjust if needed
• Sleep timer on the phone: after 15 to 120 minutes (or a custom value) the phone turns the TV off or runs an automation – even when the app is closed. The phone has to point at the TV.

QUICK CONTROLS WITHOUT THE APP
• Quick Settings tiles for power, volume down, volume up and input (Android 7+) – they work on the lock screen too
• Home screen widgets with 1 to 6 keys of your choice – any key of the remote or an automation, e.g. one widget for volume and one for HDMI
• Optionally a silent notification with keys of your choice on the lock screen – pull it down for a mini remote with arrow keys
• Optionally the full remote over the lock screen, without unlocking (tap the notification or the "TV remote" tile) – just the keys, nothing else

AD-FREE, NO TRACKING
• No ads, no in-app purchases, no account
• Internet only for the optional outdoor temperature: only if you turn it on, the chosen place is sent to Open-Meteo. Everything else works in airplane mode too.
• No data collection, no analytics
• The code databases are built into the app (Flipper-IRDB, CC0; SmartIR, MIT); weather data: Open-Meteo.com (CC BY 4.0)
• Open source: github.com/RolufS/IR-Remote-APK

11 LANGUAGES
English, German, Chinese (Simplified), Hindi, Spanish, French, Arabic, Bengali, Portuguese, Russian and Indonesian. Choose the language on first start and change it any time in the menu.

IMPORTANT
Requires a phone with an infrared transmitter (IR blaster) and Android 6 or later. Many phones don’t have one, so on Google Play the app is offered only to phones with an IR transmitter. TVs and air conditioners that are controlled only via Wi-Fi or Bluetooth are not supported.

Brand names belong to their owners. The app is not affiliated with the manufacturers.
```

## Zeichenzahlen

| Text | Zeichen | Grenze |
|---|---|---|
| App name | 20 | 30 |
| Short description | 74 | 80 |
| Full description | 3508 | 4000 |

Gezählt als Unicode-Zeichen (Python `len`) des Codeblock-Inhalts, Zeilenumbrüche mitgezählt.
