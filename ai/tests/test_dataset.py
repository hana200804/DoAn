"""UT-A05, UT-A06 và kiểm tra nhãn."""

from pathlib import Path

from signa_ai import dataset
from signa_ai.labels import LabelMap


def _make(root: Path, seq: str, name: str, label: str, content: bytes = b"img") -> Path:
    img = root / "images" / seq / f"{name}.jpg"
    img.parent.mkdir(parents=True, exist_ok=True)
    img.write_bytes(content)
    lbl = root / "labels" / seq / f"{name}.txt"
    lbl.parent.mkdir(parents=True, exist_ok=True)
    lbl.write_text(label)
    return img


def test_ut_a05_frames_of_one_sequence_share_a_split(tmp_path):
    for seq in ["route-a", "route-b", "route-c", "route-d"]:
        for i in range(3):
            _make(tmp_path, seq, f"f{i}", "0 0.5 0.5 0.1 0.1\n")
    lm = LabelMap.load()
    dataset.write_manifest(tmp_path, tmp_path / "m.csv", lm)
    rows = dataset.read_manifest(tmp_path / "m.csv")
    by_seq: dict[str, set[str]] = {}
    for r in rows:
        by_seq.setdefault(r["sequence"], set()).add(r["split"])
    assert all(len(splits) == 1 for splits in by_seq.values())


def test_ut_a06_hash_changes_only_for_edited_image(tmp_path):
    a = _make(tmp_path, "s", "a", "0 0.5 0.5 0.1 0.1\n", b"aaa")
    _make(tmp_path, "s", "b", "0 0.5 0.5 0.1 0.1\n", b"bbb")
    lm = LabelMap.load()
    dataset.write_manifest(tmp_path, tmp_path / "1.csv", lm)
    a.write_bytes(b"changed")
    dataset.write_manifest(tmp_path, tmp_path / "2.csv", lm)
    before = {r["path"]: r["sha256"] for r in dataset.read_manifest(tmp_path / "1.csv")}
    after = {r["path"]: r["sha256"] for r in dataset.read_manifest(tmp_path / "2.csv")}
    assert before["images/s/a.jpg"] != after["images/s/a.jpg"]
    assert before["images/s/b.jpg"] == after["images/s/b.jpg"]


def test_validate_reports_bad_class_and_coordinates(tmp_path):
    _make(tmp_path, "s", "ok", "0 0.5 0.5 0.1 0.1\n")
    _make(tmp_path, "s", "bad", "999 0.5 0.5 0.1 0.1\n0 1.5 0.5 0.1 0.1\n")
    errors = dataset.validate(tmp_path, LabelMap.load())
    assert len(errors) == 2
    assert all("bad.txt" in e for e in errors)
