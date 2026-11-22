# Fernbedienung

Offline-Fernbedienung für Hotel-Fernseher per Infrarot (Paket `de.hotelremote`).
Ein Wizard findet den passenden Code für den TV, danach steht eine Fernbedienung
mit HDMI-Reihe und Zusatztasten bereit. Automationen senden mehrere Tasten
nacheinander, ein Sleep-Timer schaltet den TV vom Handy aus (auch bei
geschlossener App). Die App hat keine INTERNET-Berechtigung;
die Code-Datenbank steckt im APK. Benötigt ein Handy mit IR-Sender.

## Sprachen

Die App gibt es in 11 Sprachen: Deutsch (Standard, `values/`), Englisch,
Chinesisch (vereinfacht), Hindi, Spanisch, Französisch, Arabisch, Bengalisch,
Portugiesisch, Russisch und Indonesisch (`values-en`, `values-zh-rCN`,
`values-hi`, `values-es`, `values-fr`, `values-ar`, `values-bn`, `values-pt`,
`values-ru`, `values-in`). Beim ersten Start fragt die App nach der Sprache
(vorausgewählt: Systemsprache, sonst Englisch); später lässt sie sich im
⋮-Menü der Fernbedienung oder auf der Markenwahl unter „Sprache“ ändern, ab
Android 13 auch in den Android-Einstellungen der App.

Die Übersetzungen sind maschinell erstellt. Korrekturen sind willkommen –
einfach die passende `app/src/main/res/values-*/strings.xml` ändern; der Test
`TranslationsTest` prüft, dass Namen und Platzhalter (`%1$s`, `%1$d`) zu
`values/strings.xml` passen.

## APK installieren

- USB: `adb install -r dist/Fernbedienung-1.2.apk`
- Oder die Datei aufs Handy kopieren und dort öffnen. Dabei die Installation
  aus unbekannten Quellen erlauben.

## App bauen

```
./gradlew :app:assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/Fernbedienung-1.2.apk
```

Gradle legt `app/build/outputs/apk/release/app-release.apk` an; die Kopie unter
`dist/Fernbedienung-1.2.apk` ist die Datei zum Installieren (`dist/` steht nicht
im Repo). Zum Signieren stehen
in `~/.gradle/gradle.properties` (außerhalb des Repos) vier Properties:
`HOTELREMOTE_STORE_FILE`, `HOTELREMOTE_STORE_PASSWORD`, `HOTELREMOTE_KEY_ALIAS`,
`HOTELREMOTE_KEY_PASSWORD`. `HOTELREMOTE_STORE_FILE` muss ein absoluter Pfad
sein (kein `~`; ein relativer Pfad würde relativ zu `app/` aufgelöst).
Fehlen sie, wird unsigniert gebaut (mit Warnung).
Den Schlüssel anlegen: `keytool -genkeypair -keystore ~/.android/hotelremote.jks -alias hotelremote -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Fernbedienung"`.

## Play Store

Für den Play Store wird ein App Bundle (AAB) hochgeladen. Es wird mit demselben
Schlüssel signiert wie das APK (Properties wie oben):

```
./gradlew :app:bundleRelease
mkdir -p dist
cp app/build/outputs/bundle/release/app-release.aab dist/Fernbedienung-1.2.aab
```

Play App Signing: Beim ersten Upload in der Play Console „Play App Signing“
aktiv lassen. Google erzeugt und verwaltet dann den eigentlichen App-Signaturschlüssel;
`~/.android/hotelremote.jks` dient als Upload-Schlüssel. Mit diesem Schlüssel
müssen alle späteren Bundles signiert sein – Keystore und Passwörter sicher
aufbewahren (nicht im Repo). Store-Texte, Formular-Antworten und Grafiken liegen
unter `docs/play-store/`.

Grafiken neu erzeugen:

