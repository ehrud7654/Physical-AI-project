# 실물 시연 수집 매뉴얼 — 구운감자 pick & place

작성: 김준태(트랙 B) · 2026-09-22 · 수치는 전부 당일 실측 🟢

**태스크** 구운감자를 집어 들어 상자에 넣는다
**물체** 구운감자 · 넓은 면 **48mm** · 좁은 면 41mm → **넓은 면으로 파지**
**계획** 오늘 100편 + 내일 아침 100편 (동일 조건)

---

## 0. 근거 수치 — 이 매뉴얼의 기준선

기존 `s22_pick_20260921_atlas_v1` **70편 전수** 🟢

```
접근 개구(편별 최대)   중앙 70.40mm   q1 69.64  q3 71.15  max 75.79
파지 개구(편별 최소)   중앙 39.10mm   q1 38.65  q3 39.50  min 35.37
편 길이               중앙  7.23s    5.50 ~ 10.00
마커 검출률            중앙  0.86     최소 0.74
outcome               success 70 / 70
```

**기록 오프셋 −9mm** — 독립 2점에서 일치 🟢

```
핸들 물리 80mm  →  기록 70.40mm    차 -9.6
물체 물리 48mm  →  기록 39.10mm    차 -8.9      (파지 접촉 = 물체 폭)
```

→ 기록되는 `gripper_width_mm` 는 **물리값 − 9mm**. 상수라 나중에 뺀다. **지금 고치지 않는다.**

**실물 로봇에서 확인된 실패 원인** 🟢 — 정책은 접근 중 좌우 보정을 못 한다.
접근 구간 명령 Δy 가 `+2.6 −3.6 +0.4 +2.4 −1.0 +1.5 +2.1` 로 부호가 계속 뒤집히는 잡음이다.
같은 구간 Δx 는 `+9.8 → +2.6` 으로 단조 수렴한다.
→ **이번 수집의 최우선 변경은 물체 위치를 흩는 것 하나다.**

---

## 1. 세션 세팅 — 한 번만 (5분)

- [ ] **테이블 태그**: 손대지 않는다. 치워야 하면 테두리를 테이프로 따놓고 정확히 복귀
- [ ] **물체 기준 위치** 테이프 표시 (무작위화의 중심점)
- [ ] **상자 위치** 테이프 표시. 오늘·내일 고정. 수집 중 옮기지 않는다
- [ ] **포즈 기준점** 테이프 표시 (기준 클립 C 용)
- [ ] 핸들·폰 마운트 분해하지 않는다
- [ ] `camera_tcp` 설정 그대로 (`4e16c0a2…`)
- [ ] 감자 폭을 자로 재서 적는다 (넓은 면 / 좁은 면)

## 2. 기준 클립 3개 — 세션 시작에 (1분)

**내일 데이터를 오늘 기준으로 보정하려면 이게 근거의 전부다. 빼먹으면 보정 못 한다.**

- [ ] **A** 핸들을 **80mm** 로 고정하고 5초 정지 녹화
- [ ] **B** 핸들을 **48mm** 로 고정하고 5초 정지 녹화
- [ ] **C** 표시한 포즈 기준점에 핸들을 놓고 5초 정지 녹화
- [ ] 파일명에 `ref_start` 표시

## 3. 편별 촬영 절차

```
① 감자를 무작위 위치에 놓는다
     좌우 ±30mm · 전후 ±20mm      기준 테이프 중심에서
     ★ 매 편 다르게. 편 번호와 대략 위치를 종이에 적는다
     ★ 자세는 매 편 동일 — 넓은 면(48mm)이 그리퍼를 향하게
     ★ 회전 변주는 주지 않는다

② 핸들을 80mm 로 벌리고 시작

③ 접근 — 80mm 를 유지한 채 감자 옆까지

④ 파지 — 손가락이 감자에 닿아 저항이 느껴질 때까지 확실히 조인다
     ★ 여유를 남기지 마라. 기준은 기록 39mm (= 물리 48mm 접촉)
     ★ 오늘 정책이 43.0mm 에서 멈춰 놓쳤다. 그 여유가 시연에서 온 것이다

⑤ 든다

⑥ 상자 위로 옮긴다

⑦ 80mm 로 열어 놓는다   ★ 여는 폭도 규약이다

⑧ 편당 7~10초. 일정한 속도로
```

