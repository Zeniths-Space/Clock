#!/usr/bin/env python3
"""Writes app/src/main/assets/land.bin, the land mask for the city map.

One bit per 1-degree cell from 80 N to 60 S and 180 W to 180 E (the same frame as WorldMap): 140 rows of
360 cells, row 0 the band from 80 N to 79 N, first cell at 180 W, bits most significant first. A cell
is land when at least 35% of it is land in Natural Earth's 1:50m land polygons (public domain).

Run: python3 tools/land.py [ne_50m_land.geojson]   (needs Pillow; downloads the file when not given)
"""
import json
import pathlib
import sys
import urllib.request

from PIL import Image, ImageDraw

URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_land.geojson"
NORTH, SOUTH = 80, -60
FINE = 10  # Pixels per degree when drawing the polygons, before counting each cell's land.


def main() -> None:
    if len(sys.argv) > 1:
        data = json.loads(pathlib.Path(sys.argv[1]).read_text())
    else:
        with urllib.request.urlopen(URL) as r:
            data = json.load(r)
    w, h = 360 * FINE, (NORTH - SOUTH) * FINE
    img = Image.new("L", (w, h), 0)
    draw = ImageDraw.Draw(img)
    for feature in data["features"]:
        geom = feature["geometry"]
        polys = geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]
        for poly in polys:
            for i, ring in enumerate(poly):  # The first ring is land, the others are holes (lakes).
                pts = [((lon + 180) * FINE, (NORTH - lat) * FINE) for lon, lat in ring]
                draw.polygon(pts, fill=0 if i else 255)
    small = img.resize((360, NORTH - SOUTH), Image.Resampling.BOX)
    bits = bytearray()
    for row in range(NORTH - SOUTH):
        for byte in range(45):
            b = 0
            for k in range(8):
                b = b << 1 | (small.getpixel((byte * 8 + k, row)) >= 0.35 * 255)
            bits.append(b)
    out = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/land.bin"
    out.write_bytes(bits)
    land = sum(bin(b).count("1") for b in bits)
    print(f"{land} land cells of {360 * (NORTH - SOUTH)} -> {out}")


if __name__ == "__main__":
    main()
