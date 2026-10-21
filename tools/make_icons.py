"""Generuje ikonę OpenMail (koperta z plakietką) we wszystkich rozmiarach.

Uruchomienie z katalogu repozytorium:  python tools/make_icons.py
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app" / "src" / "main" / "res"
S = 2048  # rysujemy w dużej rozdzielczości i zmniejszamy


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def gradient(size, top, bottom, diagonal=False):
    w, h = size
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    t = (xs / w) * 0.35 + (ys / h) * 0.65 if diagonal else ys / h
    t = np.clip(t, 0, 1)[..., None]
    a = np.array(top, np.float32)
    b = np.array(bottom, np.float32)
    return Image.fromarray((a + (b - a) * t).astype(np.uint8), "RGBA")


def mask_rounded(size, box, radius):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle(box, radius=radius, fill=255)
    return m


def mask_poly(size, pts):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m


def paste(base, layer, mask):
    base.alpha_composite(Image.composite(layer, Image.new("RGBA", base.size, (0, 0, 0, 0)), mask))


def envelope(scale=1.0):
    """Pełna ikona na przezroczystym tle (S×S)."""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cx, cy = S / 2, S / 2 + 40 * scale
    w, h = 1560 * scale, 1080 * scale
    l, t, r, b = cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2
    rad = int(150 * scale)

    # Cień pod kopertą.
    sh = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(sh).rounded_rectangle((l + 20, t + 70, r - 20, b + 60), radius=rad, fill=(0, 0, 0, 150))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(50 * scale)))

    body = mask_rounded((S, S), (l, t, r, b), rad)
    paste(img, gradient((S, S), (88, 156, 255, 255), (64, 60, 220, 255), diagonal=True), body)

    # Dolne skrzydła koperty (ciemniejsze trójkąty od rogów do środka).
    mid = (cx, cy + 60 * scale)
    left_wing = mask_poly((S, S), [(l, b), (l, t + 120 * scale), mid])
    right_wing = mask_poly((S, S), [(r, b), (r, t + 120 * scale), mid])
    bottom = mask_poly((S, S), [(l, b), (r, b), mid])
    paste(img, gradient((S, S), (70, 120, 245, 255), (52, 54, 196, 255)), ImageChops.multiply(left_wing, body))
    paste(img, gradient((S, S), (78, 110, 240, 255), (60, 50, 190, 255)), ImageChops.multiply(right_wing, body))
    paste(img, gradient((S, S), (96, 150, 255, 255), (74, 70, 230, 255)), ImageChops.multiply(bottom, body))

    # Linie zagięć.
    crease = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(crease).line([(l, b), mid, (r, b)], fill=(255, 255, 255, 70), width=int(12 * scale), joint="curve")
    img.alpha_composite(Image.composite(crease, Image.new("RGBA", (S, S), (0, 0, 0, 0)), body))

    # Górna klapa – jasna, z połyskiem.
    flap_tip = (cx, cy + 150 * scale)
    flap = mask_poly((S, S), [(l, t), (r, t), flap_tip])
    flap = ImageChops.multiply(flap, body)
    flap_shadow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(flap_shadow).polygon([(l, t + 30 * scale), (r, t + 30 * scale), (cx, cy + 210 * scale)], fill=(10, 10, 60, 120))
    flap_shadow = flap_shadow.filter(ImageFilter.GaussianBlur(30 * scale))
    fs = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    fs.alpha_composite(flap_shadow)
    img.alpha_composite(Image.composite(fs, Image.new("RGBA", (S, S), (0, 0, 0, 0)), body))
    paste(img, gradient((S, S), (255, 255, 255, 255), (214, 226, 255, 255)), flap)


    # Plakietka nowej wiadomości.
    bx, by, br = r - 90 * scale, t + 40 * scale, 250 * scale
    sh2 = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(sh2).ellipse((bx - br + 10, by - br + 40, bx + br + 10, by + br + 40), fill=(0, 0, 0, 140))
    img.alpha_composite(sh2.filter(ImageFilter.GaussianBlur(36 * scale)))
    badge = Image.new("L", (S, S), 0)
    ImageDraw.Draw(badge).ellipse((bx - br, by - br, bx + br, by + br), fill=255)
    paste(img, gradient((S, S), (255, 120, 90, 255), (232, 30, 60, 255)), badge)
    ring = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(ring).ellipse((bx - br, by - br, bx + br, by + br), outline=(255, 255, 255, 255), width=int(34 * scale))
    img.alpha_composite(ring)
    hl = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(hl).ellipse((bx - br * 0.62, by - br * 0.78, bx + br * 0.5, by - br * 0.12), fill=(255, 255, 255, 110))
    img.alpha_composite(hl.filter(ImageFilter.GaussianBlur(14 * scale)))
    return img


def fit(img, size, content):
    """Zmniejsza ikonę tak, by zajmowała [content] części kwadratu [size]."""
    bbox = img.getbbox()
    crop = img.crop(bbox)
    side = max(crop.size)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.alpha_composite(crop, ((side - crop.size[0]) // 2, (side - crop.size[1]) // 2))
    inner = max(1, int(size * content))
    sq = sq.resize((inner, inner), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.alpha_composite(sq, ((size - inner) // 2, (size - inner) // 2))
    return out


def monochrome(img):
    a = img.getchannel("A").point(lambda v: 255 if v > 90 else 0)
    out = Image.new("RGBA", img.size, (255, 255, 255, 0))
    out.putalpha(a)
    return out


def main():
    icon = envelope()
    (ROOT / "artwork").mkdir(exist_ok=True)
    fit(icon, 1254, 0.86).save(ROOT / "artwork" / "app-icon.png")
    nodpi = RES / "drawable-nodpi"
    nodpi.mkdir(parents=True, exist_ok=True)
    fit(icon, 512, 0.94).save(nodpi / "openmail_mark.png")
    # Ikona adaptacyjna: treść w strefie bezpiecznej (ok. 2/3 środka).
    fit(icon, 432, 0.62).save(nodpi / "brand_launcher.png")
    fit(icon, 432, 0.62).save(nodpi / "ic_launcher_foreground.png")
    monochrome(fit(icon, 432, 0.62)).save(nodpi / "brand_monochrome.png")
    for name, px in {"ldpi": 36, "mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        d = RES / f"mipmap-{name}"
        d.mkdir(parents=True, exist_ok=True)
        m = fit(icon, px, 0.92)
        m.save(d / "ic_launcher.png")
        m.save(d / "ic_launcher_round.png")


if __name__ == "__main__":
    main()
