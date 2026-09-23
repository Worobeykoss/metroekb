#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Настоящая геометрия линии из OpenStreetMap -> app/src/main/assets/geometry.json.

Берёт самый длинный путь railway=subway основной линии (без service=yard/spur/crossover -
это депо и съезды; два пути тоннеля идут рядом, одного достаточно), проецирует на него станции
и режет на перегоны. Перегон упрощается (Дуглас-Пекер, ~4 м) и сохраняется списком [lat, lon].

Запуск:  python tools/fetch_geometry.py [файл_overpass.json]
Без аргумента качает с Overpass (перебирает зеркала). Данные © участники OpenStreetMap, ODbL.
"""
import json
import math
import os
import sys
import urllib.parse
import urllib.request

QUERY = '[out:json][timeout:100];way["railway"="subway"](56.78,60.55,56.92,60.68);out geom tags;'
MIRRORS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://maps.mail.ru/osm/tools/overpass/api/interpreter",
]
SIMPLIFY_M = 4.0

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets"))


def dist_m(a, b):
    r = 6371000.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp, dl = p2 - p1, math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def fetch():
    body = urllib.parse.urlencode({"data": QUERY}).encode()
    for url in MIRRORS:
        try:
            req = urllib.request.Request(url, data=body, headers={"User-Agent": "MetroEkb/1.0"})
            raw = urllib.request.urlopen(req, timeout=120).read()
            if raw[:1] == b"{":
                return json.loads(raw)
        except Exception as e:  # noqa: BLE001 - перебираем зеркала
            print("mirror failed:", url, e)
    raise SystemExit("Overpass недоступен - попробуйте позже")


def simplify(points, eps):
    """Дуглас-Пекер в локальной плоской проекции (метры)."""
    if len(points) < 3:
        return points
    lat0 = math.radians(points[0][0])
    xy = [(p[1] * 111320 * math.cos(lat0), p[0] * 110540) for p in points]

    def seg_dist(p, a, b):
        ax, ay = a
        bx, by = b
        px, py = p
        dx, dy = bx - ax, by - ay
        if dx == dy == 0:
            return math.hypot(px - ax, py - ay)
        t = max(0, min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)))
        return math.hypot(px - (ax + t * dx), py - (ay + t * dy))

    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        i, j = stack.pop()
        best, idx = 0.0, -1
        for k in range(i + 1, j):
            d = seg_dist(xy[k], xy[i], xy[j])
            if d > best:
                best, idx = d, k
        if best > eps:
            keep[idx] = True
            stack += [(i, idx), (idx, j)]
    return [p for p, k in zip(points, keep) if k]


def project(poly, pt):
    """Ближайшая точка ломаной к pt: (расстояние вдоль ломаной, индекс сегмента, точка)."""
    lat0 = math.radians(pt[0])
    to_xy = lambda p: (p[1] * 111320 * math.cos(lat0), p[0] * 110540)
    px, py = to_xy(pt)
    best = None
    along = 0.0
    for i, (a, b) in enumerate(zip(poly, poly[1:])):
        ax, ay = to_xy(a)
        bx, by = to_xy(b)
        dx, dy = bx - ax, by - ay
        l2 = dx * dx + dy * dy
        t = 0.0 if l2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / l2))
        qx, qy = ax + t * dx, ay + t * dy
        d = math.hypot(px - qx, py - qy)
        seg = math.sqrt(l2)
        if best is None or d < best[0]:
            q = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
            best = (d, along + seg * t, i, q)
        along += seg
    return best


def main():
    data = json.load(open(sys.argv[1], encoding="utf-8")) if len(sys.argv) > 1 else fetch()
    stations = json.load(open(os.path.join(ASSETS, "schedule.json"), encoding="utf-8"))["stations"]

    # Основная линия - самый длинный путь без service (депо/съезды). Два пути тоннеля идут
    # рядом (метров 10-20), поэтому одного достаточно.
    main_ways = [w for w in data["elements"] if w.get("type") == "way" and not w.get("tags", {}).get("service")]
    way = max(main_ways, key=lambda w: len(w["geometry"]))
    poly = [(p["lat"], p["lon"]) for p in way["geometry"]]
    first = (stations[0]["lat"], stations[0]["lon"])
    if dist_m(poly[0], first) > dist_m(poly[-1], first):
        poly.reverse()  # от Проспекта Космонавтов к Ботанической

    proj = [project(poly, (s["lat"], s["lon"])) for s in stations]
    for s, p in zip(stations, proj):
        print(f"  {s['name']:20s} до пути {p[0]:5.0f} м, вдоль {p[1]:6.0f} м")

    segments = []
    for k, (a, b) in enumerate(zip(stations, stations[1:])):
        pa, pb = proj[k], proj[k + 1]
        if pb[1] <= pa[1]:
            print(f"  {a['name']} -> {b['name']}: порядок нарушен, оставляю прямую")
            pts = [(a["lat"], a["lon"]), (b["lat"], b["lon"])]
        else:
            # Концы - точно в станциях, чтобы поезд приходил ровно в кружок на карте.
            pts = [(a["lat"], a["lon"])] + poly[pa[2] + 1: pb[2] + 1] + [(b["lat"], b["lon"])]
            pts = simplify(pts, SIMPLIFY_M)
        length = sum(dist_m(x, y) for x, y in zip(pts, pts[1:]))
        straight = dist_m((a["lat"], a["lon"]), (b["lat"], b["lon"]))
        print(f"  {a['name']:20s} -> {b['name']:20s} {length:6.0f} м (по прямой {straight:5.0f}), точек {len(pts)}")
        segments.append([[round(p[0], 6), round(p[1], 6)] for p in pts])

    out = os.path.join(ASSETS, "geometry.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump({"source": "© OpenStreetMap contributors, ODbL", "segments": segments}, f, ensure_ascii=False)
    print("saved:", out)


if __name__ == "__main__":
    main()
