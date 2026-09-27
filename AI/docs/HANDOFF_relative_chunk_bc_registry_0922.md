# RelativeChunkBC 알고리즘 레지스트리 인계

> 상태: `candidate` · 학습 어댑터 자체검사 완료 · 실물 배포 승인 아님

## 제공 경계

### 1. 공식 UMI Zarr → v10

- 구현: `AI/tools/convert_umi_zarr_to_v10.py`
- 입력 필수 배열:
  - `data/camera0_rgb` (`uint8`, `N×224×224×3`)
  - `data/robot0_eef_pos` (`N×3`)
  - `data/robot0_eef_rot_axis_angle` (`N×3`)
  - `data/robot0_gripper_width` (`N×1` 또는 `N`)
  - `meta/episode_ends`
- 출력: `umi_relative_chunk/0.2.0-provisional` 에피소드별 NPZ
- 기본 계약: H=2, K=8, rot6d 첫 두 **행**, gap 절대 m

사용 예:

```bash
python AI/tools/convert_umi_zarr_to_v10.py \
  --input /data/dataset.zarr.zip \
  --output /data/dataset_v10 \
  --rate-hz 10
```

공식 UMI Zarr에는 원본 timestamp와 v10 `source_row`가 없으므로 rate를 명시하거나
동일 basename의 `.provenance.json`에 `rate_hz`가 있어야 한다. 출력 timestamp는
그 rate에서 합성된다. 따라서 기존 v10과 비트 동일하게 되돌리는 변환은 아니지만,
절대 EEF pose에서 현재 pose 기준 상대 proprio/action을 다시 계산하므로
RelativeChunkBC 학습 계약에는 맞는다. 전역/시뮬 grasp 정렬은 상대변환에서 상쇄된다.

### 2. 레지스트리 학습 진입점

- 구현: `AI/algorithms/relative_chunk_bc_adapter.py`
- 엔트리포인트: `algorithms.relative_chunk_bc_adapter:train`
- 서명:

```python
train(dataset, profile, run_dir, reporter, cancel_event) -> manifest
```

`dataset`은 변환 완료된 v10 디렉터리다. `profile`은 mapping이며 `config`가
필수다. 지원 override는 다음과 같다.

```json
{
  "config": "configs/train/relative_chunk_bc_spatial4.yaml",
  "device": "cuda",
  "epochs": 30,
  "batch_size": 32,
  "seed": 0,
  "lateral_sampling_factor": 1.0,
  "num_workers": 0
}
```

`reporter`는 현재 GPU FastAPI의
`report(status, stage, percent, **extra)`를 지원하며 단일 payload callable도
지원한다. `cancel_event.is_set()`은 시작 전, 매 epoch 및 매 batch 경계에서
확인한다.

출력:

- `run_dir/checkpoints/last.pt`
- `run_dir/checkpoints/best.pt`
- `run_dir/manifest.json`
- 반환값: 같은 manifest dict

manifest에는 config/dataset index/checkpoint SHA-256, split, epoch별 loss,
best checkpoint와 현재 FastAPI 호환 필드(`checkpointStorageKey`, `sizeBytes`,
`sha256`, `metadata`)가 포함된다.

## 레지스트리 기술자

`AI/registry/algorithms/relative_chunk_bc.json`에 candidate 항목을 만들었다.
기본 profile은 현재 상대 BC 중 시각 위치 보존 결과가 더 좋았던 spatial4이지만,
제품 정책이 diffusion이라는 결정은 바꾸지 않는다. RelativeChunkBC는 비교·진단
후보다.

## 검사

```bash
python AI/tools/convert_umi_zarr_to_v10.py --selftest
python AI/tools/check_relative_chunk_bc_platform_adapter.py
```

- 변환 수학·v10 계약: `6/6 PASS`
- 정확한 함수 서명, 취소, reporter, 1 epoch CPU 학습, 체크포인트·manifest:
  `8/8 PASS`

현재 로컬 Python에는 `zarr` 패키지가 없어 1.6GB 실데이터 Zarr 파일의 완전한
입출력 smoke는 실행하지 않았다. 트랙 B 학습 환경에는 공식 UMI와 zarr가 있으므로,
레지스트리 연결 전 다음 한 번이 남는다.

1. 소형 Zarr 또는 한 편 subset을 v10으로 변환한다.
2. 생성된 `dataset.json`과 모든 에피소드를 기존 verifier로 검사한다.
3. `device=cpu, epochs=1` 또는 GPU 2 smoke로 `train(...)`을 호출한다.
4. reporter 진행률, 취소, manifest 및 best checkpoint 해시를 확인한다.

이 smoke에 이상이 없으면 트랙 B는 레지스트리 loader에서 JSON 기술자를 읽고
두 Python 경계를 호출하면 된다.
