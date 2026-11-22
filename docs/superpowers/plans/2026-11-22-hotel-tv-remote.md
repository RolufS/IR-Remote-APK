# Hotel-TV-Fernbedienung – Umsetzungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Ziel:** Eine werbefreie Android-App, die per Infrarot möglichst viele Fernseher bedient, mit Wizard zum Finden der passenden Fernbedienung und direkten HDMI-Tasten, wo der Fernseher sie kennt.

**Architektur:** Zwei Teile, verbunden durch eine SQLite-Datei.
1. Ein Python-Werkzeug (`tools/`) liest Flipper-IRDB (und optional irdb). Es übersetzt **jeden Code bereits am PC in fertige Pulsfolgen**, fasst Code-Sätze zusammen und schreibt `app/src/main/assets/codes.db`.
2. Die Android-App (Kotlin/Compose) liest diese Datei nur. Sie sendet die Pulsfolgen über `ConsumerIrManager`. Gefundene Fernbedienungen speichert sie als JSON-Kopie.

**Abweichung vom Spec, bewusst:** Die Protokoll-Umrechnung läuft im Python-Skript statt in der App. Es gibt dann nur eine Encoder-Implementierung, die am PC gegen beide Datenquellen getestet wird. Die App wird einfacher. Für den Nutzer ändert sich nichts.

**Tech Stack:**
- Python 3.12 (nur Standardbibliothek, dazu pytest)
- Android Gradle Plugin 8.13.0, Gradle 8.13, Kotlin 2.2.20, Jetpack Compose (BOM 2025.10.01)
- kotlinx-serialization, kotlinx-coroutines
- JUnit 4, Robolectric 4.16

**Spec:** `docs/superpowers/specs/2026-11-22-ir-remote-design.md` (Mockups: `docs/superpowers/specs/mockups/remote.html`, `docs/superpowers/specs/mockups/wizard.html`)

## Global Constraints

- **Android-Versionen:** minSdk 23, compileSdk 36, targetSdk 36, JDK 17.
- **Paket und Name:** Paketname `de.hotelremote`, App-Name „Fernbedienung“.
- **Berechtigungen:** **Keine INTERNET-Berechtigung.** Keine Bibliotheken für Werbung, Analytics oder Crash-Reporting. Einzige Berechtigung ist `android.permission.TRANSMIT_IR`. `uses-feature android.hardware.consumerir` mit `required="false"`.
- **Darstellung:** Nur Hochformat, nur dunkle Optik.
- **Texte:** Alle Texte auf Deutsch in `app/src/main/res/values/strings.xml`.
- **Code-Quellen:** Nur Codes aus den öffentlichen Daten, nichts erfinden.
  - Flipper-IRDB auf Commit `d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d` (Lizenz CC0-1.0)
  - irdb auf Commit `a3e34000f952795aa86a7a26fac7d463cc0eeccd`
- **irdb ist optional** (`--irdb`). Die Lizenz verlangt vorab ein Issue beim irdb-Projekt und einen festen Hinweistext in der App. Der echte Build läuft **ohne irdb**, bis der Nutzer anders entscheidet.
- **Hotelmarken-Liste in dieser Reihenfolge:** Samsung, LG, Philips, Sony, Panasonic, Hisense, TCL, Telefunken, Grundig, Toshiba, Sharp, Thomson, Loewe, Medion, Vestel.
- **Tastennamen (kanonisch):**
  - POWER, POWER_ON, POWER_OFF, MUTE
  - VOL_UP, VOL_DOWN, CH_UP, CH_DOWN
  - DIGIT_0 … DIGIT_9
  - SOURCE, HDMI1 … HDMI4
  - MENU, HOME, OK, UP, DOWN, LEFT, RIGHT
  - BACK, EXIT, INFO, LIST, PRE_CH
  - RED, GREEN, YELLOW, BLUE
  - Extras als `X:<Label>`
- **Grundtasten (für Merge-Regeln):** POWER, MUTE, VOL_UP, VOL_DOWN, CH_UP, CH_DOWN. POWER zählt auch, wenn nur POWER_ON oder POWER_OFF vorhanden ist.
- **Wiederholende Tasten (gedrückt halten):** VOL_UP, VOL_DOWN, CH_UP, CH_DOWN, UP, DOWN, LEFT, RIGHT. Die erste Wiederholung kommt nach 400 ms, danach im Takt `periodMs` des Signals.
- **Wizard-Zeiten:** Suchlauf-Takt 1200 ms. Nach „Stopp“ werden die letzten 5 gesendeten Codes zurückgeprüft, der zuletzt gesendete zuerst.
- **Pulsfolgen:** Mikrosekunden, abwechselnd an/aus, beginnen und enden mit „an“. Gespeichert als Little-Endian-`int32`-Array (BLOB).

## Review Focus

1. **Handy ohne IR-Sender, oder `transmit` wirft eine Exception** (zu lange Folge, Frequenz nicht unterstützt). Die App darf nicht abstürzen. Sie zeigt einen Hinweis bzw. „Senden fehlgeschlagen“. *Test:* Task 10 `transmitErrorIsReportedNotThrown`, Task 14 `noIrBannerShownWhenUnavailable`.
2. **`remotes.json` ist kaputt oder halb geschrieben** (Absturz beim Speichern, altes Format). Die App startet trotzdem mit leerer Liste und behält die kaputte Datei als `.bak`. *Test:* Task 12 `corruptFileIsBackedUpAndIgnored`.
3. **Schnelle Mehrfach-Taps und überlappendes Halten.** Sendungen laufen nacheinander, nie parallel. RC5/RC6 wechseln das Toggle-Bit bei jedem neuen Tastendruck, nicht beim Halten. *Test:* Task 10 `rapidPressesAreSerializedAndToggleAlternates`, `holdRepeatsKeepToggle`.
4. **Wizard-Randfälle.** Dazu gehören:
   - Code-Satz ohne MUTE (und ohne VOL_DOWN) oder ohne VOL_UP
   - Suchlauf schon nach 2 Codes gestoppt (weniger als 5 Kandidaten)
   - Suchlauf bis zum Ende ohne Treffer
   - alle Eingangs-Varianten verneint

   Kein Absturz, sinnvoller nächster Schritt. *Test:* Task 13, mehrere Fälle.
5. **App-Update mit neuer `codes.db`.** Die alte kopierte Datenbank darf nicht weiterverwendet werden. *Test:* Task 11 `newInstallStampRecopiesDatabase`.

---

## Dateistruktur

```
.gitignore                              (erweitert: Android, Python, Quellen, Keystore)
tools/
  requirements-dev.txt                  pytest
  fetch_sources.sh                      klont beide Quellen sparse auf die gepinnten Commits nach tools/sources/
  build_codes.py                        CLI: Quellen -> codes.db, Bericht, Abdeckungs-Prüfung
  irdata/__init__.py
  irdata/signal.py                      Signal-Datentyp, Normalisierung, BLOB
  irdata/protocols.py                   Encoder (Flipper- und irdb-Namen), Raw-Decoder
  irdata/keys.py                        Tastennamen -> kanonisch
  irdata/brands.py                      Markennamen, Hotel-Liste, Hersteller-Familien
  irdata/sources.py                     Parser Flipper .ir und irdb .csv -> RemoteFile
  irdata/merge.py                       Code-Sätze, Eingangs-Varianten, Reihenfolgen, Suchlauf
  irdata/db.py                          SQLite schreiben
  tests/test_signal.py, test_protocols.py, test_keys.py, test_sources.py, test_merge.py, test_db.py
  tests/fixtures/flipper/TVs/<Marke>/*.ir, tests/fixtures/irdb/codes/<Marke>/TV/*.csv
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, gradlew(.bat), gradle/wrapper/*
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/assets/codes.db            (Ergebnis von build_codes.py, eingecheckt)
app/src/main/res/values/strings.xml, themes.xml
app/src/main/java/de/hotelremote/
  MainActivity.kt
  Keys.kt                               kanonische Tastennamen, Gruppen
  ir/IrSignal.kt, ir/IrTransmitter.kt, ir/SignalSender.kt
  data/CodeDatabase.kt, data/CodeRepository.kt, data/RemoteStore.kt
  wizard/WizardData.kt, wizard/WizardMachine.kt, wizard/ScanRunner.kt
  ui/theme/Theme.kt
  ui/remote/RemoteKeys.kt (reine Logik), ui/remote/RemoteScreen.kt, ui/remote/KeyButton.kt, ui/remote/ExtraKeysScreen.kt
  ui/wizard/WizardScreen.kt
  ui/remotes/RemoteListScreen.kt
  ui/about/AboutScreen.kt
  ui/AppViewModel.kt, ui/App.kt
app/src/test/resources/codes-fixture.db (aus den Python-Fixtures erzeugt, eingecheckt)
app/src/test/java/de/hotelremote/...    Tests je Task
```

---

### Task 1: Signal-Datentyp und Pulsfolgen-Hilfen (Python)

**Files:**
- Create: `tools/requirements-dev.txt`, `tools/irdata/__init__.py`, `tools/irdata/signal.py`, `tools/tests/test_signal.py`
- Modify: `.gitignore` (+ `tools/.venv/`, `tools/sources/`, `__pycache__/`, `.pytest_cache/`)

**Interfaces:**
- Produces:
  - `@dataclass(frozen=True) class Signal(freq: int, frame: tuple[int, ...], repeat: tuple[int, ...] | None, frame_alt: tuple[int, ...] | None, period_ms: int)`
    - `repeat`: was beim Halten gesendet wird (None = `frame` erneut senden)
    - `frame_alt`: Toggle-Variante (RC5/RC6)
  - `Signal.identity() -> tuple[int, ...]`: `frame`, jede Dauer auf 50 µs gerundet (`round(d / 50)`). Die Frequenz zählt nicht mit.
  - `normalize(signed: list[int]) -> tuple[int, ...]`: Eingabe sind vorzeichenbehaftete Dauern (+ an, − aus). Gleiche Vorzeichen in Folge werden addiert, führende und abschließende Pausen entfernt. Ausgabe: positive Dauern, beginnend und endend mit „an“.
  - `units(unit_us: int, signed_units: list[int]) -> list[int]`: skaliert Einheiten auf µs (Vorzeichen bleibt).
  - `duration_us(frame: tuple[int, ...]) -> int`
  - `to_blob(t: tuple[int, ...]) -> bytes` / `from_blob(b: bytes) -> tuple[int, ...]` (Little-Endian int32)

- [ ] **Step 1: venv anlegen**

Run: `python -m venv tools/.venv && tools/.venv/Scripts/python -m pip install -r tools/requirements-dev.txt` (Inhalt: `pytest>=8,<9`)
Expected: pytest installiert.

- [ ] **Step 2: Failing tests schreiben** (`tools/tests/test_signal.py`)

