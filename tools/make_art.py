"""Draws the app icon, the TV banner and the site's images (Pillow).

    python3 tools/make_art.py            # Free My TV's own icon, banner and site images
    python3 tools/make_art.py --brand    # your brand's icon and banner, from brand/brand.properties and brand/logo.png
                                         # (drawn too when there is none: a day/night disc in the accent color)
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app/assets/fonts/InterVariable.ttf")
STOPS = [(0.0, (255, 194, 122)), (0.5, (255, 122, 158)), (1.0, (154, 140, 255))]   # sunrise -> pink -> dusk


def gradient(w, h, angle=35):
    """Diagonal three-color gradient."""
    img = Image.new("RGB", (w, h))
    px = img.load()
    a = math.radians(angle)
    dx, dy = math.cos(a), math.sin(a)
    lo = min(0, w * dx, h * dy, w * dx + h * dy)
    hi = max(0, w * dx, h * dy, w * dx + h * dy)
    for y in range(h):
        for x in range(w):
            t = ((x * dx + y * dy) - lo) / (hi - lo)
            for i in range(len(STOPS) - 1):
                t0, c0 = STOPS[i]
                t1, c1 = STOPS[i + 1]
                if t <= t1:
                    f = (t - t0) / (t1 - t0)
                    px[x, y] = tuple(round(c0[k] + (c1[k] - c0[k]) * f) for k in range(3))
                    break
    return img


def sparkle(draw, cx, cy, r, fill):
    """Four-point star."""
    pts = []
    for i in range(8):
        ang = math.pi / 2 * (i // 2) + (0 if i % 2 == 0 else math.pi / 4)
        rad = r if i % 2 == 0 else r * 0.26
        pts.append((cx + rad * math.cos(ang - math.pi / 2), cy + rad * math.sin(ang - math.pi / 2)))
    draw.polygon(pts, fill=fill)


def mark(size, bg=True):
    """The mark: a TV with a sparkle, on the gradient squircle (bg) or alone (white)."""
    s = size * 4
    img = gradient(s, s).convert("RGBA") if bg else Image.new("RGBA", (s, s), (0, 0, 0, 0))
    if bg:
        mask = Image.new("L", (s, s), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * 0.23), fill=255)
        img.putalpha(mask)
    layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    white = (255, 255, 255, 255)
    # screen
    x0, y0, x1, y1 = s * 0.2, s * 0.27, s * 0.8, s * 0.66
    d.rounded_rectangle((x0, y0, x1, y1), radius=int(s * 0.07), outline=white, width=int(s * 0.055))
    # stand
    d.rounded_rectangle((s * 0.36, s * 0.715, s * 0.64, s * 0.765), radius=int(s * 0.025), fill=white)
    # sparkle inside the screen
    sparkle(d, s * 0.5, s * 0.465, s * 0.12, white)
    sparkle(d, s * 0.655, s * 0.355, s * 0.045, white)
    if bg:
        shadow = layer.copy().filter(ImageFilter.GaussianBlur(s * 0.02))
        tint = Image.new("RGBA", (s, s), (120, 40, 80, 0))
        tint.putalpha(shadow.getchannel("A").point(lambda v: int(v * 0.22)))
        img.alpha_composite(tint, (0, int(s * 0.012)))
    img.alpha_composite(layer)
    return img.resize((size, size), Image.LANCZOS)


def font(px, weight=600):
    f = ImageFont.truetype(FONT, px)
    try:
        axes = f.get_variation_axes()
        values = []
        for a in axes:
            name = a["name"] if isinstance(a["name"], str) else a["name"].decode()
            values.append(weight if "eight" in name else 32 if "ptical" in name else a.get("default", 0))
        f.set_variation_by_axes(values)
    except Exception:
        pass
    return f


def banner(w=640, h=360):
    s = 4
    img = gradient(w * s, h * s, angle=20).convert("RGBA")
    m = mark(int(h * 0.5 * s), bg=False)
    d = ImageDraw.Draw(img)
    f = font(int(h * 0.15 * s), 650)
    text = "Free My TV"
    box = d.textbbox((0, 0), text, font=f)
    gap = int(w * 0.02 * s)
    total = m.width + gap + (box[2] - box[0])
    x = (w * s - total) // 2
    img.alpha_composite(m, (x, int((h * s - m.height) / 2)))
    y = (h * s - (box[3] - box[1])) // 2 - box[1]
    d.text((x + m.width + gap - box[0], y), text, font=f, fill=(255, 255, 255, 255))
    return img.resize((w, h), Image.LANCZOS)


# MARK: - Your brand (brand/, read by build.sh)

BRAND = os.path.join(ROOT, "brand")


def brand_props():
    props = {}
    with open(os.path.join(BRAND, "brand.properties"), encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key.strip()] = value.strip()
    return props


def rgb(hex_color):
    h = hex_color.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, f):
    return tuple(round(a[i] + (b[i] - a[i]) * f) for i in range(3))


def shaded(w, h, color):
    """The accent, a little lighter top left and darker bottom right."""
    top, bottom = mix(color, (255, 255, 255), 0.16), mix(color, (0, 0, 0), 0.2)
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        for x in range(w):
            px[x, y] = mix(top, bottom, (x / w * 0.35 + y / h * 0.65))
    return img


def day_night(size, color):
    """The generated logo: a disc, its left half filled and its right half a ring (day and night)."""
    s = size * 4
    c, r = s / 2, s * 0.4
    inner = r - s * 0.075
    mask = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(mask)
    d.ellipse((c - r, c - r, c + r, c + r), fill=255)
    d.pieslice((c - inner, c - inner, c + inner, c + inner), -90, 90, fill=0)
    img = Image.new("RGBA", (s, s), color + (255,))
    img.putalpha(mask)
    return img.resize((size, size), Image.LANCZOS)


def silhouette(logo, size, color):
    """The logo's shape in one color, fitted into size × size (the app tints it the same way)."""
    fitted = logo.copy()
    fitted.thumbnail((size, size), Image.LANCZOS)
    out = Image.new("RGBA", fitted.size, color + (0,))
    out.putalpha(fitted.getchannel("A"))
    return out


