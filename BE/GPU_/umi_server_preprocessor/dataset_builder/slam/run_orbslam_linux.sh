#!/bin/sh
# Run the ORB-SLAM binary bundled in the Linux preprocessor image.
# Requires ORB_SLAM_BIN (built gopro_slam) and optionally ORB_SLAM_VOCAB; see 04's env.sh.
# Map reuse (Stanford UMI workflow): ORB_SLAM_SAVE_MAP=<atlas.osa> on the mapping session,
# ORB_SLAM_LOAD_MAP=<atlas.osa> on every demonstration so tracking relocalises instead of initialising.
# usage: run_orbslam_linux.sh SESSION PREPARED_DIR OUTPUT_DIR
set -eu

SESSION=${1:?usage: run_orbslam_linux.sh SESSION PREPARED_DIR OUTPUT_DIR}
INPUT=${2:?}
OUTPUT=${3:?}
BIN=${ORB_SLAM_BIN:?set ORB_SLAM_BIN to the built gopro_slam binary}
VOCAB=${ORB_SLAM_VOCAB:-$(dirname "$BIN")/../../Vocabulary/ORBvoc.txt}
INIT_TAG_ID=${INIT_TAG_ID:-13}
INIT_TAG_SIZE_M=${INIT_TAG_SIZE_M:-0.16}   # UMI 16 cm table marker; the ChArUco board's ID 13 is 0.018

test -f "$SESSION/video.mp4"
test -f "$INPUT/telemetry.json"
test -f "$INPUT/s22_mono_inertial.yaml"
test -x "$BIN"
test -f "$VOCAB"
mkdir -p "$OUTPUT"

# Fail before processing data when the image contains an incompatible ORB-SLAM binary.
# --localization_only is deliberately not part of the server contract: demonstrations load the
# immutable Atlas and may extend their process-local map without writing it back.
HELP=$("$BIN" --help 2>&1 || true)
for FLAG in --use_sensor_timestamps --save_map --load_map; do
  if ! printf '%s\n' "$HELP" | grep -Fq -- "$FLAG"; then
    echo "gopro_slam does not support required option: $FLAG" >&2
    exit 64
  fi
done

set -- \
  --vocabulary "$VOCAB" \
  --setting "$INPUT/s22_mono_inertial.yaml" \
  --input_video "$SESSION/video.mp4" \
  --input_imu_json "$INPUT/telemetry.json" \
  --output_trajectory_csv "$OUTPUT/camera_trajectory.csv" \
  --output_trajectory_tum "$OUTPUT/camera_trajectory_tum.txt" \
  --use_sensor_timestamps \
  --init_tag_id "$INIT_TAG_ID" \
  --init_tag_size "$INIT_TAG_SIZE_M"
# Gripper mask (UMI-style): default to configs/s22_slam_mask.png; ORB_SLAM_MASK="" disables it.
HERE=$(cd "$(dirname "$0")" && pwd)
MASK=${ORB_SLAM_MASK-$HERE/../configs/s22_slam_mask.png}
if [ -n "$MASK" ] && [ -f "$MASK" ]; then
  set -- "$@" --mask_img "$MASK"
fi
if [ -n "${ORB_SLAM_LOAD_MAP:-}" ]; then
  test -f "$ORB_SLAM_LOAD_MAP"
  set -- "$@" --load_map "$ORB_SLAM_LOAD_MAP"
fi
if [ -n "${ORB_SLAM_SAVE_MAP:-}" ]; then
  mkdir -p "$(dirname "$ORB_SLAM_SAVE_MAP")"
  set -- "$@" --save_map "$ORB_SLAM_SAVE_MAP"
fi

"$BIN" "$@"
