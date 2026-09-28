# DEVLOG 2026-09-23 — pilot_A (73편 · map3 Atlas) 학습 입력 조립

작성 김준태 (트랙 B). 전부 서버(jupyter01) 🟢 로그 확인.

## 입력

| 항목 | 값 | 출처 |
|---|---|---|
| raw | 74 세션 (매핑 1 + 12 + 61) | `~/data/data2_pilotA/raw` · `sessions_after_2.zip`(61) + `data_extra.zip`(13, 도경 업로드) |
| SLAM | 73/73 attempt1 `camera_trajectory.csv` | `~/S15P21A103_umi/AI/datasets/orbslam/episode_runs` |
| 매핑 궤적 | map3 4028 프레임 | `map3_mapping_traj.tgz` → `processed_mapping/rec_1790128977527_8032e6a1/slam/` |
| prepare | 61/61 + 13/13 성공, 실패 0 | `prepare.log`, `prepare_extra.log` |
| 그리퍼 캘리브레이션 | `raw_closed_marker_center_mm 37.983` = 9/21 동일 | `configs/s22_gripper.json` |

## slam-tag (빌더 게이트) — pass

| | 9/21 atlas92 | 오늘 map3 |
|---|---|---|
| tracked_frames | 2148 | 4007 |
| frames_with_tag | — | 682 (17.0%) |
| MAD | 2.0mm | 2.49mm |
| max | 47.5mm | 48.5mm |

도경 정렬 게이트는 p95 30.643 > 30 **REJECT**. 지표가 다르다 (빌더: median-radius inlier spread / 도경: ArUco 재투영 p95). 둘 다 기록, 어느 쪽이 롤아웃과 상관 있는지는 pilot_B 비교로 본다.

## gate_slam_batch — 73/73 미판정

원인: attempt1 에 `slam/trajectory_validation.json` · `slam/receipt.json` 없음 (도경 v3 산출물 포맷). 파일을 만들어 넣지 않았다.
대체 근거:
- 도경 `orbslam_attempt1_audit.json`: pose coverage 1.0 · loss_episodes 0 — 73/73
- 독립 연속성 검사: state==2 프레임 간 최대 step 19.24mm < 50mm — 73/73 (9/23 오전)
- 지도 단일성: audit `atlas_source` 하나, run 스크립트 하나. receipt 부재로 sha 대조는 못 했다 ⚠️

## plan — 60 / 73

규칙: `outcome==success ∧ gripper.maximum_missing_run_frames ≤ 15` (빌더 `configs/dataset.json` 한계와 동일).
탈락 13 = outcome 4 (failure 3 · unrated 1) + missing_run>15 9. 파일 부재 0.
missing_run 분포: ≤7 29편 · 8~15 31편 · >15 9편. 9/21 E 도 10~12 편 포함됐다.

## build — 완료

`~/data/data2_pilotA/out/ds_pilotA_map3` 1.59GB · report `physical_deployment_ready: true` · 16:45 → 17:20 (35분, 60편).
⚠️ 출력 경로에 `.zarr.zip` 확장자를 안 붙였다 (어시스턴트 명령 오류). report 내부 경로가 이 이름이라 이름은 그대로 둔다.

## camera_tcp 함정 (TS)

`ubuntu_umi/02_umi_dataset_builder/configs/s22_camera_tcp.json` (기본값) = `cad_estimate_axis_mapping_unvalidated`, `physical_deployment_allowed: false`, sha `00955e02…`.
9/21 E 가 쓴 것 = `~/S15P21A103/AI/configs/umi_builder/s22_camera_tcp.json`, `physically_validated`, sha `4e16c0a2…`.
build 시작 후 report 해시 대조로 잡아 중단·재시작. **기본값으로 돌리면 hand-eye 가 조용히 바뀐다.** "설정 기본값 당연히 된다 가정 금지" 사례 추가.
→ build 명령에 `--camera-tcp` 절대경로 명시 고정.

## UMI 체크아웃

트레이너 기본 `tmp/camera/third_party/umi` 없음 → `FileNotFoundError`.
`--umi-root ~/hyeonseok/umi_gpu_bundle/third_party/umi` (커밋 d095ba95 = 요구 커밋, C run manifest 의 umi_root 와 동일) 🟢.
`~/handoff/third_party/umi` 는 git 커밋 확인 불가 — 사용 안 함.

## 학습 설정 — 정정

