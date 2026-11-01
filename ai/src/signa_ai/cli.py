"""Dòng lệnh: ``signa-ai <lệnh>``. Xem ``signa-ai -h``."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from . import dataset, subsign
from .labels import DEFAULT_PATH, LabelMap


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="signa-ai")
    p.add_argument("--labels", type=Path, default=DEFAULT_PATH)
    sub = p.add_subparsers(dest="cmd", required=True)

    sub.add_parser("labels-sql", help="In câu lệnh nạp bảng sign_types")

    v = sub.add_parser("validate", help="Kiểm tra nhãn của bộ dữ liệu gốc")
    v.add_argument("root", type=Path)

    m = sub.add_parser("manifest", help="Sinh manifest có hash và chia tập theo chuỗi")
    m.add_argument("root", type=Path)
    m.add_argument("out", type=Path)
    m.add_argument("--seed", type=int, default=0)

    pr = sub.add_parser("prepare", help="Sinh tập detector (theo nhóm) và classifier (ảnh cắt)")
    pr.add_argument("root", type=Path)
    pr.add_argument("manifest", type=Path)
    pr.add_argument("out", type=Path)

    t = sub.add_parser("train", help="Huấn luyện detector + classifier, hiệu chỉnh, xuất TFLite INT8")
    t.add_argument("prepared", type=Path, help="Thư mục do lệnh prepare sinh ra")
    t.add_argument("out", type=Path)
    t.add_argument("--version", required=True, help="Ví dụ model-v0.1")
    t.add_argument("--epochs", type=int, default=100)
    t.add_argument("--batch", type=int, default=16)

    s = sub.add_parser("subsign", help="Phân tích chữ biển phụ")
    s.add_argument("text")

    a = p.parse_args(argv)
    lm = LabelMap.load(a.labels)

    if a.cmd == "labels-sql":
        print(lm.to_sql(), end="")
    elif a.cmd == "validate":
        errors = dataset.validate(a.root, lm)
        print("\n".join(errors) or "Không có lỗi")
        return 1 if errors else 0
    elif a.cmd == "manifest":
        print(f"{dataset.write_manifest(a.root, a.out, lm, a.seed)} ảnh → {a.out}")
    elif a.cmd == "prepare":
        print(dataset.build_detector_dataset(a.root, a.manifest, a.out / "detector", lm))
        print(dataset.build_classifier_dataset(a.root, a.manifest, a.out / "classifier", lm))
    elif a.cmd == "train":
        from . import train

        det = train.train_detector(a.prepared / "detector" / "data.yaml", a.out, a.epochs, batch=a.batch)
        cls = train.train_classifier(a.prepared / "classifier", a.out, a.epochs)
        cal = train.calibrate_classifier(cls, a.prepared / "classifier" / "val", lm)
        print(json.dumps(cal, indent=2))
        bundle = train.export_bundle(
            det,
            cls,
            a.out / a.version,
            a.version,
            lm,
            a.prepared / "detector" / "data.yaml",
            cal["temperature"],
        )
        print(f"Bộ mô hình: {bundle}")
    elif a.cmd == "subsign":
        print(json.dumps(subsign.parse(a.text), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
