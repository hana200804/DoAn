"""Danh mục nhãn hai cấp: nhóm (detector) và mã biển (classifier)."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

import yaml

DEFAULT_PATH = Path(__file__).resolve().parents[2] / "data" / "labels.yaml"


@dataclass(frozen=True)
class LabelMap:
    version: int
    groups: list[str]
    codes: list[dict]
    sign_height_m: dict[str, float]

    @classmethod
    def load(cls, path: Path = DEFAULT_PATH) -> LabelMap:
        raw = yaml.safe_load(Path(path).read_text(encoding="utf-8"))
        lm = cls(raw["version"], raw["groups"], raw["codes"], raw.get("sign_height_m", {}))
        lm.validate()
        return lm

    def validate(self) -> None:
        seen: set[str] = set()
        for c in self.codes:
            if c["code"] in seen:
                raise ValueError(f"Mã trùng: {c['code']}")
            if c["group"] not in self.groups:
                raise ValueError(f"{c['code']}: nhóm không hợp lệ {c['group']}")
            seen.add(c["code"])

    @property
    def code_names(self) -> list[str]:
        """Thứ tự lớp của classifier."""
        return [c["code"] for c in self.codes]

    def group_of(self, code: str) -> str:
        return next(c["group"] for c in self.codes if c["code"] == code)

    def group_index(self, code: str) -> int:
        return self.groups.index(self.group_of(code))

    def to_sql(self) -> str:
        """Câu lệnh nạp bảng sign_types của sign-service."""

        def q(s: str | None) -> str:
            return "NULL" if s is None else "'" + str(s).replace("'", "''") + "'"

        rows = [
            f"  ({q(c['code'])}, {q(c['group'].upper())}, {q(c['name'])}, "
            f"{c.get('speed_kmh', 'NULL')}, {self.version})"
            for c in self.codes
        ]
        return (
            "INSERT INTO sign_types (code, group_code, name, speed_kmh, label_map_version) VALUES\n"
            + ",\n".join(rows)
            + "\nON DUPLICATE KEY UPDATE group_code = VALUES(group_code), name = VALUES(name),\n"
            "  speed_kmh = VALUES(speed_kmh), label_map_version = VALUES(label_map_version);\n"
        )
