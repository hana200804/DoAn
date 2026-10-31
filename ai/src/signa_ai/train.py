"""Huấn luyện, hiệu chỉnh và xuất hai mô hình bằng Ultralytics (cần extra ``train``)."""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np

from .calibration import ece, fit_temperature
from .dataset import sha256
from .labels import LabelMap


def train_detector(data_yaml: Path, out: Path, epochs: int, imgsz: int = 640, batch: int = 16) -> Path:
    from ultralytics import YOLO

    model = YOLO("yolov8n.pt")
    # Không lật ngang: làm đổi nghĩa biển rẽ trái/phải (docs/05-du-lieu.md §6)
    model.train(
        data=str(data_yaml),
        epochs=epochs,
        imgsz=imgsz,
        batch=batch,
        fliplr=0.0,
        degrees=10.0,
        project=str(out),
        name="detector",
        exist_ok=True,
    )
    return out / "detector" / "weights" / "best.pt"


def train_classifier(data_dir: Path, out: Path, epochs: int, imgsz: int = 96, batch: int = 64) -> Path:
    from ultralytics import YOLO

    model = YOLO("yolov8n-cls.pt")
    model.train(
        data=str(data_dir),
        epochs=epochs,
        imgsz=imgsz,
        batch=batch,
        fliplr=0.0,
        project=str(out),
        name="classifier",
        exist_ok=True,
    )
    return out / "classifier" / "weights" / "best.pt"


def calibrate_classifier(weights: Path, val_dir: Path, lm: LabelMap) -> dict:
    """Chạy classifier trên tập val, trả nhiệt độ tối ưu và ECE trước/sau."""
    from ultralytics import YOLO

    model = YOLO(str(weights))
    names = model.names
    probs, labels = [], []
    for code_dir in sorted(p for p in val_dir.iterdir() if p.is_dir()):
        idx = next(i for i, n in names.items() if n == code_dir.name)
        for r in model.predict(source=str(code_dir), verbose=False, stream=True):
            probs.append(r.probs.data.cpu().numpy())
            labels.append(idx)
    probs_arr, labels_arr = np.stack(probs), np.array(labels)
    t = fit_temperature(probs_arr, labels_arr)
    from .calibration import apply_temperature

    return {
        "temperature": t,
        "ece_before": ece(probs_arr, labels_arr),
        "ece_after": ece(apply_temperature(probs_arr, t), labels_arr),
    }


def export_bundle(
    detector: Path,
    classifier: Path,
    out: Path,
    version: str,
    lm: LabelMap,
    detector_data: Path,
    temperature: float,
) -> Path:
    """Xuất cả hai sang TFLite INT8 và viết manifest bộ mô hình cho app (FR-15)."""
    from ultralytics import YOLO

    out.mkdir(parents=True, exist_ok=True)
    det = Path(YOLO(str(detector)).export(format="tflite", int8=True, data=str(detector_data), imgsz=640))
    cls = Path(YOLO(str(classifier)).export(format="tflite", int8=True, imgsz=96))
    det_out, cls_out = out / "detector.tflite", out / "classifier.tflite"
    det_out.write_bytes(det.read_bytes())
    cls_out.write_bytes(cls.read_bytes())
    classifier_names = YOLO(str(classifier)).names
    manifest = {
        "version": version,
        "labelMapVersion": lm.version,
        "detector": {"file": det_out.name, "sha256": sha256(det_out), "inputSize": 640, "groups": lm.groups},
        "classifier": {
            "file": cls_out.name,
            "sha256": sha256(cls_out),
            "inputSize": 96,
            "codes": [classifier_names[i] for i in range(len(classifier_names))],
            "temperature": temperature,
        },
        "signHeightM": lm.sign_height_m,
    }
    (out / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return out
