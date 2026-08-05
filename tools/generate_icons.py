#!/usr/bin/env python3
"""
Cross-platform icon generator for the VSCode Android redesign.

Generates all launcher, adaptive, sidebar, actionbar, notification and splash
assets from a single geometric 'code chevron' glyph (</>) drawn on a blue-to-navy
diagonal gradient. Pure Python + Pillow, no ImageMagick / sips required.

Usage:
    python3 tools/generate_icons.py [REPO_ROOT]
"""

import os
import sys
import math

from PIL import Image, ImageDraw

# --------------------------------------------------------------------------
# Palette
# --------------------------------------------------------------------------
BLUE = (0x00, 0x7A, 0xCC)      # VSCode-inspired accent blue
BLUE_LIGHT = (0x3B, 0xA5, 0xE8)
CYAN = (0x7F, 0xD1, 0xFF)      # slash accent
NAVY = (0x0A, 0x1A, 0x2F)      # deep navy
WHITE = (255, 255, 255, 255)

SS = 4  # supersampling factor (anti-aliasing)

# Glyph geometry (normalized 0..1 coordinates on the square canvas)
GLYPH = {
    "left":  [(0.30, 0.29), (0.18, 0.50), (0.30, 0.71)],
    "right": [(0.70, 0.29), (0.82, 0.50), (0.70, 0.71)],
    "slash": [(0.76, 0.25), (0.34, 0.75)],
    "width": 0.052,
}


def _lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def gradient_square(size, radius):
    """Diagonal blue->navy gradient on a rounded-rect alpha mask, RGBA."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    n = size - 1
    denom = 2 * n
    # draw one anti-diagonal stripe (x + y == k) per step: cheap C-speed gradient
    for k in range(2 * size + 1):
        t = min(1.0, max(0.0, k / denom))
        col = _lerp(BLUE, NAVY, t) + (255,)
        x0 = min(k, n)
        y0 = max(0, k - n)
        x1 = max(0, k - n)
        y1 = min(k, n)
        d.line([(x0, y0), (x1, y1)], fill=col, width=1)
    # rounded-rect alpha mask
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    r = int(size * radius)
    md.rounded_rectangle([0, 0, size - 1, size - 1], radius=r, fill=255)
    img.putalpha(mask)
    return img


def _line(draw, pts, width, color, rounded=True):
    """Polyline with rounded joints and caps."""
    draw.line(pts, fill=color, width=width, joint="curve")
    if rounded:
        r = width // 2
        for (x, y) in pts:
            draw.ellipse([x - r, y - r, x + r, y + r], fill=color)


def glyph_layer(size, scale, chevron=WHITE, slash_color=CYAN):
    """Return an RGBA image (size x size) with the </> glyph drawn.

    scale controls how much of the canvas the glyph occupies (0..1).
    """
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(canvas)

    margin = (1 - scale) / 2
    def px(v):
        return margin + v * scale

    w = int(size * GLYPH["width"])

    def to_pts(points):
        return [(px(x) * size, px(y) * size) for (x, y) in points]

    _line(d, to_pts(GLYPH["left"]), w, chevron)
    _line(d, to_pts(GLYPH["right"]), w, chevron)
    slash = to_pts(GLYPH["slash"])
    _line(d, slash, int(w * 0.82), slash_color)

    return canvas


def supersample(fn):
    """Render fn at SS*src resolution then downsample for smooth edges."""
    def wrapper(size, *args, **kwargs):
        big = fn(size * SS, *args, **kwargs)
        return big.resize((size, size), Image.LANCZOS)
    return wrapper


@supersample
def make_glyph(size, scale, chevron=WHITE, slash_color=CYAN):
    return glyph_layer(size, scale, chevron, slash_color)


@supersample
def make_icon(size, glyph_scale=0.58, radius=0.20):
    base = gradient_square(size, radius)
    glyph = glyph_layer(size, glyph_scale)
    base.alpha_composite(glyph)
    return base


def ensure_dir(path):
    os.makedirs(path, exist_ok=True)


def save(img, path, fmt=None):
    ensure_dir(os.path.dirname(path))
    if fmt is None:
        ext = os.path.splitext(path)[1].lstrip(".")
        fmt = ext.upper() if ext else "PNG"
    img.save(path, format=fmt)
    print("  %-85s %dx%d" % (os.path.relpath(path, ROOT), img.size[0], img.size[1]))


def main():
    global ROOT
    ROOT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
    ROOT = os.path.abspath(ROOT)
    RES = os.path.join(ROOT, "app", "src", "main", "res")

    print("Generating assets in %s" % ROOT)

    # Master source assets (repo root, used by the original macOS scripts)
    save(make_icon(1024), os.path.join(ROOT, "AppIcon"))
    save(make_glyph(720, 0.42), os.path.join(ROOT, "NotificationIcon"))
    header = gradient_square(1024, 0.0).resize((1000, 242), Image.LANCZOS)
    glyph = make_glyph(1000, 0.46)
    header.alpha_composite(glyph, (0, -40))
    save(header, os.path.join(ROOT, "HeaderImage"))

    # Legacy launcher icons (full rounded gradient + glyph)
    for dpi, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        save(make_icon(size), os.path.join(RES, "mipmap-%s" % dpi, "ic_launcher.png"))

    # Adaptive icon foreground (glyph only, inside the safe zone)
    for dpi, size in (("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)):
        save(make_glyph(size, 0.58), os.path.join(RES, "mipmap-%s" % dpi, "ic_launcher_foreground.png"))

    # Sidebar logo: navy glyph for light mode, white glyph for night
    for dpi, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        save(make_glyph(size, 0.90, chevron=NAVY + (255,), slash_color=BLUE + (255,)),
             os.path.join(RES, "mipmap-%s" % dpi, "ic_sidebar_logo.png"))
        save(make_glyph(size, 0.90), os.path.join(RES, "mipmap-night-%s" % dpi, "ic_sidebar_logo.png"))

    # Actionbar logo (rounded gradient icon, both light and night)
    for dpi, size in (("mdpi", 36), ("hdpi", 54), ("xhdpi", 72), ("xxhdpi", 108), ("xxxhdpi", 144)):
        save(make_icon(size), os.path.join(RES, "drawable-%s" % dpi, "ic_actionbar.png"))
        save(make_icon(size), os.path.join(RES, "drawable-night-%s" % dpi, "ic_actionbar.png"))

    # Notification icon (white glyph)
    for dpi, size in (("mdpi", 24), ("hdpi", 36), ("xhdpi", 48), ("xxhdpi", 72), ("xxxhdpi", 96)):
        save(make_glyph(size, 0.80), os.path.join(RES, "drawable-%s" % dpi, "ic_notification.png"))

    # Splash icon (white glyph on transparent; bg comes from @color/splash_background)
    for dpi, size in (("mdpi", 360), ("hdpi", 540), ("xhdpi", 720), ("xxhdpi", 1080), ("xxxhdpi", 1440)):
        save(make_glyph(size, 0.55), os.path.join(RES, "drawable-%s" % dpi, "splash.png"))
        save(make_glyph(size, 0.55), os.path.join(RES, "drawable-night-%s" % dpi, "splash.png"))

    print("Done.")


if __name__ == "__main__":
    main()
