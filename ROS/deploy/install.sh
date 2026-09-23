#!/bin/bash
# SO-101 자동 실행 서비스 설치 (젯슨에서 실행).
#   부팅 → sync-time-http(시간) → so101-estop → so101-orchestrator
# 유닛의 @ROS_ROOT@ 를 이 레포의 실제 ROS 경로로 치환해 /etc/systemd/system 에 설치한다.
set -euo pipefail

ROS_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"   # deploy/ 의 부모 = ROS/
UNIT_DIR=/etc/systemd/system
DEPLOY="$ROS_ROOT/deploy"

# 서비스는 root 가 아니라 워크스페이스를 빌드한 유저로 돌아야 한다(소유권·DDS 권한).
# sudo 로 실행되면 $SUDO_USER 가 원래 유저다.
RUN_USER="${SUDO_USER:-$USER}"
USER_HOME="$(getent passwd "$RUN_USER" | cut -d: -f6)"
echo "ROS_ROOT = $ROS_ROOT"
echo "RUN_USER = $RUN_USER (HOME=$USER_HOME)"

# 1) .env 확인 — 없으면 예시에서 만들고 멈춘다(secret 은 사람이 채워야 한다).
if [ ! -f "$ROS_ROOT/.env" ]; then
    cp "$DEPLOY/.env.example" "$ROS_ROOT/.env"
    chmod 600 "$ROS_ROOT/.env"
    echo "!! $ROS_ROOT/.env 를 만들었다. SO101_DEVICE_SECRET 을 채우고 다시 실행해라."
    exit 1
fi
chmod 600 "$ROS_ROOT/.env"
if ! grep -q '^SO101_DEVICE_SECRET=.\+' "$ROS_ROOT/.env"; then
    echo "!! $ROS_ROOT/.env 의 SO101_DEVICE_SECRET 이 비어 있다. 채우고 다시 실행해라."
    exit 1
fi

# 2) sync-time-http.sh 존재 확인(orchestrator 가 ExecStartPre 로 부른다).
if [ ! -x /usr/local/bin/sync-time-http.sh ]; then
    echo "!! /usr/local/bin/sync-time-http.sh 가 없다(시간동기 스크립트). 먼저 설치해라."
    exit 1
fi

# 3) 유닛 설치 — @ROS_ROOT@·@USER_HOME@ 치환.
for unit in so101-estop.service so101-orchestrator.service; do
    sed -e "s#@ROS_ROOT@#$ROS_ROOT#g" -e "s#@USER_HOME@#$USER_HOME#g" \
        -e "s#@USER@#$RUN_USER#g" \
        "$DEPLOY/$unit" | sudo tee "$UNIT_DIR/$unit" >/dev/null
    echo "설치: $UNIT_DIR/$unit"
done

# 4) 활성화.
sudo systemctl daemon-reload
sudo systemctl enable so101-estop.service so101-orchestrator.service
echo
echo "완료. 지금 바로 시작하려면:"
echo "  sudo systemctl start so101-estop so101-orchestrator"
echo "상태·로그:"
echo "  systemctl status so101-orchestrator"
echo "  journalctl -u so101-orchestrator -f"
