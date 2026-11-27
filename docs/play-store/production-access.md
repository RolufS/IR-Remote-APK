# Antrag auf Produktionszugriff

Neue private Entwicklerkonten müssen vor dem ersten Release in „Produktion“ einen
geschlossenen Test durchführen (mindestens 12 Tester, 14 Tage ohne Unterbrechung)
und danach in der Play Console den Produktionszugriff beantragen. Dabei stellt
die Console Fragen zum Test, zur App und zur Produktionsreife (auf Englisch).

Diese Datei enthält vorbereitete Antworten. **Alles in eckigen Klammern
`[…]` kannst nur du ausfüllen** – es hängt davon ab, wie dein Test tatsächlich
gelaufen ist. Bitte nichts stehen lassen, was nicht stimmt: Google prüft die
Antworten gegen die Testdaten (Anzahl Tester, Aktivität).

Hinweise:

- Wortlaut, Reihenfolge und Auswahlmöglichkeiten der Fragen können sich in der
  Console ändern. Die Antworten unten passen zu den üblichen Themen; bei
  abweichenden Fragen die passende Antwort nehmen und anpassen.
- Die Console zeigt bei Freitextfeldern eine Zeichengrenze an; die Antworten
  sind bewusst kurz gehalten.
- Zuerst steht jeweils die englische Fassung (zum Einfügen), darunter die
  deutsche zur Kontrolle.

---

## 1. Zum geschlossenen Test

### Wie hast du Tester gewonnen? / How did you recruit users for your closed test?

Auswahl in der Console (eine oder mehrere): z. B. Familie und Freunde,
Kollegen, soziale Medien, Test-Communitys, sonstige. → **[zutreffende Auswahl]**

Freitext (falls verlangt):

> I invited [people from my circle of friends and family / colleagues / members of community X] who own a phone with an infrared transmitter (e.g. Xiaomi, Redmi or POCO models), because the app only works on such phones. Testers joined via [an email list / a Google Group] and the opt-in link from the Play Console.

> Ich habe [Freunde und Familie / Kollegen / Mitglieder der Community X] eingeladen, die ein Handy mit Infrarot-Sender haben (z. B. Xiaomi, Redmi oder POCO), weil die App nur auf solchen Handys funktioniert. Die Tester kamen über [eine E-Mail-Liste / eine Google-Gruppe] und den Opt-in-Link aus der Play Console dazu.

### Wie leicht war es, Tester zu finden? / How easy was it to recruit testers for your app?

Auswahl auf einer Skala (sehr leicht … sehr schwer). → **[deine Einschätzung]**

Freitext (falls verlangt):

> [Easy / Somewhat difficult / Difficult]. The main limitation was that testers need a phone with a built-in infrared transmitter; many current phones don't have one. [Optional: further details]

> [Leicht / Eher schwer / Schwer]. Die größte Einschränkung: Tester brauchen ein Handy mit eingebautem Infrarot-Sender, den viele aktuelle Handys nicht haben. [Optional: weitere Details]

### Wie aktiv waren die Tester? / Describe the engagement you received from testers

> [Number] testers took part for at least 14 days. They used the app on [number] different phone models with an IR transmitter, including [models]. They tested it with [TVs of brands X, Y / hotel TVs / their own TVs]. [Further details on how often/what they used.]

> [Anzahl] Tester waren mindestens 14 Tage dabei. Sie haben die App auf [Anzahl] verschiedenen Handymodellen mit IR-Sender benutzt, darunter [Modelle], mit [Fernsehern der Marken X, Y / Hotel-Fernsehern / eigenen Fernsehern]. [Weitere Details, wie oft/was genutzt wurde.]

### Zusammenfassung des Feedbacks / Provide a summary of the feedback you received. Include how you collected it.

