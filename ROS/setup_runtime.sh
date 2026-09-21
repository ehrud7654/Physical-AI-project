#!/usr/bin/env bash
# UMI 체크포인트 런타임용 venv 설치 (S15P21A103-46)
#
# 젯슨(aarch64 + JetPack)의 torch 는 PyPI 에 없다. NVIDIA 전용 휠이 모여있는
# wheelhouse 에서 오프라인(--no-index) 으로 설치한다.
#
#   ./setup_runtime.sh [WHEELHOUSE_경로]
#
# 기본 wheelhouse: ~/Workspace/stanford_umi_demo/wheelhouse-jetson
# 결과: ROS/.venv  (git 추적 안 함 — .gitignore)
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WHEELHOUSE="${1:-$HOME/Workspace/stanford_umi_demo/wheelhouse-jetson}"
VENV="$HERE/.venv"

if [ ! -d "$WHEELHOUSE" ]; then
  echo "wheelhouse 없음: $WHEELHOUSE" >&2
  echo "젯슨 전용 휠 디렉터리 경로를 인자로 넘기세요: ./setup_runtime.sh /path/to/wheelhouse" >&2
  exit 1
fi

echo "venv 생성: $VENV"
python3 -m venv "$VENV"
"$VENV/bin/pip" install --upgrade pip

echo "오프라인 설치 (wheelhouse=$WHEELHOUSE)"
"$VENV/bin/pip" install --no-index --find-links "$WHEELHOUSE" -r "$HERE/requirements-jetson.txt"

echo
echo "설치 완료. torch 는 cusparselt 라이브러리 경로가 있어야 import 된다:"
echo "  export LD_LIBRARY_PATH=$VENV/lib/python3.10/site-packages/cusparselt/lib:/usr/local/cuda/lib64"
echo "확인:"
echo "  LD_LIBRARY_PATH=$VENV/lib/python3.10/site-packages/cusparselt/lib:/usr/local/cuda/lib64 \\"
echo "    $VENV/bin/python -c 'import torch; print(torch.__version__, torch.cuda.is_available())'"
