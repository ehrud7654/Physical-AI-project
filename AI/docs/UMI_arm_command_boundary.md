# UMI 실물 명령 경계 — S15P21A103-156

`umi.arm_preflight.prepare_arm_commands`는 한 번의 추론을 수행하고 전체
목표 묶음을 검증한 뒤 `ArmCommand` 튜플을 반환한다.

- `arm_positions_rad`: 팔 관절 5개, shape `(5,)`, rad
- `gripper_width_m`: 집게 전체 폭, m
- 모든 상대 목표는 추론 시작 전에 복사한 동일한 pose를 기준으로 복원한다.
- 팔 IK 시드만 직전 해로 갱신한다. Cartesian 목표는 누적하지 않는다.
- 그리퍼 보정 곡선 및 encoder 변환은 실물 드라이버의 책임이다.

실물용 `ArmSolver`는 팔 5개 관절각과 위치/접근축/roll 잔차를 반환한다.
기존 6축 시뮬 솔버는 이 인터페이스에 직접 넣을 수 없다. TCP와 관절 기준을
확인한 후 명시적인 어댑터를 제공해야 한다. 시뮬의 그리퍼 형상이 FK에
영향을 준다면 그 계산은 시뮬 어댑터 내부에서 처리한다.

관절 범위, gap 범위, 각 단위의 jump 한계와 IK 잔차 한계는 필수 인자다.
학습 계약의 제안 범위 0~0.09m를 실제 로봇 한계로 자동 채택하지 않는다.
테스트에 사용한 범위와 예산은 가상 값이며 실물 검증값이 아니다.

호출자는 동일 관측 시점의 pose/관절/이미지를 일관되게 확보해야 한다.
입력 복사는 추론 중 배열 변경을 방지하지만 서로 다른 시각에 취득한
관측을 동기화하지는 않는다. 실제 timestamp 기준, stale 검사, watchdog,
실제 명령 송신, 속도/가속도 및 충돌 검사는 아직 구현하지 않았다.
이 모듈 통과는 실물 실행 허가를 뜻하지 않는다.

검증 명령 (저장소 루트):

```sh
python -m unittest discover -s AI/tools -p 'check_umi*preflight.py'
```

가짜 policy/IK/driver로 기준 pose 고정, 관측 보존, 팔 5 rad + 폭 m 전달,
후속 목표 실패 시 전체 chunk 거부, 범위/점프/잔차 거부를 검사한다.
실제 체크포인트 추론과 실제 하드웨어 연결 검증은 별도로 필요하다.

## N-step 실행 및 시뮬 어댑터

`tools.umi_mujoco.MujocoArmAdapter`는 기존 시뮬 IK를 팔 5축 인터페이스에
연결한다. gap→각도 변환은 시뮬의 seed 구성 내부에만 쓰고 외부 명령은 m를
유지한다. 120개 목표에서 기존 6축 결과와 일치하는지 자동 대조한다.

`umi.chunk_runtime.run_chunks`는 유한한 cycles만큼 관측 획득→전체 chunk
검증→N개 송신→새 관측/추론을 반복한다. 남은 chunk 목표는 버린다.
첫 목표 시간은 관측 timestamp이며 이후 목표는 period_s 간격이다.
추론 지연이 max_lateness_s를 넘으면 실행을 중단한다. 늦은 목표를 빠르게
몰아 보내거나 임의로 시간축을 옮기지 않는다. 실제 checkpoint의 horizon
시간 의미에 맞춘 지연 보상은 후속 작업이다.

timestamp는 주입한 monotonic clock과 같은 기준이어야 한다. 미래 timestamp,
오래된 관측, deadline 초과, 짧은 chunk, planner/driver 오류에서 추가 송신을
차단하고 driver.stop을 호출한다. send의 True는 수신 허용이며 도달 보장이
아니다. 실제 드라이버는 호출 자체에 timeout을 제공해야 한다. 동기식 코드라
멈춰 있는 send/predict 호출을 강제 중단하는 독립 watchdog은 아니다.

가짜 시계 테스트 6개와 명령 경계 테스트를 통과했다. 실제 30Hz 처리량 또는
실물 안전성에 대한 측정 결과는 아니다.

## 확정 실물 URDF와 상태 경계 (2026-09-09)

하드웨어 전달 수정본을 `configs/real/so101_ver1.urdf`로 고정했다. 원본 바이트
SHA는 `78e20d4e...2efc18`이며, 저장소 줄바꿈 정규화본 SHA는 real config에
기록한다. 기준은 `base_link`, TCP는 `gripper_tcp`다. J1~J5 zero tick과
direction=+1, J3 encoder-safe 하한 `-1.57079632679rad`를 실물 설정에 반영했다.

`umi.real_fk.SO101UrdfFK`는 이 URDF의 base→TCP 체인을 직접 읽어 FK한다.
`snapshot_from_joint_state`는 이름 순서에 의존하지 않고 J1~J5 rad와 `gripper`
전체 접촉면 폭 `0~0.09m`를 추출한다. ROS timestamp는 그대로 보존하며 monotonic
clock으로 가장하지 않는다. 순차 READ의 관절 간 skew와 발행 지연이 계측되기 전에는
실물 freshness가 검증됐다고 볼 수 없다.

시뮬 J3 모델 하한은 `-1.69rad`, 실물 encoder-safe 하한은 `-pi/2`로 서로 다르다.
시뮬 정본을 변조하지 않고 실물 preflight에서 더 좁은 real config를 적용한다.
그리퍼도 시뮬 hinge 각도와 실물 전체 폭을 별도 경계로 유지한다. 실물 endpoint는
아직 자 실측 전이므로 `gripper_endpoint_measured=false`다.
