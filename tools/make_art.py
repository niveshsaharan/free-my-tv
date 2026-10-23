"""Draws the app's icon and TV banner from brand/ (Pillow). Usage: python3 tools/make_art.py

Reads the name and the accent from brand/brand.properties and the logo from brand/logo.png (a transparent PNG; one
color reads best). Without a logo it draws Lumen's: a disc, its left half filled and its right half a ring (day and
night), in the accent. Writes brand/icon.png and brand/banner.png, which build.sh puts in the app, the same two
into app/res/drawable-nodpi (what a build without brand/ shows), and the LG and Samsung icons (webos/, tizen/).
"""
import os
import shutil

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "app/assets/fonts/InterVariable.ttf")
BRAND = os.path.join(ROOT, "brand")
RES = os.path.join(ROOT, "app/res/drawable-nodpi")


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


def brand_props():
    props = {}
    path = os.path.join(BRAND, "brand.properties")
    if not os.path.exists(path):
        return props
    with open(path, encoding="utf-8") as f:
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
    """Lumen's logo: a disc, its left half filled and its right half a ring (day and night)."""
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


def icon(logo, accent, size=192):
    s = size * 4
    img = shaded(s, s, accent).convert("RGBA")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, s - 1, s - 1), radius=int(s * 0.23), fill=255)
    img.putalpha(mask)
    m = silhouette(logo, int(s * 0.58), (255, 255, 255))
    img.alpha_composite(m, ((s - m.width) // 2, (s - m.height) // 2))
    return img.resize((size, size), Image.LANCZOS)


def banner(logo, name, accent, w=640, h=360):
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


if __name__ == "__main__":
    props = brand_props()
    name = props.get("name", "").strip() or "Lumen"
    accent = rgb(props.get("accent", "").strip() or "#0A84FF")
    os.makedirs(BRAND, exist_ok=True)
    logo_path = os.path.join(BRAND, "logo.png")
    if not os.path.exists(logo_path):
        day_night(512, accent).save(logo_path)
        print("drew brand/logo.png")
    logo = Image.open(logo_path).convert("RGBA")
    icon(logo, accent).save(os.path.join(BRAND, "icon.png"))
    banner(logo, name, accent).save(os.path.join(BRAND, "banner.png"))
    os.makedirs(RES, exist_ok=True)
    for image in ("icon.png", "banner.png"):
        shutil.copyfile(os.path.join(BRAND, image), os.path.join(RES, image))
    for path, size in (("webos/icon.png", 80), ("webos/largeIcon.png", 130), ("tizen/icon.png", 512)):
        icon(logo, accent, size).save(os.path.join(ROOT, path))
    print(name + ": brand/icon.png and brand/banner.png (also in app/res/drawable-nodpi), and the LG and Samsung icons")
