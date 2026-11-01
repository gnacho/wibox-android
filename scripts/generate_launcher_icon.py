#!/usr/bin/env python3
"""Generate the launcher icon assets from the source artwork.

The source is the 1024x1024 master drawing: white line art over a vertical blue
gradient inside a rounded square. The script extracts an alpha matte for the line
art, then writes the adaptive icon layers (background, foreground, monochrome),
the legacy bitmaps for every density, and the gradient background drawable.

Usage:
    python3 scripts/generate_launcher_icon.py [--source art/icono_wibox_monitor.png]
"""

from __future__ import annotations

import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw

REPO_ROOT = Path(__file__).resolve().parent.parent
RES = REPO_ROOT / "app/src/main/res"

# The background gradient is sampled from the source artwork, so the layers keep
# the exact same colour ramp as the master drawing.
GRADIENT_STOPS = [
    (0.00, "#81D2EE"),
    (0.25, "#5CBBDD"),
    (0.50, "#2997BF"),
    (0.75, "#0A81AB"),
    (1.00, "#0277A2"),
]

# Adaptive icon geometry: the layer is 108 dp and the content must stay inside the
# 66 dp safe circle (33 dp radius). The art is centred, so its furthest pixel from
# the centre is placed at this radius.
FOREGROUND_SAFE_RADIUS_DP = 35.0
ADAPTIVE_SIZE_DP = 108

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}

LEGACY_SIZE_DP = 48


