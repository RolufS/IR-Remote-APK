# Versionshinweise („Neu in dieser Version“)

Texte für das Feld „Versionshinweise“ beim Erstellen eines Release in der Play
Console (geschlossener Test oder Produktion). Grenze: **500 Zeichen je Sprache**.
Die Texte in den Codeblöcken werden 1:1 übernommen; Zeichenzahlen stehen am Ende.

In der Play Console können beide Sprachen in einem Feld stehen, jeweils in
Sprach-Tags eingeschlossen (so schlägt die Console es selbst vor):

```
<de-DE>
…deutscher Text…
</de-DE>
<en-US>
…englischer Text…
</en-US>
```

## Version 1.8.0 (versionCode 13)

### Deutsch (de-DE)

```
Neu in 1.8.0:
• Klimaanlagen: über 400 Modelle, eigener Assistent, Temperatur, Modus, Lüfter und Swing.
• Fernseher und Klimaanlage eines Zimmers: per Wischen wechseln.
• Optional die Außentemperatur (Open-Meteo, Standard aus).
• Klima-Automatik: z. B. 5 °C kühler als draußen, aber nicht kälter als 25 °C.
• Gedrückte Tasten werden kurz dunkler.
```

### English (en-US)

```
New in 1.8.0:
• Air conditioners: over 400 models, own setup assistant, temperature, mode, fan and swing.
• TV and air conditioner of a room: switch with a swipe.
• Optional outdoor temperature (Open-Meteo, off by default).
• Climate automation: e.g. 5 °C cooler than outside, but not colder than 25 °C.
• Pressed keys briefly darken.
```

## Version 1.7.0 (versionCode 11)

### Deutsch (de-DE)

```
Neu in 1.7.0:
• HDMI-Tasten: Auf kleinen Bildschirmen ist die Ziffer nicht mehr abgeschnitten, auch während einer Aufnahme.
• Im Play Store nur für Handys mit Infrarot-Sender.

Die App: Assistent zum Finden des passenden Codes, Fernbedienung mit HDMI 1–4, Automationen mit Aufnahme und Vorlagen, Sleep-Timer, Kacheln, Widget und Sperrbildschirm-Steuerung. 11 Sprachen, ohne Werbung, ohne Internet.
```

### Englisch (en-US)

```
New in 1.7.0:
• HDMI keys: on small screens the number is no longer cut off, also while recording.
• On Google Play, offered only to phones with an infrared transmitter.

The app: a wizard that finds the right code, a remote with HDMI 1–4, automations with recording and templates, sleep timer, tiles, widget and lock-screen controls. 11 languages, no ads, no internet.
```

## Zeichenzahlen

| Text | Zeichen | Grenze |
|---|---|---|
| Deutsch | 397 | 500 |
| Englisch | 369 | 500 |

Gezählt als Unicode-Zeichen (Python `len`) des Codeblock-Inhalts, Zeilenumbrüche mitgezählt.
