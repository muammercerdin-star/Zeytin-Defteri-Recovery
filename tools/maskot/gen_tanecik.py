#!/usr/bin/env python3
"""Tanecik (Zeytin Defteri maskotu) çizimlerini üretir.

Tek kaynaktan iki çıktı:
  - app/src/main/res/drawable/tanecik_<poz>.xml  (Android VectorDrawable, ek kütüphane yok, minSdk 24)
  - tools/maskot/onizleme/<poz>.svg              (gözle kontrol için)

Kullanım: python3 tools/maskot/gen_tanecik.py
Çizim tamamen özgündür. Düz renk kullanılır (gradyan yok): küçük boyutta da net görünür.
"""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
VW, VH = 240, 270          # görünüm kutusu
OX, OY = 120, 142          # karakter merkezi

# Renkler
BODY = "#86AD34"
BODY_SHADE = "#5E8A1E"
BODY_LIGHT = "#B2D25C"
LIMB = "#3E5A14"
HAND = "#5E8A1E"
LEAF = "#71895F"
LEAF_VEIN = "#C9D4B5"
STEM = "#6B4F2A"
CAP = "#5A4A3A"
CAP_DARK = "#3A2F25"
CAP_LINE = "#7D6A55"
EYE = "#1E2814"
WHITE = "#FFFFFF"
CHEEK = "#F08A7A"
MOUTH = "#5A1E1A"
TONGUE = "#E86C6C"
SPARK = "#F2C94C"


def ellipse(cx, cy, rx, ry):
    return (f"M{cx - rx},{cy} a{rx},{ry} 0 1,0 {2 * rx},0 a{rx},{ry} 0 1,0 {-2 * rx},0 z")


def fill(d, color, alpha=1.0):
    return ("fill", d, color, alpha, 0)


def stroke(d, color, width, alpha=1.0):
    return ("stroke", d, color, alpha, width)


STAND_LEGS = (("M-22,78 q-2,22 -6,34", -31, 113), ("M22,78 q2,22 6,34", 31, 113))
# Yürüme: sağa bakar (kasketin siperi sağda); ön bacak sağda.
STEP_LEGS = (("M-22,78 q-8,18 -20,30", -44, 110), ("M22,78 q12,16 26,28", 54, 108))
PASS_LEGS = (("M-14,80 q-2,22 -2,34", -16, 115), ("M14,80 q2,22 6,34", 22, 115))


def legs(spec):
    out = []
    for d, fx, fy in spec:
        out.append(stroke(d, LIMB, 7))
    for d, fx, fy in spec:
        out.append(fill(ellipse(fx, fy, 13, 6), LIMB))
    return out


def base(leg_spec=STAND_LEGS):
    """Gövde, bacaklar, yanaklar: her pozda aynı (yapraklar kasketle birlikte çizilir)."""
    return [fill(ellipse(0, 118, 56, 8), "#000000", 0.22)] + legs(leg_spec) + [
        fill(ellipse(0, 0, 72, 90), BODY_SHADE),
        fill(ellipse(-6, -6, 64, 82), BODY),
        fill(ellipse(-18, -22, 40, 50), BODY_LIGHT, 0.45),
        fill(ellipse(-32, -44, 13, 22), WHITE, 0.30),
        fill(ellipse(-40, 14, 13, 8), CHEEK, 0.55),
        fill(ellipse(40, 14, 13, 8), CHEEK, 0.55),
    ]


def cap():
    """Düz köy kasketi (yapraklar kasketin üstünden çıkar)."""
    return [
        fill("M-66,-44 q-4,-34 30,-46 q56,-14 92,20 q10,10 6,22 q-64,-10 -128,4 z", CAP),
        stroke("M-60,-60 q50,-22 104,2", CAP_LINE, 2, 0.7),
        fill("M52,-52 q30,0 44,12 q-26,8 -50,2 z", CAP_DARK),
        stroke("M-66,-44 q64,-14 128,-4", "#2E251D", 3),
        # yapraklar kasketin deliğinden çıkıyor
        stroke("M0,-86 q2,-14 8,-22", STEM, 6),
        fill("M8,-108 q34,-24 66,-8 q-30,22 -66,8 z", LEAF),
        stroke("M10,-108 q28,-10 58,-8", LEAF_VEIN, 2, 0.8),
        fill("M6,-106 q-30,-30 -58,-22 q22,28 58,22 z", LEAF),
        stroke("M4,-107 q-24,-16 -50,-20", LEAF_VEIN, 2, 0.8),
    ]


