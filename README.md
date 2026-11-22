# Fernbedienung

Offline-Fernbedienung für Hotel-Fernseher per Infrarot (Paket `de.hotelremote`).
Ein Wizard findet den passenden Code für den TV, danach steht eine Fernbedienung
mit HDMI-Reihe und Zusatztasten bereit. Die App hat keine INTERNET-Berechtigung;
die Code-Datenbank steckt im APK. Benötigt ein Handy mit IR-Sender.

## APK installieren

- USB: `adb install -r dist/Fernbedienung-1.0.apk`
- Oder die Datei aufs Handy kopieren und dort öffnen. Dabei die Installation
  aus unbekannten Quellen erlauben.

## App bauen

```
./gradlew :app:assembleRelease
```

Ergebnis: `app/build/outputs/apk/release/app-release.apk`. Zum Signieren stehen
in `~/.gradle/gradle.properties` (außerhalb des Repos) vier Properties:
`HOTELREMOTE_STORE_FILE`, `HOTELREMOTE_STORE_PASSWORD`, `HOTELREMOTE_KEY_ALIAS`,
`HOTELREMOTE_KEY_PASSWORD`. Fehlen sie, wird unsigniert gebaut (mit Warnung).
Den Schlüssel anlegen: `keytool -genkeypair -keystore ~/.android/hotelremote.jks -alias hotelremote -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Fernbedienung"`.

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

1. Erster Start öffnet den Wizard.
2. Die Marke des eigenen TVs wird gefunden, MUTE schaltet stumm.
3. Bestätigungstest und Eingangs-Test laufen.
4. Speichern führt zur Fernbedienung, HDMI-Reihe je nach Ergebnis.
5. VOL halten zählt hoch und stoppt beim Loslassen.
6. Die App neu starten öffnet die letzte Fernbedienung.
7. Der Suchlauf schaltet den TV aus, der Stopp mit Kandidatenprüfung schaltet ihn wieder an.
8. Flugmodus an: Alles funktioniert unverändert.
