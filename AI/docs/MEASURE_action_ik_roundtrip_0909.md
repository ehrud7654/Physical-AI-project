# UMI action 디코딩 → IK → FK 왕복 검증 (2026-09-09)

현재 umi 브랜치 작업사본에서 실행. 실제 checkpoint 출력이나 실물 측정이 아닌
알려진 관절 궤적에서 만든 합성 action의 기구학 왕복 결과다.

## 구현

- `umi/action_ik.py`: 디코더 → 주입된 IKSolver → (T,6) 라디안 관절 목표.
- 모든 pose는 동일한 관측 기준 `T_current`에서 복원한다.
- IK 초기값만 이전 성공 관절 해를 이어받는다.
- 기존 MujocoIK는 gap 인자를 받아도 그리퍼 변환을 수행하지 않는다. 어댑터가
  설정의 `[angle_rad, gap_cm]` 곡선을 역변환해 Joint6를 별도로 설정한다.
- 미수렴, 위치/축/roll 오차, 관절 한계, 비유한 값, 호출자가 지정한 관절 점프
  예산 위반 시 전체 chunk 반환을 중단한다. 중간 실패를 건너뛰어 실행하지 않는다.
- 결과는 오프라인 관절 목표이며 서보 실행 승인이 아니다. 시간·속도·가속·충돌
  검사와 하드웨어 좌표/TCP 확인은 별도다.

## 재현

Python 3.12 / MuJoCo 3.12.0 / numpy 2.5.3 / PyYAML 6.0.3.
로컬 검증 의존성은 저장소 루트 `.tmp-umi-deps`에 설치했다(커밋 대상 아님).
렌더링 없이 `mj_forward`를 사용한다.

AI 폴더에서 실행:

```powershell
$env:PYTHONUTF8='1'
# 일반 환경은 mujoco==3.12.0, numpy, pyyaml 설치 후 python으로 실행.
# 이번 로컬 별도 의존성 폴더를 사용할 때:
$env:PYTHONPATH=(Resolve-Path ../.tmp-umi-deps).Path
python tools/check_umi_action_decode.py
python tools/check_umi_action_ik.py
python tools/check_umi_action_roundtrip.py
```

## 조건과 결과

설정 `configs/so101.yaml`, 씬 `sim/mujoco/scenes/pick_place.xml`.
seed 20260909, 관절 범위 중앙 주변 5개 시작 자세 × 24개 목표 = 120개.
각 궤적은 관절당 최대 0.04rad의 작은 이동. 현재 관절 상태에서 IK를 시작한다.
합성 목표 FK → 현재 pose 역행렬로 relative 생성 → 첫 두 행 인코딩 → 디코딩
→ 기존 IK → 복원 관절의 FK를 원래 목표와 대조한다.

| 지표 | 결과 |
|---|---:|
| 최대 위치 오차 | 0.00009999 mm |
| 최대 전체 SO(3) 회전 오차 | 0.00003871° |
| 최대 그리퍼 폭 왕복 오차 | 6.939e-18 m |
| 최대 인접 관절 변화(첫 상태 포함) | 0.00172054 rad |
| 도달 불가 목표 | 거부 통과 |
| 디코더 회귀 테스트 | 8개 통과 |
| IK 어댑터 테스트 | 5개 통과 |

왕복 실패 기준은 위치 5mm, 전체 회전 0.1°, gap 1e-8m 초과.
어댑터에는 max_step_rad=0.1을 이 실험의 점프 검사 예산으로 전달했다.
이는 실물 속도 한계가 아니며 제어 주기와 무관한 실험 설정이다.

## 남은 검증

- 실제 UMI checkpoint의 rotation decoder와 대조(현재는 팀 답변 기반 구현).
- 넓은 작업공간/큰 orientation 변화/관절 경계/턱 180° 대칭 사례.
- timestamp 기반 qdot/qddot 검사, 물리 rollout, 접촉·자기충돌 검사.
- 실제 관절 읽기, FK의 TCP 정의, 관측 시간과 기준 pose 일치.
- 실물 그리퍼 보정 곡선. 현재는 시뮬 설정의 곡선이다.

작은 도달 가능 궤적에서의 통과를 임의의 6DoF 목표나 실제 UMI 성공률로 해석하면 안 된다.
