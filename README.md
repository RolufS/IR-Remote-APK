# Fernbedienung

Offline-Fernbedienung für Hotel-Fernseher per Infrarot (Paket `de.hotelremote`).
Ein Wizard findet den passenden Code für den TV, danach steht eine Fernbedienung
mit HDMI-Reihe und Zusatztasten bereit. Automationen senden mehrere Tasten
nacheinander, ein Sleep-Timer schaltet den TV vom Handy aus (auch bei
geschlossener App). Ein/Aus, Lautstärke und Eingang gehen auch ohne die App zu
öffnen: über Kacheln in den Schnelleinstellungen (auch auf dem Sperrbildschirm),
Widgets mit frei wählbaren Tasten und Automationen und optional eine
Benachrichtigung auf dem Sperrbildschirm, die sich zur Mini-Fernbedienung aufziehen lässt;
auf Wunsch auch die ganze Fernbedienung über dem Sperrbildschirm.
Die App hat keine INTERNET-Berechtigung;
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

## Automationen

Eine Automation sendet mehrere Tasten nacheinander, mit einer Wartezeit nach
jedem Schritt (0,5–10 s). Anlegen geht auf zwei Wegen:

- **Liste:** ⋮ → Automationen → „+ Neue Automation“, Tasten einzeln hinzufügen.
- **Aufnahme:** „● Aufnehmen“ in der Automationen-Liste, „● Neue Automation
  aufnehmen“ im ⚡-Sheet oder „● Per Fernbedienung aufnehmen“ im Editor (hängt an
  die vorhandenen Schritte an). Danach einfach die Fernbedienung benutzen – auch
  HDMI-Reihe und ⋮ → „Weitere Tasten“: jede Taste wird normal gesendet und als
  Schritt aufgezeichnet (gehaltene VOL/CH/Pfeile als ein Schritt), die Pause bis
  zur nächsten Taste wird zur Wartezeit (auf 0,5 s gerundet). Die rote Leiste
  zeigt die Schrittzahl, „Rückgängig“ entfernt den letzten Schritt, „Fertig“
  öffnet den Editor (Name eingeben, Wartezeiten anpassen, speichern),
  „Abbrechen“ bzw. Zurück verwirft die Aufnahme nach Rückfrage. Während der
  Aufnahme sind ☰, ⚡ und das Wechseln der Fernbedienung gesperrt.

Ausgeführt wird eine Automation über ⚡ oder ▶ in der Liste.

## Sleep-Timer-Vorlagen

Unter den Automationen stehen fertige Vorlagen, die den Sleep-Timer **des Fernsehers**
stellen (unabhängig vom Sleep-Timer im Handy):

- **Sleep-Taste** (zuerst, klappt im Hotelmodus am ehesten): für jede Fernbedienung mit
  eigener Sleep-Taste, ≈ 30 / 60 / 90 min. Die Zahl der Drücke richtet sich nach der Marke
  (z. B. Vestel-Familie 10-min-Schritte: 6× für 60 min; LG, Sony, Philips 4×; Samsung 2×).
- **Menüweg** der Marke (Samsung Tizen/TOOLS, LG webOS, Sony Bravia/Google TV, Philips Saphi,
  Panasonic Viera, Hisense VIDAA, TCL Google TV), nur wenn alle nötigen Tasten vorhanden sind.
  „Weitere Vorlagen (andere Marken)“ zeigt die Menüwege anderer Marken.

