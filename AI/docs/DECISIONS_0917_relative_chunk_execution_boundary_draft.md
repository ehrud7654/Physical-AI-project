# D-AI 번호 미배정 — UMI 상대 청크의 EEF→관절 경계 담당 (초안)

관련 Jira: `S15P21A103-154`(UMI 상대 청크),
`S15P21A103-156`(실물 명령 경계).

작성일: 2026-09-17. **트랙 간 D-AI 번호·승인 미확정.** 이 문서는 현재
코드의 구현 범위와 트랙 A가 맡을 연결 구간을 기록한다. 구현 완료나
실물 실행 승인을 뜻하지 않는다.

## 결정할 담당 경계

| 구간 | 담당 |
|---|---|
| 영상·proprio → 상대 EEF SE(3)+gap `(8,10)` 정책 출력 | 트랙 B |
| 상대 EEF 청크 → 현재 TCP 기준 목표 복원 → 5축 IK → 관절·gap 청크 검증 | 트랙 A |
| 검증된 관절궤적을 받는 ROS 제어·송신 및 하위 제어 | HW/ROS 담당 |

트랙 A가 가운데 **EEF→관절 경계의 소유자**다. 트랙 B에 이 구현을
중복 요청하지 않는다. 다만 양쪽 인터페이스의 shape·좌표계·시간 의미는
트랙 A/B/HW가 함께 확인해야 한다.

## v10의 두 `false`와 좌표계

`robot_base_alignment_applied: false` 및 `robot_ik_applied: false`는
**데이터셋 생성 시** 실 UMI 라벨에 로봇 베이스 등록이나 IK를 굽지
않았다는 뜻이다. 실행 시 상대 목표는 동일한 관측 시점에 얻은
`T_base_tcp_current`를 기준으로 다음처럼 복원한다.

```text
T_base_tcp_target[k] = T_base_tcp_current @ T_tcp_current_tcp_target[k]
```

따라서 이 상대 청크 복원식에는 수집 당시 ARCore world→로봇 base
행렬을 자동으로 곱하지 않는다. `T_base_tcp_current`의 frame/TCP와
관측 시각이 맞아야 하며, 독립적인 실물 object/workspace↔base 등록은
별도의 미해결 문제다. `false`를 "앞으로도 정렬·IK를 하지 않는다"로
해석하지 않는다.

## 현재 코드에서 확인된 범위

- `umi/action_decode.py`: `(8,10)` 6D 회전·상대 pose·절대 gap을
  동일한 현재 TCP anchor의 목표 pose들로 복원한다.
- `umi/arm_preflight.py`: 주입된 5축 IK solver를 호출해 전체 청크의
  IK 잔차·관절 범위·gap·점프를 검사하고 `ArmCommand` 묶음을 반환한다.
- `umi/relative_robot_preflight.py`, `umi/timing.py`: MuJoCo solver를
  연결한 오프라인 검사 및 잠정 속도·가속도 시간 확대를 계산한다.
- `tools/render_relative_chunk_rollout.py`: 위 경계를 MuJoCo 진단에서
  호출한다. 이 호출 경로가 트랙 B 정책 출력→HW ROS 입력의 완성된
  실물 연결을 증명하지는 않는다.
- `umi/ros_adapter.py`: 관절·gap 명령의 ROS 메시지 필드 생성 helper가
  있지만, 실제 ROS 송신 경로의 관통 검증과 동일하지 않다.

## 아직 이 결정으로 완료 처리하지 않는 것

실물 관측에서 동기화된 현재 TCP/관절 상태를 공급하는 연결, 실물용
5축 IK solver/adapter의 채택·검증, 시간 배치된 전체 관절청크의
트랙 B 출력→HW ROS 입력 관통 검증은 남아 있다. 이 문서는 소유권과
구현 현황을 분리해 기록할 뿐, 해당 작업을 완료했다고 선언하지 않는다.

## D-AI 확정 시 확인할 항목

1. 트랙 B 출력의 `(8,10)` 회전행 **첫 두 행**, m 단위 translation,
   절대 gap(m), 모든 step이 같은 현재 TCP anchor를 쓴다는 의미.
2. 트랙 A 출력의 J1~J5 rad, 절대 gap(m), waypoint 시간 및
   IK/범위/시간 검사 실패 시 전체 청크 거부 의미.
3. HW/ROS 입력의 관절 이름·TCP frame·timestamp·명령 수용 의미.

관련: `SPEC_umi_relative_target_trajectory_0914.md`,
`UMI_arm_command_boundary.md`.
