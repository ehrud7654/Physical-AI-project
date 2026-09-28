#!/bin/bash
# 로봇 플랫폼 자동 실행 서비스 설치 (젯슨에서 실행).
#   부팅 → sync-time-http(시간) → robot-estop → robot-orchestrator
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

# 2) 시간동기 스크립트·유닛 설치(orchestrator 가 Requires·ExecStartPre 로 쓴다).
sudo install -m 755 "$DEPLOY/sync-time-http.sh" /usr/local/bin/sync-time-http.sh
sudo install -m 644 "$DEPLOY/sync-time-http.service" "$UNIT_DIR/sync-time-http.service"
echo "설치: /usr/local/bin/sync-time-http.sh, $UNIT_DIR/sync-time-http.service"

# 3) 유닛 설치 — @ROS_ROOT@·@USER_HOME@ 치환.
for unit in robot-estop.service robot-orchestrator.service; do
    sed -e "s#@ROS_ROOT@#$ROS_ROOT#g" -e "s#@USER_HOME@#$USER_HOME#g" \
        -e "s#@USER@#$RUN_USER#g" \
        "$DEPLOY/$unit" | sudo tee "$UNIT_DIR/$unit" >/dev/null
    echo "설치: $UNIT_DIR/$unit"
done

# 4) 활성화.
sudo systemctl daemon-reload
sudo systemctl enable sync-time-http.service robot-estop.service robot-orchestrator.service
echo
echo "완료. 지금 바로 시작하려면:"
echo "  sudo systemctl start robot-estop robot-orchestrator"
echo "상태·로그:"
echo "  systemctl status robot-orchestrator"
echo "  journalctl -u robot-orchestrator -f"