Ein Tipp öffnet den Editor mit Schritten und Namen; gespeichert wird eine normale Automation.
**Alle Vorlagen sind ungeprüft:** Menüpfade stammen aus Handbüchern oder Foren, die Zahl der
Tastendrücke ist geschätzt – bitte einmal mit „Testen“ prüfen und bei Bedarf anpassen.
Recherche und Quellen: [docs/sleep-timer-research.md](docs/sleep-timer-research.md).
Rückmeldungen (klappt / klappt nicht, mit Marke und Baujahr) gerne als
[Issue](https://github.com/RolufS/IR-Remote-APK/issues).

## Schnellsteuerung

Die wichtigsten Tasten ohne die App zu öffnen. Alle Wege senden an die **zuletzt
benutzte Fernbedienung** (die in der App zuletzt geöffnete). Android erlaubt auf
Handys keine Widgets auf dem Sperrbildschirm; deshalb drei Wege:

- **Kacheln** (ab Android 7): „TV Ein/Aus“, „TV leiser“, „TV lauter“, „TV Eingang“
  und „TV Fernbedienung“ (siehe „Sperrbildschirm“).
  Schnelleinstellungen aufklappen → Bearbeiten (Stift) → Kacheln hineinziehen; ab
  Android 13 auch über ⋮ → Schnellsteuerung → „Kachel … hinzufügen“. Ein Tipp sendet
  sofort, auch auf dem Sperrbildschirm ohne Entsperren (kurzes Vibrieren; bei Fehlern
  ein kurzer Hinweis). Untertitel (ab Android 10): Name der Fernbedienung. Manche
  Hersteller verlangen für Kacheln fremder Apps trotzdem das Entsperren.
- **Widget** (4×1, skalierbar): lange auf den Startbildschirm drücken → Widgets →
  Fernbedienung → „Schnellsteuerung“; ab Android 8 (je nach Launcher) auch über
  „Widget hinzufügen“. Zeigt den Namen der Fernbedienung und 1–6 runde Knöpfe;
  ein Tipp auf den Namen öffnet die App. Beim Hinzufügen öffnet sich „Widget
  einrichten“ (ab Android 12 optional, dann gilt sofort die Standardbelegung
  Ein/Aus · leiser · lauter · Eingang). Jedes Widget hat eine eigene Belegung, z. B.
  ein Lautstärke- und ein HDMI-Widget nebeneinander. Später ändern: ⋮ →
  Schnellsteuerung → Abschnitt „Widget“ → „Bearbeiten“, ab Android 12 auch lange auf
  das Widget drücken → bearbeiten.
- **Sperrbildschirm-Benachrichtigung** (Standard aus): ⋮ → Schnellsteuerung →
  „Steuerung auf dem Sperrbildschirm anzeigen“. Stille, dauerhafte Benachrichtigung
  im Kanal „Schnellsteuerung“ mit 1–5 Knöpfen (eigene Belegung: „Tasten der
  Benachrichtigung bearbeiten“), die ohne Entsperren senden; ab
  Android 13 fragt die App nach der Erlaubnis für Benachrichtigungen. Nach einem
  Neustart erscheint sie wieder. Ab Android 14 lässt sie sich entsperrt wegwischen;
  beim nächsten Öffnen der App kommt sie zurück.

### Sperrbildschirm

Echte Widgets auf dem Sperrbildschirm gibt es auf Android-Handys nicht. Deshalb zwei
Wege, beide ohne Entsperren:

- **Aufziehbare Benachrichtigung**: unter „Steuerung auf dem Sperrbildschirm anzeigen“
  die Ansicht wählen. **Klein** zeigt nur die Knopfreihe, **Erweitert** (Standard) lässt
  sich zusätzlich herunterziehen zur **Mini-Fernbedienung** mit großen Tasten in zwei
  Spalten: links Ein/Aus · Stumm, Eingang · HOME, Lautstärke −/+ und CH ▼/▲, rechts das
  Steuerkreuz mit OK und darunter BACK. Tasten, die der
  zuletzt benutzten Fernbedienung fehlen, sind ausgeblendet. Eingeklappt bleibt die frei
  gewählte Knopfreihe.
- **Ganze Fernbedienung über dem Sperrbildschirm** (Standard aus): Schalter „Ganze
  Fernbedienung über dem Sperrbildschirm erlauben“. Dann öffnet das Tippen auf die
  Sperrbildschirm-Benachrichtigung selbst (Kopfzeile oder freie Fläche, Hinweis „Tippen für die
  ganze Fernbedienung“) oder die Kachel „TV Fernbedienung“ die normale Fernbedienung der
  zuletzt benutzten Fernbedienung über dem Sperrbildschirm, ohne zu entsperren. Einen eigenen
  Knopf dafür gibt es nicht: Android verlangt bei Knöpfen in der Benachrichtigung immer das
  Entsperren, nur das Tippen auf die Benachrichtigung selbst darf über dem Sperrbildschirm
  öffnen. Die Kachel öffnet ab Android 14 ohne Entsperren; vor Android 14 fragt das System
  dort zuerst nach dem Entsperren. Dort gibt es nur Tasten und ⋮ → „Weitere
  Tasten“: kein ☰, kein Wechseln, kein ⚡, keine Automationen, kein Assistent, keine
  Einstellungen. Zurück oder die Ein/Aus-Taste des Handys schließt sie; wer dabei entsperrt,
  kann weiter bedienen, die volle App öffnet sich wie gewohnt über den Launcher. Wer das
  Handy in der Hand hat, kann damit den Fernseher bedienen – sonst nichts. Ist die Option
  aus, ist die Kachel ausgegraut („In der App aktivieren“).

**Belegung** (Widget und Benachrichtigung): jede Taste der zuletzt benutzten
Fernbedienung (Gruppen „Häufig“, „Alle Tasten“, „Weitere Tasten“ mit den Extra-Tasten
wie „Netflix“), „Eingang“ oder eine Automation dieser Fernbedienung. Automationen laufen
direkt im Hintergrund (wie beim Sleep-Timer) und dürfen höchstens 8 s Wartezeit haben;
längere sind in der Auswahl gesperrt. Bekannte Tasten zeigen ein Symbol (Ein/Aus rot,
Lautsprecher −/+, Stumm, Pfeile), HDMI und SOURCE sind blau, Farbtasten farbig, alle anderen
tragen eine kurze Beschriftung (höchstens 6 Zeichen), Automationen einen Blitz mit Namen.
Fehlt die Taste oder Automation bei der aktuellen Fernbedienung (andere Marke), erscheint ein
kurzer Hinweis. Die Kacheln bleiben fest belegt.

**Eingang** sendet die in ⋮ → Schnellsteuerung gewählte Taste (HDMI 1–4 oder SOURCE,
angeboten werden nur Tasten der aktuellen Fernbedienung), sonst HDMI 1, sonst SOURCE.
Die Wahl gilt für alle Fernbedienungen; fehlt einer die gewählte Taste, greift der
Standard. **Ein/Aus** sendet wie ein kurzer Druck in der App den Umschalter POWER,
sonst „Einschalten“ bzw. „Ausschalten“.

Code: `app/src/main/java/de/hotelremote/quick/` (Kacheln `QuickTileService.kt`, Widget
`QuickWidgetProvider.kt`, Einrichten `WidgetConfigActivity.kt`, Benachrichtigung
`QuickNotification.kt` mit Mini-Fernbedienung `MiniRemoteViews.kt`, ganze Fernbedienung über
dem Sperrbildschirm `LockScreenRemoteActivity.kt`, Knöpfe `QuickControlReceiver.kt`, Belegung `QuickButton.kt` und
`QuickButtonLook.kt`), Einstellungen `ui/quick/QuickControlsScreen.kt` und
`ui/quick/QuickButtonsEditor.kt`, Layouts `res/layout/widget_quick.xml` (bis 4 Knöpfe),
`widget_quick_dense.xml` (5–6), `notification_quick.xml` und `notification_quick_big.xml`,
Widget-Info
`res/xml/quick_widget_info.xml`.

## APK installieren

- USB: `adb install -r dist/Fernbedienung-1.6.2.apk`
- Oder die Datei aufs Handy kopieren und dort öffnen. Dabei die Installation
  aus unbekannten Quellen erlauben.

## App bauen

```
./gradlew :app:assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/Fernbedienung-1.6.2.apk
```

Gradle legt `app/build/outputs/apk/release/app-release.apk` an; die Kopie unter
`dist/Fernbedienung-1.6.2.apk` ist die Datei zum Installieren (`dist/` steht nicht
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
cp app/build/outputs/bundle/release/app-release.aab dist/Fernbedienung-1.6.2.aab
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
11. Automationen → „● Aufnehmen“: Menü, Pfeil runter (kurz warten), OK drücken – der TV
    reagiert jeweils, die Leiste zählt „Schritte: 3“. VOL+ halten ergibt einen Schritt,
    „Rückgängig“ entfernt ihn. „Fertig“ zeigt die Schritte mit den gemessenen Wartezeiten;
    Name eingeben, speichern, über ⚡ ausführen: gleiche Folge wie aufgenommen.
12. ⚡ → Sleep-Timer (Handy) → eigener Wert 2 min, „Ausschalten“: Akku-Hinweis (nur beim
    ersten Mal), ab Android 14 Frage nach „Wecker und Erinnerungen“, ab Android 13 nach
    Benachrichtigungen. Die Benachrichtigung zählt herunter; App schließen (aus „Zuletzt
    verwendet“ wischen), Handy sperren und auf den TV legen: nach 2 min geht der TV aus,
    danach „Sleep-Timer ausgeführt“.
13. Timer starten und über „Abbrechen“ in der Benachrichtigung beenden: nichts wird gesendet.
14. Timer mit 10 min starten, Handy neu starten: der Timer läuft weiter und löst aus.
15. Auf Xiaomi/HyperOS: Akku „Keine Einschränkungen“ setzen und Punkt 12 wiederholen.
16. Kacheln: Schnelleinstellungen → Bearbeiten → die vier TV-Kacheln (Ein/Aus, leiser, lauter, Eingang) hinzufügen (ab Android 13
    auch über ⋮ → Schnellsteuerung). Untertitel zeigt die Fernbedienung. Handy sperren,
    Schnelleinstellungen vom Sperrbildschirm aufklappen: Ein/Aus, leiser, lauter und Eingang
    wirken am TV ohne Entsperren, das Handy vibriert kurz.
17. ⋮ → Schnellsteuerung → Eingangstaste z. B. HDMI 2: die Kachel „TV Eingang“ schaltet auf HDMI 2.
18. Widget hinzufügen (lange auf den Startbildschirm drücken oder „Widget hinzufügen“): „Widget
    einrichten“ öffnet sich (vor Android 12 immer). „Abbrechen“: vor Android 12 wird kein Widget
    angelegt. „Speichern“ mit der Standardbelegung: die vier Knöpfe senden; in der App auf eine
    andere Fernbedienung wechseln oder sie umbenennen – der Name im Widget ändert sich.
19. Zweites Widget mit z. B. Stumm, HDMI 1, HDMI 2, „Netflix“ (Extra-Taste), OK und einer
    Automation „Sleep 60“ (6 Knöpfe, Vorschau prüfen): das Widget zeigt sechs kleinere Knöpfe mit
    Symbol bzw. Beschriftung, jeder sendet; die Automation läuft komplett, auch bei gesperrtem
    Handy. Eine Automation mit mehr als 8 s Wartezeit ist in der Auswahl ausgegraut („zu lang
    für das Widget“). Das erste Widget ist unverändert.
20. ⋮ → Schnellsteuerung → „Widget“: beide Widgets stehen in der Liste („Widget 2 · Tasten: 6“);
    „Bearbeiten“, Reihenfolge ändern, speichern: das Widget folgt sofort. Ab Android 12 auch
    lange auf das Widget drücken → bearbeiten. Widget entfernen: es verschwindet aus der Liste.
21. In der App auf eine Fernbedienung einer anderen Marke ohne HDMI 2 wechseln, im Widget HDMI 2
    tippen: Hinweis „Diese Taste fehlt der Fernbedienung.“; fehlende Automation entsprechend.
22. Philips-TV (RC5/RC6): Stumm im Widget zweimal tippen (dazwischen App aus „Zuletzt
    verwendet“ wischen): der TV schaltet beide Male um.
23. ⋮ → Schnellsteuerung → „Steuerung auf dem Sperrbildschirm anzeigen“ (ab Android 13 mit
    Erlaubnisfrage): stille Benachrichtigung mit vier Knöpfen; auf dem Sperrbildschirm senden
    sie ohne Entsperren. „Tasten der Benachrichtigung bearbeiten“ (höchstens 5): die
    Benachrichtigung zeigt die neue Belegung. Handy neu starten: die Benachrichtigung ist wieder
    da. Schalter aus: sie verschwindet.
24. Alle Fernbedienungen löschen: Kacheln sind ausgegraut („Keine Fernbedienung“), das Widget
    zeigt „Keine Fernbedienung“.
25. Sperrbildschirm-Benachrichtigung an, Ansicht „Erweitert“: Handy sperren, die Benachrichtigung
    herunterziehen: die Mini-Fernbedienung erscheint zweispaltig mit hohen Tasten (nichts
    abgeschnitten). Ein/Aus, Stumm,
    Eingang, −/+, CH ▲/▼, Pfeile, OK, BACK, HOME senden ohne Entsperren. Bei einer
    Fernbedienung ohne z. B. HOME fehlt der Knopf. Ansicht „Klein“: nur die Knopfreihe, kein
    Aufziehen.
26. „Ganze Fernbedienung über dem Sperrbildschirm erlauben“ an: die Kopfzeile der
    Benachrichtigung zeigt „Tippen für die ganze Fernbedienung“, ein eigener Knopf ist nicht mehr
    da. Handy sperren, auf die Benachrichtigung (Kopfzeile oder freie Fläche, nicht auf eine
    Taste) tippen: keine Entsperr-Abfrage, die
    Fernbedienung öffnet sich über dem Sperrbildschirm ohne Entsperren, nur mit ⋮ → „Weitere
    Tasten“; VOL halten zählt hoch. Zurück: der Sperrbildschirm ist wieder da. Erneut öffnen,
    Ein/Aus-Taste des Handys: Bildschirm aus, nach dem Einschalten der Sperrbildschirm. Ohne
    gespeicherte Fernbedienung: Hinweis „Noch keine Fernbedienung – App öffnen“.
27. Kachel „TV Fernbedienung“ hinzufügen (ab Android 13 auch über ⋮ → Schnellsteuerung): bei
    ausgeschalteter Option ausgegraut mit „In der App aktivieren“; eingeschaltet öffnet ein Tipp
    auf dem Sperrbildschirm die ganze Fernbedienung (ab Android 14 ohne Entsperr-Abfrage, vorher
    mit). Die Tasten der Mini-Fernbedienung senden weiter ohne Entsperren. Option wieder aus:
    Tippen auf die Benachrichtigung öffnet die App (nach dem Entsperren), die Kachel ist ausgegraut.

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
