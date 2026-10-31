"""Phân tích chữ trên biển phụ thành điều kiện áp dụng (docs/04-thiet-ke-ai.md §6)."""

from __future__ import annotations

import re
import unicodedata

_TIME = re.compile(
    r"(?:từ\s*)?(\d{1,2})\s*h\s*(\d{2})?\s*(?:-|–|—|đến)\s*(\d{1,2})\s*h\s*(\d{2})?", re.IGNORECASE
)
_DISTANCE_AHEAD = re.compile(r"cách\s*(\d+)\s*m\b", re.IGNORECASE)
_ZONE_LENGTH = re.compile(r"(?:đoạn đường dài|dài)\s*(\d+)\s*m\b", re.IGNORECASE)
_WEIGHT = re.compile(r">\s*(\d+(?:[.,]\d+)?)\s*t\b", re.IGNORECASE)

# Từ khoá → loại xe của hệ thống. Thứ tự quan trọng: cụm dài khớp trước.
_VEHICLES = [
    ("xe khách", "BUS"),
    ("xe buýt", "BUS"),
    ("xe tải", "TRUCK"),
    ("xe con", "CAR"),
    ("ô tô", "CAR"),
]
_EXCEPT = re.compile(r"\b(?:ngoại trừ|trừ)\b", re.IGNORECASE)


def parse(text: str) -> dict:
    """Trả {"rawText", "parsed", "conditions"}; conditions=None nếu không nhận ra mẫu nào."""
    raw = text
    text = unicodedata.normalize("NFC", text).lower().strip()
    cond: dict = {}

    windows = [
        {"from": f"{int(h1):02d}:{int(m1 or 0):02d}", "to": f"{int(h2):02d}:{int(m2 or 0):02d}"}
        for h1, m1, h2, m2 in _TIME.findall(text)
        if int(h1) <= 24 and int(h2) <= 24
    ]
    if windows:
        cond["timeWindows"] = windows

    include, exclude = _vehicles(text)
    if include or exclude:
        cond["vehicles"] = {"include": include, "exclude": exclude}

    if m := _DISTANCE_AHEAD.search(text):
        cond["distanceAheadM"] = int(m.group(1))
    if m := _ZONE_LENGTH.search(text):
        cond["zoneLengthM"] = int(m.group(1))
    if m := _WEIGHT.search(text):
        cond["weightLimitTon"] = float(m.group(1).replace(",", "."))

    return {"rawText": raw, "parsed": bool(cond), "conditions": cond or None}


def _vehicles(text: str) -> tuple[list[str], list[str]]:
    split = _EXCEPT.split(text, maxsplit=1)
    include = _find_vehicles(split[0])
    exclude = _find_vehicles(split[1]) if len(split) > 1 else []
    return include, exclude


def _find_vehicles(part: str) -> list[str]:
    found: list[str] = []
    for word, kind in _VEHICLES:
        if word in part:
            part = part.replace(word, " ")
            if kind not in found:
                found.append(kind)
    return found
