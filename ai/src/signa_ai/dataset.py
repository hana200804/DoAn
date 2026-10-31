"""Chuẩn bị dữ liệu: manifest có hash, chia tập theo chuỗi ảnh, sinh tập detector và classifier.

Dữ liệu gốc gán nhãn theo **mã biển** ở định dạng YOLO: ``images/<seq>/<name>.jpg`` và
``labels/<seq>/<name>.txt`` (mỗi dòng ``code_index cx cy w h``, chỉ số theo ``LabelMap.code_names``).
Thư mục cấp một dưới ``images/`` là mã chuỗi (tuyến đường, video, nguồn) để chia tập không rò rỉ.
"""

from __future__ import annotations

import csv
import hashlib
import random
from pathlib import Path

from .labels import LabelMap

IMAGE_EXT = {".jpg", ".jpeg", ".png"}


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def images(root: Path) -> list[Path]:
    return sorted(p for p in (root / "images").rglob("*") if p.suffix.lower() in IMAGE_EXT)


def sequence_of(root: Path, image: Path) -> str:
    return image.relative_to(root / "images").parts[0]


def split_sequences(sequences: list[str], ratios=(0.7, 0.15, 0.15), seed: int = 0) -> dict[str, str]:
    """Gán mỗi chuỗi vào train/val/test; mọi khung của một chuỗi luôn cùng một tập (UT-A05)."""
    uniq = sorted(set(sequences))
    random.Random(seed).shuffle(uniq)
    n = len(uniq)
    n_train = max(1, round(n * ratios[0])) if n else 0
    n_val = round(n * ratios[1])
    out = {}
    for i, s in enumerate(uniq):
        out[s] = "train" if i < n_train else "val" if i < n_train + n_val else "test"
    return out


def label_path(root: Path, image: Path) -> Path:
    return root / "labels" / image.relative_to(root / "images").with_suffix(".txt")


def validate(root: Path, lm: LabelMap) -> list[str]:
    """Trả danh sách lỗi: chỉ số lớp ngoài danh mục, toạ độ ngoài [0, 1], thiếu file nhãn."""
    errors = []
    n_codes = len(lm.code_names)
    for img in images(root):
        lbl = label_path(root, img)
        if not lbl.exists():
            errors.append(f"{img}: thiếu file nhãn")
            continue
        for i, line in enumerate(lbl.read_text().splitlines(), 1):
            parts = line.split()
            if not parts:
                continue
            if len(parts) != 5:
                errors.append(f"{lbl}:{i}: cần 5 giá trị")
                continue
            cls, *box = parts
            if not cls.isdigit() or int(cls) >= n_codes:
                errors.append(f"{lbl}:{i}: chỉ số lớp {cls} không có trong danh mục")
            if any(not 0 <= float(v) <= 1 for v in box):
                errors.append(f"{lbl}:{i}: toạ độ ngoài [0, 1]")
    return errors


def write_manifest(root: Path, out: Path, lm: LabelMap, seed: int = 0) -> int:
    """Manifest CSV theo docs/05-du-lieu.md §9. Trả số ảnh."""
    imgs = images(root)
    splits = split_sequences([sequence_of(root, p) for p in imgs], seed=seed)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["path", "sha256", "split", "sequence", "label_map_version"])
        for img in imgs:
            seq = sequence_of(root, img)
            w.writerow([img.relative_to(root).as_posix(), sha256(img), splits[seq], seq, lm.version])
    return len(imgs)


def read_manifest(path: Path) -> list[dict]:
    with path.open(encoding="utf-8") as f:
        return list(csv.DictReader(f))


def build_detector_dataset(root: Path, manifest: Path, out: Path, lm: LabelMap) -> Path:
    """Tập YOLO theo nhóm biển: đổi chỉ số mã → chỉ số nhóm. Trả đường dẫn data.yaml."""
    for row in read_manifest(manifest):
        img = root / row["path"]
        split = "val" if row["split"] == "test" else row["split"]
        if row["split"] == "test":
            continue  # tập test chỉ dùng cho đánh giá cuối, không đưa vào huấn luyện
        dst_img = out / split / "images" / img.name
        dst_lbl = out / split / "labels" / img.with_suffix(".txt").name
        dst_img.parent.mkdir(parents=True, exist_ok=True)
        dst_lbl.parent.mkdir(parents=True, exist_ok=True)
        dst_img.write_bytes(img.read_bytes())
        lines = []
        for line in label_path(root, img).read_text().splitlines():
            if line.strip():
                cls, *box = line.split()
                lines.append(" ".join([str(lm.group_index(lm.code_names[int(cls)])), *box]))
        dst_lbl.write_text("\n".join(lines) + "\n")
    data_yaml = out / "data.yaml"
    data_yaml.write_text(
        f"path: {out.resolve().as_posix()}\ntrain: train/images\nval: val/images\nnames: {lm.groups}\n",
        encoding="utf-8",
    )
    return data_yaml


def build_classifier_dataset(root: Path, manifest: Path, out: Path, lm: LabelMap, pad: float = 0.1) -> Path:
    """Ảnh cắt theo mã biển, xếp thư mục ``<split>/<code>/`` cho YOLO classification."""
    import cv2  # chỉ cần khi huấn luyện

    for row in read_manifest(manifest):
        if row["split"] == "test":
            continue
        img_path = root / row["path"]
        img = cv2.imread(str(img_path))
        h, w = img.shape[:2]
        for i, line in enumerate(label_path(root, img_path).read_text().splitlines()):
            if not line.strip():
                continue
            cls, cx, cy, bw, bh = line.split()
            cx, cy, bw, bh = (
                float(cx) * w,
                float(cy) * h,
                float(bw) * w * (1 + 2 * pad),
                float(bh) * h * (1 + 2 * pad),
            )
            x0, y0 = max(0, int(cx - bw / 2)), max(0, int(cy - bh / 2))
            x1, y1 = min(w, int(cx + bw / 2)), min(h, int(cy + bh / 2))
            if x1 - x0 < 8 or y1 - y0 < 8:
                continue
            dst = out / row["split"] / lm.code_names[int(cls)] / f"{img_path.stem}_{i}.jpg"
            dst.parent.mkdir(parents=True, exist_ok=True)
            cv2.imwrite(str(dst), img[y0:y1, x0:x1])
    return out
