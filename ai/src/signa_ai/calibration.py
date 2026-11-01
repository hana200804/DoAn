"""Hiệu chỉnh độ tin cậy bằng temperature scaling và đo ECE (docs/04-thiet-ke-ai.md §3)."""

from __future__ import annotations

import numpy as np


def apply_temperature(probs: np.ndarray, t: float) -> np.ndarray:
    """Áp nhiệt độ lên xác suất softmax: tương đương chia logit cho t."""
    logits = np.log(np.clip(probs, 1e-12, 1.0)) / t
    logits -= logits.max(axis=1, keepdims=True)
    e = np.exp(logits)
    return e / e.sum(axis=1, keepdims=True)


def ece(probs: np.ndarray, labels: np.ndarray, bins: int = 15) -> float:
    """Expected Calibration Error theo độ tin cậy lớn nhất."""
    conf = probs.max(axis=1)
    correct = probs.argmax(axis=1) == labels
    edges = np.linspace(0, 1, bins + 1)
    total = 0.0
    for lo, hi in zip(edges[:-1], edges[1:], strict=True):
        mask = (conf > lo) & (conf <= hi)
        if mask.any():
            total += mask.mean() * abs(conf[mask].mean() - correct[mask].mean())
    return float(total)


def fit_temperature(probs: np.ndarray, labels: np.ndarray) -> float:
    """Chọn t trong lưới [0.5, 5] làm nhỏ nhất negative log-likelihood trên tập validation."""
    best_t, best_nll = 1.0, float("inf")
    for t in np.linspace(0.5, 5.0, 91):
        p = apply_temperature(probs, t)
        nll = -np.log(np.clip(p[np.arange(len(labels)), labels], 1e-12, 1.0)).mean()
        if nll < best_nll:
            best_t, best_nll = float(t), nll
    return best_t
