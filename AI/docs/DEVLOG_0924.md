# DEVLOG 2026-09-24 — 새 장소 시연 수집 (data3_0924)

작성 김준태 (트랙 B).

## 데이터 구분 — 혼동 금지

| | 9/23 | 9/24 |
|---|---|---|
| 장소·배치 | 기존 | **새 장소** (탁상 달력·삼성 책자·종이 상자·꽃무늬 천, 태그 테이블 위 눕힘) |
| 서버 | `~/data/data2_pilotA` | `~/data/data3_0924` |
| 상태 | raw 74 → plan 60 → zarr `ds_pilotA_map3` → pilot_A 학습 | raw 92 → plan 90 → zarr `ds_0924.zarr.zip` → 학습 중 |
| Atlas | map3 (도경 정렬 REJECT 30.643) | 신규 후보 3 (도경 정렬 PASS p95 22.3mm) |

## 수집

- 92 세션 = 매핑 1 + 실패 1 + 성공 90. outcome 검산 🟢 `success 91 · failure 1` (**매핑이 앱에 success 로 찍힘** — 폴더 이름으로 수동 분리)
  - 매핑 `rec_1790228304037_7de23571` · 실패 `rec_1790229680784_f4e513fb`
- 폰 → 노트북: MTP 는 `Android/data` 를 빈 폴더로 보여줌 → ADB `pull`
  - 앱 경로 `/sdcard/Android/data/org.umi.collect/files/sessions`
  - 오늘 편 필터: rec id(ms) ≥ 1790175600000 (2026-09-24 00:00 KST) → 92
- 노트북 → 서버: `data_0924.tar` (무압축) Jupyter 업로드, sha256 앞 16자 `940f1a54a3f21ae4`, 풀어서 92 / manifest 92 🟢
- prepare 92/92 · 실패 0

## SLAM (트랙 A 도경) — 보고 + 독립 검산

도경 보고: 90/90 · Atlas 후보 3 sha `2056738c…` · ArUco ID13 PASS (p95 병진 22.3mm · 회전 1.81°) · 편별 validation·receipt 90개.

독립 검산 🟢
```
편 90 · 파일 누락 0 · 지도 sha 1종 (90/90) · validation pass 90
프레임 간 최대 이동 (state==2 ∧ !is_lost)  중앙 16.1mm · 최대 72.6mm
50mm 초과 2편  6c77f225 52.7mm · 28c7407e 72.6mm   ← 도경 validation·우리 gate 둘 다 PASS 로 통과시킴
```

## slam-tag (우리 빌더) — pass

| | 9/21 | 9/23 map3 | 9/24 |
|---|---|---|---|
| 태그 프레임 / 추적 | — | 682 / 4007 (17%) | 2102 / 4263 (49%) |
| MAD | 2.0mm | 2.49mm | **8.88mm** |
| max | 47.5mm | 48.5mm | 47.6mm |

⚠️ 예측 빗나감 — "비슷하거나 더 좋다" 로 봤는데 MAD 가 3.5배 나빠졌다. 원인 미분리 🟡 (비스듬한 각도 샘플 혼입 후보).

## gate_slam_batch — PASS 90 · FAIL 1 (SLAM 없는 실패 편)

receipt 가 들어오면서 9/23 의 "73/73 미판정" 이 해소됐다. 지도 1종.

## plan — 90편 (사용자 결정)

- 1차: 게이트 PASS 90 → 점프 2 제외 → 마커 결손 >15 제외 51 → 37편. **사용자 중단**
- 최종: **게이트 PASS 90 전부.** 마커 결손 한계 해제 (`out/dataset_nogap.json`)
- 근거: 공식 UMI `06_generate_dataset_plan.py` 는 미검출을 보간, 검출률 <0.9 는 경고만 🔵. 15프레임 한계는 `ubuntu_umi` 빌더 추가 제한
- 경위·오류: `TS_marker_gap_gate_0924.md`

마커 결손 계측 (`probe_marker_gaps.py`, 0923 기준선 대비): 검출률 0.841→0.623 · 미검출 중간·뒤 1/3 0.141·0.058 → 0.442·0.437 · 시작 폭 70.3mm(정상) · 결손>15 편의 48/52 는 전후 폭 차 ≤3mm.

## build — 완료 🟢

```
out/ds_0924.zarr.zip   episodes 90 · frames 25244 · status pass
camera_tcp  physically_validated (sha 4e16c0a2, --camera-tcp 명시)
training_input_status ready · physical_deployment_ready true
```

## 학습 — 진행 중

```
run-id  d0924_90_b64_HHMMSS · runs-dir ~/data/data3_0924/runs · GPU 1
설정    --umi-root 현석 번들(d095ba95) · --batch 64 · 나머지 기본 (pilot_A 와 동일)
train 21340 / dataloader 334 (= batch 64 확인) · 에폭 약 2분 18초 → 120 에폭 약 4시간 40분
```

## 계획 정정

- 오늘 데이터는 **pilot_B 가 아니다.** pilot_B = 같은 장면 매핑 재촬영 비교였다. 장면이 바뀌었다
- 9/23 사전등록(pilot_A ≤ pilot_B) 은 **보류.** 장면·배치·Atlas 가 동시에 바뀌어 원인 분리 불가
- D-AI-52 의 되돌릴 조건(pilot_B 비교)도 판정 수단이 사라졌다 → pilot_A 롤아웃 단독 결과로 재검토

## 다음

- `TRAIN_DONE_0924` → best.ckpt → 젯슨 패키징 → 실물 롤아웃 (`--gripper-close-offset-ticks 0` · settle 재명령)
- 도경에게: 점프 2편 (validation 이 못 잡음) · 마커 결손 한계 출처 확인
- 다음 수집 전: 결손 최장 편 영상에서 파지·운반 구간 가림 원인 확인

## 어시스턴트 오류

1. `adb pull` 에 `MSYS_NO_PATHCONV=1` 를 걸어 로컬 경로까지 변환이 막힘 → 92/92 FAIL. 로컬은 `cygpath -m` 윈도우 경로로 수정
2. 수집 환경 검토 중 "매핑 후 2편 선확인" 을 강조했으나 92편을 먼저 찍음 — 순서를 강제할 체크포인트를 매뉴얼에 못 박지 못했다
3. prepare 분기에서 매핑을 outcome `unrated` 로 판별 — 실제는 `success` 여서 processed 로 들어감. 사후 이동
4. 20줄 가까운 heredoc 을 붙여넣게 함 → 터미널에서 미실행 (`out/` 도 안 생김). 파일로 전환
5. **빌더 마커 결손 한계(15)를 공식 규칙처럼 게이트로 씀** — 공식 UMI 소스 확인 없이 51편 탈락. 사용자 지적으로 중단 (TS_marker_gap_gate_0924)
6. 단일 편(64.8mm) 보고 "핸들 덜 벌림" 가설 제시 — 배치 중앙 70.3mm 로 기각
