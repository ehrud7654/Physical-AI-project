# v10 공간 위치 보존 영상 인코더 대조 실험 (2026-09-18)

관련 Jira: `S15P21A103-154`, `S15P21A103-171`.

## 목적

현재 진단용 relative-chunk BC의 영상 인코더는 마지막 convolution feature
map을 전역 평균해 1개 벡터로 만든다. 이 구조가 물체의 좌우 위치를 약하게
만드는지는 아직 확인되지 않은 **가설**이다. 데이터 분할·시드·손실·학습률을
고정하고, 평균 전의 공간 격자만 `1x1`에서 `4x4`로 바꿔 검증한다.

- 대조군: 기존 `configs/train/relative_chunk_bc.yaml`, 암묵적 `1x1`
- 처치군: `configs/train/relative_chunk_bc_spatial4.yaml`, 명시적 `4x4`
- 데이터: `umi_real_relative_20260911_v10`
- 고정 조건: seed 0, 56/14 episode split, 30 epochs, batch 32,
  lateral sampling factor 1
- 파라미터: 대조군 1,031,280개, 처치군 2,014,320개

파라미터 수가 함께 늘어나므로 결과가 좋아져도 공간 보존만의 순수 효과라고
단정하지 않는다. 다만 기존 전역 평균이 병목인지 빠르게 거르는 첫 대조군으로
사용한다. factor-4 재표집과 동시에 적용하지 않아 두 개의 처치를 섞지 않는다.

## V100 GPU 2 학습

공유 서버에서는 **학습만** 실행한다. 체크포인트 추론·probe·rollout은 실행하지
않는다. 먼저 3 epoch smoke를 통과한 뒤 30 epoch를 실행한다.

```bash
cd ~/S15P21A103_umi/AI
conda activate aiot_v100

export CUDA_VISIBLE_DEVICES=2
export AI_THREADS=2
export OMP_NUM_THREADS=2
export MKL_NUM_THREADS=2
export OPENBLAS_NUM_THREADS=2
export NUMEXPR_NUM_THREADS=2

python -c "import torch; print(torch.cuda.is_available(), torch.cuda.device_count(), torch.cuda.get_device_name(0))"
nvidia-smi -i 2 --query-gpu=index,name,memory.used,memory.total,utilization.gpu --format=csv

python tools/train_relative_chunk_bc.py \
  --data datasets/umi_real_relative_20260911_v10 \
  --config configs/train/relative_chunk_bc_spatial4.yaml \
  --device cuda \
  --epochs 3 \
  --batch-size 32 \
  --seed 0 \
  --out checkpoints/relative_chunk/umi_real_relative_v10_spatial4_smoke.pt

python tools/train_relative_chunk_bc.py \
  --data datasets/umi_real_relative_20260911_v10 \
  --config configs/train/relative_chunk_bc_spatial4.yaml \
  --device cuda \
  --epochs 30 \
  --batch-size 32 \
  --seed 0 \
  --out checkpoints/relative_chunk/umi_real_relative_v10_spatial4_seed0.pt
```

로그에 `image encoder spatial grid: 4x4`가 반드시 표시돼야 한다. 기존
seed-0 best-val 체크포인트와 새 `spatial4_seed0_bestval.pt`를 로컬 PC로
내려받아 평가한다.

## 로컬 판정

1. 같은 14개 홀드아웃 시연에서 정상/평균색/가산 노이즈 입력의 네 번째
   목표 local-x 오차를 비교한다.
2. 로봇·카메라를 고정하고 물체만 `+/-10mm` 이동한 paired render에서 첫
   청크의 방향과 투영 gain을 비교한다.
3. 행 수가 아니라 episode를 통계 단위로 사용한다. 큰 측면 목표가 현재
   3편뿐인 한계도 그대로 기록한다.
4. validation loss만으로 채택하지 않는다. 정상 영상의 episode-level 오차와
   물체 이동 방향·크기가 함께 개선되고, 공간정보를 없앤 입력보다 명확히
   좋아야 다음 폐루프 검사로 간다.

```powershell
$env:PYTHONPATH='C:\Users\SSAFY\Desktop\S15P21A103\.tmp\umi-runtime-deps;C:\Users\SSAFY\Desktop\S15P21A103\AI'
& 'C:\Users\SSAFY\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' AI/tools/eval_v10_heldout_visual_response.py `
  --data AI/datasets/umi_real_relative_20260911_v10 `
  --match AI/out/v10_open_gap_sim_scale_visual_match_counts_20260917.json `
  --baseline C:\Users\SSAFY\Downloads\umi_real_relative_v10_seed0_bestval.pt `
  --candidate C:\Users\SSAFY\Downloads\umi_real_relative_v10_spatial4_seed0_bestval.pt `
  --out AI/out/v10_heldout_spatial4_visual_response_20260918.json
```

이 검사는 로컬 진단이며 실물 성공, 배포 승인 또는 모터 구동 허가가 아니다.
