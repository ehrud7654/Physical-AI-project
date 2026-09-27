# Normalized real-UMI source sample (2026-09-16)

## Sample

- Bundle: `AI/out/real_20260911_normalized/rec_20260911_145557.zip`
- Size: 22,731,775 bytes
- SHA-256: `6222c5c8f9e78d5fe3e60ff5cb00d43782afa9792eddb8ca691fda0a62d1823f`
- Frames: 78

## Source contract

### Pose and image time

- `poses.csv`: `index,timestamp_ns,tracking,x,y,z,qx,qy,qz,qw`
- Pose meaning: `T_world_camera`
- World frame: ARCore world, right-handed, gravity-aligned `+Y` up
- Camera local axes: `+X` right, `+Y` up, `-Z` forward (ARCore/OpenGL)
- Translation unit: metres
- Quaternion order: `xyzw`
- `frames.csv` has the same `timestamp_ns` as `poses.csv` for every frame.
- All 70 normalized episodes have strictly increasing pose/frame timestamps.
- Clock: Android `elapsedRealtimeNanos`; episode metadata declares all streams use
  the same clock.

### Gripper

- `gripper.csv`: `frame_index,gap_m,status`
- Unit: metres, absolute contact-face gap.
- The file has no independent timestamp. Join by `frame_index` to `poses.csv` or
  `frames.csv` and use that row's `timestamp_ns`.

### Images

- `frames/NNNNNN.jpg`: stored RGB observations, one JPEG per pose row.
- The source is not yet `camera0.mp4`; the official-input converter must encode
  these frames while preserving their timestamp order.

### IMU

- `imu.csv`: `timestamp_ns,sensor,x,y,z`
- Sensors: `accel`, `gyro`
- Accelerometer: Android raw accelerometer, m/s^2, gravity included.
- Gyroscope: Android raw gyroscope, rad/s.
- Per-sensor timestamps are strictly increasing in all 70 episodes.
- No measured `orientation_wxyz` field exists. If the HW exporter requires it,
  it must be explicitly marked and filled as unused/dummy; it must not be
  presented as a measured orientation.

## Required mapping to the HW four-file input

1. `metadata.json`: derive episode id/task/success policy from `episode.json` and
   explicit acceptance metadata. Do not invent success labels silently.
2. `recording.npz`: compute the pinch/TCP pose as
   `T_world_camera @ T_camera_pinch`, then encode rotation as true axis-angle;
   copy `gap_m` as `robot0_gripper_width`.
3. `camera0.mp4`: encode ordered JPEG frames. Preserve timing or record the
   chosen constant-rate approximation explicitly.
4. `physics.npz`: resample accel/gyro as required. Use an explicitly documented
   dummy orientation only because the downstream UMI loader does not consume it.

The latest side-grasp camera-to-pinch calibration is
`AI/configs/real/umi_s22_canonical_pinch_side_grasp_provisional.json`.
It remains provisional and must be carried into provenance metadata.