```
# App-Icon (Vektor + Legacy-PNGs), icon-512.png, feature-graphic.png (benötigt Pillow)
tools/.venv/Scripts/python tools/make_graphics.py
# Screenshots 1080 x 1920 aus den echten Compose-Bildschirmen (Robolectric, ohne Emulator)
./gradlew :app:testDebugUnitTest --tests de.hotelremote.StoreScreenshots -Pscreenshots
```

Ohne `-Pscreenshots` wird der Screenshot-Test übersprungen.

## Code-Datenbank neu bauen

```
bash tools/fetch_sources.sh
tools/.venv/Scripts/python tools/build_codes.py --flipper tools/sources/flipper --out app/src/main/assets/codes.db
```

Danach die App neu bauen.

## irdb (optional)

Mit `--irdb tools/sources/irdb` fließen zusätzlich Codes aus irdb ein. Deren
Lizenz verlangt, vorher ein Issue beim irdb-Projekt zu eröffnen und in der App
einen Hinweis anzuzeigen. Der Info-Bildschirm zeigt den Hinweis automatisch,
wenn irdb enthalten ist. Der Standard-Build verwendet irdb nicht.

## Prüfliste am Gerät (mit echtem Fernseher)

1. Erster Start fragt nach der Sprache, danach öffnet sich der Wizard.
2. Die Marke des eigenen TVs wird gefunden, MUTE schaltet stumm.
3. Bestätigungstest und Eingangs-Test laufen.
4. Speichern führt zur Fernbedienung, HDMI-Reihe je nach Ergebnis.
5. VOL halten zählt hoch und stoppt beim Loslassen.
6. Die App neu starten öffnet die letzte Fernbedienung.
7. Der Suchlauf schaltet den TV aus, der Stopp mit Kandidatenprüfung schaltet ihn wieder an.
8. Flugmodus an: Alles funktioniert unverändert.
9. ⋮ → Sprache → z. B. English: Texte wechseln, die gespeicherten Fernbedienungen bleiben.
10. ⋮ → Automationen → „+ Neue Automation“: Name, Schritte (z. B. Menü, Pfeil runter, OK)
    mit Wartezeiten, „Testen“ sendet die Folge, Speichern. Über ⚡ startet ein Tipp die
    Automation; die Leiste zeigt „Schritt i von n“, „Abbrechen“ stoppt, der Bildschirm bleibt an.
11. ⚡ → Sleep-Timer (Handy) → eigener Wert 2 min, „Ausschalten“: Akku-Hinweis (nur beim
    ersten Mal), ab Android 14 Frage nach „Wecker und Erinnerungen“, ab Android 13 nach
    Benachrichtigungen. Die Benachrichtigung zählt herunter; App schließen (aus „Zuletzt
    verwendet“ wischen), Handy sperren und auf den TV legen: nach 2 min geht der TV aus,
    danach „Sleep-Timer ausgeführt“.
12. Timer starten und über „Abbrechen“ in der Benachrichtigung beenden: nichts wird gesendet.
13. Timer mit 10 min starten, Handy neu starten: der Timer läuft weiter und löst aus.
14. Auf Xiaomi/HyperOS: Akku „Keine Einschränkungen“ setzen und Punkt 11 wiederholen.

Hinweis: Vor Android 13 erscheinen die Sleep-Timer-Benachrichtigungen in der
Systemsprache, nicht in der in der App gewählten Sprache (die App-Sprache gilt
dort nur für die Bildschirme der App).

## Datenschutz

Die App erhebt keine Daten und hat keinen Internetzugang, siehe
[PRIVACY.md](PRIVACY.md). Diese Datei ist auch die Datenschutzerklärung für die
Play Console: https://github.com/RolufS/IR-Remote-APK/blob/main/PRIVACY.md

## Lizenz

Der Code steht unter der MIT-Lizenz, siehe [LICENSE](LICENSE).
Die Code-Daten in `app/src/main/assets/codes.db` stammen aus der
[Flipper-IRDB](https://github.com/Lucaslhm/Flipper-IRDB) (CC0-1.0).
