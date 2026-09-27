# HW v4 Zarr vs Track A v10 audit (2026-09-20)

## Decision

The latest received HW artifact is the `v4.zarr.zip` downloaded from the
server on 2026-09-20. Its byte-level receipt identity is:

- size: `1,596,844,538` bytes
- SHA-256: `62abd956c89e1057abbd24cf9966c994b4cea4f1969aecf00a93f49842ddd8ec`
- episodes: `76`
- frames: `16,328`

This receipt supersedes the older outer-ZIP SHA-256 recorded in the 2026-09-18
handoff report (`3c941b2f...e40465b`) for identifying the currently received
container. The handoff report itself is retained as historical provenance. A
different ZIP hash does not by itself prove that the logical arrays changed;
the received array shapes and episode/frame counts match the report.

The received v4 file is **not** accepted directly as a corrected Track A TCP
label source. Its `robot0_eef_*` arrays use the older HW camera-to-TCP axis
assumption. The image arrays, gap arrays and SLAM result remain useful inputs.

## Sources compared

- HW: received `v4.zarr.zip`
- HW provenance: `handoff/reference/v4.zarr.report.json`, ZIP entry timestamp
  `2026-09-18 17:18:06 KST`
- Track A:
  `AI/datasets/umi_real_relative_20260918_v10_orbslam_cadtcp_video_aligned_provisional_74ep`
- Track A per-frame reference bundle:
  `AI/datasets/umi_real_relative_20260918_v10_orbslam_cadtcp_video_aligned_provisional_74ep_reference`

Both use `DICT_4X4_50` table marker ID 13 with a measured black-square side of
`0.16 m`.

## Episode composition

| Item | Count |
| --- | ---: |
| HW v4 | 76 |
| Track A v10 | 74 |
| Common | 64 |
| HW-only | 12 |
| Track-A-only | 10 |

The two outputs are therefore not the same selection with a two-episode count
difference. They must not be merged or substituted without retaining the
per-episode trajectory source and gate decision.

For the 64 common episodes all per-frame sequence lengths agree. The comparison
covers `13,859` frames.

## Common-frame numeric comparison

### Gap

Track A `gripper.csv` and HW `robot0_gripper_width` agree to float
serialization precision:

- median absolute difference: `0.00000122 mm`
- maximum absolute difference: `0.00000372 mm`

This confirms that the gap definition used by these common episodes is the
same. Gap does not explain the pipeline discrepancy.

### TCP arrays as currently stored

Directly comparing HW v4 TCP to the Track A axis-corrected TCP gives:

- position difference median: `43.44 mm`
- position difference p95: `45.90 mm`
- rotation difference median: `89.93 deg`
- rotation difference p95: `90.11 deg`

This is the expected signature of the previous camera-to-TCP axis assumption.
It is not evidence that the raw image should be rotated. The raw upright image
contract stays unchanged; only the TCP extrinsic and derived pose labels differ.

### Underlying camera/SLAM agreement

Track A camera poses were composed with the **old** HW
`s22_camera_tcp.json` transform and compared with the HW v4 TCP arrays. This
isolates most of the TCP-definition change:

- position difference median: `4.09 mm`
- position difference p95: `6.73 mm`
- position difference maximum: `31.66 mm`
- rotation difference median: `0.65 deg`
- rotation difference p95: `0.80 deg`
- rotation difference maximum: `1.61 deg`

Thus the dominant 90-degree discrepancy is the camera-to-TCP convention, while
the two independently produced camera/SLAM trajectories are close but not
bit-identical.

## Required handling

1. Keep the received v4 images and gap values unchanged.
2. Do not use its existing `robot0_eef_*` arrays as the corrected deployment
   contract.
3. To derive corrected TCP poses from v4, post-compose each old TCP pose with
   `inverse(T_camera_tcp_old) @ T_camera_tcp_corrected`.
4. Keep the corrected origin explicitly provisional until the physical
   camera-to-pad-centre translation is measured.
5. Validate the 12 HW-only episodes with the same complete-run gate before
   adding them to a new dataset revision. Do not splice pose rows between runs.
6. Keep the current Track A 74-episode dataset and the `f0918_B_42` result as
   their original fixed conditions; do not silently relabel or replace them.

## Status

`LATEST_HW_RECEIPT_VERIFIED_AXIS_CORRECTION_REQUIRED`

This audit is a data-contract comparison. It is not hardware deployment
approval and does not authorize learned-policy inference on the shared GPU
server or real-motor commands.
