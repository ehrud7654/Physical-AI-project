"""MuJoCo kinematic roundtrip (no rendering, dynamics or motor commands)."""
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import numpy as np
import mujoco
from tools.umi_mujoco import (MujocoIK, eef_pose_from_joints, gap_from_angle,
                              load_config, build_model, DEFAULT_CONFIG, DEFAULT_SCENE)
from umi.ik import quat_to_matrix
from umi.action_ik import actions_to_joints


def main():
    cfg = load_config(DEFAULT_CONFIG)
    model = build_model(cfg, DEFAULT_SCENE)
    ik = MujocoIK(model, cfg)
    data = mujoco.MjData(model)
    curve = cfg['grasp']['gap_curve']
    def fk(q):
        pos, quat = eef_pose_from_joints(model, data, q, ik.pinch)
        t = np.eye(4)
        t[:3, :3], t[:3, 3] = quat_to_matrix(quat), pos
        return t
    rng = np.random.default_rng(20260909)
    pos_errors, rot_errors, gap_errors, jumps = [], [], [], []
    for trial in range(5):
        q0 = (ik.ranges[:, 0] + ik.ranges[:, 1]) / 2
        q0[:5] += rng.uniform(-0.15, 0.15, 5)
        current = fk(q0)
        direction = rng.uniform(-0.04, 0.04, 6)
        truth = q0 + np.linspace(0, 1, 24)[:, None] * direction
        absolute = np.stack([fk(q) for q in truth])
        relative = np.linalg.inv(current) @ absolute
        a = np.zeros((len(truth), 10))
        a[:, :3] = relative[:, :3, 3]
        a[:, 3:9] = relative[:, :2, :3].reshape(-1, 6)
        a[:, 9] = [gap_from_angle(q[5], curve) for q in truth]
        recovered = actions_to_joints(a, current, q0, ik, ik.ranges, curve, max_step_rad=0.1)
        for target, q, gap in zip(absolute, recovered, a[:, 9]):
            achieved = fk(q)
            pos_errors.append(np.linalg.norm(target[:3, 3] - achieved[:3, 3]))
            cosine = (np.trace(target[:3, :3].T @ achieved[:3, :3]) - 1) / 2
            rot_errors.append(np.degrees(np.arccos(np.clip(cosine, -1, 1))))
            gap_errors.append(abs(gap_from_angle(q[5], curve) - gap))
        jumps.append(np.abs(np.diff(np.vstack([q0, recovered]), axis=0)).max())
    print(f'MuJoCo={mujoco.__version__}, seed=20260909, targets={len(pos_errors)}')
    print(f'max position_mm={max(pos_errors)*1000:.8f}, rotation_deg={max(rot_errors):.8f}')
    print(f'max gap_m={max(gap_errors):.3e}, joint_step_rad={max(jumps):.8f}')
    if max(pos_errors) > 5e-3 or max(rot_errors) > 0.1 or max(gap_errors) > 1e-8:
        raise AssertionError('roundtrip error budget exceeded')
    # An unreachable Cartesian target must not yield an executable chunk.
    a = a[:1].copy()
    a[0, :3] = [10, 10, 10]
    try:
        actions_to_joints(a, current, q0, ik, ik.ranges, curve, max_step_rad=0.1)
    except ValueError:
        print('unreachable target: rejected; PASS')
    else:
        raise AssertionError('unreachable target accepted')


if __name__ == '__main__':
    main()