**바뀌는 것은 감자 위치 하나뿐이다.**

---

## 4. 20편에서 끊는다 — 전처리

100편 다 찍고 틀리면 100편을 버린다. 20편이면 20편만 버린다.

### 4-1. 경로 설정 (한 줄)

```bash
# [서버]
export NAME=onsite_0922; export REPO=~/S15P21A103; export BUILDER=~/ubuntu_umi/02_umi_dataset_builder; export PYG=~/envs/handoff312/bin/python; export PYB=~/envs/umi02/bin/python; export RAW=~/data/$NAME/raw; export PROC=~/data/$NAME/processed; export TAG=~/data/$NAME/processed_mapping/tx_slam_tag.json; export OUT=$REPO/out/$NAME; mkdir -p $OUT; echo "$OUT"
```

### 4-2. 촬영 게이트 — SLAM 돌리기 전에 거른다

```bash
# [서버]
$PYG $REPO/AI/tools/gate_raw_capture.py --raw $RAW --out $OUT/capture_gate.json
```

### 4-3. SLAM (WSL 구간)

```
# 1) 매핑 편 -> 아틀라스
ORB_SLAM_SAVE_MAP=PREPMAP/map_atlas.osa INIT_TAG_ID=13 INIT_TAG_SIZE_M=0.16 sh 02_umi_dataset_builder/slam/run_orbslam_linux.sh MAPPING_REC PREPMAP PREPMAP/slam

# 2) tx_slam_tag
python build_dataset.py slam-tag MAPPING_REC PREPMAP

# 3) 시연 편마다 같은 아틀라스로 localization
ORB_SLAM_LOAD_MAP=PREPMAP/map_atlas.osa sh 02_umi_dataset_builder/slam/run_orbslam_linux.sh REC PREPREC PREPREC/slam
```

산출물을 `$PROC` 에 둔다: `processed/rec_*/` (slam/camera_trajectory.csv ·
trajectory_validation.json · gripper_width.csv · telemetry.json) + `processed_mapping/*/tx_slam_tag.json`

### 4-4. SLAM 게이트 · plan · zarr 빌드 · 감사

```bash
# [서버]
$PYG $REPO/AI/tools/gate_slam_batch.py --processed $PROC --out $OUT/slam_gate.json
```

```bash
# [서버]
$PYG $REPO/AI/tools/make_episode_plan.py --gate $OUT/slam_gate.json --raw $RAW --processed $PROC --slam-tag $TAG --out $OUT/plan.json
```

```bash
# [서버]
cd $BUILDER && $PYB build_dataset.py build $OUT/plan.json $OUT/ds_$NAME --camera-tcp $BUILDER/configs/s22_camera_tcp.json
```

```bash
# [서버]
$PYG $REPO/AI/tools/audit_umi_zarr.py --zarr $OUT/ds_$NAME.zarr.zip --label $NAME --gate --rate-hz 30.0 --out $OUT/zarr_gate.json
```

각 단계 출력에서 **통과 / 불합격 / 미판정 / 전체** 를 본다. **미판정은 통과가 아니다.**

---

## 5. 20편 판정 — `check_collection.py`

```bash
# [서버] 자체검사 먼저
$PYG ~/check_collection.py --selftest
```

`자체검사 통과 10 / 전체 10` 확인하고:

```bash
# [서버] 판정
$PYG ~/check_collection.py $OUT/ds_$NAME.zarr.report.json --object-mm 48 --min-episodes 20
```

검사 항목 (전부 기준선 대조 · 모수 병기)

| 항목 | 기준 |
|---|---|
| 편수 | ≥ 20 |
| outcome | 전부 success |
| 접근 개구 중앙 | 70.40 ±3 mm |
| 접근 개구 IQR | ≤ 3.0 mm |
| **파지 개구 중앙** | **≤ 40.0 mm** (물체 48 − 오프셋 9 + 여유 1) |
| 덜 조인 편 | ≤ 10% |
| 편 길이 중앙 | 5.0 ~ 12.0 s |
| 마커 검출률 중앙 / 최소 | ≥ 0.80 / ≥ 0.70 |
| 추적 손실 0 인 편 | 전부 |