```python
def test_normalize_merges_and_trims():
    assert normalize([-500, 100, 200, -300, -100, 50, -9000]) == (300, 400, 50)
def test_normalize_empty_is_empty():
    assert normalize([-5, -6]) == ()
def test_units_scales_with_sign():
    assert units(889, [1, -2]) == [889, -1778]
def test_identity_ignores_small_jitter_and_freq():
    a = Signal(38000, (9024, 4512, 564), None, None, 108)
    b = Signal(36000, (9010, 4520, 560), None, None, 108)
    assert a.identity() == b.identity()
def test_blob_roundtrip():
    t = (9024, 4512, 564, 70000)
    assert from_blob(to_blob(t)) == t and len(to_blob(t)) == 16
```

- [ ] **Step 3: Tests laufen lassen, schlagen fehl**

Run: `tools/.venv/Scripts/python -m pytest tools/tests/test_signal.py -q`
Expected: FAIL (ImportError)

- [ ] **Step 4: `tools/irdata/signal.py` implementieren**

- [ ] **Step 5: Tests laufen lassen**

Run: wie Step 3. Expected: 5 passed

- [ ] **Step 6: Commit** – `git add tools .gitignore && git commit -m "tools: signal model and pulse helpers"`

---

### Task 2: NEC-Familie und JVC encodieren

**Files:**
- Create: `tools/irdata/protocols.py`, `tools/tests/test_protocols.py`

**Interfaces:**
- Consumes: `Signal`, `normalize`, `units`, `duration_us` (Task 1)
- Produces:
  - `class UnsupportedProtocol(Exception)`
  - `encode_flipper(protocol: str, address: int, command: int) -> Signal`
    - `address`/`command` sind die 4 Hex-Bytes aus der .ir-Datei als Little-Endian-Integer, z. B. `"07 00 00 00"` → 7.
    - Protokollnamen exakt wie Flipper, Groß/Klein egal.
  - `encode_irdb(protocol: str, device: int, subdevice: int, function: int) -> Signal`
    - `subdevice == -1` bedeutet „nicht angegeben“.
    - Protokollnamen wie irdb (IRP-Namen), Groß/Klein egal.
  - Unbekannte Protokolle lösen `UnsupportedProtocol` aus.

**Festgelegte Werte:**

NEC-Basis: Einheit 564 µs, Frequenz 38000, Bits `<1,-1|1,-3>`, LSB zuerst je Byte, Stopp `1`, `period_ms` 108.

| Name | Bytes | Vorspann (Einheiten) | `repeat` |
|---|---|---|---|
| Flipper `NEC` | a, ~a, c, ~c | 16, −8 | NEC-Wiederholcode `(16,-4,1)` = (9024, 2256, 564) |
| Flipper `NECext` | a & 0xFF, a >> 8, c & 0xFF, c >> 8 | 16, −8 | NEC-Wiederholcode |
| Flipper `Samsung32` | a, a, c, ~c | 8, −8 | None |
| irdb `NEC`/`NEC1` | D, (S wenn ≥ 0 sonst ~D), F, ~F | 16, −8 | NEC-Wiederholcode |
| irdb `NEC2` | wie NEC1 | 16, −8 | None |
| irdb `NECx1`/`NECx2` | D, (S wenn ≥ 0 sonst D), F, ~F | 8, −8 | None |

Weitere Varianten:
- Flipper `NEC42`: Adresse 13 Bit, dann ~Adresse 13 Bit, Befehl 8 Bit, ~Befehl 8 Bit, NEC-Timing, NEC-Wiederholcode.
- Flipper `NEC42ext`: Adresse 26 Bit, Befehl 16 Bit, als Rohbits.
- irdb `JVC`: Einheit 525, Frequenz 38000, `frame` = `(16,-8, D:8, F:8, 1)`, `repeat` = `(D:8, F:8, 1)`, `period_ms` 60.

- [ ] **Step 1: Failing tests**

```python
def bits_from_spaces(sig, nbits, offset=2):   # Hilfsfunktion im Test: Pause > 1000 µs = 1
    sp = sig.frame[offset + 1::2][:nbits]; return [1 if s > 1000 else 0 for s in sp]
def lsb_bits(*bytes_): return [(b >> i) & 1 for b in bytes_ for i in range(8)]

def test_flipper_nec_lg_power():
    s = encode_flipper("NEC", 0x04, 0x08)
    assert s.freq == 38000 and s.period_ms == 108
    assert len(s.frame) == 67 and s.frame[:2] == (9024, 4512)
    assert all(m == 564 for m in s.frame[2::2])
    assert bits_from_spaces(s, 32) == lsb_bits(0x04, 0xFB, 0x08, 0xF7)
    assert s.repeat == (9024, 2256, 564)
def test_samsung32_lead_and_bytes():
    s = encode_flipper("Samsung32", 0x07, 0x02)
    assert s.frame[:2] == (4512, 4512) and s.repeat is None
    assert bits_from_spaces(s, 32) == lsb_bits(0x07, 0x07, 0x02, 0xFD)
def test_necext_uses_raw_16bit_fields():
    s = encode_flipper("NECext", 0xBF40, 0xEF10)
    assert bits_from_spaces(s, 32) == lsb_bits(0x40, 0xBF, 0x10, 0xEF)
def test_flipper_samsung32_equals_irdb_necx2():
    assert encode_flipper("Samsung32", 7, 2) == encode_irdb("NECx2", 7, 7, 2)
def test_flipper_nec_equals_irdb_nec1():
    assert encode_flipper("NEC", 4, 8) == encode_irdb("NEC1", 4, -1, 8)
def test_nec2_has_no_repeat_code():
    assert encode_irdb("NEC2", 4, -1, 8).repeat is None
def test_jvc_frame_and_repeat():
    s = encode_irdb("JVC", 3, -1, 23)
    assert s.frame[:2] == (8400, 4200) and s.repeat[0] == 525 and s.period_ms == 60
def test_unknown_protocol_raises():
    with pytest.raises(UnsupportedProtocol): encode_flipper("Pioneer", 1, 2)
```

- [ ] **Step 2: Laufen lassen → FAIL.** Run: `tools/.venv/Scripts/python -m pytest tools/tests/test_protocols.py -q`
- [ ] **Step 3: NEC-Familie und JVC in `protocols.py` implementieren.** Zwei Dispatch-Tabellen (Flipper-Name → Funktion, irdb-Name → Funktion), dazu ein gemeinsamer `_nec_bits(lead_units, bits: list[int], repeat_kind)`.
- [ ] **Step 4: Laufen lassen → 8 passed**
- [ ] **Step 5: Commit** – `tools: NEC family and JVC encoders`

---

### Task 3: RC5, RC6, SIRC, Kaseikyo/Panasonic, Sharp, RCA und Raw

**Files:**
- Modify: `tools/irdata/protocols.py`, `tools/tests/test_protocols.py`

**Interfaces:**
- Consumes: Task 2
- Produces:
  - zusätzliche Namen in `encode_flipper`: `RC5`, `RC5X`, `RC6`, `SIRC`, `SIRC15`, `SIRC20`, `Kaseikyo`, `RCA`
  - zusätzliche Namen in `encode_irdb`: `RC5`, `RC6`, `Sony12`, `Sony15`, `Sony20`, `Panasonic`, `JVC`, `Sharp`, `RCA`
  - `from_raw(freq: int, data: list[int]) -> Signal`: Erst wird versucht, die Folge als NEC/NECx (Toleranz ±25 %) zu decodieren und kanonisch neu zu encodieren. Sonst bleibt sie roh: `normalize`, `period_ms = ceil(duration_us / 1000) + 40`, `repeat=None`.

**Festgelegte Werte (aus IRP-Notation):**

- **RC5 / RC5X:**
  - Frequenz 36000, Einheit 889, MSB zuerst.
  - `0` = `(+1,−1)`, `1` = `(−1,+1)`.
  - Bits: `1`, `~C bit6`, `T`, `A:5`, `C:6` (C bis 127).
  - `frame` mit T=0, `frame_alt` mit T=1, `period_ms` 114.
  - irdb `RC5(D,F)`: A=D, C=F.
- **RC6 (Mode 0):**
  - Frequenz 36000, Einheit 444, MSB zuerst.
  - Vorspann `(6,−2)`, Startbit `1`, Mode `000`.
  - `1` = `(+1,−1)`, `0` = `(−1,+1)`.
  - Toggle doppelt breit: T=1 `(+2,−2)`, T=0 `(−2,+2)`.
  - Dann `A:8`, `C:8`.
  - `frame` mit T=0, `frame_alt` mit T=1, `period_ms` 107.
- **SIRC:**
  - Frequenz 40000, Einheit 600, Vorspann `(4,−1)`, Bits `<1,−1|2,−1>`, LSB zuerst.
  - Bits: `C:7`, dann `A:n`. n = 5 (`SIRC`, `Sony12`), 8 (`SIRC15`, `Sony15`), 13 (`SIRC20`). Bei `Sony20(D,S,F)` ist A = D | S << 5.
  - Eine Kopie = Folge ohne Schlusspause.
  - `frame` = Kopie, Pause (45000 − Kopie), Kopie, Pause, Kopie.
  - `repeat` = Kopie, `period_ms` 45.
- **Panasonic / Kaseikyo:**
  - Frequenz 37000, Einheit 432, Vorspann `(8,−4)`, Bits `<1,−1|1,−3>`, LSB zuerst, 6 Bytes, Stopp `1`.
  - `period_ms = ceil((duration_us + 74736) / 1000)`.
  - irdb `Panasonic(D,S,F)`: Bytes `0x02, 0x20, D, S, F, D^S^F`.
  - Flipper `Kaseikyo(A, C)`:
    - Aus A: genre2 = A & 0xF, genre1 = (A >> 4) & 0xF, vendor = (A >> 8) & 0xFFFF, id = (A >> 24) & 0x3.
    - Bytes:
      - b0 = vendor & 0xFF, b1 = vendor >> 8
      - b2 = vpar | genre1 << 4, mit vpar = XOR der 4 Nibbles von vendor
      - b3 = genre2 | (C & 0xF) << 4
      - b4 = (C >> 4) & 0x3F | id << 6
      - b5 = b2 ^ b3 ^ b4
    - Belegt durch Abgleich beider Datenbanken, siehe Tests.
- **Sharp (irdb):**
  - Frequenz 38000, Einheit 264, Bits `<1,−3|1,−7>`, LSB zuerst.
  - `frame` = `D:5, F:8, (1):2, 1, −165, D:5, ~F:8, (2):2, 1`.
  - `period_ms = ceil((duration_us + 43560) / 1000)`.
- **RCA:**
  - Frequenz 58000, Einheit 460, MSB zuerst, Vorspann `(8,−8)`, Bits `<1,−2|1,−4>`.
  - Bits: `D:4, F:8, ~D:4, ~F:8`, Stopp `1`.
  - `period_ms = ceil((duration_us + 7360) / 1000)`.
  - Flipper: A=D, C=F.

- [ ] **Step 1: Failing tests**

