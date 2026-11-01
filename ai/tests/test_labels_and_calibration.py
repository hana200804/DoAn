import numpy as np

from signa_ai.calibration import apply_temperature, ece, fit_temperature
from signa_ai.labels import LabelMap


def test_label_map_is_consistent_and_groups_codes():
    lm = LabelMap.load()
    assert lm.group_of("P.127_50") == "prohibitory"
    assert lm.group_index("W.224") == lm.groups.index("warning")
    sql = lm.to_sql()
    assert "('P.127_50', 'PROHIBITORY', 'Tốc độ tối đa cho phép 50 km/h', 50, 1)" in sql


def test_temperature_reduces_overconfidence():
    rng = np.random.default_rng(0)
    labels = rng.integers(0, 3, 600)
    probs = np.full((600, 3), 0.025)
    # Mô hình luôn tự tin 95% nhưng chỉ đúng 60%
    pred = np.where(rng.random(600) < 0.6, labels, (labels + 1) % 3)
    probs[np.arange(600), pred] = 0.95
    t = fit_temperature(probs, labels)
    assert t > 1
    assert ece(apply_temperature(probs, t), labels) < ece(probs, labels)
