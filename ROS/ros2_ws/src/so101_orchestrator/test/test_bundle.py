"""배포 번들(tgz) 해제 self-check — 레이아웃 2종·traversal 차단 (S15P21A103-46)."""
import tarfile

import pytest

from so101_orchestrator import bundle


def _make_bundle(tmp_path, inner: str | None, extra=()):
    """inner=None 이면 파일이 tgz 최상위, 아니면 inner/ 한 겹 아래."""
    src = tmp_path / "src"
    root = src / inner if inner else src
    root.mkdir(parents=True)
    for name in bundle.REQUIRED + tuple(extra):
        (root / name).write_text("x", encoding="utf-8")
    tgz = tmp_path / "b.tgz"
    with tarfile.open(tgz, "w:gz") as tar:
        for item in sorted(src.iterdir()):
            tar.add(item, arcname=item.name)
    return tgz


def test_extract_flat_layout(tmp_path):
    """필수 파일이 tgz 최상위에 있는 경우."""
    tgz = _make_bundle(tmp_path, None, extra=("dataset.report.json",))
    model_dir = bundle.extract_bundle(tgz, tmp_path / "out")
    assert model_dir == tmp_path / "out"
    assert (model_dir / "encoder.pt").is_file()
    assert bundle.missing_recommended(model_dir) == []


def test_extract_nested_layout(tmp_path):
    """tgz 가 <name>_jetson/ 한 겹으로 감싼 경우도 실제 모델 디렉터리를 찾는다."""
    tgz = _make_bundle(tmp_path, "pick_v1_jetson")
    model_dir = bundle.extract_bundle(tgz, tmp_path / "out")
    assert model_dir == tmp_path / "out" / "pick_v1_jetson"
    assert (model_dir / "denoiser.pt").is_file()
    # dataset.report.json 없으면 경고 목록에 뜬다(런타임 --move 시 필요)
    assert bundle.missing_recommended(model_dir) == ["dataset.report.json"]


def test_missing_required_raises(tmp_path):
    src = tmp_path / "src"
    src.mkdir()
    (src / "metadata.json").write_text("{}", encoding="utf-8")
    tgz = tmp_path / "bad.tgz"
    with tarfile.open(tgz, "w:gz") as tar:
        tar.add(src / "metadata.json", arcname="metadata.json")
    with pytest.raises(FileNotFoundError):
        bundle.extract_bundle(tgz, tmp_path / "out")


def test_path_traversal_blocked(tmp_path):
    """../ 로 상위에 쓰려는 tgz 는 거부."""
    evil = tmp_path / "evil.txt"
    evil.write_text("pwned", encoding="utf-8")
    tgz = tmp_path / "evil.tgz"
    with tarfile.open(tgz, "w:gz") as tar:
        tar.add(evil, arcname="../escaped.txt")
    with pytest.raises(ValueError):
        bundle.extract_bundle(tgz, tmp_path / "out")
