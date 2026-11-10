#!/usr/bin/env python3
"""Writes app/src/main/assets/cities.tsv, the offline city list for the world clock.

Every place in the tz database's zone.tab (one per time zone, with its coordinates), plus large cities
that share another city's zone (Mumbai, San Francisco, ...). Rows: name, country code, zone, lat, lon,
short code, and 1 for a large city (labelled first on the map) else 0. Run: python3 tools/cities.py [/usr/share/zoneinfo/zone.tab]
"""
import pathlib
import sys

# Short codes where the first three letters would be odd or clash with a better-known city.
CODES = {
    "Kathmandu": "KTM", "London": "LON", "New York": "NYC", "Tokyo": "TYO", "Dubai": "DXB", "Delhi": "DEL",
    "Los Angeles": "LAX", "San Francisco": "SFO", "Hong Kong": "HKG", "Singapore": "SIN", "Sydney": "SYD",
    "Paris": "PAR", "Kolkata": "CCU", "Mumbai": "BOM", "Bengaluru": "BLR", "Beijing": "BJS", "Shanghai": "SHA",
    "Seoul": "SEL", "Bangkok": "BKK", "Toronto": "YTO", "Chicago": "CHI", "Washington": "WAS", "Berlin": "BER",
    "Madrid": "MAD", "Rome": "ROM", "Moscow": "MOW", "Istanbul": "IST", "Cairo": "CAI", "Doha": "DOH",
    "Sao Paulo": "SAO", "Mexico City": "MEX", "Auckland": "AKL", "Honolulu": "HNL", "Dhaka": "DAC",
    "Karachi": "KHI", "Lahore": "LHE", "Melbourne": "MEL", "Amsterdam": "AMS", "Pokhara": "PKR",
}

# Big cities with no zone of their own: name, country, zone, lat, lon.
EXTRA = [
    ("Delhi", "IN", "Asia/Kolkata", 28.6139, 77.2090), ("Mumbai", "IN", "Asia/Kolkata", 19.0760, 72.8777),
    ("Bengaluru", "IN", "Asia/Kolkata", 12.9716, 77.5946), ("Chennai", "IN", "Asia/Kolkata", 13.0827, 80.2707),
    ("Pokhara", "NP", "Asia/Kathmandu", 28.2096, 83.9856), ("Beijing", "CN", "Asia/Shanghai", 39.9042, 116.4074),
    ("Shenzhen", "CN", "Asia/Shanghai", 22.5431, 114.0579), ("Osaka", "JP", "Asia/Tokyo", 34.6937, 135.5023),
    ("Busan", "KR", "Asia/Seoul", 35.1796, 129.0756), ("Lahore", "PK", "Asia/Karachi", 31.5204, 74.3587),
    ("Abu Dhabi", "AE", "Asia/Dubai", 24.4539, 54.3773), ("Jeddah", "SA", "Asia/Riyadh", 21.4858, 39.1925),
    ("Mecca", "SA", "Asia/Riyadh", 21.3891, 39.8579), ("Hanoi", "VN", "Asia/Ho_Chi_Minh", 21.0278, 105.8342),
    ("Bali", "ID", "Asia/Makassar", -8.4095, 115.1889), ("Cape Town", "ZA", "Africa/Johannesburg", -33.9249, 18.4241),
    ("San Francisco", "US", "America/Los_Angeles", 37.7749, -122.4194), ("Seattle", "US", "America/Los_Angeles", 47.6062, -122.3321),
    ("Las Vegas", "US", "America/Los_Angeles", 36.1699, -115.1398), ("Washington", "US", "America/New_York", 38.9072, -77.0369),
    ("Boston", "US", "America/New_York", 42.3601, -71.0589), ("Miami", "US", "America/New_York", 25.7617, -80.1918),
    ("Atlanta", "US", "America/New_York", 33.7490, -84.3880), ("Dallas", "US", "America/Chicago", 32.7767, -96.7970),
    ("Houston", "US", "America/Chicago", 29.7604, -95.3698), ("Montreal", "CA", "America/Toronto", 45.5019, -73.5674),
    ("Rio de Janeiro", "BR", "America/Sao_Paulo", -22.9068, -43.1729), ("Barcelona", "ES", "Europe/Madrid", 41.3874, 2.1686),
    ("Milan", "IT", "Europe/Rome", 45.4642, 9.1900), ("Munich", "DE", "Europe/Berlin", 48.1351, 11.5820),
    ("Frankfurt", "DE", "Europe/Berlin", 50.1109, 8.6821), ("Manchester", "GB", "Europe/London", 53.4808, -2.2426),
    ("Edinburgh", "GB", "Europe/London", 55.9533, -3.1883), ("St Petersburg", "RU", "Europe/Moscow", 59.9311, 30.3609),
]
EXTRA_NAMES = {row[0] for row in EXTRA}


def degrees(s: str) -> float:
    """ISO 6709 +DDMM, +DDDMM, +DDMMSS or +DDDMMSS to degrees."""
    sign = -1 if s[0] == "-" else 1
    s = s[1:]
    deg_len = 2 if len(s) in (4, 6) else 3
    d, rest = int(s[:deg_len]), s[deg_len:]
    m = int(rest[:2])
    sec = int(rest[2:4]) if len(rest) >= 4 else 0
    return sign * (d + m / 60 + sec / 3600)


def split(coords: str) -> tuple[float, float]:
    i = max(coords.rfind("+"), coords.rfind("-"))
    return degrees(coords[:i]), degrees(coords[i:])


def main() -> None:
    src = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "/usr/share/zoneinfo/zone.tab")
    rows = {}
    for line in src.read_text().splitlines():
        if not line or line.startswith("#"):
            continue
        cc, coords, zone = line.split("\t")[:3]
        lat, lon = split(coords)
        name = zone.rsplit("/", 1)[-1].replace("_", " ")
        rows[(name, zone)] = (name, cc, zone, lat, lon)
    for name, cc, zone, lat, lon in EXTRA:
        rows[(name, zone)] = (name, cc, zone, lat, lon)
    out = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/cities.tsv"
    with out.open("w") as f:
        for name, cc, zone, lat, lon in sorted(rows.values()):
            code = CODES.get(name, "".join(c for c in name if c.isalpha())[:3].upper())
            major = int(name in CODES or name in EXTRA_NAMES)
            f.write(f"{name}\t{cc}\t{zone}\t{lat:.4f}\t{lon:.4f}\t{code}\t{major}\n")
    print(f"{len(rows)} cities -> {out}")


if __name__ == "__main__":
    main()