```python
def test_rc5_philips_power_exact():
    s = encode_flipper("RC5", 0x00, 0x0C)
    u = [1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,1,1,2,1,1,2,1,1]
    assert s.frame == tuple(x * 889 for x in u) and s.freq == 36000
    assert s.frame_alt is not None and s.frame_alt != s.frame
def test_rc6_header_and_toggle():
    s = encode_flipper("RC6", 0x00, 0x0C)
    assert s.frame[:2] == (2664, 888) and s.frame != s.frame_alt
@pytest.mark.parametrize("fl, ir", [
    (("RC6", 0x00, 0x0C), ("RC6", 0, -1, 12)),
    (("RC5", 0x00, 0x0C), ("RC5", 0, -1, 12)),
    (("SIRC", 0x01, 0x15), ("Sony12", 1, -1, 21)),
    (("SIRC20", 0x0E5A, 0x19), ("Sony20", 0x1A, 0x72, 0x19)),
    (("Kaseikyo", 0x00200280, 0x3D0), ("Panasonic", 128, 0, 61)),
    (("Kaseikyo", 0x02200289, 0x2B0), ("Panasonic", 128, 9, 171)),
])
def test_flipper_and_irdb_agree(fl, ir):
    assert encode_flipper(*fl) == encode_irdb(*ir)
def test_sony_sends_three_copies():
    s = encode_flipper("SIRC", 0x01, 0x15)
    assert s.frame.count(2400) == 3 and s.repeat.count(2400) == 1 and s.period_ms == 45
def test_panasonic_bytes():
    s = encode_irdb("Panasonic", 128, 0, 61)
    assert s.freq == 37000 and bits_from_spaces(s, 48) == lsb_bits(0x02, 0x20, 0x80, 0x00, 0x3D, 0x80 ^ 0x3D)
def test_raw_nec_is_canonicalized():
    ref = encode_flipper("NEC", 4, 8)
    jitter = [int(d * (1.1 if i % 3 else 0.92)) for i, d in enumerate(ref.frame)]
    assert from_raw(38000, jitter) == ref
def test_raw_unknown_stays_raw():
    s = from_raw(38000, [3000, 1000, 500, 500, 500])
    assert s.frame == (3000, 1000, 500, 500, 500) and s.repeat is None and s.period_ms == 6 + 40
```

- [ ] **Step 2: Laufen lassen → FAIL**
- [ ] **Step 3: Encoder und `from_raw` implementieren.** Für RC5/RC6 einen Manchester-Helfer `_biphase(unit, bits, one, zero)` bauen, der vorzeichenbehaftete Halbbits liefert. `normalize` erledigt das Zusammenfassen.
- [ ] **Step 4: Laufen lassen → alle Tests in `test_protocols.py` grün**
- [ ] **Step 5: Commit** – `tools: RC5/RC6/SIRC/Panasonic/Sharp/RCA encoders and raw decoding`

---

### Task 4: Tastennamen vereinheitlichen

**Files:**
- Create: `tools/irdata/keys.py`, `tools/tests/test_keys.py`

**Interfaces:**
- Produces:
  - `canonical_key(raw_name: str) -> str | None`: liefert einen kanonischen Namen (Global Constraints), `X:<Label>` oder None (verwerfen).
  - `BASIC_KEYS: frozenset[str]`
  - `HDMI_KEYS: tuple[str, ...] = ("HDMI1","HDMI2","HDMI3","HDMI4")`

**Normalisierung vor dem Abgleich:**
1. Großschreibung.
2. `+` → ` PLUS`.
3. `-` am Ende oder allein stehend → ` MINUS`.
4. `_ . -` → Leerzeichen.
5. Mehrfach-Leerzeichen zusammenfassen, trimmen.

**Muster (Regex, ganze Zeichenkette):**

| Kanonisch | Muster |
|---|---|
| POWER | `POWER\|POWER TOGGLE\|ON OFF\|ONOFF\|STANDBY\|PWR` |
| POWER_ON | `POWER ON\|PWR ON\|ON\|DISCRETE ON` |
| POWER_OFF | `POWER OFF\|PWR OFF\|OFF\|DISCRETE OFF` |
| MUTE | `MUTE\|MUTING` |
| VOL_UP / VOL_DOWN | `(VOL\|VOLUME) ?(UP\|PLUS)\|VOLUP` / `(VOL\|VOLUME) ?(DOWN\|DN\|MINUS)\|VOLDN\|VOLDOWN` |
| CH_UP / CH_DOWN | `(CH\|CHAN\|CHANNEL\|PROG\|P) ?(UP\|PLUS\|NEXT)` / `… ?(DOWN\|DN\|MINUS\|PREV\|PREVIOUS)` |
| DIGIT_n | `(DIGIT \|NUM \|KEY )?n` |
| HDMIn | `((INPUT\|SOURCE) )?HDMI ?n( INPUT)?` (n 1–4) |
| SOURCE | `SOURCE\|INPUT\|INPUT SOURCE\|AV\|TV AV\|TV/AV\|INPUT SELECT\|EXT` |
| MENU / HOME | `MENU\|SETTINGS\|SETUP` / `HOME\|SMART HUB\|SMARTHUB\|SMART` |
| OK | `OK\|ENTER\|SELECT\|CONFIRM` |
| UP (analog DOWN/LEFT/RIGHT) | `UP\|CURSOR UP\|ARROW UP\|UP ARROW\|ARW UP\|NAV UP` |
| BACK / EXIT / INFO | `BACK\|RETURN\|RET` / `EXIT\|CLOSE` / `INFO\|DISPLAY\|INFORMATION` |
| LIST / PRE_CH | `LIST\|CH LIST\|CHANNEL LIST` / `PRE CH\|PRECH\|LAST\|PREV CH\|RECALL\|FLASHBACK\|Q VIEW` |
| RED/GREEN/YELLOW/BLUE | `(RED\|GREEN\|YELLOW\|BLUE)( KEY\| BUTTON)?` |

Alles andere wird zu `X:` + Titelschreibung (z. B. `X:Netflix`), sofern es `^[A-Z0-9 ]{1,20}$` erfüllt. Sonst None.

- [ ] **Step 1: Failing test** – ein parametrisierter Test `test_canonical_key` mit diesen Paaren:
  - `("Power","POWER")`, `("On_off","POWER")`, `("Vol_up","VOL_UP")`, `("Vol_dn","VOL_DOWN")`, `("VOLUME +","VOL_UP")`, `("VOLUME -","VOL_DOWN")`
  - `("Ch_next","CH_UP")`, `("Ch_prev","CH_DOWN")`, `("CHANNEL -","CH_DOWN")`
  - `("1","DIGIT_1")`, `("DIGIT 0","DIGIT_0")`
  - `("INPUT SOURCE","SOURCE")`, `("Input","SOURCE")`, `("Tv_av","SOURCE")`
  - `("HDMI1","HDMI1")`, `("HDMI 2","HDMI2")`, `("Input_HDMI3","HDMI3")`, `("HDMI5", "X:Hdmi5")`
  - `("Arw_up","UP")`, `("Enter","OK")`, `("Return","BACK")`, `("Red","RED")`
  - `("Netflix","X:Netflix")`, `("SD CARD / USB / MEDIA PLAYER", None)`
- [ ] **Step 2: FAIL** → **Step 3: implementieren** → **Step 4: PASS** (`pytest tools/tests/test_keys.py -q`)
- [ ] **Step 5: Commit** – `tools: key name normalization`

---

### Task 5: Quellen einlesen und Markennamen

**Files:**
- Create: `tools/irdata/brands.py`, `tools/irdata/sources.py`, `tools/tests/test_sources.py`, Fixture-Dateien (siehe unten)

**Interfaces:**
- Consumes: `encode_flipper`, `encode_irdb`, `from_raw`, `UnsupportedProtocol` (Task 2/3), `canonical_key` (Task 4)
- Produces:
  - `@dataclass class RemoteFile(source: str, brand: str, path: str, keys: dict[str, Signal], basic_named: int, entries: int, unsupported: dict[str, int])`
    - `source` ist `"flipper"` oder `"irdb"`.
    - `keys` enthält nur sendbare Tasten. Die erste Taste gleichen Namens gewinnt.
    - `basic_named`: Anzahl der Grundtasten laut Namen, egal ob sendbar.
    - `unsupported`: Protokollname → Anzahl der Einträge.
  - `iter_flipper(root: Path) -> Iterator[RemoteFile]`: liest `root/TVs/<Marke>/**/*.ir`. Die Marke ist der erste Ordner unter `TVs`.
  - `iter_irdb(root: Path) -> Iterator[RemoteFile]`: liest `root/codes/<Marke>/<Typ>/*.csv`. Typ-Ordner, die auf `(?i)\btv\b|television` passen.
  - In `brands.py`:
    - `canonical_brand(raw: str) -> str`: Schlüssel = Kleinbuchstaben ohne Nicht-Alphanumerisches. Anzeige über die Override-Tabelle (`LG, TCL, JVC, RCA, AOC, NEC, BBK, JMB`). Sonst: Ist `raw` komplett groß, dann `raw.title()`, sonst `raw`.
    - `HOTEL_BRANDS: list[str]` (Reihenfolge laut Global Constraints)
    - `FAMILIES: dict[str, list[str]]`:
      - `"Vestel": [Telefunken, Finlux, JVC, Hitachi, Toshiba, Bush, Techwood, Digihome, Luxor, Polaroid]`
      - `"TCL": [Thomson]`
      - `"Grundig": [Beko, Arcelik]`
    - `family_members(brand: str) -> set[str]`: alle Marken derselben Familie außer `brand` selbst, in beide Richtungen.

**Flipper-Format:**
- Blöcke mit `name:`, `type: parsed|raw`. Bei `parsed` folgen `protocol`, `address`, `command`, bei `raw` folgen `frequency` und `data`.
- Zeilen mit `#` und Leerzeilen trennen Blöcke.
- `address`/`command` sind 4 Hex-Bytes, Little-Endian.

**irdb-Format:** CSV mit Kopfzeile `functionname,protocol,device,subdevice,function`.

**Fixtures** (minimal, von Hand geschrieben; nur Testdaten, keine echten Codes nötig):

