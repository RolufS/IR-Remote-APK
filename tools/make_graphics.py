"""Erzeugt App-Icon und Play-Store-Grafiken aus einer gemeinsamen Motiv-Beschreibung.

Ausgabe (alle Dateien werden überschrieben, nicht von Hand bearbeiten):
  app/src/main/res/drawable/ic_launcher_foreground.xml   Vektor-Vordergrund des adaptiven Icons
  app/src/main/res/mipmap-*/ic_launcher(.png|_round.png)  Legacy-Icons für API 23-25
  docs/play-store/icon-512.png                            Store-Icon 512x512
  docs/play-store/feature-graphic.png                     Store-Grafik 1024x500

Aufruf: tools/.venv/Scripts/python tools/make_graphics.py [--font-bold PFAD] [--font-regular PFAD]
Benötigt Pillow (tools/requirements-dev.txt). Schriften: Segoe UI (Windows), sonst DejaVu Sans.
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
STORE = ROOT / "docs" / "play-store"

# Farben aus ui/theme/Theme.kt (RemoteColors)
BACKGROUND = "#3A67B8"  # SourceTop: Hintergrund des adaptiven Icons
BODY = "#2E3035"  # BackgroundTop
SHADOW = "#1D1E21"  # BackgroundBottom
KEY = "#484B51"  # KeyTop
POWER = "#CF3B31"  # PowerTop
SOURCE = "#3A67B8"  # SourceTop
BEAM = "#ECEEF0"  # KeyText
GRADIENT = ("#2E3035", "#1D1E21")

# Motiv im 108x108-Raster des adaptiven Icons (sichtbar: Kreis mit Radius 33 um die Mitte).
# Formen: ("rect", x0, y0, x1, y1, radius, farbe) | ("circle", cx, cy, r, farbe)
#         ("arc", cx, cy, r, breite, farbe, alpha)  - Bogen von -135° bis -45° (nach oben)
SHAPES = [
    ("rect", 40, 39.5, 68, 85.5, 7, SHADOW),  # Schatten unter dem Gehäuse
    ("rect", 40, 38, 68, 84, 7, BODY),  # Gehäuse
    ("circle", 54, 46.5, 4.2, POWER),  # Ein/Aus
    ("rect", 44, 54, 50, 59, 1.5, KEY),
    ("rect", 51, 54, 57, 59, 1.5, KEY),
    ("rect", 58, 54, 64, 59, 1.5, KEY),
    ("rect", 44, 61.5, 50, 66.5, 1.5, KEY),
    ("rect", 51, 61.5, 57, 66.5, 1.5, KEY),
    ("rect", 58, 61.5, 64, 66.5, 1.5, KEY),
    ("rect", 44, 70.5, 64, 75.5, 1.5, SOURCE),  # SOURCE-Taste
    ("arc", 54, 36.5, 6, 2.4, BEAM, 1.0),  # IR-Strahl
    ("arc", 54, 36.5, 11, 2.4, BEAM, 0.55),
]

ARC_FROM, ARC_TO = -135, -45
LEGACY_SIZES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
LEGACY_VISIBLE = 80  # Legacy-Icons zeigen etwas mehr Rand, damit die runde Maske nichts abschneidet
SUPERSAMPLE = 4


def _num(v: float) -> str:
    return f"{v:.3f}".rstrip("0").rstrip(".")


def _rect_path(x0, y0, x1, y1, r) -> str:
    w, h = x1 - x0, y1 - y0
    n = _num
    return (
        f"M{n(x0 + r)},{n(y0)} h{n(w - 2 * r)} a{n(r)},{n(r)} 0 0,1 {n(r)},{n(r)} v{n(h - 2 * r)} "
        f"a{n(r)},{n(r)} 0 0,1 {n(-r)},{n(r)} h{n(-(w - 2 * r))} a{n(r)},{n(r)} 0 0,1 {n(-r)},{n(-r)} "
        f"v{n(-(h - 2 * r))} a{n(r)},{n(r)} 0 0,1 {n(r)},{n(-r)} z"
    )


def _arc_points(cx, cy, r):
    a0, a1 = math.radians(ARC_FROM), math.radians(ARC_TO)
    return (cx + r * math.cos(a0), cy + r * math.sin(a0)), (cx + r * math.cos(a1), cy + r * math.sin(a1))


def write_vector(path: Path) -> None:
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        "<!-- Erzeugt von tools/make_graphics.py, nicht von Hand bearbeiten. -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="108dp"',
        '    android:height="108dp"',
        '    android:viewportWidth="108"',
        '    android:viewportHeight="108">',
    ]
    for s in SHAPES:
        if s[0] == "rect":
            _, x0, y0, x1, y1, r, color = s
            lines.append(f'    <path android:fillColor="{color}" android:pathData="{_rect_path(x0, y0, x1, y1, r)}" />')
        elif s[0] == "circle":
            _, cx, cy, r, color = s
            d = f"M{_num(cx - r)},{_num(cy)} a{_num(r)},{_num(r)} 0 1,0 {_num(2 * r)},0 a{_num(r)},{_num(r)} 0 1,0 {_num(-2 * r)},0 z"
            lines.append(f'    <path android:fillColor="{color}" android:pathData="{d}" />')
        else:
            _, cx, cy, r, width, color, alpha = s
            (x0, y0), (x1, y1) = _arc_points(cx, cy, r)
            d = f"M{_num(x0)},{_num(y0)} A{_num(r)},{_num(r)} 0 0,1 {_num(x1)},{_num(y1)}"
            lines.append(
                f'    <path android:pathData="{d}" android:strokeColor="{color}" android:strokeAlpha="{_num(alpha)}"'
                f' android:strokeWidth="{_num(width)}" android:strokeLineCap="round" />'
            )
    lines.append("</vector>")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


def _rgba(color: str, alpha: float = 1.0):
    c = color.lstrip("#")
    return int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), round(alpha * 255)


def render_adaptive(px: int, background: str | None = BACKGROUND) -> Image.Image:
    """Volles 108er-Raster in px*px, ohne Maske (Hintergrund optional transparent)."""
    size = px * SUPERSAMPLE
    k = size / 108
    img = Image.new("RGBA", (size, size), _rgba(background) if background else (0, 0, 0, 0))
    for s in SHAPES:
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(layer)
        if s[0] == "rect":
            _, x0, y0, x1, y1, r, color = s
            d.rounded_rectangle([x0 * k, y0 * k, x1 * k, y1 * k], radius=r * k, fill=_rgba(color))
        elif s[0] == "circle":
            _, cx, cy, r, color = s
            d.ellipse([(cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k], fill=_rgba(color))
        else:
            _, cx, cy, r, width, color, alpha = s
            outer = (r + width / 2) * k
            full = _rgba(color)
            d.arc([cx * k - outer, cy * k - outer, cx * k + outer, cy * k + outer],
                  ARC_FROM, ARC_TO, fill=full, width=round(width * k))
            for x, y in _arc_points(cx, cy, r):
                cap = width / 2 * k
                d.ellipse([x * k - cap, y * k - cap, x * k + cap, y * k + cap], fill=full)
            if alpha < 1:
                a = layer.getchannel("A").point(lambda v: round(v * alpha))
                layer.putalpha(a)
        img = Image.alpha_composite(img, layer)
    return img.resize((px, px), Image.Resampling.LANCZOS)


def masked_icon(px: int, round_mask: bool, margin: float = 0.0, visible: float = 72) -> Image.Image:
    """Mittlerer Ausschnitt ([visible] von 108 Einheiten) mit Maske: Kreis oder abgerundetes Quadrat."""
    full_px = round(px * 108 / visible / (1 - 2 * margin))
    full = render_adaptive(full_px)
    inner = round(px * (1 - 2 * margin))
    off = (full_px - inner) // 2
    crop = full.crop((off, off, off + inner, off + inner))
    size = px * SUPERSAMPLE
    mask = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(mask)
    m = margin * size
    if round_mask:
        d.ellipse([m, m, size - m, size - m], fill=255)
    else:
        d.rounded_rectangle([m, m, size - m, size - m], radius=(size - 2 * m) * 0.18, fill=255)
    mask = mask.resize((px, px), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (px, px), (0, 0, 0, 0))
    out.paste(crop, (round(margin * px), round(margin * px)))
    out.putalpha(Image.composite(out.getchannel("A"), Image.new("L", out.size, 0), mask))
    return out


def write_legacy_mipmaps() -> None:
    for density, px in LEGACY_SIZES.items():
        folder = RES / f"mipmap-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        masked_icon(px, round_mask=False, margin=0.04, visible=LEGACY_VISIBLE).save(folder / "ic_launcher.png", optimize=True)
        masked_icon(px, round_mask=True, margin=0.04, visible=LEGACY_VISIBLE).save(folder / "ic_launcher_round.png", optimize=True)


def write_store_icon(path: Path) -> None:
    """512x512, volle Fläche ohne Transparenz (Play legt die Maske selbst an)."""
    full = render_adaptive(768)
    full.crop((128, 128, 640, 640)).convert("RGBA").save(path, optimize=True)


def _font(paths: list[str], size: int) -> ImageFont.FreeTypeFont:
    for p in paths:
        try:
            return ImageFont.truetype(p, size)
        except OSError:
            continue
    raise SystemExit(f"Keine Schrift gefunden: {paths}")


def write_feature_graphic(path: Path, bold: list[str], regular: list[str]) -> None:
    w, h = 1024, 500
    top, bottom = _rgba(GRADIENT[0]), _rgba(GRADIENT[1])
    img = Image.new("RGBA", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / (h - 1)
        d.line([(0, y), (w, y)], fill=tuple(round(a + (b - a) * t) for a, b in zip(top, bottom)))

    icon_px = 280
    icon_x, icon_y = 80, (h - icon_px) // 2
    img.alpha_composite(masked_icon(icon_px, round_mask=False), (icon_x, icon_y))

    text_x = icon_x + icon_px + 64
    max_w = w - text_x - 56
    title, subtitle = "Fernbedienung", "Werbefrei. Offline. Für Hotel-Fernseher."

    def fit(text: str, paths: list[str], start: int) -> ImageFont.FreeTypeFont:
        size = start
        while size > 10:
            f = _font(paths, size)
            if d.textlength(text, font=f) <= max_w:
                return f
            size -= 1
        return _font(paths, size)

    f_title = fit(title, bold, 88)
    f_sub = fit(subtitle, regular, 36)
    t_box = d.textbbox((0, 0), title, font=f_title)
    s_box = d.textbbox((0, 0), subtitle, font=f_sub)
    gap = 26
    block = (t_box[3] - t_box[1]) + gap + (s_box[3] - s_box[1])
    y0 = (h - block) // 2
    d.text((text_x, y0 - t_box[1]), title, font=f_title, fill=_rgba(BEAM))
    y1 = y0 + (t_box[3] - t_box[1]) + gap
    d.text((text_x, y1 - s_box[1]), subtitle, font=f_sub, fill=_rgba(BEAM, 0.75))
    # Akzentlinie in Ein/Aus-Rot zwischen Titel und Untertitel
    d.rounded_rectangle([text_x, y1 - gap // 2 - 2, text_x + 64, y1 - gap // 2 + 2], radius=2, fill=_rgba(POWER))
    img.convert("RGB").save(path, optimize=True)


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--font-bold", action="append", default=[])
    p.add_argument("--font-regular", action="append", default=[])
    a = p.parse_args()
    bold = a.font_bold + ["C:/Windows/Fonts/segoeuib.ttf", "DejaVuSans-Bold.ttf"]
    regular = a.font_regular + ["C:/Windows/Fonts/segoeui.ttf", "DejaVuSans.ttf"]

    write_vector(RES / "drawable" / "ic_launcher_foreground.xml")
    write_legacy_mipmaps()
    STORE.mkdir(parents=True, exist_ok=True)
    write_store_icon(STORE / "icon-512.png")
    write_feature_graphic(STORE / "feature-graphic.png", bold, regular)
    print("Icon und Store-Grafiken geschrieben.")


if __name__ == "__main__":
    main()