def brand_icon(logo, accent, size=192):
    s = size * 4
    img = shaded(s, s, accent).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * 0.23), fill=255)
    img.putalpha(mask)
    m = silhouette(logo, int(s * 0.58), (255, 255, 255))
    img.alpha_composite(m, ((s - m.width) // 2, (s - m.height) // 2))
    return img.resize((size, size), Image.LANCZOS)


def brand_banner(logo, name, accent, w=640, h=360):
    s = 4
    img = shaded(w * s, h * s, accent).convert("RGBA")
    m = silhouette(logo, int(h * 0.36 * s), (255, 255, 255))
    d = ImageDraw.Draw(img)
    f = font(int(h * 0.17 * s), 650)
    box = d.textbbox((0, 0), name, font=f)
    gap = int(w * 0.035 * s)
    total = m.width + gap + (box[2] - box[0])
    if total > w * s * 0.86:    # a long name: smaller text, so it fits
        f = font(int(h * 0.17 * s * (w * s * 0.86 - m.width - gap) / (box[2] - box[0])), 650)
        box = d.textbbox((0, 0), name, font=f)
        total = m.width + gap + (box[2] - box[0])
    x = (w * s - total) // 2
    img.alpha_composite(m, (x, (h * s - m.height) // 2))
    y = (h * s - (box[3] - box[1])) // 2 - box[1]
    d.text((x + m.width + gap - box[0], y), name, font=f, fill=(255, 255, 255, 255))
    return img.resize((w, h), Image.LANCZOS)


def make_brand():
    props = brand_props()
    name = props.get("name", "").strip() or "Free My TV"
    accent = rgb(props.get("accent", "#0A84FF"))
    logo_path = os.path.join(BRAND, "logo.png")
    if not os.path.exists(logo_path):
        day_night(512, accent).save(logo_path)
        print("drew brand/logo.png")
    logo = Image.open(logo_path).convert("RGBA")
    brand_icon(logo, accent).save(os.path.join(BRAND, "icon.png"))
    brand_banner(logo, name, accent).save(os.path.join(BRAND, "banner.png"))
    print("brand: " + name + " (icon.png, banner.png)")


if __name__ == "__main__" and "--brand" in sys.argv[1:]:
    make_brand()
elif __name__ == "__main__":
    res = os.path.join(ROOT, "app/res/drawable-nodpi")
    os.makedirs(res, exist_ok=True)
    mark(192).save(os.path.join(res, "icon.png"))
    banner().save(os.path.join(res, "banner.png"))
    site = os.path.join(ROOT, "docs/assets")
    os.makedirs(site, exist_ok=True)
    mark(512).save(os.path.join(site, "icon-512.png"))
    mark(180).save(os.path.join(site, "apple-touch-icon.png"))
    mark(64).save(os.path.join(site, "favicon-64.png"))
    print("ok")
