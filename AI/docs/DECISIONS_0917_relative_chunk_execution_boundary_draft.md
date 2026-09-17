# D-AI-55 제안 — UMI 상대 청크의 EEF→관절 경계 담당 (초안)

관련 Jira: `S15P21A103-154`(UMI 상대 청크),
`S15P21A103-156`(실물 명령 경계).

작성일: 2026-09-17. 팀원 회신에서 D-AI-54 다음 번호가 55라고 확인했으나,
**D-AI-55의 트랙 간 승인·등록은 미확정**이다. 이 문서는 현재
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
- `umi/joint_chunk_handoff.py`: 8개 `ArmCommand`를 한꺼번에 검사해
  J1~J5 `(8,5)` rad·절대 gap `(8,)` m·엄격 증가하는 목표 시각 `(8,)` s를
  반환한다. 원 URDF 범위와 별개로 설치된 wrist_roll 상한을 다시 적용하고,
  0.09m gap 계약·속도/가속도·명시적 시간확대 상한을 검사한다. **ROS 송신
  또는 연속 충돌 검사는 하지 않는 오프라인 경계**다.
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

## 2026-09-17 트랙 B/HW 회신 반영: 입출력 사양

트랙 B 정책 출력은 `action_pred` `(8,10)`이다. 각 행은 상대 translation
`(3,)` m, 상대 회전행렬의 **첫 두 행**을 순서대로 펼친 `rot6d (6,)`,
절대 전체 접촉면 간격 `gripper_width_m (1,)`이다. 10Hz에서 8개 미래
목표가 0.8초를 덮으며, 각 목표는 같은 관측 시점
`T_base_tcp_current`를 기준으로 `T_target[k] = T_current @ A_relative[k]`로
복원한다. 이는 현재 `umi/action_decode.py`의 해석과 일치한다.

트랙 A가 HW/ROS 쪽에 넘길 **합의 대상 출력 계약**은 다음과 같다.

| 필드 | 형상/단위 | 의미 |
|---|---|---|
| `joint_names` | 고정 5개 | `[shoulder_pan, shoulder_lift, elbow_flex, wrist_flex, wrist_roll]` |
| `arm_positions_rad` | `(8,5)`, rad | 같은 TCP 정의의 5축 IK 목표, 위 joint 순서 |
| `gripper_width_m` | `(8,)`, m | 절대 접촉면 전체 간격 `0..0.09` |
| `time_from_start_s` | `(8,)`, s | 양수·엄격 증가; 원시간이면 0.1..0.8, 확대하면 실제 일정 명시 |

### 트랙 B의 4점 실행과 v10 인덱스 구분 (2026-09-17 확인)

트랙 B 회신과 전달 패키지의 `simulation/evaluate.py`에서는
`action_steps=4`가 기본이고, 공식 UMI 정책에서 복원한 `absolute[1:5]`
(0-based index 1,2,3,4)를 실행한 뒤 재관측한다. 팀원 설명상 이 정책
출력의 index 0은 현재 자세에 해당한다. 전달 패키지의
`get_real_umi_action()`은 현재 자세를 앞에 **추가하지 않고** 입력 action과
같은 길이의 `absolute`를 반환한다. 이 규칙은 트랙 B의 해당 정책/데이터
평가 조건이며 모든 8점 청크에 공통으로 적용할 수 없다.

반면 이 문서의 v10 `relative_target_trajectory`는 관측 시점 `t` 다음의
8개 미래 목표다. 생성기는 `current+1 .. current+8`을 선택하고 검증기는
모든 `action_timestamp`가 현재 관측보다 큰지 확인한다. 따라서 v10
정책 출력과 위 오프라인 `(8,5)` handoff의 index 0은 **현재 자세가 아니라
첫 미래 목표 `t+0.1s`**이다. 동일한 4점 재관측 진단을 하려면 v10 쪽은
`[0:4]`(index 0,1,2,3; 원시간 `t+0.1..0.4s`)를 선택해야 한다.
`umi/joint_chunk_handoff.py`의 `select_future_target_prefix(...,
execute_steps=4)`는 이 오프라인 선택을 명시하고, 현재 자세 행을
삽입하거나 ROS 명령을 송신하지 않는다. 시간 확대가 필요하면 실제
재관측 시점은 0.4초로 고정하지 않고 반환된 `time_from_start_s[3]`를
기록해야 한다.

