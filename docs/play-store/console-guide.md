# Play Console: Schritt für Schritt

Reihenfolge der Schritte vom leeren Konto bis zur Veröffentlichung, jeweils mit
der Datei aus diesem Repo, die dazugehört. Die Play Console ändert ihre
Oberfläche gelegentlich: Menünamen und Position von Schaltflächen können
abweichen. Im Zweifel zeigt das **Dashboard** der App unter „App einrichten“
bzw. „Aufgaben“ an, was noch fehlt.

Dateien:

| Datei | Inhalt |
|---|---|
| [listing-de.md](listing-de.md) | Store-Texte Deutsch, Antworten für alle Formulare, Grafik-Liste |
| [listing-en.md](listing-en.md) | Store-Texte Englisch (Übersetzung en-US) |
| [release-notes.md](release-notes.md) | „Neu in dieser Version“ (Deutsch und Englisch) |
| [production-access.md](production-access.md) | Antworten für den Antrag auf Produktionszugriff |
| `icon-512.png`, `feature-graphic.png`, `screenshot-1-…` bis `screenshot-8-…` | Grafiken in diesem Ordner |
| `dist/Fernbedienung-1.7.0.aab` | App Bundle zum Hochladen (nicht im Repo, wird lokal gebaut, siehe README) |
| [PRIVACY.md](../../PRIVACY.md) | Datenschutzerklärung |

---

## 0. Vorher

1. **Entwicklerkonto** unter https://play.google.com/console anlegen (einmalige
   Gebühr, Identitätsprüfung; bei privaten Konten auch Bestätigung von
   Telefonnummer und Android-Gerät).
2. **GitHub-Repo öffentlich**, damit die Datenschutzerklärung erreichbar ist:
   `https://github.com/RolufS/IR-Remote-APK/blob/main/PRIVACY.md` im Browser
   (abgemeldet) öffnen und prüfen.
3. **App Bundle bauen**: `dist/Fernbedienung-1.7.0.aab` (README → „Für
   Entwickler“ → „Play Store und Grafiken“). Signiert mit
   `~/.android/hotelremote.jks`; Keystore und Passwörter sicher aufbewahren.
   Welche Rolle dieser Schlüssel bei Play bekommt, entscheidest du in Schritt 5.4.