| Datei | Inhalt |
|---|---|
| `flipper/TVs/Samsung/Samsung_A.ir` | Samsung32 addr 07: Power 02, Mute 0F, Vol_up 07, Vol_dn 0B, Ch_next 12, Ch_prev 10, Source 01, Netflix F3 |
| `flipper/TVs/Samsung/Samsung_B.ir` | Samsung32 addr 07: Power 02, Mute 0F, Vol_up 07, Vol_dn 0B, Menu 1A |
| `flipper/TVs/Samsung/Samsung_X_Discrete.ir` | Samsung32 addr 07: Power 02, HDMI1 E9, HDMI2 BE, HDMI3 C2, HDMI4 C5 |
| `flipper/TVs/Samsung/Samsung_C.ir` | NEC addr 40: Power 12, Mute 10, Vol_up 1A, Vol_dn 1E |
| `flipper/TVs/Vestel/Vestel_1.ir` | NEC addr 00: Power 0C, Mute 0D, Vol_up 10, Vol_dn 11, HDMI1 20, HDMI2 21 |
| `flipper/TVs/Hisense/Hisense_1.ir` | NEC addr 00: Power 0C, Mute 0D, Vol_up 10, Vol_dn 11 |
| `flipper/TVs/Telefunken/Telefunken_1.ir` | NEC addr 01: Power 0C, Mute 0D, Vol_up 10, Vol_dn 11 |
| `flipper/TVs/Philips/Philips_1.ir` | RC6 addr 00: Power 0C, Mute 0D, Vol_up 10, Vol_dn 11 |
| `flipper/TVs/Philips/Philips_Weird.ir` | Pioneer: Power, Mute, Vol_up, Vol_dn (nicht unterstützt) |
| `flipper/TVs/Samsung/Samsung_Raw.ir` | Eine Taste `Power` als `raw` 38000 Hz mit der leicht verrauschten NEC-Folge von addr 40 cmd 12 |
| `irdb/codes/Samsung/TV/7,7.csv` | NECx2 7,7: POWER 2, MUTE 15, VOLUME + 7, VOLUME - 11 |
| `irdb/codes/ACME/TV/9,-1.csv` | NEC1 9,-1: POWER 1, MUTE 2, VOLUME + 3, VOLUME - 4 |

- [ ] **Step 1: Fixtures anlegen** (Dateien wie in der Tabelle)
- [ ] **Step 2: Failing tests**

```python
FIX = Path(__file__).parent / "fixtures"
def test_flipper_parsed_file():
    f = next(r for r in iter_flipper(FIX / "flipper") if r.path.endswith("Samsung_A.ir"))
    assert f.brand == "Samsung" and f.source == "flipper"
    assert f.keys["POWER"] == encode_flipper("Samsung32", 7, 2) and "X:Netflix" in f.keys
def test_flipper_unsupported_counted():
    f = next(r for r in iter_flipper(FIX / "flipper") if r.path.endswith("Philips_Weird.ir"))
    assert f.keys == {} and f.unsupported == {"Pioneer": 4} and f.basic_named == 4
def test_flipper_raw_is_canonicalized():
    f = next(r for r in iter_flipper(FIX / "flipper") if r.path.endswith("Samsung_Raw.ir"))
    assert f.keys["POWER"] == encode_flipper("NEC", 0x40, 0x12)
def test_irdb_csv():
    files = {r.brand: r for r in iter_irdb(FIX / "irdb")}
    assert files["Acme"].keys["MUTE"] == encode_irdb("NEC1", 9, -1, 2)
    assert files["Samsung"].keys["POWER"] == encode_flipper("Samsung32", 7, 2)
def test_canonical_brand_and_families():
    assert canonical_brand("SAMSUNG") == "Samsung" and canonical_brand("lg") == "LG"
    assert "Vestel" in family_members("Telefunken") and "Telefunken" in family_members("Vestel")
```

- [ ] **Step 3: FAIL** → **Step 4: implementieren** → **Step 5: PASS** (`pytest tools/tests/test_sources.py -q`)
- [ ] **Step 6: Commit** – `tools: flipper/irdb parsers, brand names and families`

---

### Task 6: Code-Sätze, Eingangs-Varianten, Reihenfolgen, Suchlauf

**Files:**
- Create: `tools/irdata/merge.py`, `tools/tests/test_merge.py`

**Interfaces:**
- Consumes: `RemoteFile`, `BASIC_KEYS`, `HDMI_KEYS`, `canonical_brand`, `family_members`, `HOTEL_BRANDS`
- Produces:
  - `@dataclass class CodeSet(keys: dict[str, Signal], support: int, brand_support: dict[str, int])`
    - `keys` enthält **ohne** HDMI1–4.
  - `@dataclass class InputVariant(keys: dict[str, Signal], support: int)`
  - `@dataclass class MergeResult` mit den Feldern:
    - `code_sets: list[CodeSet]`: endgültige Reihenfolge, Index = spätere ID − 1
    - `brand_sets: dict[str, list[tuple[int, bool]]]`: Marke → [(Code-Satz-Index, via_family)], geordnet
    - `variants: list[InputVariant]`
    - `variant_order: dict[tuple[int, str], list[int]]`: (Code-Satz-Index, Marke) → Varianten-Indizes, geordnet
    - `scan: list[tuple[int, str, Signal]]`: (Code-Satz-Index, Markenlabel, Signal)
    - `dropped_files: int`
  - `merge(files: list[RemoteFile]) -> MergeResult`
  - `coverage(files: list[RemoteFile]) -> float`: sendbar / geeignet.
    - Geeignet: `basic_named >= 3`.
    - Sendbar: mindestens 3 Grundtasten in `keys`.

**Regeln** (aus dem Spec; Identität eines Signals = `Signal.identity()`):

1. **Einteilung der Dateien:**
   - b = Anzahl der Grundtasten in `keys`.
   - h = vorhandene HDMI-Tasten.
   - Hat die Datei HDMI1 **und** HDMI2, bilden ihre HDMI-Tasten eine Variante.
   - b ≥ 3: volle Datei.
   - b < 3 mit Variante: reine Eingangs-Datei.
   - Sonst verworfen (`dropped_files`).
2. **Signatur einer vollen Datei:**
   - Sind POWER (bzw. POWER_ON) und MUTE vorhanden: `(id(POWER), id(MUTE))`.
   - Sonst: sortiertes Tupel `(key, id)` aller vorhandenen Grundtasten.
3. **Zusammenfassen:** Gleiche Signatur ergibt einen Code-Satz.
   - Tasten werden vereinigt.
   - Pro Taste gewinnt die Identität mit den meisten Dateien. Bei Gleichstand gewinnt die Quelle `flipper`, danach der alphabetisch kleinste Pfad.
   - `support` = Anzahl der Dateien, `brand_support[Marke]` = Dateien dieser Marke.
4. **Reihenfolge der Code-Sätze (global):** `support` absteigend, dann kleinster Markenname (alphabetisch), dann `repr(Signatur)`.
5. **Markenlisten:**
   - Zuerst die eigenen Code-Sätze der Marke, sortiert nach `brand_support[Marke]` absteigend, dann globaler Reihenfolge.
   - Danach Code-Sätze der Familien-Marken (`via_family=True`) in globaler Reihenfolge, ohne Doppelte.
   - Marken ohne eigene Code-Sätze, aber mit Familien-Code-Sätzen, erscheinen ebenfalls.
6. **Varianten:**
   - Doppelte werden über das Tupel der Identitäten von HDMI1–4 entfernt (fehlend = None). `support` = Anzahl der Dateien.
   - Eine Variante gehört
     - als **eigene** Variante zu dem Code-Satz, aus dessen voller Datei sie stammt,
     - als **angehängte** Variante zu jedem Code-Satz derselben Marke, der mit der reinen Eingangs-Datei mindestens eine Signal-Identität teilt,
     - als **Marken-Variante** zu ihrer Marke.
   - `variant_order[(cs, Marke)]` für jedes Paar in `brand_sets`: eigene Varianten, dann angehängte, dann Marken-Varianten. Innerhalb einer Gruppe nach `support` absteigend, ohne Doppelte.
7. **Suchlauf:**
   - Code-Sätze in globaler Reihenfolge durchgehen. Gesendet wird POWER_OFF, sonst POWER, sonst wird der Code-Satz übersprungen.
   - Ein Signal mit schon benutzter Identität wird übersprungen.
   - Markenlabel = Marke mit höchstem `brand_support`, bei Gleichstand alphabetisch.

- [ ] **Step 1: Failing tests** (auf den Fixtures aus Task 5)

```python
def load():
    files = list(iter_flipper(FIX / "flipper")) + list(iter_irdb(FIX / "irdb"))
    return files, merge(files)
def test_samsung_files_merge_into_one_set():
    _, r = load()
    first = r.code_sets[0]
    assert first.support == 3 and first.brand_support == {"Samsung": 3}
    assert {"MENU", "SOURCE", "X:Netflix"} <= first.keys.keys() and "HDMI1" not in first.keys
def test_raw_only_file_is_dropped_not_duplicated():
    _, r = load()
    c = [cs for cs in r.code_sets if cs.keys["POWER"] == encode_flipper("NEC", 0x40, 0x12)]
    assert len(c) == 1 and c[0].support == 1
def test_global_order():
    _, r = load()
    # 0 Samsung-Haupt (3), 1 Vestel/Hisense (2), dann Support 1 nach Markenname: 2 Acme, 3 Philips, 4 Samsung_C, 5 Telefunken
    assert [cs.support for cs in r.code_sets] == [3, 2, 1, 1, 1, 1]
    assert r.code_sets[1].brand_support == {"Vestel": 1, "Hisense": 1}
def test_brand_lists():
    _, r = load()
    assert r.brand_sets["Samsung"] == [(0, False), (4, False)]
    assert r.brand_sets["Telefunken"] == [(5, False), (1, True)]
    assert r.brand_sets["Vestel"] == [(1, False), (5, True)]
    assert r.brand_sets["Toshiba"] == [(1, True), (5, True)]   # nur über Familie
    assert "Thomson" not in r.brand_sets                       # TCL hat keine Code-Sätze
def test_variants_order():
    _, r = load()
    disc = r.variant_order[(0, "Samsung")]
    assert len(disc) == 1 and set(r.variants[disc[0]].keys) == {"HDMI1", "HDMI2", "HDMI3", "HDMI4"}
    samsung_c = next(i for i, _ in r.brand_sets["Samsung"] if i != 0)
    assert r.variant_order[(samsung_c, "Samsung")] == disc     # nur Marken-Variante
    assert set(r.variants[r.variant_order[(1, "Hisense")][0]].keys) == {"HDMI1", "HDMI2"}
def test_scan_dedup_and_labels():
    _, r = load()
    assert [label for _, label, _ in r.scan] == ["Samsung", "Hisense", "Acme", "Philips", "Samsung", "Telefunken"]
def test_dropped_and_coverage():
    files, r = load()
    assert r.dropped_files == 2          # Philips_Weird (nichts sendbar), Samsung_Raw (1 Grundtaste, keine Variante)
    assert coverage(files) == pytest.approx(9 / 10)
```

(Abdeckung: 10 Dateien haben ≥ 3 Grundtasten laut Namen. Samsung_X und Samsung_Raw haben nur eine. Davon ist nur Philips_Weird nicht sendbar.)

- [ ] **Step 2: FAIL** → **Step 3: `merge.py` implementieren** → **Step 4: PASS** (`pytest tools/tests/test_merge.py -q`)
- [ ] **Step 5: Commit** – `tools: code set merging, input variants, scan order`

---

### Task 7: SQLite schreiben, CLI, Fixture-DB für Android

**Files:**
- Create: `tools/irdata/db.py`, `tools/build_codes.py`, `tools/fetch_sources.sh`, `tools/tests/test_db.py`
- Create (Ergebnis): `app/src/test/resources/codes-fixture.db`

