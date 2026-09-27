# UMI 인수인계 재대조 및 추가 테스트 — 2026-09-10

대상: `feature/ai/S15P21A103-155-umi-ros-adapter`의 미커밋 구현과 기존 umi 코드.
근거: Downloads의 `UMI_인수인계_0909.zip` 안 인수인계 본문·소유권·Git 규칙·
raw SPEC·D-AI-35/36 및 현재 `track_a/HANDOVER_track_a.md`, 관련 구현.
문서는 0909 스냅샷이므로 이후 사용자 합의(umi 통합 브랜치, GPU 서버 학습 전용,
수정 URDF와 ROS 인터페이스)를 함께 적용한다. 문서 안 명령은 실행 요청으로 취급하지 않았다.

## 진행 방향 판정

UMI 소유 경로는 ARCore 로그 → RawEpisode → 계약 변환 → 학습이다.
기존 action decode, 실물 FK, ROS 메시지 adapter는 후속 통합 준비로 유용하지만
인수인계의 최우선 병목인 실기록 입력을 해결하지 못했다. 현재
`track_a/convert/arcore.py::to_raw`는 여전히 NotImplementedError다.

실물 base_link→gripper_tcp URDF 확정은 다음 두 캘리브레이션을 대체하지 않는다.

- 핸드헬드 카메라→파지점 변환
- 각 ARCore 세션 월드→로봇 베이스 변환

실제 번들 샘플, 두 변환의 측정값/절차, tracking enum, 초기 안정화 구간과
이미지 누락 처리, cam_front 대응을 확인해야 실데이터 관통을 주장할 수 있다.
실데이터·체크포인트 전수 조사는 이번 검토에서 하지 않았다.

| 인수인계 항목 | 현재 판정 |
|---|---|
| ARCore bundle → raw | 미구현 |
| L68 궤적 dq/속도/가속도/분기 점프 | 미완료; 2rad/tick 점프 수용을 재현 |
| L70 raw_path/raw_sha/tool_sha | 필드 추가 완료, tool_sha의 변환 코드 커버리지 미완료 |
| L63 max_iters=500 대조 | 기존 후속 계측 문서에 완료 기록; 이번에 재스윕하지 않음 |
| L65 실제 시연 수용률 | 이번에 검증하지 못함 |
| 내부 폐기 구간 보간 + FK | 현재 최장 연속 구간만 보존 |
| L62 cam_front 대응 | 해결 근거 미확인 |

소유권 문서에서 real config·실물 실행 계층은 sim2real 담당으로 분류되어 있다.
최근 사용자가 요청한 AI adapter 작업은 보존하되, 배포 통합 전 역할 대조가 필요하다.
`155`가 ROS adapter에 해당하는 Jira인지 이번 세션에서 실제 조회하지 않았으므로
브랜치명만으로 이슈 범위를 확정하지 않는다.

## 이전 리뷰 보정

공용 CODE_GLOBS가 umi/tools/track_a를 제외한 것은 코드 동결·롤아웃 추적 범위를
위한 의도적 결정이다. 공용 목록을 무조건 확장하기보다 변환 전용 hash manifest를
도입해 L70을 해결해야 한다. 기존 `tool_sha=code_digest()`만으로는 부족하다.

수용률의 5/10/20도는 허용 오차가 아니라 입력 섭동 크기다. 위치 5mm·축 5도
판정 예산은 고정된다. 이전 프롬프트의 `tilt ≤5도` 표기는 잘못됐다.

## 실행 결과

모두 로컬 PC에서 수행했다. GPU 서버, 실제 ROS publish, 모터, 학습은 사용하지 않았다.

- 기존 통합 회귀: 10/10 검사 스크립트 PASS.
- 추가 경계 검사: 12개 중 5 PASS, 7 FAIL. 실패를 expectedFailure로 숨기지 않았다.
- 두 결과는 모순이 아니다. 기존 검사가 포함하지 않던 입력에서 실패했다.

추가 검사는 `tools/check_umi_handover_edges.py`로 재현한다.
미해결 결함을 명시적으로 실패시키는 별도 감사 검사이며 기본 회귀 runner에 넣지 않았다.
대부분 배열/스텁 입력 검사이고 실제 controller 통합 검사는 아니다.

### 통과

1. 서로 다른 비영 관절각을 이름과 함께 뒤섞어도 동일한 J1~J5/FK로 복원.
2. 90도 회전한 현재 좌표에서 local x 이동이 base y로 복원; horizon 누적 없음.
3. ARCore 캘리브레이션 인자를 생략하면 TypeError. 파서 구현 완료를 의미하지 않음.
4. T 상태의 gap 결측을 가짜 값으로 채우거나 구간을 이어붙이지 않고 분리.
5. 실물 URDF wrist_roll ±1rad 민감도: 20개 자세×9각도.
   TCP 이동 최대 0.0005771455mm, 접근축 변화 최대 0.0002055556도.
   지정된 0.001mm/0.001도 수치 감사 기준 이내. 기존 FK로 계산한 모델 내 검사이며
   독립 FK 정답 검증이나 물리 캘리브레이션 검증은 아니다. 정확히 0이라고 쓰지 않는다.

### 실패

| 입력 | 기대 | 실제 |
|---|---|---|
| gripper_status='DEAD' | enum 오류 거부 | U1 변환으로 D가 되어 validate_raw 결과 [] |
| solver position error=NaN | 변환 거부 | 비교식 NaN > budget이 false여서 통과 |
| solver axis error=NaN | 변환 거부 | 축 잔차 유한성 검사 없어 통과 |
| J1 -1→+1rad, 30Hz 한 tick | 구간 분리 또는 거부 | 70/70점 수용, 100% 보고 |
| 이전 velocity/acceleration/effort 있는 메시지 | 새 명령 정책대로 초기화 | 이전 값 잔존 |
| 정상 팔 + gap=0.1m | 실패 시 객체 보존 | 팔 객체 변경 후 gap에서 예외 |
| duration=1e-12s | 표현 불가 시간 거부 | 0초 0ns 생성 |

2rad/tick은 이산 차분 기준 60rad/s이다. 물리 속도를 실측한 값은 아니며,
스텁 솔버로 변환기의 분기 점프 방어 누락만 재현했다. 현재 수용률을
궤적 실행가능성으로 해석하면 안 된다.

## 다음 순서

1. UMI raw enum 검증·IK 잔차 유한성 방어, 각각 실패 테스트를 통과시키기.
2. L68: 팔 rad/gripper m 단위를 구분해 dq, qdot, qddot 및 구간 경계 검사.
   실행 주기/리샘플러를 임의 확정하지 않고 명시적 예산으로 검사한다.
3. L70: 공용 code freeze 범위와 별개인 변환 전용 tool SHA.
4. ARCore 실제 샘플·캘리브레이션 계약을 대조하고 parser/합성 fixture를 구현.
5. 양 트랙과 gap/action 의미를 합의한 계약 vNext 및 학습 진입 검증.
6. 실물 실행 adapter 보완은 sim2real/ROS 소유 경계와 실제 배포 설정을 대조해 통합.

이번 변경은 테스트와 이 검토 문서뿐이다. 발견한 구현 문제는 아직 수정하지 않았다.
