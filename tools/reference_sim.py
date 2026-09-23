#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Референс-реализация логики TrainSimulator на Python - чтобы проверить модель
на примерах пользователя ДО порта в Kotlin.

Два выхода из одних данных:
  1) панель станции: 2 ближайших поезда + сколько прошло с последнего (по направлению);
  2) поезда «в пути» на перегонах: позиция как доля пройденного времени между станциями.

Запуск:  python tools/reference_sim.py
"""
import json
import os

SOUTH = "to_botanicheskaya"
NORTH = "to_prospekt_kosmonavtov"

HERE = os.path.dirname(os.path.abspath(__file__))
DOC = json.load(open(os.path.join(HERE, "..", "app", "src", "main", "assets", "schedule.json"), encoding="utf-8"))
STATIONS = DOC["stations"]
ID2IDX = {s["id"]: i for i, s in enumerate(STATIONS)}
ID2NAME = {s["id"]: s["name"] for s in STATIONS}


def to_min(hhmm):
    """'HH:MM' -> служебная минута суток. Часы 0..2 -> +24ч, чтобы полуночные
    рейсы шли ПОСЛЕ 23:xx. Та же шкала применяется и к 'сейчас' (minutes_now)."""
    h, m = map(int, hhmm.split(":"))
    if h <= 2:
        h += 24
    return h * 60 + m


# now считаем по той же служебной шкале, что и расписание
minutes_now = to_min


# ---------- 1) панель станции ----------
def station_panel(station_id, day_type, now_hhmm):
    now = minutes_now(now_hhmm)
    sched = DOC["schedule"][day_type].get(station_id, {})
    result = {}
    for d, times in sched.items():
        pairs = sorted(((to_min(t), t) for t in times))  # (служебная минута, строка)
        upcoming = [(mm, t) for mm, t in pairs if mm >= now]
        passed = [mm for mm, _ in pairs if mm < now]
        result[d] = {
            "next": [(t, mm - now) for mm, t in upcoming[:2]],
            "since_last": (now - passed[-1]) if passed else None,
        }
    return result


# ---------- 2) поезда на перегонах ----------
def inflight_trains(day_type, now_hhmm):
    now = minutes_now(now_hhmm)
    trains = []
    n = len(STATIONS)
    for d in (SOUTH, NORTH):
        # порядок пар: юг - индекс растёт (A=i, B=i+1); север - индекс падает (A=i, B=i-1)
        pairs = [(i, i + 1) for i in range(n - 1)] if d == SOUTH else [(i, i - 1) for i in range(n - 1, 0, -1)]
        for a_idx, b_idx in pairs:
            a_id, b_id = STATIONS[a_idx]["id"], STATIONS[b_idx]["id"]
            a_times = sorted(to_min(t) for t in DOC["schedule"][day_type].get(a_id, {}).get(d, []))
            b_times = sorted(to_min(t) for t in DOC["schedule"][day_type].get(b_id, {}).get(d, []))
            if not a_times or not b_times:
                continue
            for k, tA in enumerate(a_times):
                next_tA = a_times[k + 1] if k + 1 < len(a_times) else 10 ** 9
                # tB - ближайшее время на B строго позже tA и раньше следующего поезда с A
                tB = next((t for t in b_times if tA < t < next_tA), None)
                if tB is None:
                    continue
                if tA <= now < tB:
                    frac = (now - tA) / (tB - tA)
                    trains.append({"dir": d, "from": a_id, "to": b_id,
                                   "frac": round(frac, 3), "eta_b": tB - now})
                    break  # на перегоне максимум один поезд
    return trains


def fmt_panel(station_id, day_type, now):
    print(f"\n== Станция {ID2NAME[station_id]} | {day_type} | {now} ==")
    panel = station_panel(station_id, day_type, now)
    labels = {SOUTH: "в сторону Ботанической (с Проспекта)",
              NORTH: "в сторону Проспекта Космонавтов (с Машиностроителей)"}
    for d in (SOUTH, NORTH):
        if d not in panel:
            continue
        p = panel[d]
        nxt = ", ".join(f"{t} (через {dt} мин)" for t, dt in p["next"])
        sl = f"{p['since_last']} мин назад" if p["since_last"] is not None else "-"
        print(f"  {labels[d]}:")
        print(f"     следующие: {nxt}")
        print(f"     последний прошёл: {sl}")


if __name__ == "__main__":
    # Пример пользователя №2: Уралмаш, суббота 12:00 -> 5 и 6 минут
    fmt_panel("uralmash", "weekend", "12:00")

    print("\n== Поезда в пути | weekend | 12:00 (первые 8) ==")
    for t in inflight_trains("weekend", "12:00")[:8]:
        arrow = "v" if t["dir"] == SOUTH else "^"
        print(f"  {arrow} {ID2NAME[t['from']]:20s} -> {ID2NAME[t['to']]:20s} "
              f"прогресс {int(t['frac']*100):3d}%  прибытие через {t['eta_b']} мин")