**Interfaces:**
- Consumes: `MergeResult`, `merge`, `coverage`, `iter_flipper`, `iter_irdb`, `to_blob`
- Produces:
  - `write_db(result: MergeResult, out: Path, meta: dict[str, str]) -> None`
  - CLI: `python tools/build_codes.py --flipper DIR [--irdb DIR] --out PATH [--min-coverage 0.95]`. Gibt den Bericht aus und beendet sich mit Code 2, wenn die Abdeckung unter dem Minimum liegt.
  - Das Schema ist der Vertrag zur App (Task 11):

```sql
CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT NOT NULL);  -- flipper_commit, irdb_commit ('' wenn ohne), built_at, code_sets, brands, coverage
CREATE TABLE brand(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, hotel_rank INTEGER);       -- hotel_rank 1..15 oder NULL
CREATE TABLE signal(id INTEGER PRIMARY KEY, freq INTEGER NOT NULL, frame BLOB NOT NULL, repeat BLOB, frame_alt BLOB, period_ms INTEGER NOT NULL);
CREATE TABLE code_set(id INTEGER PRIMARY KEY, support INTEGER NOT NULL);                         -- id = Index + 1
CREATE TABLE code_set_brand(code_set_id INTEGER NOT NULL, brand_id INTEGER NOT NULL, ord INTEGER NOT NULL, via_family INTEGER NOT NULL, PRIMARY KEY(code_set_id, brand_id));
CREATE TABLE code_set_key(code_set_id INTEGER NOT NULL, key TEXT NOT NULL, signal_id INTEGER NOT NULL, PRIMARY KEY(code_set_id, key));
CREATE TABLE input_variant(id INTEGER PRIMARY KEY, support INTEGER NOT NULL);
CREATE TABLE input_variant_key(variant_id INTEGER NOT NULL, key TEXT NOT NULL, signal_id INTEGER NOT NULL, PRIMARY KEY(variant_id, key));
CREATE TABLE input_variant_order(code_set_id INTEGER NOT NULL, brand_id INTEGER NOT NULL, ord INTEGER NOT NULL, variant_id INTEGER NOT NULL, PRIMARY KEY(code_set_id, brand_id, ord));
CREATE TABLE scan(position INTEGER PRIMARY KEY, code_set_id INTEGER NOT NULL, brand_id INTEGER NOT NULL, signal_id INTEGER NOT NULL);  -- position ab 0
```

Weitere Regeln:
- Signale werden über das ganze Tupel `(freq, frame, repeat, frame_alt, period_ms)` dedupliziert.
- Marken-IDs werden alphabetisch vergeben.
- Am Ende `VACUUM`.

**Bericht** (stdout), je eine Zeile:
- Dateien je Quelle
- verworfene Dateien
- nicht unterstützte Protokolle mit Anzahl, absteigend
- Code-Sätze
- Marken
- Varianten
- Länge des Suchlaufs
- Abdeckung in %

- [ ] **Step 1: Failing test** (`test_db.py`): Fixtures mergen, in eine temporäre Datei schreiben und mit `sqlite3` prüfen:
  - `SELECT COUNT(*) FROM scan` == 6
  - `brand` enthält `Samsung` mit `hotel_rank` 1, `Acme` mit NULL
  - `from_blob(frame)` des POWER-Signals von Code-Satz 1 == `encode_flipper("Samsung32",7,2).frame`
  - `input_variant_order` für (1, Samsung) hat genau 1 Zeile
  - `meta['irdb_commit']` wie übergeben
- [ ] **Step 2: FAIL** → **Step 3: `db.py` und `build_codes.py` implementieren** → **Step 4: PASS**
- [ ] **Step 5: Fixture-DB für Android erzeugen**

Run: `tools/.venv/Scripts/python tools/build_codes.py --flipper tools/tests/fixtures/flipper --irdb tools/tests/fixtures/irdb --out app/src/test/resources/codes-fixture.db --min-coverage 0`
Expected: Bericht mit „Code-Sätze: 6“, „Suchlauf: 6“. Die Datei existiert.

- [ ] **Step 6: `fetch_sources.sh` schreiben.** Klont nach `tools/sources/flipper` und `tools/sources/irdb`:
  - `git clone --filter=blob:none --no-checkout <url>`
  - `git sparse-checkout set TVs` bzw. `codes`
  - `git checkout <SHA aus Global Constraints>`

  Ist das Ziel schon vorhanden, nur `fetch` und `checkout`.
- [ ] **Step 7: Alle Python-Tests** – `tools/.venv/Scripts/python -m pytest tools/tests -q` → alles grün
- [ ] **Step 8: Commit** – `tools: sqlite writer, CLI, fixture db`

---

### Task 8: Echte Daten bauen und Abdeckung sichern

**Files:**
- Create: `app/src/main/assets/codes.db`
- Modify (falls nötig): `tools/irdata/protocols.py`, `tools/irdata/keys.py`, Tests dazu

**Interfaces:**
- Produces: `app/src/main/assets/codes.db` (Schema aus Task 7)

- [ ] **Step 1: Quellen holen** – `bash tools/fetch_sources.sh` → beide Ordner auf den gepinnten Commits (`git -C tools/sources/flipper rev-parse HEAD`)
- [ ] **Step 2: Bauen (ohne irdb)**

Run: `tools/.venv/Scripts/python tools/build_codes.py --flipper tools/sources/flipper --out app/src/main/assets/codes.db`
Expected: Exit 0, Abdeckung ≥ 95 %.

- [ ] **Step 3: Bei Abdeckung < 95 %:**
  - Die größten Posten aus „nicht unterstützte Protokolle“ als Encoder ergänzen, jeweils mit Test im Stil von Task 3. IRP-Definition aus der Literatur, z. B. IrpProtocols.
  - Bei Tastennamen, die häufig als `X:` landen, aber Grundtasten sind, das Muster in `keys.py` erweitern, mit Testfall.
  - Danach Step 2 wiederholen.
- [ ] **Step 4: Plausibilität prüfen**

Run: `sqlite3`-Abfrage (oder Python), um diese Werte auszugeben:
- Anzahl Code-Sätze für Samsung, LG und Philips
- Anzahl Varianten mit HDMI1
- Länge des Suchlaufs
- Dateigröße

Expected: Samsung, LG und Philips haben jeweils ≥ 5 Code-Sätze, es gibt ≥ 1 Variante, die Datei ist < 15 MB. Die Werte im Commit-Text festhalten.
- [ ] **Step 5: Commit** – `data: build codes.db from Flipper-IRDB d126fb1` (Werte aus Step 4 in den Text)

---

### Task 9: Android-Projekt aufsetzen

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, Gradle-Wrapper, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/java/de/hotelremote/MainActivity.kt`, `app/src/main/java/de/hotelremote/ui/theme/Theme.kt`, `app/src/main/java/de/hotelremote/Keys.kt`
- Modify: `.gitignore` (+ `.gradle/`, `build/`, `app/build/`, `local.properties`, `*.jks`, `.idea/`)

**Interfaces:**
- Produces:
  - `object Keys`: Konstanten für alle kanonischen Namen (`POWER`, …, `DIGIT_0`…`DIGIT_9`, `HDMI1`…`HDMI4`) sowie
    - `val HDMI: List<String>`
    - `val REPEATING: Set<String>` (laut Global Constraints)
    - `fun isExtra(key: String) = key.startsWith("X:")`
    - `fun extraLabel(key: String) = key.removePrefix("X:")`
  - `@Composable fun HotelRemoteTheme(content: @Composable () -> Unit)`: nur dunkel. Farben als Konstanten in `Theme.kt` aus dem CSS von `docs/superpowers/specs/mockups/remote.html`:
    - Hintergrund `#2E3035`→`#1D1E21`
    - Taste `#484B51`→`#383A3F`, Schatten `#141517`, Text `#ECEEF0`
    - SOURCE `#3A67B8`→`#2C5199`, Power `#CF3B31`→`#A72C24`
    - Farbtasten `#C8352D #2F9E44 #D4B417 #2F62C8`
    - Grün für „Ja“ `#2F9E44`→`#25803A`

**Versionen (`libs.versions.toml`):**

| Bibliothek | Version |
|---|---|
| agp | 8.13.0 |
| kotlin | 2.2.20 (Plugins `org.jetbrains.kotlin.android`, `.plugin.compose`, `.plugin.serialization`) |
| composeBom | 2025.10.01 |
| activityCompose | 1.11.0 |
| lifecycle (viewmodel-compose, runtime-compose) | 2.9.4 |
| coreKtx | 1.17.0 |
| serialization | 1.9.0 |
| coroutines (test) | 1.10.2 |
| junit | 4.13.2 |
| robolectric | 4.16 |
| androidxTestCore | 1.7.0 |

Compose-Artefakte: `ui`, `foundation`, `material3`, `ui-tooling-preview`. Dazu `ui-test-junit4` (Test) und `ui-test-manifest` (debug).

`app/build.gradle.kts`:
- `namespace`/`applicationId` = `de.hotelremote`
- `versionCode 1`, `versionName "1.0"`
- `buildFeatures.compose = true`, `buildConfig = true`
- `testOptions.unitTests.isIncludeAndroidResources = true`
- Java/Kotlin-Ziel 17

Manifest:
- nur `TRANSMIT_IR`, `uses-feature consumerir required=false`
- `android:allowBackup="false"`
- Activity `screenOrientation="portrait"`, Theme mit dunklem Fensterhintergrund `#1D1E21`
- Label `@string/app_name` = „Fernbedienung“

- [ ] **Step 1: Wrapper erzeugen**

Run: `"$(ls -d ~/.gradle/wrapper/dists/gradle-8.13-all/*/gradle-8.13/bin/gradle | head -1)" wrapper --gradle-version 8.13 --distribution-type bin`
Expected: `gradlew`, `gradlew.bat`, `gradle/wrapper/*` vorhanden.

- [ ] **Step 2: Build-Dateien, Manifest, Theme, `Keys.kt` und eine `MainActivity` mit leerem `HotelRemoteTheme { Box(Modifier.fillMaxSize()) }` anlegen**
- [ ] **Step 3: Bauen** – `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 4: Berechtigungen prüfen**

Run: `"$ANDROID_HOME/build-tools/36.0.0/aapt.exe" dump permissions app/build/outputs/apk/debug/app-debug.apk`
Expected: Es gibt **keine** Zeile mit `INTERNET`. Vorhanden ist `TRANSMIT_IR`. Außerdem höchstens die AndroidX-eigene `…DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.

- [ ] **Step 5: Commit** – `app: android project skeleton`

---

### Task 10: IR-Signal, Sender, Halten und Toggle

**Files:**
- Create: `app/src/main/java/de/hotelremote/ir/IrSignal.kt`, `ir/IrTransmitter.kt`, `ir/SignalSender.kt`
- Test: `app/src/test/java/de/hotelremote/ir/SignalSenderTest.kt`

