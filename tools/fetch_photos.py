#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Фото станций из Wikimedia Commons -> app/src/main/assets/photos.json.

Для каждой станции: главное фото (Wikidata P18) + до MAX_PER_STATION файлов из её категории
на Commons (P373). Для каждого файла - URL превью шириной THUMB_W, автор и лицензия (для
подписи в приложении). Сами картинки не бандлятся: приложение грузит превью по сети.

Запуск:  python tools/fetch_photos.py
Фото © их авторы, лицензии указаны у каждого файла (обычно CC BY-SA).
"""
import html
import json
import os
import re
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "MetroEkb/1.0 (personal app; contact via github)"
MAX_PER_STATION = 5
THUMB_W = 640
HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(HERE, "..", "app", "src", "main", "assets"))

SPARQL = """SELECT ?s ?sLabel ?img ?cat WHERE {
  ?s wdt:P81 ?line . ?line wdt:P361|wdt:P16 ?net .
  ?net rdfs:label "Екатеринбургский метрополитен"@ru .
  OPTIONAL { ?s wdt:P18 ?img } OPTIONAL { ?s wdt:P373 ?cat }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "ru". }
}"""


def get_json(url, params):
    """GET с вежливой паузой и повтором при 429 (Commons ограничивает частоту запросов)."""
    req = urllib.request.Request(url + "?" + urllib.parse.urlencode(params), headers={"User-Agent": UA})
    for attempt in range(5):
        time.sleep(1.0 + attempt * 4)
        try:
            return json.load(urllib.request.urlopen(req, timeout=60))
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
    raise SystemExit("Commons не отвечает (429) - попробуйте позже")


def clean(s):
    return html.unescape(re.sub("<[^>]+>", "", s or "")).strip()


def file_info(titles):
    """title -> {thumb, author, license, page} через Commons API (пачкой)."""
    out = {}
    for i in range(0, len(titles), 40):
        r = get_json("https://commons.wikimedia.org/w/api.php", {
            "action": "query", "format": "json", "titles": "|".join(titles[i:i + 40]),
            "prop": "imageinfo", "iiprop": "url|extmetadata|mime", "iiurlwidth": THUMB_W,
        })
        for p in r["query"]["pages"].values():
            ii = (p.get("imageinfo") or [{}])[0]
            if not ii.get("thumburl") or not str(ii.get("mime", "")).startswith("image/"):
                continue
            meta = ii.get("extmetadata", {})
            out[p["title"]] = {
                "thumb": ii["thumburl"],
                "page": ii.get("descriptionurl", ""),
                "author": clean(meta.get("Artist", {}).get("value", ""))[:80],
                "license": clean(meta.get("LicenseShortName", {}).get("value", "")),
            }
    return out


def main():
    stations = json.load(open(os.path.join(ASSETS, "schedule.json"), encoding="utf-8"))["stations"]
    rows = get_json("https://query.wikidata.org/sparql", {"query": SPARQL, "format": "json"})["results"]["bindings"]
    by_name = {}
    for b in rows:
        name = b["sLabel"]["value"]
        e = by_name.setdefault(name, {"imgs": [], "cat": None})
        if "img" in b:
            t = "File:" + urllib.parse.unquote(b["img"]["value"].rsplit("/", 1)[1])
            if t not in e["imgs"]:
                e["imgs"].append(t)
        if "cat" in b:
            e["cat"] = b["cat"]["value"]

    result = {}
    for s in stations:
        e = by_name.get(s["name"], {"imgs": [], "cat": None})
        titles = list(e["imgs"])
        if e["cat"]:
            r = get_json("https://commons.wikimedia.org/w/api.php", {
                "action": "query", "format": "json", "list": "categorymembers",
                "cmtitle": "Category:" + e["cat"], "cmtype": "file", "cmlimit": 30,
            })
            for m in r["query"]["categorymembers"]:
                if m["title"] not in titles and re.search(r"\.(jpe?g|png)$", m["title"], re.I):
                    titles.append(m["title"])
        info = file_info(titles[: MAX_PER_STATION * 2])
        photos = [dict(title=t, **info[t]) for t in titles if t in info][:MAX_PER_STATION]
        result[s["id"]] = photos
        print(f'{s["name"]:22s} -> {len(photos)} фото (категория: {e["cat"]})')

    path = os.path.join(ASSETS, "photos.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"source": "Wikimedia Commons", "photos": result}, f, ensure_ascii=False, indent=1)
    print("saved:", path)


if __name__ == "__main__":
    main()