def open_eyes(dy=0):
    return [
        fill(ellipse(-24, -10, 10, 14), EYE),
        fill(ellipse(24, -10, 10, 14), EYE),
        fill(ellipse(-20, -16 + dy, 4, 4), WHITE),
        fill(ellipse(28, -16 + dy, 4, 4), WHITE),
    ]


def arm(d, hx, hy):
    return [stroke(d, LIMB, 8), fill(ellipse(hx, hy, 11, 11), HAND)]


def spark(x, y, s):
    a, b = round(s * .36, 2), round(s * .64, 2)
    return fill(f"M{x},{y - s} l{a},{b} {b},{a} {-b},{a} {-a},{b} {-a},{-b} {-b},{-a} {b},{-a} z", SPARK)


def pose(name):
    back, front = [], []
    leg_spec = STAND_LEGS
    if name in ("duruyor", "duruyor_kirp"):
        back += arm("M-66,14 q-24,18 -26,44", -92, 60)
        back += arm("M66,14 q24,18 26,44", 92, 60)
        if name == "duruyor":
            front += open_eyes()
        else:  # göz kırpma karesi
            front += [stroke("M-34,-8 q10,6 20,0", EYE, 5), stroke("M14,-8 q10,6 20,0", EYE, 5)]
        front += [stroke("M-16,24 q16,16 32,0", EYE, 5)]
    elif name in ("yuruyor_1", "yuruyor_2"):
        first = name == "yuruyor_1"
        leg_spec = STEP_LEGS if first else PASS_LEGS
        back += arm("M-66,14 q-20,20 -12,46" if first else "M-66,14 q-26,16 -34,40", -78 if first else -100, 62 if first else 56)
        back += arm("M66,14 q26,16 34,40" if first else "M66,14 q20,20 12,46", 100 if first else 78, 56 if first else 62)
        front += [
            fill(ellipse(-20, -10, 10, 14), EYE), fill(ellipse(28, -10, 10, 14), EYE),
            fill(ellipse(-14, -16, 4, 4), WHITE), fill(ellipse(34, -16, 4, 4), WHITE),
            stroke("M-10,24 q16,14 32,0", EYE, 5),
        ]
    elif name == "uyuyor":
        back += [stroke("M76,-74 l12,0 -12,14 12,0", WHITE, 3, 0.85),
                 stroke("M92,-100 l15,0 -15,18 15,0", WHITE, 3, 0.85)]
        back += arm("M-66,14 q-24,18 -26,44", -92, 60)
        back += arm("M66,14 q24,18 26,44", 92, 60)
        front += [
            stroke("M-34,-8 q10,8 20,0", EYE, 5),
            stroke("M14,-8 q10,8 20,0", EYE, 5),
            fill(ellipse(0, 30, 7, 6), MOUTH),
        ]
    elif name == "merhaba":
        back += arm("M-66,10 q-30,-20 -34,-62", -100, -56)
        back += arm("M66,14 q24,18 26,44", 92, 60)
        front += open_eyes() + [stroke("M-20,22 q20,22 40,0", EYE, 5)]
    elif name == "sevinc":
        back += [spark(-96, -96, 16), spark(102, -66, 12), spark(92, -126, 9)]
        back += arm("M-64,-4 q-30,-30 -30,-70", -94, -76)
        back += arm("M64,-4 q30,-30 30,-70", 94, -76)
        front += [
            stroke("M-34,-8 q10,-14 20,0", EYE, 5),
            stroke("M14,-8 q10,-14 20,0", EYE, 5),
            fill("M-24,16 q24,34 48,0 z", MOUTH),
            fill("M-12,26 q12,10 24,0 q-12,-6 -24,0 z", TONGUE),
        ]
    elif name in ("konusuyor", "konusuyor_kapali"):
        back += arm("M-66,14 q-24,18 -26,44", -92, 60)
        back += arm("M66,6 q26,-6 34,-30", 100, -28)
        front += open_eyes()
        if name == "konusuyor":
            front += [fill(ellipse(0, 28, 15, 13), MOUTH), fill(ellipse(0, 34, 9, 5), TONGUE)]
        else:  # konuşurken ağzın kapalı karesi (ağız oynatma için)
            front += [fill(ellipse(0, 28, 13, 5), MOUTH)]
    elif name == "dusunuyor":
        back += [fill(ellipse(88, -88, 5, 5), WHITE, 0.9), fill(ellipse(100, -106, 8, 8), WHITE, 0.9),
                 fill(ellipse(106, -126, 11, 11), WHITE, 0.9)]
        back += arm("M-66,14 q-24,18 -26,44", -92, 60)
        front += [
            fill(ellipse(-24, -10, 10, 14), EYE), fill(ellipse(24, -10, 10, 14), EYE),
            fill(ellipse(-20, -19, 4, 4), WHITE), fill(ellipse(28, -19, 4, 4), WHITE),
            stroke("M-30,-34 l18,-4", EYE, 4), stroke("M12,-38 l18,4", EYE, 4),
            stroke("M-12,30 q12,-6 24,2", EYE, 5),
            stroke("M62,34 q18,26 -14,30 q-12,0 -20,-10", LIMB, 8),
            fill(ellipse(26, 50, 11, 11), HAND),
        ]
    else:
        raise ValueError(name)
    return back + base(leg_spec) + cap() + front


