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
# 젯슨엔 python3-venv(ensurepip)가 없을 수 있다. 그러면 venv 안에 pip 이 안 생긴다.
# 그 경우 --without-pip 로 만들고 **시스템 pip** 으로 --prefix 설치한다.
# (venv python 의 sys.path 에 $VENV/lib/pythonX.Y/site-packages 가 이미 있으므로 그대로 보인다)
if python3 -c "import ensurepip" 2>/dev/null; then
  python3 -m venv "$VENV"
  PIP=("$VENV/bin/pip" install)
else
  echo "ensurepip 없음 → --without-pip + 시스템 pip(--prefix) 사용"
  python3 -m venv --without-pip "$VENV"
  PIP=(python3 -m pip install --prefix "$VENV")
fi

# wheelhouse 의 휠 = 검증된 런타임 의존 세트. 이름으로 풀지 않고 파일을 직접 넣는다.
# --ignore-installed: 시스템 dist-packages 가 보여도 venv 안에 확실히 설치되게.
echo "오프라인 설치 (wheelhouse=$WHEELHOUSE)"
shopt -s nullglob
WHEELS=("$WHEELHOUSE"/*.whl)
shopt -u nullglob
if [ ${#WHEELS[@]} -eq 0 ]; then
  echo "wheelhouse 에 .whl 이 없습니다: $WHEELHOUSE" >&2
  exit 1
fi
echo "휠 ${#WHEELS[@]}개 설치"
"${PIP[@]}" --no-index --find-links "$WHEELHOUSE" --ignore-installed "${WHEELS[@]}"

echo
echo "설치 검증 (torch import — cusparselt 경로 필요)"
# 표준 venv(lib/.../site-packages)와 Debian pip --prefix(local/lib/.../dist-packages) 둘 다 탐색
CUSPARSE="$(dirname "$(find "$VENV" -name 'libcusparseLt.so*' -print -quit 2>/dev/null)" 2>/dev/null || true)"
[ -n "$CUSPARSE" ] && echo "cusparselt: $CUSPARSE" || echo "⚠️ cusparselt 를 못 찾음"
LD_LIBRARY_PATH="$CUSPARSE:/usr/local/cuda/lib64:${LD_LIBRARY_PATH:-}" \
  "$VENV/bin/python" -c \
  "import torch, diffusers, cv2; print('torch', torch.__version__, 'cuda', torch.cuda.is_available())"

echo
echo "설치 완료. torch 는 cusparselt 라이브러리 경로가 있어야 import 된다:"
echo "  export LD_LIBRARY_PATH=$VENV/lib/python3.10/site-packages/cusparselt/lib:/usr/local/cuda/lib64"
echo "확인:"
echo "  LD_LIBRARY_PATH=$VENV/lib/python3.10/site-packages/cusparselt/lib:/usr/local/cuda/lib64 \\"
echo "    $VENV/bin/python -c 'import torch; print(torch.__version__, torch.cuda.is_available())'"