## 6. 판정별 조치 — 미리 정해둔다

| 미통과 항목 | 원인 | 조치 |
|---|---|---|
| **파지 개구 중앙 > 40mm** | ④에서 덜 조였다 | **가장 흔하다.** 조이는 감을 다시 맞추고 20편 재촬영 |
| 접근 개구 중앙이 70 아님 | 시작 개구가 80mm 가 아니다 | 핸들 시작 폭 재확인 |
| 접근 개구 IQR > 3mm | 편마다 시작 폭이 다르다 | 시작 폭을 고정하는 절차를 만든다 |
| 마커 검출률 저하 | 조명·사람·마커 오염 | 조명 고정, 마커 청소, 카메라 방향 |
| 추적 손실 편 존재 | SLAM 품질 | 그 편만 제외. 여러 편이면 매핑부터 |
| 편 길이가 짧다 | 너무 빠르다 | 느리게. 7~10초 |
| outcome 실패 포함 | 시연 실패 | 그 편 폐기. 재촬영 |

**게이트를 못 맞추면 게이트를 낮추지 않는다. 프로토콜을 고치고 다시 찍는다.**

## 7. 나머지 80편

판정 PASS 면 **같은 절차로** 80편. 프로토콜은 중간에 바꾸지 않는다.
바꿔야 하면 멈추고, 기록하고, 그 지점부터 새 배치로 센다.

## 8. 세션 종료 (1분)

- [ ] 기준 클립 **A' · B' · C'** 재촬영, `ref_end` 표시
- [ ] 100편 전체로 4-2 ~ 5 다시 돌려 최종 리포트 생성
- [ ] 데이터셋 메타 기록 (아래)

---

## 9. 내일 아침 세션

**바꾸지 말 것**

```
테이블 태그 물리 위치 · 상자 위치 · 물체 기준 위치 · 포즈 기준점
핸들·폰 마운트 · camera_tcp 설정 · dataset_config
```

**바뀌어도 되는 것**

```
SLAM 지도 — 사람이 앉아 있으면 새로 떠도 된다
  world frame 은 SLAM 지도가 아니라 테이블 태그에 걸려 있다
  (pose_contract: T_world_tcp = T_world_slam @ T_slam_camera @ T_camera_tcp,
   world_frame: mapping_table_tag)
  tx_slam_tag 를 같은 물리 태그로 다시 뽑으면 좌표계는 같다
```

**사람이 많을 때**

- 카메라가 사람 많은 쪽을 크게 담지 않게 — 테이블 쪽을 보게
- 매핑은 테이블 주변 위주로
- `marker_detection_rate` 중앙이 0.80 밑이면 그날 품질이 다른 것 → 합치기 전에 따진다
- `lost_frames_after_initialization` 이 0 이 아닌 편은 의심

**보정 방법**

```
클립 C (오늘) vs 클립 C (내일)  의 T_world_tcp 차이 = 두 날의 좌표 차이
클립 A·B 의 기록 폭 차이        = 그리퍼 축 드리프트
오늘 기준선은 -9mm. 내일도 -9mm 면 그대로 합친다. 다르면 그 차이만큼 뺀다.
```

---

## 하지 않는 것

```
회전 변주        변주 예산은 위치 하나에 몰아라
상자 위치 변주    변수는 하나씩
그리퍼 폭 고정    정책의 위상 신호다. 실물에서 2/2 로 정책을 망가뜨렸다
수집 중 규약 변경  바꿔야 하면 멈추고 기록하고 새 배치로
게이트 낮추기     n 을 올리거나 프로토콜을 고친다
```

## 데이터셋 메타에 적을 것

```
물체        구운감자 · 넓은 면 48mm · 좁은 면 41mm · 넓은 면 파지
기록 오프셋  마커 기준 기록값 = 물리 - 9mm  (80/48 두 점, 2026-09-22)
접근 개구    물리 80mm = 기록 70.4mm
파지 개구    물리 48mm 접촉 = 기록 39.1mm
무작위화     좌우 ±30mm · 전후 ±20mm · 자세 고정 · 회전 없음
상자        고정
camera_tcp  4e16c0a2db7e3a4c3f71246201af13be19512560bfa6eb8ec9105f567852104a
기준 클립    ref_start A/B/C · ref_end A/B/C
```
