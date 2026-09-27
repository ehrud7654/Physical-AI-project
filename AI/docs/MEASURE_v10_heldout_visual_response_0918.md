# v10 시연 단위 홀드아웃의 측면 목표·영상 교란 재검사 (2026-09-18)

관련 Jira: `S15P21A103-154`, `S15P21A103-171`. 학습 정책 추론은 **로컬 CPU**에서만 실행했습니다. 로봇 명령·접촉 성공률 검사가 아닙니다.

## 비교 조건

- 데이터: `umi_real_relative_20260911_v10`, 70편 중 체크포인트가 학습에 사용하지 않은 14편. 두 체크포인트의 `val_episodes`, seed 0, 입력 `(2,3,224,224)`·출력 `(8,10)`, 10Hz 계약이 같은지 검사했습니다.
- 기준 모델: seed-0 best-val SHA-256 `5bc8a25de054bc29967ebe387d808d5add0e7ad8f791c60e40b20f3440e6adbb`.
- 처치 모델: 측면 이동 사례 train-only 4배 재표집 best-val SHA-256 `74f770c58c994ebfb48d69c668b0e74fe29916ce01ad37a5cd48fbad5f318448`.
- 선택: 기존 시뮬 handoff 영상의 물체 bbox 크기 ±25%와 관측 gap `0.06–0.07m`에 맞는 **모든** 홀드아웃 프레임. 14편 중 10편에서 28행이 선택됐습니다. 이전의 편당 1행 검사보다 넓지만, 28행은 독립 시연 28편이 아닙니다.
- 타깃: 첫 4개 미래 목표 중 마지막(index 3)의 TCP-local x 이동. 실제 물체 배치 좌표가 아니라 영상 속 물체 중심과 기록된 상대 이동을 비교합니다.
- 교란: **추론 시점**, 같은 체크포인트·proprio에서 정상 영상, 프레임/채널별 평균색 채움, Gaussian 가산 노이즈 σ=96을 비교했습니다. 노이즈 draw는 행별 고정하고 두 모델에 동일하게 적용했습니다.

## 결과

| 홀드아웃 조건 | 기준 정상 | 4배 재표집 정상 | 4배 재표집 평균색 | 4배 재표집 노이즈 |
|---|---:|---:|---:|---:|
| 전체 28행의 x MAE | 12.71mm | 8.66mm | 13.22mm | 13.19mm |
| 편별 MAE를 동일 가중 평균 | 9.70mm | 7.39mm | 10.47mm | 11.36mm |
| 중앙 영상 위치, 23행 x MAE | 2.09mm | 2.37mm | 2.63mm | 3.85mm |
| 실제 목표 `|x|>10mm`, 5행 x MAE | 61.55mm | 37.55mm | 61.95mm | 56.13mm |
| 큰 목표 방향 일치 | 2/5 | 4/5 | 2/5 | 3/5 |

큰 목표 5행은 **3편**에만 속합니다. 그중 오른쪽으로 치우친 1편의 2행은 기록 타깃이 약 −70mm인데 재표집 정책은 각각 약 −3.3, −3.5mm만 냈습니다. 즉 방향 일치 4/5는 물체를 충분히 따라간다는 뜻이 아닙니다. 반면 왼쪽으로 크게 치우친 1편의 2행(타깃 약 +76, +80mm)은 재표집 모델이 약 +62, +54mm를 출력했고, 평균색으로 바꾸면 약 −2mm가 됐습니다. 이 일부 사례에서는 영상 정보가 반응에 영향을 주지만, 3편만으로 일반화 성능을 판정할 수 없습니다.

### 시연 단위 짝지은 불확실성 확인

28행을 독립 표본처럼 취급하지 않기 위해, 각 시연의 평균 오차를 먼저 계산하고 **시연 10편을 재표집 단위**로 탐색적 percentile bootstrap 20,000회를 실행했습니다. 양수는 앞 조건보다 뒤 조건의 오차가 낮음을 뜻합니다.

| 짝지은 오차 차이 | 개선된 시연 | 편별 평균 차이 | 탐색적 95% 구간 |
|---|---:|---:|---:|
| 기준 정상 − 4배 재표집 정상 | 4/10 | +2.31mm | −0.39 ~ +6.81mm |
| 4배 재표집 평균색 − 정상 | 6/10 | +3.08mm | −0.02 ~ +8.07mm |
| 4배 재표집 노이즈 − 정상 | 8/10 | +3.96mm | +1.02 ~ +7.20mm |

기준 대비 재표집 개선 구간과 평균색 대비 정상 영상 이점 구간은 **0을 포함**합니다. 따라서 행 평균의 개선과 일부 영상 교란 민감도를 전체 시연에 일관된 효과로 해석하지 않습니다. 노이즈 조건은 이 작은 선택 집합에서 오차를 높였지만, 이는 물체 위치를 올바르게 추적한다는 증거가 아닙니다. 이 구간은 사전등록된 성공 판정이 아니라 이미 선택된 시연 10편에 대한 사후 불확실성 진단입니다. 큰 목표가 있는 독립 시연은 여전히 3편뿐이므로 그 부분의 안정적 구간 추정은 하지 않았습니다.

기존 ±10mm **물체만 이동** 시뮬 검사의 16/52→28/52 방향 일치와도 해석이 다릅니다. 이번 검사는 서로 다른 실물 시연의 기록 타깃을 예측했으며 카메라 자세·손·배경·수행 단계가 함께 달라집니다. 실제 물체 배치 좌표 라벨은 없고, 영상 bbox는 색 기반 자동 검출입니다. 두 검사를 합쳐 독립 성공률처럼 사용하지 않습니다.

결론: 재표집은 희소한 큰 측면 이동 사례의 기록 목표 오차를 일부 줄였지만 중앙 사례 오차는 소폭 늘었고, 큰 이동 3편에서 일관된 대응을 보이지 못했습니다. 현재 결과는 **시각적 위치 대응 통과, 실물 파지 성공 또는 배포 승인 근거가 아닙니다.** 새 수집분에서 물체 배치를 실제로 달리한 시연을 에피소드 단위로 분리해 재검사해야 합니다.

## 재현

코드: `AI/tools/eval_v10_heldout_visual_response.py`, 시연 단위 분석 `AI/tools/analyze_v10_heldout_episode_effect.py`, 집중 검사 `AI/tools/check_v10_heldout_visual_response.py`. 원시 행별 결과와 시연 단위 결과는 각각 Git 제외 산출물 `AI/out/v10_heldout_all_matched_visual_response_20260918.json`, `AI/out/v10_heldout_episode_paired_effect_20260918.json`입니다.

```powershell
$env:PYTHONPATH='C:\Users\SSAFY\Desktop\S15P21A103\.tmp\umi-runtime-deps;C:\Users\SSAFY\Desktop\S15P21A103\AI'
& 'C:\Users\SSAFY\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' AI/tools/eval_v10_heldout_visual_response.py `
  --data AI/datasets/umi_real_relative_20260911_v10 `
  --match AI/out/v10_open_gap_sim_scale_visual_match_counts_20260917.json `
  --baseline C:\Users\SSAFY\Downloads\umi_real_relative_v10_seed0_bestval.pt `
  --lateral4 C:\Users\SSAFY\Downloads\umi_real_relative_v10_seed0_lateral4_bestval.pt `
  --out AI/out/v10_heldout_all_matched_visual_response_20260918.json
```

재실행하려면 기존 `--out` 파일을 덮어쓰지 말고 새 이름을 지정해야 합니다.