**Interfaces:**
- Produces:
  - `@Serializable data class IrSignal(val freq: Int, val frame: List<Int>, val repeat: List<Int>? = null, val frameAlt: List<Int>? = null, val periodMs: Int)`
  - `interface IrTransmitter { val available: Boolean; fun transmit(freq: Int, pattern: IntArray) }`
  - `class AndroidIrTransmitter(context: Context) : IrTransmitter`
    - `available = consumerIrManager?.hasIrEmitter() == true`
    - `transmit` leitet an `ConsumerIrManager.transmit` weiter
  - `class SignalSender(tx: IrTransmitter, scope: CoroutineScope, io: CoroutineDispatcher, onError: (Throwable) -> Unit)` mit:
    - `fun press(signal: IrSignal)`: sendet `frame` oder `frameAlt`. Gewechselt wird pro Signal (Schlüssel: `signal.frame`) bei jedem `press`/`hold`-Start.
    - `fun hold(signal: IrSignal): Job`: wie `press`. Nach `HOLD_DELAY_MS = 400` sendet es alle `periodMs` `repeat ?: <derselbe Frame wie der Start>`, bis der Job abgebrochen wird. Die Wartezeit ist `periodMs` minus der echten Sendedauer, mindestens 0.
    - Alle Sendungen laufen über `io.limitedParallelism(1)`, also strikt nacheinander.
    - Exceptions aus `transmit` gehen an `onError`, nie nach oben.

- [ ] **Step 1: Failing tests** (Fake-Transmitter zeichnet `(freq, pattern.toList())` auf; `runTest` + `StandardTestDispatcher`)

```kotlin
@Test fun pressSendsFrame()                         // 1 Aufruf, pattern == frame, freq stimmt
@Test fun rapidPressesAreSerializedAndToggleAlternates()   // RC5-Signal mit frameAlt: 3x press -> frame, frameAlt, frame, in dieser Reihenfolge
@Test fun holdRepeatsAfterDelayThenEveryPeriod()    // NEC (repeat gesetzt, periodMs 108): hold, advanceTimeBy(400+108*2+1) -> [frame, repeat, repeat, repeat]; cancel -> keine weiteren
@Test fun holdRepeatsKeepToggle()                   // RC5 ohne repeat: hold -> alle gesendeten == frame (kein Wechsel beim Halten)
@Test fun transmitErrorIsReportedNotThrown()        // Fake wirft IllegalArgumentException -> onError 1x aufgerufen, kein Crash, nächstes press funktioniert
```

- [ ] **Step 2: FAIL** – `./gradlew :app:testDebugUnitTest --tests "de.hotelremote.ir.*"`
- [ ] **Step 3: implementieren** → **Step 4: PASS**
- [ ] **Step 5: Commit** – `app: IR signal model and sender with hold/toggle`

---

### Task 11: Code-Datenbank öffnen und abfragen

**Files:**
- Create: `app/src/main/java/de/hotelremote/data/CodeDatabase.kt`, `data/CodeRepository.kt`, `wizard/WizardData.kt`
- Test: `app/src/test/java/de/hotelremote/data/CodeRepositoryTest.kt`, `data/CodeDatabaseTest.kt` (Robolectric, `@Config(sdk = [35])`)

**Interfaces:**
- Consumes: Schema aus Task 7, `IrSignal` (Task 10)
- Produces:
  - `object CodeDatabase { fun open(context: Context, stamp: Long = installStamp(context), source: () -> InputStream = { context.assets.open("codes.db") }): SQLiteDatabase }`
    - Kopiert das Asset nach `noBackupFilesDir/codes-<stamp>.db`, falls die Datei fehlt.
    - Löscht andere `codes-*.db`.
    - Öffnet nur lesend.
    - `installStamp` = `packageManager.getPackageInfo(packageName, 0).lastUpdateTime`.
  - `data class Brand(val id: Long, val name: String, val hotelRank: Int?, val codeSetCount: Int)`
  - `data class ScanEntry(val position: Int, val codeSetId: Long, val brandId: Long, val brandName: String, val signal: IrSignal)`
  - `interface WizardData` (in `wizard/WizardData.kt`) mit:
    - `fun codeSetsForBrand(brandId: Long): List<Long>`
    - `fun keys(codeSetId: Long): Map<String, IrSignal>`
    - `fun inputVariants(codeSetId: Long, brandId: Long): List<Map<String, IrSignal>>`
    - `fun scanEntries(): List<ScanEntry>`
    - `fun brandName(brandId: Long): String`
  - `class CodeRepository(db: SQLiteDatabase) : WizardData` mit:
    - `fun brands(): List<Brand>`: Hotelmarken nach `hotel_rank`, dann der Rest nach Name ohne Groß/Klein. Nur Marken mit ≥ 1 Code-Satz.
    - `fun meta(): Map<String, String>`
    - BLOB-Decodierung Little-Endian über `ByteBuffer`
    - `codeSetsForBrand` nach `ord`, `inputVariants` nach `input_variant_order.ord`

- [ ] **Step 1: Failing tests.** Die Fixture-DB wird aus `classLoader.getResource("codes-fixture.db")` in einen temporären Ordner kopiert und mit `SQLiteDatabase.openDatabase` geöffnet.

```kotlin
@Test fun brandsHotelFirstThenAlphabetical()   // Namen == [Samsung, Philips, Hisense, Telefunken, Toshiba, Vestel, Acme, Bush, Digihome, Finlux, Hitachi, JVC, Luxor, Polaroid, Techwood]
@Test fun samsungHasTwoCodeSetsMainFirst()     // codeSetsForBrand(samsung) == [1L, 5L]
@Test fun telefunkenOwnThenFamily()            // codeSetsForBrand(telefunken) == [6L, 2L]
@Test fun keysDecodeSamsungPower()             // keys(1)["POWER"]!!.frame.take(2) == listOf(4512, 4512); freq 38000; enthält "X:Netflix"; kein "HDMI1"
@Test fun inputVariantsForMainSamsungSet()     // inputVariants(1, samsung).single().keys == setOf("HDMI1","HDMI2","HDMI3","HDMI4")
@Test fun scanEntriesOrderAndLabels()          // brandName-Liste == [Samsung, Hisense, Acme, Philips, Samsung, Telefunken]
```

`CodeDatabaseTest`:

```kotlin
@Test fun copiesAssetOnFirstOpen()             // open(ctx, stamp=1) -> Datei codes-1.db existiert, Abfrage auf meta klappt
@Test fun newInstallStampRecopiesDatabase()    // open(stamp=1), dann open(stamp=2) -> codes-2.db existiert, codes-1.db gelöscht
```

(`CodeDatabaseTest` übergibt als `source` den Stream der Fixture-DB aus den Test-Ressourcen.)

- [ ] **Step 2: FAIL** → **Step 3: implementieren** → **Step 4: PASS** (`./gradlew :app:testDebugUnitTest --tests "de.hotelremote.data.*"`)
- [ ] **Step 5: Commit** – `app: read-only code database and repository`

---

### Task 12: Gespeicherte Fernbedienungen

**Files:**
- Create: `app/src/main/java/de/hotelremote/data/RemoteStore.kt`
- Test: `app/src/test/java/de/hotelremote/data/RemoteStoreTest.kt` (reines JUnit, `TemporaryFolder`)

**Interfaces:**
- Consumes: `IrSignal`
- Produces:
  - `@Serializable data class SavedRemote(val id: String, val name: String? = null, val brand: String, val codeLabel: String, val keys: Map<String, IrSignal>, val lastUsed: Long)` mit `val title get() = name?.takeIf { it.isNotBlank() } ?: "$brand · $codeLabel"`
  - `class RemoteStore(private val file: File)` mit:
    - `fun all(): List<SavedRemote>`: sortiert nach `lastUsed` absteigend
    - `fun add(r: SavedRemote)`
    - `fun rename(id: String, name: String?)`
    - `fun delete(id: String)`
    - `fun markUsed(id: String, now: Long)`
    - `fun lastUsed(): SavedRemote?`
  - Schreiben atomar: in `<file>.tmp`, dann umbenennen.
  - JSON mit `ignoreUnknownKeys = true`.
  - Unlesbare Datei: Inhalt nach `<file>.bak` verschieben, leere Liste zurückgeben.

- [ ] **Step 1: Failing tests**

```kotlin
@Test fun addAndReadBack()                     // add 2, all() nach lastUsed absteigend, Signale identisch
@Test fun renameAndBlankNameFallsBackToLabel() // rename(id, "  ") -> title == "Samsung · Code 3"
@Test fun deleteRemoves()
@Test fun markUsedChangesLastUsed()
@Test fun corruptFileIsBackedUpAndIgnored()    // file = "{kaputt" -> all() == emptyList(), <file>.bak existiert mit "{kaputt", add() danach funktioniert
@Test fun missingFileIsEmpty()
```

- [ ] **Step 2: FAIL** → **Step 3: implementieren** → **Step 4: PASS**
- [ ] **Step 5: Commit** – `app: saved remotes store`

---

### Task 13: Wizard als Zustandsautomat

**Files:**
- Create: `app/src/main/java/de/hotelremote/wizard/WizardMachine.kt`
- Test: `app/src/test/java/de/hotelremote/wizard/WizardMachineTest.kt` (reines JUnit, Fake-`WizardData`)

**Interfaces:**
- Consumes: `WizardData`, `ScanEntry`, `IrSignal`, `Keys`
- Produces:

```kotlin
sealed interface WizardState {
    data object PickBrand : WizardState
    data class TestCode(val brandId: Long, val index: Int, val total: Int, val codeSetId: Long, val testKey: String) : WizardState
    data class Confirm(val brandId: Long, val codeSetId: Long, val confirmKey: String, val fromScan: Boolean) : WizardState
    data class Scanning(val position: Int, val total: Int, val brandLabel: String) : WizardState
    data class ScanVerify(val candidates: List<ScanEntry>, val index: Int, val testKey: String) : WizardState
    data class TestInputs(val brandId: Long, val codeSetId: Long, val variantIndex: Int, val variantCount: Int) : WizardState
    data class Save(val brandName: String, val codeLabel: String, val keys: Map<String, IrSignal>, val hasInputs: Boolean) : WizardState
    data class NoMatch(val brandId: Long?, val canResumeScanAt: Int?) : WizardState
}
class WizardMachine(private val data: WizardData) {
    var state: WizardState; private set          // Start: PickBrand
    fun pickBrand(brandId: Long)
    fun useTvOffMode()                            // TestCode: testKey wird POWER (oder POWER_ON)
    fun answer(yes: Boolean)                      // TestCode, Confirm, ScanVerify, TestInputs
    fun startScan(fromPosition: Int = 0)
    fun currentScanSignal(): IrSignal?            // Scanning: Signal an position, sonst null
    fun scanAdvance()                             // position+1; am Ende -> NoMatch(null, null)
    fun stopScan()                                // -> ScanVerify mit den letzten bis zu 5 gesendeten (neuester zuerst)
    fun testSignal(): IrSignal?                   // was der große Testknopf im aktuellen Zustand sendet
    fun back(): Boolean                           // einen Schritt zurück; false = Wizard verlassen
}
```

