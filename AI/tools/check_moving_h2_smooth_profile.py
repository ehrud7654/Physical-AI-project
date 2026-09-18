"""Focused, MuJoCo-free checks for moving-H=2 quintic command fractions."""
from __future__ import annotations

from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import numpy as np

from tools.probe_moving_history_object_only import _command_fraction


def main() -> int:
    assert _command_fraction("step", 0.01, 0.1) == 1.0
    values = [_command_fraction("quintic", value, 0.4)
              for value in (0.0, 0.1, 0.2, 0.4, 0.5)]
    assert np.allclose(values, [0.0, 0.103515625, 0.5, 1.0, 1.0])
    assert values == sorted(values)
    for profile, elapsed, duration in (
            ("bad", 0.1, 0.4), ("quintic", -0.1, 0.4),
            ("quintic", 0.1, 0.09)):
        try:
            _command_fraction(profile, elapsed, duration)
        except ValueError:
            pass
        else:
            raise AssertionError("invalid smooth-profile input was accepted")
    print("moving H2 smooth profile checks: 4/4 passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
