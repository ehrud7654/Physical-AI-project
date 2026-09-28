#!/bin/bash
# wheelhouse 폴더 → 제출용 tar.gz 스냅샷 (S15P21A103-46)
#
# 원본은 wheelhouse-jetson/ 폴더다. tar.gz 는 그 스냅샷일 뿐이므로 상시 유지하지
# 않고, 휠이 바뀌었을 때(예: 의존성 추가) 또는 제출 직전에만 이 스크립트로 다시 만든다.
# tar 는 증분 갱신이 안 되어 전체를 다시 압축한다(약 1GB, 수십 초).
set -euo pipefail

ROS_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WH="$ROS_ROOT/wheelhouse-jetson"
OUT="$ROS_ROOT/wheelhouse-jetson.tar.gz"

[ -d "$WH" ] || { echo "wheelhouse 폴더 없음: $WH" >&2; exit 1; }
echo "휠 $(ls "$WH"/*.whl 2>/dev/null | wc -l)개 → $OUT"
tar czf "$OUT" -C "$ROS_ROOT" wheelhouse-jetson
ls -lh "$OUT" | awk '{print "완료:", $5, $9}'
