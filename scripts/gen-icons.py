"""Generate favicon/PWA/og-image assets from src/assets/logo.png.

Run after changing the logo:  python scripts/gen-icons.py

These used to be missing entirely — the og:image pointed at a Lovable preview
screenshot on an R2 bucket we don't control, and there was no apple-touch-icon
at all, so iOS home-screen saves fell back to a blurry page snapshot.
"""

import os

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOGO = os.path.join(ROOT, "src", "assets", "logo.png")
PUB = os.path.join(ROOT, "public")

OBSIDIAN = (10, 10, 10, 255)  # --color-obsidian
EMBER = (240, 169, 104, 255)  # --color-ember

logo = Image.open(LOGO).convert("RGBA")


def fit(img, box):
    """Scale preserving aspect ratio to fit inside a square of side `box`."""
    w, h = img.size
    scale = box / max(w, h)
    return img.resize((max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS)


def centered(canvas_size, inner_box, bg):
    canvas = Image.new("RGBA", canvas_size, bg)
    art = fit(logo, inner_box)
    canvas.alpha_composite(
        art,
        ((canvas_size[0] - art.width) // 2, (canvas_size[1] - art.height) // 2),
    )
    return canvas


_FONT_CANDIDATES = [
    r"C:\Windows\Fonts\consolab.ttf",
    r"C:\Windows\Fonts\segoeuib.ttf",
    r"C:\Windows\Fonts\arialbd.ttf",
    "/System/Library/Fonts/SFNSDisplay.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
]


def _load_font(size):
    """First available bold face. Falls back to Pillow's bitmap default, which
    ignores `size` — acceptable because the icons still generate, just plainer."""
    for path in _FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, size)
    return ImageFont.load_default()


def _draw_centered(draw, text, y, font, fill, spacing=0):
    """Draw horizontally-centred text with manual letter-spacing.

    Pillow has no tracking control, so glyphs are placed one at a time. The
    site leans hard on wide-tracked mono, and rendering these flush would look
    nothing like the brand.
    """
    widths = [draw.textlength(ch, font=font) for ch in text]
    total = sum(widths) + spacing * (len(text) - 1)
    x = (1200 - total) / 2
    for ch, w in zip(text, widths):
        draw.text((x, y), ch, font=font, fill=fill)
        x += w + spacing


def main():
    # apple-touch-icon: iOS ignores alpha and mattes to black, so composite on
    # obsidian ourselves and inset the mark clear of the rounded-rect mask.
    centered((180, 180), 132, OBSIDIAN).convert("RGB").save(
        os.path.join(PUB, "apple-touch-icon.png"), "PNG", optimize=True
    )

    # Maskable PWA icons. Maskable safe zone is the centre 80%, so keep the
    # mark well inside that.
    for size in (192, 512):
        centered((size, size), int(size * 0.62), OBSIDIAN).convert("RGB").save(
            os.path.join(PUB, f"icon-{size}.png"), "PNG", optimize=True
        )

    # og:image at the 1200x630 that Twitter/Facebook expect. Social cards are
    # usually seen at ~400px wide, so the mark is sized generously and paired
    # with a wordmark — a lone small glyph reads as an empty dark rectangle.
    og = Image.new("RGBA", (1200, 630), OBSIDIAN)
    art = fit(logo, 300)
    og.alpha_composite(art, ((1200 - art.width) // 2, 88))
    d = ImageDraw.Draw(og)

    # Vertical rhythm, top-left origins: mark 88..388, wordmark 424, rule 508,
    # tagline 536. Text is drawn from its top edge, so each band needs at least
    # the font's own height of clearance before the next one starts.
    _draw_centered(d, "STACK'D", 424, _load_font(56), (226, 226, 226, 255), spacing=14)
    d.rectangle([(555, 508), (645, 510)], fill=EMBER)
    _draw_centered(d, "PRESENCE IS THE NEW LUXURY", 536, _load_font(24), EMBER, spacing=6)

    og.convert("RGB").save(os.path.join(PUB, "og-image.png"), "PNG", optimize=True)

    for f in ("apple-touch-icon.png", "icon-192.png", "icon-512.png", "og-image.png"):
        p = os.path.join(PUB, f)
        print(f, Image.open(p).size, f"{os.path.getsize(p) // 1024}KB")


if __name__ == "__main__":
    main()