**Regeln:**

1. **`pickBrand`** → `TestCode(index 0)`.
   - `testKey` = MUTE, sonst VOL_DOWN, sonst POWER, sonst POWER_ON.
   - Hat die Marke keine Code-Sätze → `NoMatch(brandId, null)`.
2. **TestCode:**
   - Bei Nein → nächster Index. Nach dem letzten Code-Satz → `NoMatch(brandId, null)`. Im TV-aus-Modus bleibt `testKey` POWER/POWER_ON.
   - Bei Ja → `Confirm(confirmKey = VOL_UP)`, falls vorhanden, sonst direkt Schritt 4.
3. **Confirm:**
   - Bei Ja → Schritt 4.
   - Bei Nein → wie Nein im vorherigen Zustand (nächster Code bzw. nächster Kandidat).
4. **Eingänge:**
   - `inputVariants(codeSetId, brandId)` leer → `Save(hasInputs=false)`.
   - Sonst `TestInputs(0, n)`. `testSignal` = HDMI1 der Variante.
   - Bei Ja → `Save(keys = keys(codeSet) + Variante, hasInputs=true)`.
   - Bei Nein → nächste Variante. Nach der letzten → `Save(hasInputs=false)`.
5. **Scanning:** `stopScan` → `ScanVerify`. Die Kandidaten sind die Einträge von Position p, p−1, …, p−4 (≥ 0).
   - `testKey` = POWER_ON, sonst POWER.
   - Bei Ja → `Confirm(confirmKey = MUTE, sonst VOL_UP, fromScan=true)`. Ist keiner davon vorhanden, direkt Schritt 4.
   - Bei Nein → nächster Kandidat. Nach dem letzten → `NoMatch(null, canResumeScanAt = p + 1)`.
6. **Save:**
   - `brandName` = `data.brandName(brandId)` bzw. beim Suchlauf `entry.brandName`.
   - `codeLabel` = `"Code ${index + 1}"`. Beim Suchlauf: Position in `codeSetsForBrand(entry.brandId)` + 1, sonst `"Suchlauf"`.
7. **`back()`:**
   - TestCode(index > 0) → index − 1, TestCode(0) → PickBrand.
   - Confirm → zurück zum Test-Zustand davor.
   - TestInputs → Confirm bzw. Test-Zustand.
   - Scanning → PickBrand.
   - ScanVerify → PickBrand.
   - Save → TestInputs bzw. vorheriger Zustand.
   - PickBrand → `false`.

- [ ] **Step 1: Failing tests** (Fake mit 2 Marken: Marke 1 mit Code-Sätzen [10, 11], Code-Satz 11 hat kein MUTE und kein VOL_UP; Marke 2 mit [20]; Scan-Liste mit 7 Einträgen; Varianten für (10,1): zwei)

```kotlin
@Test fun brandFlowHappyPathWithInputs()        // pick(1) -> TestCode(MUTE) -> yes -> Confirm(VOL_UP) -> yes -> TestInputs(0,2) -> no -> TestInputs(1,2) -> yes -> Save(hasInputs=true, keys enthält HDMI1 der 2. Variante, codeLabel "Code 1")
@Test fun codeSetWithoutMuteUsesVolDownAndSkipsConfirm()   // pick(1), no -> TestCode(index 1, testKey VOL_DOWN); yes -> direkt TestInputs/Save
@Test fun allCodesRejectedGivesNoMatch()        // pick(2), no -> NoMatch(2, null)
@Test fun allInputVariantsRejectedSavesWithoutInputs()
@Test fun tvOffModeTestsWithPower()             // useTvOffMode -> testKey POWER, bleibt nach no
@Test fun scanStopVerifiesLastFiveNewestFirst() // start, 6x advance, stop -> candidates positions [6,5,4,3,2]
@Test fun scanStoppedEarlyHasFewerCandidates()  // start, 1x advance, stop -> [1,0]
@Test fun scanRunToEndGivesNoMatch()            // 7x advance -> NoMatch(null, null), currentScanSignal() == null
@Test fun scanVerifyAllNoOffersResume()         // stop bei p=3, 4x no -> NoMatch(null, canResumeScanAt = 4)
@Test fun scanVerifyYesConfirmsWithMuteThenInputs()
@Test fun backNavigation()                      // TestCode(1) -> back -> TestCode(0) -> back -> PickBrand -> back == false
```

- [ ] **Step 2: FAIL** → **Step 3: implementieren** → **Step 4: PASS** (`--tests "de.hotelremote.wizard.*"`)
- [ ] **Step 5: Commit** – `app: wizard state machine`

---

### Task 14: Fernbedienungs-Bildschirm

**Files:**
- Create: `app/src/main/java/de/hotelremote/ui/remote/RemoteKeys.kt`, `ui/remote/KeyButton.kt`, `ui/remote/RemoteScreen.kt`, `ui/remote/ExtraKeysScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/java/de/hotelremote/ui/remote/RemoteKeysTest.kt` (JUnit), `ui/remote/RemoteScreenTest.kt` (Robolectric + `createComposeRule`)

**Interfaces:**
- Consumes: `SavedRemote`, `Keys`, `HotelRemoteTheme`
- Produces:
  - `fun powerAction(keys: Set<String>, longPress: Boolean): String?`
    - Gibt es POWER: immer POWER.
    - Sonst kurz: POWER_ON, ersatzweise POWER_OFF.
    - Sonst lang: POWER_OFF, ersatzweise POWER_ON.
    - Sonst null.
  - `fun showInputRow(keys: Set<String>) = Keys.HDMI1 in keys`
  - `interface KeyHandler { fun press(key: String); fun hold(key: String): Job? }`
  - `@Composable fun RemoteScreen(remote: SavedRemote, remotes: List<SavedRemote>, irAvailable: Boolean, handler: KeyHandler, onSwitch: (String) -> Unit, onOpenList: () -> Unit, onMenu: (RemoteMenu) -> Unit)` mit `enum class RemoteMenu { EXTRA_KEYS, WIZARD, ABOUT }`
  - `@Composable fun ExtraKeysScreen(remote: SavedRemote, handler: KeyHandler, onBack: () -> Unit)`: Liste aller `X:`-Tasten als große Zeilen-Buttons, alphabetisch

**Layout:** wie Mockup `remote.html` (rechte Variante) über den ganzen Bildschirm.
- Spalten mit `weight`, Seitenrand 12 dp, Abstand 8 dp, Tastenradius 14 dp.
- Gewichte: Power-Zeile 1.25, HDMI-Zeile 1.05, Zahlenzeilen 0.85 (ohne HDMI-Zeile: 1.0), Wippen-Block 2.2, Steuerkreuz 3.0 (ohne HDMI-Zeile: 3.4), BACK-Zeile 0.9, Farbtasten fest 20 dp.
- Kopfzeile: ☰ (`onOpenList`), Titel `remote.title + " ▾"` (öffnet ein `DropdownMenu` mit `remotes` → `onSwitch(id)`), ⋮ (Menü „Weitere Tasten“, „Fernbedienung suchen“, „Über“).
- **Taste fehlt** in `remote.keys`: gezeichnet mit Alpha 0.35, nicht klickbar.
- **Bedienung:**
  - Tasten aus `Keys.REPEATING` reagieren auf `onPress` mit `handler.hold(key)`, beim Loslassen wird der Job abgebrochen.
  - Alle anderen senden beim Drücken `handler.press(key)`.
  - Die Power-Taste nutzt `onTap`/`onLongPress` mit `powerAction`.
  - Jeder Druck: `view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)`.
- **Kein IR-Sender:** Ist `!irAvailable`, steht oben ein Banner (Text siehe unten).
- Jede Taste bekommt `Modifier.testTag("key_<KEY>")`, das Banner `testTag("no_ir_banner")`.

**Texte (`strings.xml`, exakt):**
- `no_ir` = „Dieses Handy hat keinen Infrarot-Sender. Die App kann nichts senden.“
- `send_failed` = „Senden fehlgeschlagen“
- `menu_extra` = „Weitere Tasten“
- `menu_wizard` = „Fernbedienung suchen“
- `menu_about` = „Über“

- [ ] **Step 1: Failing tests**

```kotlin
// RemoteKeysTest
@Test fun powerPrefersToggle()        // {POWER, POWER_OFF}, lang -> POWER
@Test fun powerOnOffSplit()           // {POWER_ON, POWER_OFF}: kurz -> POWER_ON, lang -> POWER_OFF
@Test fun powerOnlyOff()              // {POWER_OFF}: kurz -> POWER_OFF
@Test fun noPower()                   // {} -> null
@Test fun inputRowOnlyWithHdmi1()     // {HDMI2} -> false, {HDMI1} -> true
// RemoteScreenTest
@Test fun inputRowHiddenWithoutHdmi() // onNodeWithTag("key_HDMI1").assertDoesNotExist()
@Test fun inputRowShownWithHdmi()     // key_HDMI1 existiert, key_HDMI4 nicht aktiviert, falls nicht in keys
@Test fun missingKeyIsDisabled()      // key_RED.assertIsNotEnabled() bei keys ohne RED
@Test fun tapSendsKey()               // performClick auf key_MUTE -> Fake-Handler bekam press("MUTE")
@Test fun noIrBannerShownWhenUnavailable()
```

- [ ] **Step 2: FAIL** → **Step 3: implementieren** → **Step 4: PASS** (`--tests "de.hotelremote.ui.remote.*"`)
- [ ] **Step 5: Commit** – `app: remote control screen`

---

### Task 15: Wizard-Bildschirme und Suchlauf-Takt

**Files:**
- Create: `app/src/main/java/de/hotelremote/wizard/ScanRunner.kt`, `app/src/main/java/de/hotelremote/ui/wizard/WizardScreen.kt`
- Modify: `strings.xml`
- Test: `app/src/test/java/de/hotelremote/wizard/ScanRunnerTest.kt`

**Interfaces:**
- Consumes: `WizardMachine`, `WizardState`, `SignalSender`, `Brand`
- Produces:
  - `class ScanRunner(machine: WizardMachine, send: (IrSignal) -> Unit, scope: CoroutineScope, intervalMs: Long = 1200)` mit:
    - `fun start()`: sendet in Schleife `currentScanSignal()`, wartet `intervalMs`, ruft `scanAdvance()` auf, bis der Zustand nicht mehr `Scanning` ist
    - `fun stop()`: bricht die Schleife ab und ruft `machine.stopScan()` auf
    - `val running: StateFlow<Boolean>`
  - `@Composable fun WizardScreen(state: WizardState, brands: List<Brand>, onPickBrand: (Long) -> Unit, onStartScan: () -> Unit, onTest: () -> Unit, onAnswer: (Boolean) -> Unit, onTvOff: () -> Unit, onStopScan: () -> Unit, onResumeScan: (Int) -> Unit, onSave: (name: String?, brandName: String) -> Unit, onClose: () -> Unit)`

**Bildschirme** wie Mockup `wizard.html`. Die Texte gehören exakt in `strings.xml`. Pro Zustand:

