# SHARE — AI 학습 파이프라인을 서버 플랫폼으로 올리기 위한 계약 (BE 최은찬)

작성자: 김준태(트랙 B) · 2026-09-22
근거: 오늘 실물 구동까지 통한 경로를 그대로 분해한 것. 추정 없음.

---

## 0. 한 줄

지금은 **주피터 노트북 하나가 잡 8개를 순서대로 부르는 구조**다.
각 잡은 이미 독립 실행 가능한 CLI 스크립트라 **그대로 잡 단위로 쪼갤 수 있다.**
막는 것은 로직이 아니라 **경로 상수 · 계보 기록 · 조건 기록** 세 가지다.

## 1. 잡 8개 — 입력 / 출력 / 게이트

| # | 잡 | 입력 | 출력 | 게이트 (통과 못 하면 다음 잡 금지) |
|---|---|---|---|---|
| 1 | ingest | 수집 tar (편별 raw + SLAM 결과) | `episodes/` | 편별 준비물 목록 대조. **모수 병기 필수** (`96/96`) |
| 2 | slam_gate | `episodes/` | `gate.json` | 필수 항목 전부 통과 · **미판정 0** |
| 3 | plan | `gate.json` | `episode_plan.json` | 통과 편수 ≥ 최소값 |
| 4 | build | `episodes/` + plan + `dataset_config` + `camera_tcp` | `ds_*.zarr.zip` + `dataset.report.json` | 설정이 실제로 먹었는지 **되읽기 assert** |
| 5 | audit | zarr | `audit.json` | 스키마·편수·프레임수 |
| 6 | train | zarr + `profile` | `run_dir/config.yaml`, `checkpoints/best.ckpt` | `train_policy.py check` 통과 |
| 7 | package | run_dir + camera_tcp | `*_jetson/` (`encoder.pt` `denoiser.pt` `metadata.json` `dataset.report.json` `config.yaml`) | 패키지 검사 12항목 |
| 8 | deploy | package + 로봇 | `runs/<id>/cycles.jsonl` | 런타임 자체 검증 |

**잡 6(학습)만 장시간이다.** 현재 120 epoch ≈ 2.6시간, GPU 1장, VRAM 약 1.3GB.
나머지는 분 단위. 큐·재시도 설계는 6번만 신경 쓰면 된다.

## 2. 파라미터로 빼야 하는 것

이미 CLI 인자로 존재해서 **코드 수정 없이 잡 스펙에 올릴 수 있다:**

```
dataset_config   trim 등 전처리 규약        (--dataset-config)
camera_tcp       카메라-TCP 보정 config     (--camera-tcp)
epochs / batch / profile                     (--epochs --batch --profile)
gpu              CUDA_VISIBLE_DEVICES
run_id / out_dir                             (--run-id --runs-dir)
seed             (패치로 노출해둠)
```

**노트북 상수로 박혀 있어 빼야 하는 것:**

```
REPO   BUNDLE   OUT   PY_GATE   PY_TRN     → 전부 환경변수/잡 파라미터로
갈래 테이블 (A/B 하드코딩)                   → dataset_config 경로만 받으면 불필요
도경 handoff 형식 적응 (파일명 매핑)         → 수집 포맷이 확정되면 잡 1 안으로 흡수
```

## 3. 계보 — 지금 가장 약한 곳 ⚠️

`metadata.json` 에 들어 있는 것:

```
dataset_sha256 · camera_tcp_sha256 · status · shape_meta · action_scale/offset · scheduler
```

**없는 것:**

```
dataset_config (trim 값) · 수집 배치 ID · 학습 하이퍼파라미터 · git sha
```

오늘 실제로 문제가 됐다 — 어떤 체크포인트가 어떤 갈래인지 **디렉터리 이름에만** 있었다
(`onsite260921_C_ours_va_075215_jetson`). 이름은 바뀔 수 있으니 계보로 삼을 수 없다.

→ **요청**: 잡 4·6·7 의 출력 메타데이터에 위 4개를 박는다. 플랫폼의 아티팩트 ID 는
`dataset_sha256 + camera_tcp_sha256 + config_sha256` 조합으로 잡으면 이름과 무관하게 추적된다.

## 4. 실행 조건 기록 — 오늘 발견한 구멍 ⚠️

잡 8(실물 구동)의 로그 `cycles.jsonl` 에 **실행 인자가 남지 않는다.**
`--speed` · `--acceleration` · 요청 사이클 수 · 물체 배치가 전부 빠져 있다.
오늘은 대화 기록으로 복원했지만 플랫폼에서는 복원 경로가 없다.

→ **요청**: 모든 잡이 **받은 인자 전체를 출력 디렉터리에 덤프**하도록 규약을 정한다.
`args.json` 한 파일이면 된다. 잡 1~7 도 동일.

## 5. 게이트 결과 스키마 (공통)

플랫폼이 잡 성공/실패를 판정하려면 게이트 출력이 통일돼야 한다. AI 쪽 기존 규약:

```json
{
  "status": "PASS | FAIL",
  "필수": {"통과": 7, "전체": 7},
  "권고": {"통과": 3, "전체": 5},
  "미판정": 0,
  "항목": [{"이름": "...", "값": ..., "임계": ..., "판정": "PASS|FAIL|미판정"}]
}
```

**규칙 두 개는 플랫폼에서도 지켜야 한다:**
- **미판정 ≠ 통과.** 미판정이 1건이라도 있으면 그 잡은 통과가 아니다
- **모수를 항상 함께 낸다.** `통과 94` 가 아니라 `통과 94 / 전체 96`

## 6. 범용성의 실제 한계 (숨기지 않고 적음)

```
스킬          지금 학습된 행동은 "집기" 1종뿐. 기획의 5종 중 4종은 데이터가 없다
시연 생성기   물체 전용이다. 물체를 바꾸면 전문가 코드를 새로 써야 한다 (시뮬 경로)
로봇          SO-101 5자유도 전용. kinematics/grasp 모듈이 이 팔에 고정
카메라        S22 + 특정 camera_tcp 보정에 묶여 있다
학습 재개     체크포인트에서 재개하는 경로를 검증한 적 없다. 2.6시간이 통째로 날아갈 수 있다
```

플랫폼을 **"데이터 → 체크포인트 → 패키지"** 파이프라인으로 잡으면 위 한계와 무관하게 범용이다.
**"어떤 로봇이든 어떤 스킬이든"** 으로 잡으면 지금 데이터가 못 따라간다.

## 7. BE 가 AI 에 물어야 할 것

1. 잡 6(학습)의 동시 실행 수 — GPU 정책. 현재 **1·2·3·4·6 번 GPU 만** 사용 가능
2. 수집 배치가 들어올 때 잡 1~5 를 자동 실행할지, 사람이 게이트를 보고 승인할지
3. 잡 8(실물)은 로봇이 물리적으로 하나뿐이라 큐가 필요하다 — 동시 실행 금지

## 8. AI 쪽에서 먼저 할 일

- [ ] 잡 1~8 에 `args.json` 덤프 추가
- [ ] `metadata.json` 에 dataset_config · trim · 수집 배치 ID · git sha 추가
- [ ] 노트북 상수를 환경변수로 분리
- [ ] 게이트 출력 스키마 통일 (§5)