> Feedback was collected via [email / a chat group / personal conversations / the Play Store's private feedback]. Summary: [feedback]. [Number] testers reported that the wizard found a working code for their TV; [problems that were reported].

> Das Feedback kam über [E-Mail / eine Chat-Gruppe / persönliche Gespräche / das private Feedback im Play Store]. Zusammenfassung: [Feedback]. [Anzahl] Tester haben berichtet, dass der Assistent einen funktionierenden Code für ihren Fernseher gefunden hat; [gemeldete Probleme].

---

## 2. Zur App

### Zielgruppe / Who is the intended audience of your app?

> Adults who travel and stay in hotels or holiday flats where the TV remote is missing, broken or unhygienic, and who own an Android phone with an infrared transmitter. Also anyone at home whose original TV remote is lost or broken. The app is not directed at children.

> Erwachsene, die reisen und in Hotels oder Ferienwohnungen übernachten, wo die Fernbedienung fehlt, kaputt oder unhygienisch ist, und die ein Android-Handy mit Infrarot-Sender haben. Außerdem alle, deren Original-Fernbedienung zu Hause verloren oder kaputt ist. Die App richtet sich nicht an Kinder.

### Mehrwert / Describe how your app provides value to users

> The app turns a phone with an infrared transmitter into a TV remote. A short wizard finds the right code (pick the brand, tap MUTE, answer yes/no), and a scan helps when the brand is unknown. It offers direct HDMI 1–4 keys (useful for a streaming stick), automations that can be recorded, a sleep timer, Quick Settings tiles, a configurable home-screen widget and optional lock-screen controls. Unlike many remote apps it has no ads, no in-app purchases, no account and collects no data; the internet permission (since 1.8.0) is used only for an optional, off-by-default outdoor temperature that sends the chosen place to Open-Meteo. Since 1.8.0 it also controls air conditioners. It is available in 11 languages and is open source.

> Die App macht ein Handy mit Infrarot-Sender zur TV-Fernbedienung. Ein kurzer Assistent findet den passenden Code (Marke wählen, MUTE antippen, Ja/Nein), ein Suchlauf hilft bei unbekannter Marke. Dazu kommen direkte HDMI-1–4-Tasten (praktisch für einen Streaming-Stick), aufnehmbare Automationen, ein Sleep-Timer, Kacheln in den Schnelleinstellungen, ein frei belegbares Widget und eine optionale Steuerung auf dem Sperrbildschirm. Anders als viele Fernbedienungs-Apps hat sie keine Werbung, keine In-App-Käufe, kein Konto und erhebt keine Daten; die Internet-Berechtigung (ab 1.8.0) dient nur der optionalen, standardmäßig ausgeschalteten Außentemperatur, die den gewählten Ort an Open-Meteo sendet. Seit 1.8.0 bedient sie auch Klimaanlagen. Sie gibt es in 11 Sprachen, der Quellcode ist offen.

### Erwartete Installationen im ersten Jahr / How many installs do you expect your app to have in your first year?

Auswahl aus Bereichen in der Console. → **[deine Schätzung]**

Hinweis zur Einschätzung: Die App wird nur Handys mit IR-Sender angeboten, das
begrenzt die mögliche Nutzerzahl.

---

## 3. Produktionsreife

### Änderungen aufgrund des Tests / What changes did you make to your app based on what you learned during your closed test?

> [Changes based on tester feedback, e.g. "Fixed …", "Added …"]. If no changes were needed: "Testers reported no problems that required changes; [optional: what was confirmed]."

> [Änderungen aufgrund des Tester-Feedbacks, z. B. „… behoben“, „… ergänzt“]. Falls keine nötig waren: „Die Tester haben keine Probleme gemeldet, die Änderungen erforderten; [optional: was bestätigt wurde].“

Nur Änderungen nennen, die wirklich aus dem Test kamen. Version 1.7.0 enthält
eine Korrektur der HDMI-Beschriftung auf kleinen Bildschirmen und den
Play-Gerätefilter für IR-Handys; ob das auf Tester-Feedback zurückgeht, weißt
nur du.

### Woran hast du festgemacht, dass die App bereit ist? / How did you decide that your app is ready for production?

> The closed test ran for [number] days with [number] testers on [number] phone models with an IR transmitter. [Summary: the core functions – finding the code, the remote, automations, sleep timer, widget – worked for the testers]. The app has an automated test suite (unit and UI tests) that passes for every release. On Google Play it is offered only to phones with an IR transmitter, so users without one cannot install a non-working app. [Further reasons]

> Der geschlossene Test lief [Anzahl] Tage mit [Anzahl] Testern auf [Anzahl] Handymodellen mit IR-Sender. [Zusammenfassung: Die Kernfunktionen – Code finden, Fernbedienung, Automationen, Sleep-Timer, Widget – haben bei den Testern funktioniert]. Die App hat eine automatische Testsuite (Unit- und UI-Tests), die bei jedem Release durchläuft. Im Play Store wird sie nur Handys mit IR-Sender angeboten, Nutzer ohne IR-Sender können also keine App installieren, die bei ihnen nicht funktioniert. [Weitere Gründe]