POSES = ["merhaba", "sevinc", "konusuyor", "konusuyor_kapali", "dusunuyor",
         "duruyor", "duruyor_kirp", "yuruyor_1", "yuruyor_2", "uyuyor"]

SCARF = "#C0392B"
SCARF_DARK = "#8E2A20"
UMBRELLA = "#2E86C1"
UMBRELLA_DARK = "#1F5F8B"


def accessory(name):
    """Her pozun üstüne binen katman (aynı görünüm kutusu): soğukta atkı, yağmurda şemsiye."""
    if name == "atki":
        return [
            fill("M-40,58 l-12,40 l16,3 l8,-40 z", SCARF),
            stroke("M-50,90 l14,3", SCARF_DARK, 3),
            fill("M-58,48 q58,20 116,0 l-4,20 q-54,18 -108,0 z", SCARF),
            stroke("M-40,56 l-3,18", SCARF_DARK, 3),
            stroke("M0,62 l0,18", SCARF_DARK, 3),
            stroke("M40,56 l3,18", SCARF_DARK, 3),
        ]
    if name == "semsiye":
        return [
            stroke("M60,-104 l32,150 q2,10 -8,10", "#5A4A3A", 5),
            fill("M2,-96 q58,-64 116,0 q-10,-9 -19,0 q-10,-9 -19,0 q-10,-9 -20,0 q-10,-9 -19,0 q-10,-9 -19,0 q-10,-9 -20,0 z", UMBRELLA),
            stroke("M60,-128 q-16,12 -22,32", UMBRELLA_DARK, 2, 0.8),
            stroke("M60,-128 q16,12 22,32", UMBRELLA_DARK, 2, 0.8),
            fill(ellipse(60, -130, 4, 4), UMBRELLA_DARK),
        ]
    raise ValueError(name)


ACCESSORIES = ["atki", "semsiye"]


def to_vector(items):
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           '<!-- tools/maskot/gen_tanecik.py ile üretildi; elle değiştirme. -->',
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           f'    android:width="{VW}dp" android:height="{VH}dp"',
           f'    android:viewportWidth="{VW}" android:viewportHeight="{VH}">',
           f'    <group android:translateX="{OX}" android:translateY="{OY}">']
    for kind, d, color, alpha, width in items:
        if kind == "fill":
            out.append(f'        <path android:pathData="{d}" android:fillColor="{color}"'
                       + (f' android:fillAlpha="{alpha}"' if alpha < 1 else '') + '/>')
        else:
            out.append(f'        <path android:pathData="{d}" android:strokeColor="{color}"'
                       f' android:strokeWidth="{width}" android:strokeLineCap="round" android:strokeLineJoin="round"'
                       + (f' android:strokeAlpha="{alpha}"' if alpha < 1 else '') + '/>')
    out += ['    </group>', '</vector>', '']
    return "\n".join(out)


def to_svg(items):
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{VW}" height="{VH}" viewBox="0 0 {VW} {VH}">',
           f'<g transform="translate({OX} {OY})">']
    for kind, d, color, alpha, width in items:
        if kind == "fill":
            out.append(f'<path d="{d}" fill="{color}" fill-opacity="{alpha}"/>')
        else:
            out.append(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="{width}" '
                       f'stroke-linecap="round" stroke-linejoin="round" stroke-opacity="{alpha}"/>')
    out += ['</g>', '</svg>', '']
    return "\n".join(out)


def main():
    res = ROOT / "app/src/main/res/drawable"
    prev = ROOT / "tools/maskot/onizleme"
    prev.mkdir(parents=True, exist_ok=True)
    for p in ACCESSORIES:
        items = accessory(p)
        (res / f"tanecik_{p}.xml").write_text(to_vector(items), encoding="utf-8")
        (prev / f"{p}.svg").write_text(to_svg(items), encoding="utf-8")
    for p in POSES:
        items = pose(p)
        (res / f"tanecik_{p}.xml").write_text(to_vector(items), encoding="utf-8")
        (prev / f"{p}.svg").write_text(to_svg(items), encoding="utf-8")
    print("üretildi:", ", ".join(POSES))


if __name__ == "__main__":
    main()