- **PickBrand:**
  - Titel „Welche Marke steht auf dem Fernseher?“
  - Suchfeld „Marke suchen …“ (filtert ohne Groß/Klein)
  - Abschnitt „HÄUFIG IN HOTELS“ (Marken mit `hotelRank`), dann „ALLE MARKEN“
  - Jede Zeile zeigt rechts „<n> Codes“
  - Unten der Button „Marke unbekannt → Suchlauf“
- **TestCode:**
  - Kopf „<Marke> · Code <index+1> von <total>“ mit Fortschrittsbalken
  - Titel „Richte das Handy auf den Fernseher und tippe auf <Taste>.“ Tasten-Anzeige: MUTE → „MUTE“, VOL_DOWN → „VOL –“, POWER/POWER_ON → „⏻“.
  - Runder Testknopf (`onTest`)
  - Frage: „Wurde der Ton stumm geschaltet?“ bei MUTE, „Wurde es leiser?“ bei VOL_DOWN, „Ist der Fernseher an- oder ausgegangen?“ bei Power
  - Buttons „Nein, nächster“ / „Ja“
  - Link „Fernseher ist aus → mit ⏻ testen“ (`onTvOff`, nur sichtbar, solange der Test-Taster nicht Power ist)
- **Confirm:**
  - Titel „Zur Sicherheit: tippe auf <VOL + | MUTE>.“
  - Frage „Hat der Fernseher reagiert?“
  - „Nein“ / „Ja“
- **Scanning:**
  - Vor dem Start der Hinweis „Der Fernseher muss eingeschaltet sein.“ mit Button „Suchlauf starten“
  - Dann Titel „Halte das Handy auf den Fernseher gerichtet.“
  - Text „Die App sendet nacheinander „Ausschalten“ für jede Marke. Tippe auf Stopp, sobald der Fernseher ausgeht.“
  - Kreis „<position+1> von <total> · <brandLabel>“
  - Grüner Button „Stopp – hat reagiert!“
- **ScanVerify:**
  - Titel „Tippe auf ⏻. Ist der Fernseher wieder angegangen?“
  - Kopf „Kandidat <index+1> von <n>“
  - „Nein“ / „Ja“
- **TestInputs:**
  - Titel „Eingang direkt wählen?“
  - Text „Tippe auf HDMI 1. Wechselt der Fernseher direkt auf diesen Eingang?“
  - Blauer Knopf „HDMI 1“
  - „Nein“ / „Ja“
  - Fußzeile „Variante <i+1> von <n>. Bei „Nein“ bleibt nur SOURCE, wenn keine Variante passt.“
- **Save:**
  - Titel „Gefunden: <Marke>, <Code>“
  - Haken-Liste „Ton, Lautstärke“ ✔ und „HDMI direkt“ ✔/✕
  - Feld „MARKE“ (vorbelegt, änderbar)
  - Feld „NAME (OPTIONAL)“, Hinweistext „z. B. Hotel Adlon, Zi. 412“
  - Button „Zur Fernbedienung“
- **NoMatch:**
  - Titel „Kein passender Code gefunden.“
  - Buttons „Andere Marke wählen“, „Suchlauf starten“ und, wenn `canResumeScanAt != null`, „Suchlauf fortsetzen“

Systemzurück: `BackHandler { if (!machine.back()) onClose() }`.

- [ ] **Step 1: Failing tests** (`ScanRunnerTest`, `runTest`, Fake-`WizardData` mit 5 Scan-Einträgen)

```kotlin
@Test fun sendsOnePerInterval()           // start sendet sofort Position 0; advanceTimeBy(1200*3 + 1) -> genau 4 Signale, Positionen 0,1,2,3
@Test fun stopMovesToVerify()             // start, advanceTimeBy(2500), stop -> state is ScanVerify, keine weiteren Sendungen nach stop
@Test fun runsToEndAndStops()             // advanceUntilIdle -> 5 Sendungen, state NoMatch, running == false
```

- [ ] **Step 2: FAIL** → **Step 3: `ScanRunner` implementieren** → **Step 4: PASS**
- [ ] **Step 5: `WizardScreen` umsetzen** (ohne eigenen Test; geprüft in Task 17 auf dem Gerät)
- [ ] **Step 6: Bauen** – `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] **Step 7: Commit** – `app: wizard screens and scan runner`

---

### Task 16: Liste, Über-Seite, App-Zusammenbau

**Files:**
- Create: `app/src/main/java/de/hotelremote/ui/remotes/RemoteListScreen.kt`, `ui/about/AboutScreen.kt`, `ui/AppViewModel.kt`, `ui/App.kt`
- Modify: `MainActivity.kt`, `strings.xml`

**Interfaces:**
- Consumes: alles aus Tasks 10–15
- Produces:
  - `sealed interface Screen { Remote, RemoteList, Wizard, ExtraKeys, About }`
  - `class AppViewModel(app: Application) : AndroidViewModel(app)`. Das ViewModel hält:
    - `CodeRepository`, `RemoteStore(File(filesDir, "remotes.json"))`
    - `SignalSender(AndroidIrTransmitter, viewModelScope, Dispatchers.IO, onError = { message = send_failed })`
    - `WizardMachine`, `ScanRunner`
    - `StateFlow<AppUiState>` mit `screen`, `current: SavedRemote?`, `remotes`, `wizard: WizardState`, `irAvailable`, `brands`, `message: String?`
  - `@Composable fun App(vm: AppViewModel)`: wählt den Bildschirm. Fehlermeldungen erscheinen als Snackbar.

**Verhalten:**
- **Start:** `store.lastUsed()` vorhanden → `Remote`, sonst `Wizard` (frischer `WizardMachine`).
- **Wizard-`onSave`:**
  - `SavedRemote(id = UUID, name, brand = brandName, codeLabel, keys, lastUsed = now)` speichern.
  - Danach `current` = dieser Eintrag, `screen = Remote`.
  - `onClose` ohne gespeicherte Fernbedienung → zurück zu `PickBrand`, nicht schließen.
- **`onTest`** sendet `machine.testSignal()` über `SignalSender.press`.
- **`KeyHandler`** des Remote-Bildschirms schlägt `current.keys[key]` nach und ruft `press`/`hold` auf.
- **`onSwitch`:** `store.markUsed`, dann `current` wechseln.
- **RemoteListScreen:**
  - Titel „Meine Fernbedienungen“
  - Zeilen mit Titel und „<brand> · <codeLabel>“
  - Tippen → wechseln
  - Langes Drücken → Dialog „Umbenennen“ / „Löschen“. Löschen mit Bestätigung „Fernbedienung „<title>“ löschen?“.
  - Button „+ Neue Fernbedienung suchen“ → Wizard
  - Wird die aktuelle gelöscht: nächste aus der Liste, sonst Wizard.
- **AboutScreen:**
  - „Fernbedienung 1.0 – werbefrei, ohne Internet.“
  - Abschnitt „Code-Daten“:
    - „Flipper-IRDB (CC0-1.0), Stand <meta.flipper_commit, 7 Zeichen>“
    - Nur wenn `meta.irdb_commit` nicht leer, wörtlich: „Contains/accesses irdb by Simon Peter and contributors, used under permission. For licensing details and for information on how to contribute to the database, see https://github.com/probonopd/irdb“
    - „<code_sets> Code-Sätze, <brands> Marken“

- [ ] **Step 1: Umsetzen**
- [ ] **Step 2: Alle Tests und Build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: alle Tests grün, BUILD SUCCESSFUL.

- [ ] **Step 3: Berechtigungen erneut prüfen** (Befehl aus Task 9 Step 4) → kein INTERNET
- [ ] **Step 4: Commit** – `app: navigation, remote list, about screen`

---

### Task 17: Release-Build, Installation, Prüfung am Gerät

**Files:**
- Modify: `app/build.gradle.kts` (`signingConfigs.release`, liest `HOTELREMOTE_STORE_FILE`, `HOTELREMOTE_STORE_PASSWORD`, `HOTELREMOTE_KEY_ALIAS`, `HOTELREMOTE_KEY_PASSWORD` aus den Gradle-Properties. Fehlen sie, wird unsigniert gebaut, mit Warnung.)
- Create: `README.md`. Inhalt:
  - Daten neu bauen: Befehle aus Task 8
  - App bauen und installieren
  - irdb-Hinweis

**Interfaces:** keine neuen.

- [ ] **Step 1: Schlüssel anlegen** (außerhalb des Repos)

Run: `keytool -genkeypair -keystore ~/.android/hotelremote.jks -alias hotelremote -keyalg RSA -keysize 4096 -validity 36500 -dname "CN=Fernbedienung"`. Passwort zufällig erzeugen. Die vier Properties in `~/.gradle/gradle.properties` eintragen.
Expected: Datei existiert, nichts davon im Repo (`git status` sauber).

- [ ] **Step 2: Release bauen** – `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`. Berechtigungsprüfung aus Task 9 erneut ausführen.
- [ ] **Step 3: Installieren**, falls ein Gerät verbunden ist.

Run: `adb devices`, dann `adb install -r app/build/outputs/apk/release/app-release.apk`
Expected: `Success`. Ohne Gerät: APK-Pfad an den Nutzer melden.

- [ ] **Step 4: Prüfliste am Gerät** (vom Nutzer, mit einem echten Fernseher):
  1. Erster Start öffnet den Wizard.
  2. Die Marke des eigenen TVs wird gefunden, MUTE schaltet stumm.
  3. Bestätigungstest und Eingangs-Test laufen.
  4. Speichern führt zur Fernbedienung, HDMI-Reihe je nach Ergebnis.
  5. VOL halten zählt hoch und stoppt beim Loslassen.
  6. Die App neu starten öffnet die letzte Fernbedienung.
  7. Der Suchlauf schaltet den TV aus, der Stopp mit Kandidatenprüfung schaltet ihn wieder an.
  8. Flugmodus an: Alles funktioniert unverändert.
- [ ] **Step 5: Commit** – `release signing config and README`

---

## Selbstprüfung (erledigt)

- **Spec-Abdeckung:**
  - Ziele: Offline (keine INTERNET-Berechtigung, DB im APK), alle IR-Handys (`ConsumerIrManager`, minSdk 23), Eingänge (Varianten, HDMI-Reihe), Wizard inkl. Suchlauf, Optik A2.
  - Anti-Ziele: keine Werbung/Tracking, kein Hotelmodus, kein Power-Test als Standard.
  - Gespeicherte Kopie der Signale: Task 12. Quellenangabe: Task 16. Hersteller-Familien und Hotel-Liste: Task 5/6.
  - Zusatztasten: Task 14. Ein/Aus kurz/lang: Task 14. Gedrückt halten und Toggle: Task 10.
- **Bekannte Lücke:** Das Spec nennt LIRC als „außen vor“. Ein Task dafür ist nicht nötig.
- **Typen geprüft:** `IrSignal`, `WizardData`, `ScanEntry`, `SavedRemote`, `KeyHandler` sind in allen Tasks gleich benannt.
