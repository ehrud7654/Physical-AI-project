# MEASURE — 2026-09-22 · 액션 계약 검증 (pose_repr · 인덱스 · 실행 스텝)

작성자: 김준태(트랙 B) · 확신도 🟢 소스·config·실측 대조

> 계기: 도경의 v10 인수인계 문서 §8.4 — *"Track B 의 `absolute[1:5]` 를 v10 에 복사하면 off-by-one"*.
> 우리 경로에서도 그 가정이 맞는지 아무도 확인한 적이 없어서 전수 검증했다.

## 0. 결과 요약

| 항목 | 판정 | 근거 |
|---|---|---|
| 좌표 규약 `pose_repr` | **정상** 🟢 | 학습 config `obs_pose_repr: relative` = 실행부 복원식과 일치 |
| 인덱스 `action[1:5]` | **정상** 🟢 | `action.latency_steps: 0`, 기준 `pose_mat[-1]` → 0행은 현재 시점 |
| 실행 스텝 수 | **불일치** ⚠️ | config `n_action_steps: 8` vs 러너 하드코딩 4 |

## 1. pose_repr — 한때 최대 용의자였으나 기각 🟢

`diffusion_policy/common/pose_repr_util.py` 에 의미가 다른 두 규약이 공존한다.

```python
elif pose_rep == 'rel':          # 주석 원문: legacy buggy implementation
    pos = pose_mat[...,:3,3] - base_pose_mat[:3,3]        # 월드 프레임 차이
    rot = pose_mat[...,:3,:3] @ inv(base_pose_mat[:3,:3])
elif pose_rep == 'relative':
    out = inv(base_pose_mat) @ pose_mat                   # TCP 프레임 상대 포즈
```

실행부는 `target = state["pose"] @ pose10d_to_mat(row)` — **`'relative'` 의 역변환**이다.
`'rel'` 로 학습됐다면 평행이동이 base 자세만큼 회전돼 나가고, 회전 합성 순서도 반대가 된다.

학습 config 확인 (run 과 젯슨 패키지 양쪽):

```yaml
task.pose_repr:
  obs_pose_repr: relative
  action_pose_repr: relative
```

→ **일치. 기각.**

⚠️ 기록해 둘 것: `umi_dataset.py` 는 action 변환에 `self.action_pose_repr` 가 아니라
**`self.obs_pose_repr` 를 쓴다** (46행에서 `action_pose_repr` 를 정의해놓고 사용하지 않는다).
지금은 둘 다 `relative` 라 결과가 같지만, **둘을 다르게 설정하면 조용히 무시된다.**

## 2. 인덱스 — `action[1:5]` 는 맞다 🟢

```
action  horizon 16 · latency_steps 0 · down_sample_steps 3   (30fps → 10Hz)
obs     horizon  2 · latency_steps 0 · down_sample_steps 3
기준     base_pose_mat = pose_mat[-1]  (마지막 관측 = 현재)
```

`latency_steps = 0` 이고 기준이 현재 관측이므로 **`action[0]` 은 현재 시점 = 항등**이어야 한다.
0행을 건너뛰는 `[1:5]` 가 규약에 맞다.

도경 문서의 경고는 **v10(청크 8, index 0 이 첫 미래 목표)에만 해당**한다. 두 경로는 다르다.

### 부수 획득 — 정책의 오차 바닥 🟢

`action[0]` 은 **정답이 0 인 행**이다. 실측(live_lift3 + live_far, 청크 30개):

```
평행이동 노름   중앙 3.13 mm · 최소 0.51 · 최대 16.69
rot6d 항등거리  중앙 0.0124 · 최소 0.0037 · 최대 0.1231
1mm 미만인 청크  5 / 30
```

→ **정책 예측의 오차 바닥은 약 3mm 다.** 앞으로 "예측이 몇 mm 정확한가"의 기준선으로 쓴다.
참고: 같은 청크의 1행 4.68mm, 15행 42~47mm.

## 3. 실행 스텝 불일치 — 신규 ⚠️

```
학습·설정   n_action_steps: 8     → 0.8초 분량 (10Hz 기준)
실행부      action[1:5] = 4스텝   → 0.4초 분량
```

러너 소스에서 **하드코딩**이고 인자가 없다:

```
motion_planner.py:67   for action_index, row in enumerate(action[1:5], start=1)
motion_planner.py:141  같은 slice (dry-run 경로)
cli.py                 "checkpoint_waypoints": 4   보고값도 상수
```

receding horizon 으로 일부만 실행하는 것 자체는 정상 설계다. 다만 **학습이 상정한 값과 다르다는 사실이
어디에도 기록돼 있지 않았다.** 영향은 **미측정.**

여기에 오늘 확정한 settle 미대기가 겹친다 — 0.4초 분량을 시간 제어 없이 쏘고,
도달 확인 없이 약 0.65초 뒤 재관측한다. **명령된 궤적을 끝내지 못한 채 다음 청크로 넘어간다.**

⚠️ 실행부는 현석 소유 코드다. 스텝 수를 바꾸려면 **트랙 간 영향 명시 후** 수정해야 한다.

## 4. 공식 UMI 체크아웃 — 두 판이 서로 다르다 🟢

```
~/hyeonseok/umi_gpu_bundle/third_party/umi   git HEAD d095ba9590df…  (핀 커밋 일치)
                                              umi_dataset.py md5 cad8b71d…
~/handoff/third_party/umi                     git 아님
                                              umi_dataset.py md5 9e5471e2…   ← 다른 파일
```

학습은 `UMI_ROOT = BUNDLE/third_party/umi` 로 **hyeonseok 쪽**을 썼고,
`umi_policy/upstream.py` 가 커밋과 패치 2종의 마커 문자열까지 검사해 통과했다.

→ **규약을 읽을 때 `handoff/` 쪽 파일을 보면 틀린다.**

## 5. 두 경로 구분 (혼동 방지)

| | 도경 v10 | 우리 pv_E |
|---|---|---|
| 스키마 | `umi_relative_chunk/0.2.0` | 공식 UMI zarr |
| 정책 | RelativeChunkBCNet (CNN + MLP) | diffusion policy (ResNet18 + UNet) |
| 손실 | L1 | diffusion |
| 청크 | 8 | 16 |
| index 0 | **첫 미래 목표** | **현재 시점(항등)** |
| 실행 경계 | `AI/umi/chunk_runtime.py` | `runtime/umi_checkpoint_executor` |

둘 다 10차원(pos3 + rot6d + gap) 상대 표현이라 **겉모습이 같다. 인덱스 규약이 반대다.**
오늘 실물에 올린 것은 **pv_E(후자)** 다.

## 6. 남은 확정 원인 (이 문서 기준 갱신)

```
1. settle 미대기 → 관측 기준점 누적 하강        7/7 런 🟢
2. 조기 폐쇄 · 시작 개구 79mm                    7/7 · 10/10 런 🟢
3. n_action_steps 8 vs 실행 4                    신규 · 영향 미측정 🟡
```

기각된 것: 시연 시작거리 균일 · 실행부 스케일 · 청크 간 상쇄 · 프레임 회전 ·
접근축 62° · "정책이 하강을 명령" · pose_repr 레거시 · `action[1:5]` off-by-one
