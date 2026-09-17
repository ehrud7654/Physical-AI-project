# Real UMI transfer dataset handoff (updated 2026-09-17)

## Task

- Object: one upright `구운감자` snack package/case.
- Demonstration: approach the package body from its two horizontal sides with the UMI parallel jaw, grasp it like lifting a cup, and lift it.
- There is no real UMI demonstration for a second action such as place-at-target yet.

## Dataset to share

- Directory: `AI/datasets/umi_real_relative_20260911_v10`
- Archive: `AI/datasets/umi_real_relative_20260911_v10.tar`
- Archive size: 305,116,160 bytes (about 291 MiB / 305 MB)
- Archive SHA-256: `c45e2dedf91ec6b067adcf36ab1e3079e93fa78208d52211b4c3201d2625a420`
- Episodes: 70
- Anchors: 1,248
- Rate: 10 Hz
- Observation history: 2
- Action horizon: 8
- Calibration: `umi-s22-marker-midpoint-canonical-side-grasp-20260914-provisional`

This is **not** an official Stanford UMI Zarr/replay-buffer dataset. Its on-disk
schema is `umi_relative_chunk/0.2.0-provisional`, stored as one NPZ per episode.
Compared with v9, the observation timestamp now records both history frames;
image, proprio, action, action timestamp and source rows are otherwise unchanged.
The archive and raw episode ZIPs are deliberately **not stored in Git**. Sharing
this document or a commit does not transfer the 305 MB archive.

Per-episode arrays:

- `image`: `(N, 2, 3, 224, 224)`, `uint8`
- `proprio`: `(N, 2, 10)`, `float32`
- `action`: `(N, 8, 10)`, `float32`
- `observation_timestamp`: `(N, 2)`, `float64`, history `[t-1, t]`
- `action_timestamp`: `(N, 8)`, `float64`
- `source_row`: `(N,)`, `int64`

The action components are relative future pinch trajectories:

1. translation `xyz` in metres;
2. rotation-matrix first two **rows** (6D representation);
3. gripper gap in metres.

The dataset does not contain robot `ctrl`, joint commands, or an IK target that
was issued by a robot controller. Robot-base registration and IK are deliberately
not applied to the labels.
Every `action[n, 0]` is the **first future** target after observation `t`, not
the current pose. All eight `action_timestamp` values must exceed the current
`observation_timestamp[n, 1]`. The gap is the UMI demonstration's estimated
absolute contact-face opening; it is not a measured SO-101 command-to-pad-gap
curve. The camera-to-pinch calibration remains a provisional marker-midpoint
estimate rather than an independently measured physical pad-centre TCP.

## Integration boundary

The HW package's official UMI `train.py` cannot consume this archive directly.
A semantic converter is required. Do not merely rename or repackage the arrays:

- official UMI expects axis-angle pose fields and a replay-buffer/Zarr layout;
- this dataset contains 8-step relative SE(3)+gap chunks using a 6D row encoding;
- the conversion must explicitly define the reference frame, `_wrt_start`
  semantics, timestamps, history/chunk horizons, and gap units.

The safest conversion source is the preserved normalized pose/image/gap stream,
not a lossy reinterpretation of the existing 6D chunk as axis-angle.
That source currently exists at `AI/out/real_20260911_normalized` (about
2.05 GB, 70 episode ZIPs plus quality metadata), but it has not been packaged
for transfer in this handoff. A one-episode source-field sample and its units
are documented in `AI/docs/SHARE_normalized_umi_source_sample_0916.md`.

## Current validation status

- `python AI/tools/verify_umi_relative_dataset.py
  AI/datasets/umi_real_relative_20260911_v10` was rerun locally on 2026-09-17:
  70 episodes / 1,248 anchors / `problems: []`.
- The policy-free ver1 side-grasp recorded-oracle gate passed 14/14 episodes,
  **only** with a fixed near-contact K=2 start, provisional contact geometry,
  and simulation-only 2 mm execution preload. It does not establish far-start,
  full-chunk, learned-policy or hardware success.
- Local v10 learned-policy visual probes have not shown reliable response in
  the required object-shift directions. Details and denominators are in
  `AI/docs/MEASURE_s22_mujoco_visual_alignment_0917.md`.
- This dataset is suitable for controlled training-path integration and
  diagnostics, not for a deployment or physical success claim.

## Git and file-transfer boundary

Team members can inspect the data semantics and implementation in this Git
branch under `AI/docs/SHARE_real_umi_transfer_dataset_0916.md`,
`AI/docs/SHARE_normalized_umi_source_sample_0916.md`,
`AI/docs/SPEC_umi_relative_target_trajectory_0914.md`, and
`AI/umi/relative_dataset.py`. To reproduce training, the separate v10 archive
must also be transferred and its SHA-256 checked. Official UMI conversion
needs the separate 70 normalized source ZIPs, not only the v10 archive.

## Two-target constraint

Only the lift task currently has real UMI demonstrations. An A/B comparison for
the second task requires new real demonstrations of that second action. Without
them, the second target can be shown only as a simulation pipeline demonstration,
not as evidence that simulation improved real-UMI fine-tuning.