현 트랙 A MuJoCo 진단 기본값은 `execute_steps=1`이고 첫 미래 목표부터
실행한다. 트랙 B의 4점 실행 성공률과 직접 비교하지 않으며,
`action_steps=1/2/4/8` 스윕도 정책/타깃/시작 장면과 인덱스 규칙을
고정한 별도 평가로 취급한다. 이 인덱스 정리는 실물 4점 실행 승인이
아니며, 실물 재관측 주기·시간 확대·충돌 검사는 별도 합의가 필요하다.

현 `ArmCommand`는 한 시점의 5관절+gap이고, `umi/ros_adapter.py`는
`/arm_controller/joint_trajectory`의 **한 점**과 별도 gripper topic
필드를 만드는 helper다. 위 `(8,5)` 전체 ROS goal 생성, 액션
`/arm_controller/follow_joint_trajectory` 송신, arm/gripper 동기 실행은
아직 완성되지 않았다. 따라서 이 표는 **제안하는 handoff 형식**이지
현 구현이 8점 ROS goal을 이미 출력한다는 뜻이 아니다. IK·관절 범위·
속도·가속도·충돌·stale state 중 하나라도 실패하면 청크 전체를 거부해야
하며, 검증되지 않은 청크를 현우 담당 ROS 경계로 보내지 않는다.
HW `BACKEND_INTERFACE.md`에는 `follow_joint_trajectory` **action**과
`joint_trajectory` **topic**이 모두 지원 경로로 적혀 있다. 현재 AI helper는
후자의 한 점만 만들므로, 8점 실행에서 어느 경로를 사용할지와 별도 그리퍼
명령의 동기화 방식은 HW/ROS와 확정해야 한다. 오프라인 handoff가 이를
대신하지 않는다.

## 설치된 폰 홀더의 wrist_roll 제약과 실물 재생 보류

URDF/`ros2_control`의 wrist_roll 상한 `+2.8412063rad`는 **관절 자체**의
한계이지 현재 폰 홀더가 달린 설치물의 허용 상한이 아니다. 팀원 회신의
2864틱, zero 2126틱, direction `+1`과 팀원이 정정한
`4096틱/회전` 기준은 `(2864-2126) × 360/4096 = +64.86328125°`
(`+1.132077821rad`)의 *보고된 기계적 정지*를 뜻한다. 기존
`0.09°/tick → +66.42°` 계산은 반올림 오류였으며 폐기한다. 직접 실물
스톱 재확인은 대기 중이다. 전달 패키지의 `KNOWN_LIMITATIONS.md`는
v010 브래킷 메시가 **+65° 샘플부터** 교차한다고 보고한다. 두 숫자가
가깝다는 사실은 연속 궤적의 충돌 자유를 증명하지 않는다.

`configs/real/so101_ver1.json`의 URDF 복사값 `limits_rad`는 보존하고,
`wrist_roll_installation.diagnostic_preflight_upper_rad`만 현재 **+60°**로
설정했다. `umi/relative_robot_preflight.arm_ranges()`가 이 값을 오프라인
IK 후보 검사 범위에 적용한다. +60°는 첫 검출 샘플보다 낮게 둔
**잠정 진단 필터**일 뿐 연속 운동·다른 관절 조합·실물 케이블·볼트의
충돌 자유를 증명하지 않는다. 브래킷 교체 후에는 근거와 설정을 함께
재측정·개정해야 하며, 이전 오라클·롤아웃 수치는 다른 한계 조건으로
표기한다. 설정의 `physical_stop_directly_verified=false`,
`swept_clearance_verified=false`가 이 미검증 상태를 보존한다.

모터가 복구됐다는 보고만으로 시뮬 전문가 관절궤적을 실물에 그대로
재생할 수는 없다. 정지 위치 직접 확인, 설치물의 swept clearance,
실제 TCP/베이스 등록, gap↔pad 간격, 속도·가속도·E-stop 및 감독 절차가
먼저 필요하다. E2 실물 롤아웃 재측정 계획은 별도 평가 프로토콜로
다루며 이 문서는 실물 재생 승인으로 해석하지 않는다.
