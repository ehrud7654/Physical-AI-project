# MEASURE — 2026-09-22 · pv_E 배포본 계보 감사 (외부 검토 대응)

작성자: 김준태(트랙 B) · 확신도 🟢 전부 해시·로그 대조

> 계기: 외부 검토자가 9/22 실물 구동의 조기 폐쇄(`79→38mm`) 재현을 위해 자료 요청.
> 처음 요청은 `A_ours` 였으나 실물 배포본은 `C_ours_va`(패키지 pv_E) 임을 알리고 대상 변경.

## 1. 실행별 패키지 대응 🟢

로그 `model_provenance` 전수 (10/10 런):

```
구동 7런   live_E_120222 · live_off25_121403 · live_rep_121600 · live_lift_122455
           live_lift2_123439 · live_lift3_124137 · live_far_124638
           → 전부 dataset_sha256 97ed0293… (C/E 계열)
예측전용   cmp_pv_E_115958 (97ed0293…)
           cmp_pv_F_120012 · gate_pv_F_01 (abf6fe08…, F 계열)
camera_tcp 10/10 런 모두 4e16c0a2… physically_validated
```

**모터 구동에 F 는 섞이지 않았다.**

## 2. 배포본 동일성 — 젯슨 = E 사본 🟢

```
                     서버 C 사본    서버 E 사본    젯슨 배포본
encoder.pt           e51a605f…      e51a605f…      e51a605f…     동일
denoiser.pt          645ee0b4…      645ee0b4…      645ee0b4…     동일
metadata.json        10c813ae…      ef2061d0…      ef2061d0…     젯슨 = E
dataset.report.json  5174b32f…      25e13b4f…      25e13b4f…     젯슨 = E
```

**가중치는 C·E 가 동일하고 계보 메타데이터만 다르다.** E 는 C 를 물리검증 `camera_tcp` 로
재포장한 것이므로 예상과 일치한다.

### C 패키지로는 구동이 불가능하다 🟢

`out/onsite260921_C_ours_va/build.log`:

```json
{"status":"pass","episodes":94,"frames":25475,
 "training_input_status":"provisional_camera_tcp",
 "physical_deployment_ready":false}
```

`cli.py:validate_model_calibration` 이 `"ready"` + `true` 를 요구한다.
→ **E 재포장이 실물 구동의 전제였다.** 논리와 해시가 일치한다.

## 3. 계보 공유 구조 🟢

```
plan.json        A~F 6갈래 전부 동일  4cbc0268f357a5619d2391e5dc018e00e2d36a163a016544f8eb963dc57ec9a8
slam_gate.json   A = B 동일          4a597e91a44a9b2a44bafcefa8c713c42a66378cb1064e67b5080fe8026bec00
                 C·D·E·F 에는 파일 없음
학습 데이터      94편 · 25,475 프레임
```

**SLAM 게이트와 편 선정은 한 번 산출되어 전 갈래가 공유했다.** C 에 `slam_gate.json` 이 없는 것은
누락이 아니라 **재사용 구조**다 — `plan.json` 해시 동일이 그 증거다.

## 4. 학습 코드 해시 🟢

외부에서 제시한 후보 해시 2건과 대조:

```
train_policy.py            45a3c86b864b4626bf722b0154b654e9e686998c4a967df3c8d2dfb092bfd3e0  일치
policy_resnet18_gpu.yaml   eace5d7a2ee76d575473a7703be383e0a7647ca8cfafee40b7c0f5e650ad6546  일치
  두 파일 모두 ~/ubuntu_umi/ 와 ~/hyeonseok/umi_gpu_bundle/ 사본이 같은 해시
policy_resnet18_8gb.yaml   5434bca6…  (기본 프로파일. 우리는 사용하지 않음)
공식 UMI                   git d095ba9590df789df5189eea5ee7e431689038a6 + 로컬 패치 2종
  ~/handoff/third_party/umi 는 git 아님 + umi_dataset.py md5 다름 → 규약 확인에 쓰면 안 된다
```

## 5. 미생성·미보유 — 있는 척하지 않는다

| 항목 | 상태 | 사유 |
|---|---|---|
| `audit.json` | **생성된 적 없음** | A·C 어느 갈래에도 없고, 빌드 로그 3종에 감사 실행 흔적 0회. 노트북 셀은 `--out OUT/audit.json` 을 주지만 갈래 빌드가 그 경로를 거치지 않았다 |
| `slam_gate.json` (C) | 없음 | §3 재사용 구조. A 것이 공유 산출물 |
| 추론 입력 RGB | **1/2 프레임만** | 관측은 2프레임인데 런 로그에는 사이클당 1장만 저장 |
| 실행 명령·전체 인자 | **기록 없음** | `cycles.jsonl` 에 offset·관측간격·settle·max-resamples 만. speed·acceleration·요청 사이클 수·물체 배치 미기록 |

마지막 항목은 9/22 확인 후 `runtime_jt` 에 `args.json` 덤프를 추가했다. **이후 런부터 적용된다.**

## 6. 검토자에게 정정한 수치 🟢

```
"79 → 38mm 조기 폐쇄" 의 79mm 는 정책 예측값 predicted_gripper_mm[0] 이다.
실제 관측 입력값은 90.2 ~ 91.5mm (시작 틱 102~129) — 7/7 런 모두 0.09m 상한 초과.
시연 분포: 시작 개구 중앙 70.7mm (q1 69.5 · q3 72.8) · 파지 개구 중앙 45.8mm
```

재현하려면 **관측값**을 맞춰야 한다. 예측값 79 를 입력으로 쓰면 조건이 다르다.

## 7. 이번 감사에서 드러난 기록 결함

1. **체크포인트 계보가 디렉터리 이름에만 있다** — `metadata.json` 에 `dataset_config`·trim·수집 배치 ID·git sha 없음
2. **실행 인자가 로그에 없다** — 대화 기록으로만 복원 가능
3. **노트북 경로와 실제 빌드 경로가 다른 게이트를 거친다** — 같은 산출물이라고 부르면 안 된다
4. **`audit.json` 이 계약상 존재해야 하는데 없다** — 노트북은 쓰게 돼 있고 빌드는 안 거쳤다