~~기본 profile · batch 64 … C 와 동일~~ **틀렸다.**
C run manifest 는 `batch: None` 이었고 config.yaml 에 `batch_size: 64` 가 있어 "기본값 = 64" 로 가정했다.
실제 우리 사본 기본값으로 돌리니 `train 18004 / dataloader 4501` → **batch 4**. C 는 `21763 / 341` = batch 64 🟢.
→ 첫 run 중단·삭제 (1 에폭 미만, ckpt 없음), `--batch 64` 명시로 재시작.
C 가 어떤 트레이너 사본(`~/ubuntu_umi` vs `~/hyeonseok/umi_gpu_bundle`)으로 돌았는지 미확인 — src diff 대조 대기.

확정 설정: `--umi-root 현석 번들 · --batch 64` · 나머지 기본 (lr 3e-4 · seed 42 · 120 epoch · checkpoint_every 1 은 config.yaml 로 재확인 필요) · GPU 1.
run-id `pilot_A_map3_b64_HHMMSS`, runs-dir `~/data/data2_pilotA/runs`.
val 분할: batch 4 run 기준 train 18004 / val 2541 (12.4%, 편 단위 분할이라 0.15 와 다름).

## 사전 등록 (결과 보기 전)

- pilot_A(map3, 정렬 REJECT) vs pilot_B(재촬영 매핑 best-of-3): **pilot_A ≤ pilot_B, 차이는 작을 것** 🟡
  참조 집단: 상대 EEF 합성은 월드 상수 오프셋을 상쇄한다. 지도 스케일 왜곡만 남는다.
- 판정은 롤아웃(젯슨 실물)이며 val loss 가 아니다.

## D-AI-52 (김준태) — pilot_A 는 도경 정렬 게이트 REJECT 매핑을 사용

근거: 빌더 자체 게이트 pass · 9/21 동급 spread · 재촬영 매핑 대기 중 학습 병렬 진행.
되돌릴 조건: pilot_B 가 동일 평가에서 유의하게 앞서면 pilot_A ckpt 폐기, 이후 정렬 게이트를 필수로 승격.

## 어시스턴트 오류 (오늘)

1. `prepare --camera-imu --gripper` 를 플래그로 씀 — `--help` 대조 규칙 위반. 9편 실패 후 발견
2. 글롭 `rec_1790133*` 가 61편 중 9편만 매칭 — 접두어 분포를 안 보고 썼다
3. 같은 nohup 을 두 번 띄우게 하는 명령 배치 (사용자 중복 실행 유발)
4. 긴 grep 명령이 터미널 히스토리와 엉킴 — 다시 발생
5. `--camera-tcp` 기본값 가정 — 해시 대조로 잡았으나 build 시작 후였다. 시작 전 대조가 맞는 순서
6. build 출력 경로 확장자 누락
7. **batch 기본값 가정** — config.yaml 의 64 를 "기본값"으로 읽음. 실제 기본 4. 오늘 기본값 가정 2번째 (5번과 같은 모양). 학습 시작 후 dataloader 수로 잡았다
8. `train_policy.py check` 결과를 안 보고 학습으로 넘어감

규칙화: **비교 대상 run 과 같은 설정이라고 주장하려면, 비교 대상 config.yaml 의 값을 전부 CLI 에 명시한다. 기본값에 기대지 않는다.**

## 도경 회신 (요청)

1. 73편 attempt1 확인, 60편 plan 으로 학습 시작
2. pilot_B 산출물부터 편별 `slam/trajectory_validation.json` + `slam/receipt.json`(load_map_sha256) 넣어달라 — `gate_slam_batch` 가 그걸 읽는다
3. 9/21 데이터 6편(cee250be, 53de6fff, b51092e6, 2899fb2e, 54b2b5f5, 95e3715a) 포즈 점프 + 82~96% 미추적인데 report 게이트 통과 → pv_E 에 들어갔음
4. 연속성 임계 50mm(state==2) + state≠2 비율 게이트 제안
5. 재촬영 매핑 ×3 는 현재 마커 위치·같은 장면

## 내일 아침

- `grep TRAIN_ ~/data/data2_pilotA/train.log` 완료 확인
- 트레이너 사본 src diff 결과 확인
- run config.yaml 의 lr/seed/epoch 를 C 와 전 항목 대조
- best.ckpt → 젯슨 롤아웃 (판정은 롤아웃)
