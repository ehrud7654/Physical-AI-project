"""SO-101 forward/inverse kinematics from base_link to gripper TCP."""
from __future__ import annotations

import math

import numpy as np

from .robot_calibration import JOINT_NAMES, RAD_LIMITS


JOINTS = (
    ((0.0388353, 0.0, 0.0624), (math.pi, 0.0, -math.pi)),
    ((-0.0303992, -0.0182778, -0.0542), (-math.pi / 2, -math.pi / 2, 0.0)),
    ((-0.11257, -0.028, 0.0), (0.0, 0.0, math.pi / 2)),
    ((-0.1349, 0.0052, 0.0), (0.0, 0.0, -math.pi / 2)),
    ((0.0, -0.0611, 0.0181), (math.pi / 2, 0.0486795, math.pi)),
)
FIXED_TO_TCP = (
    ((-0.0269679069519, -0.00131401753426, -0.0801000900269),
     (1.5707954168329252, 3.628942180264e-06, 1.6194722907041545)),
    ((0.0, -0.0780187530518, 0.0270000119209), (math.pi / 2, 0.0, 0.0)),
)
TABLE = {"j1_axis_xy_mm": (410.0, 61.2), "base_z_mm": 2.4}


def _rpy(r: float, p: float, y: float) -> np.ndarray:
    cr, sr = math.cos(r), math.sin(r)
    cp, sp = math.cos(p), math.sin(p)
    cy, sy = math.cos(y), math.sin(y)
    rz = np.array([[cy, -sy, 0], [sy, cy, 0], [0, 0, 1]])
    ry = np.array([[cp, 0, sp], [0, 1, 0], [-sp, 0, cp]])
    rx = np.array([[1, 0, 0], [0, cr, -sr], [0, sr, cr]])
    return rz @ ry @ rx


def _tf(xyz, rpy) -> np.ndarray:
    transform = np.eye(4)
    transform[:3, :3] = _rpy(*rpy)
    transform[:3, 3] = xyz
    return transform


def _rz(q: float) -> np.ndarray:
    transform = np.eye(4)
    c, s = math.cos(q), math.sin(q)
    transform[:2, :2] = [[c, -s], [s, c]]
    return transform


_JOINT_TFS = [_tf(*joint) for joint in JOINTS]
_TCP_TF = _tf(*FIXED_TO_TCP[0]) @ _tf(*FIXED_TO_TCP[1])


def fk(q) -> np.ndarray:
    transform = np.eye(4)
    for joint_transform, radians in zip(_JOINT_TFS, q):
        transform = transform @ joint_transform @ _rz(radians)
    return transform @ _TCP_TF


def ik(target_m, approach=None, finger=None, q0=None, iters=300, tol_mm=0.5):
    """Return nearby joint solution as (q, position_error_mm, ok)."""
    target = np.asarray(target_m, float)
    approach = None if approach is None else np.asarray(approach, float) / np.linalg.norm(approach)
    finger = None if finger is None else np.asarray(finger, float) / np.linalg.norm(finger)
    low, high = np.array(RAD_LIMITS).T
    pan = math.atan2(target[1], target[0] - 0.0388)
    if q0 is not None:
        # Incremental live waypoints must stay on the current wrist-roll branch.
        seeds = [np.asarray(q0, float)]
    else:
        seeds = [np.array([pan, 0.6, 1.0, -0.4, 0.0]), np.array([pan, 0.0, 0.0, 0.0, 0.0])]
        rng = np.random.default_rng(1)
        seeds += [
            np.concatenate(([pan], rng.uniform(low[1:] * 0.8, high[1:] * 0.8)))
            for _ in range(8)
        ]

    def residual(q):
        transform = fk(q)
        parts = [(target - transform[:3, 3]) * 10.0]
        if approach is not None:
            parts.append(np.cross(transform[:3, 2], approach) * 0.1)
        if finger is not None and approach is not None:
            perpendicular = finger - approach * (approach @ finger)
            parts.append(np.cross(transform[:3, 0], perpendicular) * 0.02)
        return np.concatenate(parts)

    best = None
    for seed in seeds:
        q = np.clip(seed, low, high)
        damping = 1e-3
        for _ in range(iters):
            current_residual = residual(q)
            jacobian = np.empty((current_residual.size, 5))
            for index in range(5):
                delta = np.zeros(5)
                delta[index] = 1e-6
                jacobian[:, index] = (residual(q + delta) - current_residual) / 1e-6
            step = np.linalg.solve(
                jacobian.T @ jacobian + damping * np.eye(5),
                -jacobian.T @ current_residual,
            )
            step = np.clip(step, -0.3, 0.3)
            candidate = np.clip(q + step, low, high)
            if np.linalg.norm(residual(candidate)) < np.linalg.norm(current_residual):
                q, damping = candidate, max(damping * 0.5, 1e-6)
            else:
                damping = min(damping * 4, 1e2)
            if np.linalg.norm(step) < 1e-9:
                break
        transform = fk(q)
        position_error_mm = np.linalg.norm(target - transform[:3, 3]) * 1000
        approach_error_deg = (
            0.0 if approach is None
            else math.degrees(math.acos(np.clip(transform[:3, 2] @ approach, -1, 1)))
        )
        if best is None or position_error_mm < best[1]:
            best = (q, position_error_mm, approach_error_deg)
        if position_error_mm < tol_mm and approach_error_deg < 2.0:
            return q, position_error_mm, True
    return best[0], best[1], False


def base_from_table(point_mm) -> np.ndarray:
    j1_x, j1_y = TABLE["j1_axis_xy_mm"]
    x, y, z = point_mm
    return np.array([
        (y - j1_y) / 1000 + 0.0388353,
        -(x - j1_x) / 1000,
        (z - TABLE["base_z_mm"]) / 1000,
    ])


def table_from_base(point_m) -> np.ndarray:
    j1_x, j1_y = TABLE["j1_axis_xy_mm"]
    base_x, base_y, base_z = point_m
    return np.array([
        j1_x - base_y * 1000,
        j1_y + (base_x - 0.0388353) * 1000,
        base_z * 1000 + TABLE["base_z_mm"],
    ])


def dir_base_from_table(vector) -> np.ndarray:
    x, y, z = vector
    return np.array([y, -x, z], float)


def path_min_z(q_from, q_to, steps: int = 25) -> float:
    """Lowest table-frame wrist/TCP z along a straight joint-space move."""
    lowest = math.inf
    for fraction in np.linspace(0, 1, steps):
        q = np.asarray(q_from) + (np.asarray(q_to) - np.asarray(q_from)) * fraction
        transform = np.eye(4)
        for joint_transform, radians in zip(_JOINT_TFS, q):
            transform = transform @ joint_transform @ _rz(radians)
        lowest = min(
            lowest,
            table_from_base(transform[:3, 3])[2],
            table_from_base((transform @ _TCP_TF)[:3, 3])[2],
        )
    return lowest


def self_check() -> None:
    assert np.allclose(table_from_base(base_from_table((100.0, 200.0, 30.0))), (100.0, 200.0, 30.0))
    q_seed = np.array([0.0889708857, -0.5752427955, 1.5539225381, -0.8406214718, 0.0214757310])
    q_near, _, _ = ik(
        [0.3417140761, -0.0285006256, 0.0831956599],
        [0.9808468067, -0.1605067522, -0.1103495284],
        [0.1638118269, 0.9862570994, 0.0215078982],
        q0=q_seed,
    )
    assert abs(q_near[4] - q_seed[4]) < math.radians(10)


if __name__ == "__main__":
    self_check()
    print("KINEMATICS_SELF_CHECK_OK")
