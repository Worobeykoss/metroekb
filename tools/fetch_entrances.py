#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Входы в метро из OpenStreetMap (Overpass) -> app/src/main/assets/entrances.json.

Берёт узлы railway=subway_entrance в bbox Екатеринбурга, привязывает каждый к ближайшей
станции из schedule.json (не дальше MAX_DIST_M) и сохраняет номер входа (ref) и название.
Нужен компасу «к ближайшему входу».

Запуск:  python tools/fetch_entrances.py [файл_overpass.json]
wheelchair (yes/limited/no) - доступность входа для колясок, если отмечена в OSM.
Данные © участники OpenStreetMap, ODbL.
"""
import json
import math
import os
import sys
import urllib.parse
import urllib.request

QUERY = """[out:json][timeout:50];
(node["railway"="subway_entrance"](56.78,60.55,56.92,60.68);
 node["entrance"]["subway"="yes"](56.78,60.55,56.92,60.68););
out body;"""
MAX_DIST_M = 500

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets"))


def dist_m(lat1, lon1, lat2, lon2):
    r = 6371000.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp, dl = p2 - p1, math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(a))


def main():
    stations = json.load(open(os.path.join(ASSETS, "schedule.json"), encoding="utf-8"))["stations"]
    if len(sys.argv) > 1:
        elements = json.load(open(sys.argv[1], encoding="utf-8"))["elements"]
    else:
        body = urllib.parse.urlencode({"data": QUERY}).encode()
        req = urllib.request.Request("https://overpass-api.de/api/interpreter", data=body,
                                     headers={"User-Agent": "MetroEkb/1.0"})
        elements = json.load(urllib.request.urlopen(req, timeout=90))["elements"]

    out = []
    for el in elements:
        tags = el.get("tags", {})
        nearest = min(stations, key=lambda s: dist_m(el["lat"], el["lon"], s["lat"], s["lon"]))
        d = dist_m(el["lat"], el["lon"], nearest["lat"], nearest["lon"])
        if d > MAX_DIST_M:
            continue
        out.append({
            "stationId": nearest["id"],
            "lat": round(el["lat"], 7),
            "lon": round(el["lon"], 7),
            "ref": tags.get("ref", ""),
            "name": tags.get("name", ""),
            "wheelchair": tags.get("wheelchair", ""),
        })
    out.sort(key=lambda e: ([s["id"] for s in stations].index(e["stationId"]), e["ref"]))

    path = os.path.join(ASSETS, "entrances.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"source": "© OpenStreetMap contributors, ODbL", "entrances": out},
                  f, ensure_ascii=False, indent=1)
    by_station = {}
    for e in out:
        by_station[e["stationId"]] = by_station.get(e["stationId"], 0) + 1
    for s in stations:
        print(f'{s["name"]:22s} -> {by_station.get(s["id"], 0)} входов')
    print("saved:", path)


if __name__ == "__main__":
    main()
