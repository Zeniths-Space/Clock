#!/usr/bin/env python3
"""Writes app/src/main/assets/land.bin, the land shapes for the world clock's maps.

Natural Earth's 1:50m land polygons (public domain), without Antarctica and islands too small to see, in
two versions: a coarse one for the whole world (simplified to 0.2 degrees) and a fine one (0.04 degrees,
about 4 km) cut into 10-degree tiles, so a zoomed-in map draws only the tiles on screen. Tiles overlap
by 0.05 degrees so no seam shows between them when the land is drawn opaque.

Little-endian. Coarse: int32 ring count, then per ring int32 point count and (int16 lon, int16 lat) points
in hundredths of a degree. Fine: int32 ring count, then per ring int16 tile column (0-35, from 180 W),
int16 tile row (0-17, from 90 N), int32 point count and the points. Holes (big lakes) are rings too;
draw with the even-odd rule.

Run: python3 tools/land.py [ne_50m_land.geojson]   (downloads the file when not given)
"""
import json
import pathlib
import struct
import sys
import urllib.request

URL = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_land.geojson"
COARSE = 0.2
FINE = 0.04
TILE = 10
OVERLAP = 0.05
MIN_AREA = 0.05  # Square degrees; smaller islands are left out.


def simplify(pts, tol):
    """Douglas-Peucker, iterative, on a closed ring (first point kept)."""
    if len(pts) < 4:
        return pts
    keep = [False] * len(pts)
    keep[0] = keep[-1] = True
    stack = [(0, len(pts) - 1)]
    while stack:
        a, b = stack.pop()
        ax, ay = pts[a]
        bx, by = pts[b]
        dx, dy = bx - ax, by - ay
        norm = (dx * dx + dy * dy) ** 0.5
        best, at = -1.0, -1
        for i in range(a + 1, b):
            px, py = pts[i]
            d = abs(dy * (px - ax) - dx * (py - ay)) / norm if norm else ((px - ax) ** 2 + (py - ay) ** 2) ** 0.5
            if d > best:
                best, at = d, i
        if best > tol:
            keep[at] = True
            stack += [(a, at), (at, b)]
    return [p for p, k in zip(pts, keep) if k]


def clip(ring, x0, y0, x1, y1):
    """Sutherland-Hodgman: the part of [ring] inside the rectangle."""
    def cut(pts, inside, cross):
        out = []
        for i, cur in enumerate(pts):
            prev = pts[i - 1]
            if inside(cur):
                if not inside(prev):
                    out.append(cross(prev, cur))
                out.append(cur)
            elif inside(prev):
                out.append(cross(prev, cur))
        return out

    def at_x(x):
        return lambda a, b: (x, a[1] + (b[1] - a[1]) * (x - a[0]) / (b[0] - a[0]))

    def at_y(y):
        return lambda a, b: (a[0] + (b[0] - a[0]) * (y - a[1]) / (b[1] - a[1]), y)

    pts = ring
    for inside, cross in (
        (lambda p: p[0] >= x0, at_x(x0)), (lambda p: p[0] <= x1, at_x(x1)),
        (lambda p: p[1] >= y0, at_y(y0)), (lambda p: p[1] <= y1, at_y(y1)),
    ):
        pts = cut(pts, inside, cross)
        if not pts:
            break
    return pts


def write_ring(f, ring):
    f.write(struct.pack("<i", len(ring)))
    for x, y in ring:
        f.write(struct.pack("<hh", round(x * 100), round(y * 100)))


def area(ring):
    return abs(sum(x0 * y1 - x1 * y0 for (x0, y0), (x1, y1) in zip(ring, ring[1:] + ring[:1]))) / 2


def main() -> None:
    if len(sys.argv) > 1:
        data = json.loads(pathlib.Path(sys.argv[1]).read_text())
    else:
        with urllib.request.urlopen(URL) as r:
            data = json.load(r)
    rings = []
    for feature in data["features"]:
        geom = feature["geometry"]
        for poly in geom["coordinates"] if geom["type"] == "MultiPolygon" else [geom["coordinates"]]:
            for ring in poly:
                ring = [(float(x), float(y)) for x, y in ring]
                if max(y for _, y in ring) < -58 or area(ring) < MIN_AREA:
                    continue
                rings.append(ring)
    coarse = [r for r in (simplify(r, COARSE) for r in rings) if len(r) >= 3]
    tiles = []
    for ring in rings:
        fine = simplify(ring, FINE)
        xs = [x for x, _ in fine]
        ys = [y for _, y in fine]
        for col in range(int((min(xs) + 180) // TILE), int((max(xs) + 180) // TILE) + 1):
            for row in range(int((90 - max(ys)) // TILE), int((90 - min(ys)) // TILE) + 1):
                x0 = col * TILE - 180 - OVERLAP
                y1 = 90 - row * TILE + OVERLAP
                part = clip(fine, x0, y1 - TILE - 2 * OVERLAP, x0 + TILE + 2 * OVERLAP, y1)
                if len(part) >= 3:
                    tiles.append((col, row, part))
    out = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/land.bin"
    with out.open("wb") as f:
        f.write(struct.pack("<i", len(coarse)))
        for r in coarse:
            write_ring(f, r)
        f.write(struct.pack("<i", len(tiles)))
        for col, row, r in tiles:
            f.write(struct.pack("<hh", col, row))
            write_ring(f, r)
    print(f"coarse {sum(map(len, coarse))} points, fine {len(tiles)} tile rings {sum(len(t[2]) for t in tiles)} points"
          f" -> {out} ({out.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