4. **Tester vorbereiten**: mindestens 12 Personen mit einem Handy mit
   Infrarot-Sender und einem Google-Konto. Ohne IR-Sender sehen sie die App im
   Play Store nicht (Gerätefilter, siehe `listing-de.md`). Entweder ihre
   Gmail-Adressen sammeln (E-Mail-Liste) oder eine Google-Gruppe anlegen
   (https://groups.google.com) und die Tester dort aufnehmen.

## 1. App anlegen

„Alle Apps“ → „App erstellen“:

- App-Name: `Fernbedienung für Hotel-TV` (aus `listing-de.md`, Abschnitt „App-Name“)
- Standardsprache: Deutsch (Deutschland) – de-DE
- App oder Spiel: App · Kostenlos oder kostenpflichtig: Kostenlos
- Erklärungen (Programmrichtlinien, US-Exportgesetze) lesen und bestätigen

## 2. App-Inhalte (Richtlinie)

Meist unter „Richtlinie und Programme“ → „App-Inhalte“ (oder über die Aufgaben im
Dashboard). Antworten stehen in `listing-de.md` → „Formulare in der Play Console“:

| Formular | Antwort |
|---|---|
| Datenschutzerklärung | `https://github.com/RolufS/IR-Remote-APK/blob/main/PRIVACY.md` |
| App-Zugriff | Alle Funktionen ohne besondere Zugriffsrechte verfügbar |
| Werbung | Keine Werbung |
| Einstufung des Inhalts | Fragebogen: Kategorie „Dienstprogramm …“, alle Fragen Nein |
| Zielgruppe und Inhalte | 18 Jahre und älter; spricht Kinder nicht an |
| Datensicherheit | Keine Daten erhoben, keine Daten weitergegeben |
| Werbe-ID | Wird nicht verwendet |
| Regierungs-App, Finanzfunktionen, Gesundheit, Nachrichten-App | Nein / keine |
| Exakte Alarme („Alarme und Erinnerungen“), falls angezeigt | Begründung aus `listing-de.md` → „Berechtigungen“ (Sleep-Timer) |

## 3. Store-Einstellungen

„Store-Präsenz“ → „Store-Einstellungen“ (Bezeichnung kann abweichen):

- Kategorie: App · **Tools**
- Kontaktdaten: E-Mail-Adresse (Pflicht, öffentlich sichtbar); Website optional
  `https://github.com/RolufS/IR-Remote-APK`

## 4. Store-Eintrag

„Store-Präsenz“ → „Hauptstore-Eintrag“:

1. **Texte** aus `listing-de.md`: App-Name, Kurzbeschreibung, Vollständige
   Beschreibung (Inhalt der Codeblöcke 1:1).
2. **Grafiken** aus diesem Ordner:
   - App-Symbol: `icon-512.png`
   - Vorstellungsgrafik: `feature-graphic.png`
   - Telefon-Screenshots: `screenshot-1-remote.png` bis
     `screenshot-8-lockscreen.png` in dieser Reihenfolge (8 ist das Maximum)
   - Tablet-Screenshots: leer lassen, wenn die Console es zulässt
3. Speichern.
4. **Übersetzung Englisch**: „Übersetzungen verwalten“ → „Eigene Übersetzungen
   hinzufügen“ → Englisch (Vereinigte Staaten) – en-US; Texte aus
   `listing-en.md`. Die Grafiken gelten für alle Sprachen.

## 5. Geschlossener Test

„Testen“ → „Geschlossener Test“:

1. Track wählen bzw. anlegen (die Console bietet einen Standard-Track an).
2. **Länder/Regionen** auswählen, in denen die Tester sind.
3. **Tester**: E-Mail-Liste anlegen (Adressen einfügen) oder die Google-Gruppe
   eintragen. Feedback-Kanal (E-Mail oder URL) angeben, wenn verlangt.
4. **Release erstellen**:
   - **Entscheidung zuerst: App-Signaturschlüssel.** Beim ersten Release fragt
     die Console nach **Play App Signing** und danach, welcher Schlüssel die App
     signiert. Diese Wahl lässt sich später nicht einfach rückgängig machen:
     - **Option A (empfohlen): eigenen Schlüssel `~/.android/hotelremote.jks`
       als App-Signaturschlüssel hochladen.** Play-Installationen sind dann
       genauso signiert wie die APKs auf GitHub; beide sind austauschbar, ein
       Update von der GitHub-APK auf die Play-Version (und umgekehrt) klappt
       ohne Deinstallieren. Dafür in der Console die Option zum Hochladen bzw.
       Exportieren eines vorhandenen Schlüssels wählen und **der Anleitung der
       Console folgen** (sie stellt eigene Anweisungen zum Exportieren und
       Verschlüsseln des Schlüssels bereit). Anschließend verlangt Google einen
       **Upload-Schlüssel**; das kann ein separater, neuer Schlüssel sein. Alle
       späteren Bundles müssen mit dem Upload-Schlüssel signiert werden, den
       du dabei festlegst – Keystores und Passwörter sicher aufbewahren
       (nicht im Repo).
     - **Option B: von Google erzeugten Schlüssel verwenden.**
       `~/.android/hotelremote.jks` ist dann nur der Upload-Schlüssel. Folge:
       Play-Installationen und GitHub-APKs sind unterschiedlich signiert und
       können sich **nicht gegenseitig aktualisieren**; ein Wechsel geht nur
       per Deinstallieren, dabei gehen die gespeicherten Fernbedienungen
       verloren.
   - App Bundle hochladen: `dist/Fernbedienung-1.7.0.aab`
   - Release-Name: z. B. `1.7.0` (Vorschlag der Console übernehmen)
   - Versionshinweise: aus `release-notes.md` (beide Sprachen mit Sprach-Tags)
   - „Weiter“/„Überprüfen“, Warnungen lesen, dann speichern
5. Unter „Übersicht der Veröffentlichung“ bzw. „Veröffentlichungsübersicht“ die
   Änderungen **zur Prüfung senden**. Die erste Prüfung kann einige Tage dauern.
6. Nach der Freigabe den **Opt-in-Link** (im Track unter „Tester“, z. B. „Über
   das Web teilnehmen“) an die Tester schicken. Jeder Tester öffnet den Link,
   nimmt am Test teil und installiert die App über den Play Store.
7. Tester bitten, **mindestens 14 Tage** dabeizubleiben (nicht austreten, App
   nicht deinstallieren) und die App wirklich zu benutzen. Feedback sammeln und
   notieren – es wird für den Antrag gebraucht (`production-access.md`).
8. Fehlerkorrekturen während des Tests: `versionCode` erhöhen, neu bauen, im
   selben Track ein neues Release erstellen.

## 6. Produktionszugriff beantragen

Sobald das Dashboard die Bedingungen als erfüllt anzeigt (mindestens 12 Tester,
14 Tage ohne Unterbrechung): „Zugriff auf Produktion beantragen“ (im
Dashboard). Die Fragen beantworten – vorbereitete Antworten mit Platzhaltern in
`production-access.md`. Die Prüfung dauert einige Tage; bei Ablehnung nennt
Google meist den Grund (z. B. Test verlängern).

## 7. Produktion

Nach der Freigabe: „Produktion“ → Länder/Regionen wählen → „Neuen Release
erstellen“ → dasselbe Bundle aus der Bibliothek übernehmen (oder ein neueres mit
höherem `versionCode`) → Versionshinweise aus `release-notes.md` → zur Prüfung
senden. Optional mit gestaffelter Einführung (Prozentsatz der Nutzer).

## 8. Spätere Updates

1. `versionCode` und `versionName` in `app/build.gradle.kts` erhöhen.
2. Bundle bauen (signiert mit dem Upload-Schlüssel) und nach `dist/` kopieren.
3. Neuen Abschnitt in `release-notes.md` schreiben (≤ 500 Zeichen je Sprache).
4. In der Console im gewünschten Track ein neues Release erstellen und zur
   Prüfung senden. Store-Texte und Screenshots bei Funktionsänderungen
   ebenfalls aktualisieren (Zeichengrenzen in `listing-de.md`).
