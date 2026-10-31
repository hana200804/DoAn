"""UT-A01 → UT-A04 (docs/08-kiem-thu.md §2.3)."""

from signa_ai.subsign import parse


def test_ut_a01_simple_time_window():
    r = parse("6h-22h")
    assert r["parsed"]
    assert r["conditions"]["timeWindows"] == [{"from": "06:00", "to": "22:00"}]


def test_ut_a02_time_window_with_minutes_and_words():
    r = parse("Từ 6h30 đến 22h")
    assert r["conditions"]["timeWindows"] == [{"from": "06:30", "to": "22:00"}]


def test_ut_a03_except_vehicle():
    r = parse("Trừ xe buýt")
    assert r["conditions"]["vehicles"] == {"include": [], "exclude": ["BUS"]}


def test_ut_a04_unknown_text_keeps_raw():
    r = parse("Khu vực đang thi công")
    assert r == {"rawText": "Khu vực đang thi công", "parsed": False, "conditions": None}


def test_combined_conditions():
    r = parse("Xe tải > 10T, cách 200 m, 6h-9h, 16h-19h")
    c = r["conditions"]
    assert c["vehicles"]["include"] == ["TRUCK"]
    assert c["weightLimitTon"] == 10.0
    assert c["distanceAheadM"] == 200
    assert len(c["timeWindows"]) == 2
