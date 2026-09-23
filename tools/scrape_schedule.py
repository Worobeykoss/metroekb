#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Скрапер расписания Екатеринбургского метрополитена (metro-ektb.ru).

Со страниц вида `podrobnyy-grafik-st-<slug>-grafik_12` берёт таблицы прохода
поездов через станцию. На сайте опубликованы ТОЛЬКО выходные графики
(проверено по всем 9 станциям - будних таблиц в HTML нет).

Каждая таблица = один час в левой колонке, минуты через `;` в правой. Заголовок над
таблицей задаёт направление: «в сторону Ботанической» (на юг) или
«в сторону Проспекта Космонавтов» (на север). Конечные станции - одно направление.

Результат пишется в app/src/main/assets/schedule.json. Раздел schedule.weekday
остаётся пустым - его заполняем отдельно из скриншота будней (см. weekday_from_text.py).

Запуск:  python tools/scrape_schedule.py
"""
import json
import os
import re
import sys
import html
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120 Safari/537.36"

# Порядок станций север -> юг. Координаты - из OpenStreetMap (Overpass).
# Каждый элемент: (id, name, url, lat, lon)
STATIONS = [
    ("prospekt_kosmonavtov", "Проспект Космонавтов",
     "https://metro-ektb.ru/podrobnyy-grafik-st-prospekt-kosmonavtov-grafik_12", 56.90139, 60.61400),
    ("uralmash", "Уралмаш",
     "https://metro-ektb.ru/podrobnyy-grafik-st-uralmash-grafik_12", 56.88826, 60.61350),
    ("mashinostroiteley", "Машиностроителей",
     "https://metro-ektb.ru/podrobnyy-grafik-st-mashinostroiteley-grafik_12/", 56.87701, 60.61166),
    ("uralskaya", "Уральская",
     "https://metro-ektb.ru/podrobnyy-grafik-st-uralskaya-grafik_12", 56.85747, 60.60038),
    ("dinamo", "Динамо",
     "https://metro-ektb.ru/podrobnyy-grafik-stancii-dinamo-grafik_12", 56.84761, 60.59931),
    ("ploschad_1905", "Площадь 1905 года",
     "https://metro-ektb.ru/podrobnyy-grafik-st-ploschad-1905-goda-grafik_12/", 56.83687, 60.59916),
    ("geologicheskaya", "Геологическая",
     "https://metro-ektb.ru/podrobnyy-grafik-st-geologicheskaya-grafik_12/", 56.82783, 60.60224),
    ("chkalovskaya", "Чкаловская",
     "https://metro-ektb.ru/podrobnyy-grafik-st-chkalovskaya-grafik_12", 56.80770, 60.61030),
    ("botanicheskaya", "Ботаническая",
     "https://metro-ektb.ru/podrobnye-grafik-st-botanicheskaya-grafik_12", 56.79744, 60.63154),
]

SOUTH = "to_botanicheskaya"          # поезд идёт на юг (со стороны Проспекта Космонавтов)
NORTH = "to_prospekt_kosmonavtov"    # поезд идёт на север (со стороны Машиностроителей)

LINE = {"name": "Линия 1", "color": "#E8452A"}


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return urllib.request.urlopen(req, timeout=30).read()


def _clean(cell):
    return html.unescape(re.sub("<[^>]+>", "", cell)).strip()


def parse_minutes(cell):
    """'03; 15; 27; 39; 50.' -> ['03','15','27','39','50']"""
    return re.findall(r"\d{1,2}", cell.replace(".", " "))


def parse_page(raw):
    """Возвращает список (caption, {hour: [minutes]}) в порядке следования на странице."""
    txt = raw.decode("utf-8", errors="replace")
    parts = re.split(r"(<table.*?</table>)", txt, flags=re.S | re.I)
    result = []
    pending_caption = ""
    for seg in parts:
        if seg[:6].lower() == "<table":
            data = {}
            for row in re.findall(r"<tr.*?</tr>", seg, re.S | re.I):
                cells = [_clean(c) for c in re.findall(r"<t[dh].*?</t[dh]>", row, re.S | re.I)]
                if len(cells) >= 2 and re.fullmatch(r"\d{1,2}", cells[0]):
                    data[int(cells[0])] = parse_minutes(cells[1])
            result.append((pending_caption, data))
            pending_caption = ""
        else:
            for m in re.finditer(r"<p[^>]*>(.*?)</p>", seg, re.S | re.I):
                c = _clean(m.group(1))
                if c and any(w in c.lower() for w in ("сторону", "дни", "будн", "выходн", "рабоч")):
                    pending_caption = c
    return result


def direction_of(caption, station_index):
    low = caption.lower()
    if "ботаническ" in low:
        return SOUTH
    if "космонавт" in low:
        return NORTH
    # конечная с одной таблицей без явного направления:
    # Проспект Космонавтов (index 0) -> только на юг; Ботаническая (index 8) -> только на север
    return SOUTH if station_index == 0 else NORTH


def _service_hour(hh):
    """Часы 0..2 относятся к концу служебных суток (ночные рейсы после 23:xx)."""
    return hh + 24 if hh <= 2 else hh


def flatten(hour_map):
    """{5:['51'], 6:['03'], 0:['04']} -> ['05:51','06:03',...,'00:04']
    в истинном хронологическом порядке служебных суток (полуночные рейсы - в конце)."""
    out = []
    for hh in sorted(hour_map.keys(), key=_service_hour):
        for mm in hour_map[hh]:
            out.append(f"{hh:02d}:{int(mm):02d}")
    return out


def main():
    weekend = {}
    stations_meta = []
    for idx, (sid, name, url, lat, lon) in enumerate(STATIONS):
        raw = fetch(url)
        tables = parse_page(url and raw)
        dirs = {}
        for caption, hour_map in tables:
            d = direction_of(caption, idx)
            dirs[d] = flatten(hour_map)
        weekend[sid] = dirs
        stations_meta.append({"id": sid, "name": name, "lat": lat, "lon": lon})
        counts = {k: len(v) for k, v in dirs.items()}
        print(f"{name:22s} -> {counts}")

    doc = {
        "line": LINE,
        "stations": stations_meta,
        "schedule": {"weekend": weekend, "weekday": {}},
    }

    here = os.path.dirname(os.path.abspath(__file__))
    out_path = os.path.normpath(os.path.join(here, "..", "app", "src", "main", "assets", "schedule.json"))
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
    print("\nsaved:", out_path)


if __name__ == "__main__":
    sys.exit(main())