def extract_art(source: Image.Image) -> Image.Image:
    """Return the white line art as an RGBA image with a clean alpha matte."""
    rgb = source.convert("RGB")
    width, height = rgb.size
    pixels = rgb.load()

    # Background brightness per row: the darkest pixels of each row belong to the
    # gradient, so their red channel is a solid estimate of the local background.
    row_background: list[float] = []
    for y in range(height):
        reds = sorted(pixels[x, y][0] for x in range(width))
        row_background.append(sum(reds[:25]) / 25.0)

    # Smooth the profile to keep the matte free of banding.
    window = 51
    smoothed: list[float] = []
    for y in range(height):
        start = max(0, y - window // 2)
        end = min(height, y + window // 2 + 1)
        smoothed.append(sum(row_background[start:end]) / (end - start))

    art = Image.new("RGBA", (width, height), (255, 255, 255, 0))
    art_pixels = art.load()
    for y in range(height):
        background = smoothed[y]
        span = 252.0 - background
        if span <= 1.0:
            continue
        for x in range(width):
            alpha = (pixels[x, y][0] - background) / span
            if alpha <= 0.15:
                continue
            art_pixels[x, y] = (255, 255, 255, min(255, int(round(alpha * 255))))

    # The master drawing has white paper outside the rounded square. Drop anything
    # that is not inside the shape, otherwise the sliver of paper becomes part of
    # the foreground layer.
    inset = 20
    inside = Image.new("L", (width, height), 0)
    ImageDraw.Draw(inside).rounded_rectangle(
        [inset, inset, width - inset - 1, height - inset - 1],
        radius=250,
        fill=255,
    )
    art.putalpha(Image.composite(art.getchannel("A"), Image.new("L", (width, height), 0), inside))
    return art


def art_radius(art: Image.Image) -> float:
    """Distance from the art centre to its furthest painted pixel."""
    alpha = art.getchannel("A")
    width, height = art.size
    xs: list[int] = []
    ys: list[int] = []
    alpha_pixels = alpha.load()
    for y in range(height):
        for x in range(width):
            if alpha_pixels[x, y] > 64:
                xs.append(x)
                ys.append(y)
    center_x = (min(xs) + max(xs)) / 2
    center_y = (min(ys) + max(ys)) / 2
    return max(
        math.hypot(x - center_x, y - center_y) for x, y in zip(xs, ys, strict=True)
    )


def art_bounds(art: Image.Image) -> tuple[int, int, int, int]:
    alpha = art.getchannel("A")
    return alpha.getbbox() or (0, 0, art.width, art.height)


def adaptive_layer(art: Image.Image, size_px: int, scale: float, color: tuple[int, int, int]) -> Image.Image:
    """Layer of `size_px` with the art centred and scaled to fit the safe circle."""
    layer = Image.new("RGBA", (size_px, size_px), (0, 0, 0, 0))
    art_width = max(1, int(round(art.width * scale)))
    art_height = max(1, int(round(art.height * scale)))
    scaled = art.resize((art_width, art_height), Image.LANCZOS)
    if color != (255, 255, 255):
        tinted = Image.new("RGBA", scaled.size, color + (255,))
        tinted.putalpha(scaled.getchannel("A"))
        scaled = tinted
    layer.alpha_composite(scaled, ((size_px - art_width) // 2, (size_px - art_height) // 2))
    return layer


def gradient_background(size_px: int) -> Image.Image:
    """Raster version of the background ramp, used by the legacy bitmaps."""
    stops = [(offset, tuple(int(color[i : i + 2], 16) for i in (1, 3, 5))) for offset, color in GRADIENT_STOPS]
    background = Image.new("RGB", (size_px, size_px))
    pixels = background.load()
    for y in range(size_px):
        position = y / max(1, size_px - 1)
        for index in range(len(stops) - 1):
            start_offset, start_color = stops[index]
            end_offset, end_color = stops[index + 1]
            if start_offset <= position <= end_offset:
                span = end_offset - start_offset
                mix = 0.0 if span == 0 else (position - start_offset) / span
                color = tuple(
                    int(round(start_color[channel] + (end_color[channel] - start_color[channel]) * mix))
                    for channel in range(3)
                )
                break
        else:
            color = stops[-1][1]
        for x in range(size_px):
            pixels[x, y] = color
    return background


def background_drawable() -> str:
    items = "\n".join(
        f'            <item android:offset="{offset:.2f}" android:color="{color}" />'
        for offset, color in GRADIENT_STOPS
    )
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="{ADAPTIVE_SIZE_DP}dp"
    android:height="{ADAPTIVE_SIZE_DP}dp"
    android:viewportWidth="{ADAPTIVE_SIZE_DP}"
    android:viewportHeight="{ADAPTIVE_SIZE_DP}">
    <path android:pathData="M0,0h{ADAPTIVE_SIZE_DP}v{ADAPTIVE_SIZE_DP}h-{ADAPTIVE_SIZE_DP}z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="0"
                android:endY="{ADAPTIVE_SIZE_DP}">
{items}
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''


def adaptive_xml(round_shape: bool) -> str:
    return f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''


def legacy_bitmap(
    art: Image.Image,
    size_px: int,
    scale: float,
    round_shape: bool,
) -> Image.Image:
    """Full-bleed icon for launchers that ignore the adaptive icon spec."""
    canvas = gradient_background(size_px).convert("RGBA")
    art_width = max(1, int(round(art.width * scale)))
    art_height = max(1, int(round(art.height * scale)))
    canvas.alpha_composite(
        art.resize((art_width, art_height), Image.LANCZOS),
        ((size_px - art_width) // 2, (size_px - art_height) // 2),
    )
    mask = Image.new("L", (size_px, size_px), 0)
    draw = ImageDraw.Draw(mask)
    if round_shape:
        draw.ellipse([0, 0, size_px - 1, size_px - 1], fill=255)
    else:
        draw.rounded_rectangle([0, 0, size_px - 1, size_px - 1], radius=int(size_px * 0.264), fill=255)
    canvas.putalpha(mask)
    return canvas


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    print(f"wrote {path.relative_to(REPO_ROOT)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source",
        type=Path,
        default=REPO_ROOT / "art/icono_wibox_monitor.png",
        help="master PNG drawing",
    )
    args = parser.parse_args()

    source = Image.open(args.source)
    art = extract_art(source)
    radius = art_radius(art)
    bounds = art_bounds(art)
    art_width = bounds[2] - bounds[0]
    print(f"art bounds {bounds} width {art_width}px radius {radius:.1f}px")

    adaptive_scale = (FOREGROUND_SAFE_RADIUS_DP / ADAPTIVE_SIZE_DP) * ADAPTIVE_SIZE_DP / radius
    # Legacy icons keep the proportions of the master drawing, where the art fills
    # about two thirds of the canvas.
    legacy_scale = 0.66 * LEGACY_SIZE_DP / art_width

    for density, factor in DENSITIES.items():
        adaptive_px = int(round(ADAPTIVE_SIZE_DP * factor))
        scale = adaptive_scale * adaptive_px / ADAPTIVE_SIZE_DP
        (RES / f"drawable-{density}").mkdir(parents=True, exist_ok=True)
        (RES / f"mipmap-{density}").mkdir(parents=True, exist_ok=True)
        foreground = adaptive_layer(art, adaptive_px, scale, (255, 255, 255))
        foreground.save(RES / f"drawable-{density}/ic_launcher_foreground.png")
        print(f"wrote res/drawable-{density}/ic_launcher_foreground.png ({adaptive_px}px)")

        monochrome = adaptive_layer(art, adaptive_px, scale, (255, 255, 255))
        monochrome.save(RES / f"drawable-{density}/ic_launcher_monochrome.png")
        print(f"wrote res/drawable-{density}/ic_launcher_monochrome.png ({adaptive_px}px)")

        legacy_px = int(round(LEGACY_SIZE_DP * factor))
        legacy_scale_px = legacy_scale * legacy_px / LEGACY_SIZE_DP
        legacy_bitmap(art, legacy_px, legacy_scale_px, round_shape=False).save(
            RES / f"mipmap-{density}/ic_launcher.png"
        )
        legacy_bitmap(art, legacy_px, legacy_scale_px, round_shape=True).save(
            RES / f"mipmap-{density}/ic_launcher_round.png"
        )
        print(f"wrote res/mipmap-{density}/ic_launcher.png and ic_launcher_round.png ({legacy_px}px)")

    write(RES / "drawable/ic_launcher_background.xml", background_drawable())
    write(RES / "mipmap-anydpi-v26/ic_launcher.xml", adaptive_xml(round_shape=False))
    write(RES / "mipmap-anydpi-v26/ic_launcher_round.xml", adaptive_xml(round_shape=True))


if __name__ == "__main__":
    main()
