# DEVLOG — 2026-09-22 · handoff 적응 · 축 매핑 90도 · 네 갈래 학습

작성자: 김준태(트랙 B)

---

## 0. 오늘의 한 줄

**`camera_tcp` 의 축 매핑 가정이 90도 틀려 있었고, 그게 정책을 실제로 바꿨다.**
같은 데이터·같은 trim 에서 좌표계 규약 하나만 바꿨는데 두 정책이 게이트의 2.4배로 갈렸다.

---

## 1. 도경 handoff 수령·검증

```
demonstrations_260921.zip                        d09bdd21…  2.1 GB   raw 115편
orbslam_handoff_20260921_id13_16cm_96of100.tar.gz d3bc9037…  10.3 MB  SLAM 산출물
```

sha256 양쪽 일치. 서버 `unzip` 부재 → `zipfile` 로 해제.

모수 대조:

```
camera_trajectory.csv  105 = selected 96 + rejected 8 + mapping 1
episodes_selected      96 · episodes_rejected 4 (디렉터리) · 궤적은 8
raw                    115 = 96 + 실패 13 + 매핑 1 + 캘리브 1
```

## 2. handoff 포맷 적응 — 네 가지 차이

도경 산출물은 우리 게이트·빌더가 기대하는 형태와 네 군데가 달랐다.
**도경 쪽 포맷은 고정이므로 우리가 맞춘다** (사용자 결정).

| 도경이 주는 것 | 우리가 필요한 것 | 처리 |
|---|---|---|
| 편 직하 `camera_trajectory.csv` | `<편>/slam/camera_trajectory.csv` | 링크 |
| `trajectory_validation_v10.json` | `slam/trajectory_validation.json` | 이름 맞춰 링크 |
| 없음 | `gripper_width.csv` · `telemetry.json` | raw 로 `prepare` 96편 |
| 없음 | `tx_slam_tag.json` | 매핑 편으로 `slam-tag` |

`slam-tag` 결과:

```
status pass · tag 13 / 0.16m · 태그 보인 프레임 973 / 추적 1807
위치 산포 MAD 2.88 mm · 최대 49.8 mm
```

### 왜 그리퍼 파일이 없었나

`run_orbslam_linux.sh` 소스를 읽어 확인했다. SLAM 입력은
`video.mp4` · `telemetry.json` · `s22_mono_inertial.yaml` · vocabulary · `s22_slam_mask.png` 다.
**`gripper_width.csv` 는 SLAM 입력이 아니다** — zarr 입력이다. 그래서 handoff 에 없었다.

## 3. SLAM 게이트 — 96/96 불합격에서 94/2/0 까지

초회 결과가 **96편 전부 불합격**이었다. 원인이 셋이었고 순차로 걷어냈다.

### ① 읽을 파일이 없었다

전 항목 `None`. `prepare` 미실행 + validation 파일명·위치 불일치.
2절의 적응으로 해소.

### ② 마커 검출률 기준의 전제가 깨져 있었다

```
기준       0.80   근거: 현석 v4 의 0.847
오늘 실측  최대 0.89 · 중앙 0.74 (n=45)
```

사용자 진술: **오늘 수집은 마커를 간헐적으로만 노출하는 프로토콜**이다.
0.80 의 근거였던 현석 v4 는 마커 상시 노출 조건이라 **전제가 다르다.**

파지 순간 창(±15프레임) 검출률도 같이 쟀다:

```
창 검출률   중앙 0.71
전체 검출률 중앙 0.74
0.8 기준 통과   창 18 / 전체 15  (n=45)
```

→ **"파지 순간만 보면 된다"는 가설은 수율 문제의 답이 아니다.** 창과 전체가 거의 같다.
기각 사실을 그대로 남긴다.

결론: 마커 검출률·결손 구간을 **필수에서 권고로** 내렸다. 게이트를 낮춘 게 아니라
**전제가 깨진 기준을 다시 정한 것**이다. 근거가 바뀌면 되돌린다.

⚠️ `gap` 채널 보간이 학습에 미치는 영향은 **미측정**. LIMITS 대상.

### ③ `tracked_ratio` 를 v10 스키마에서 못 읽었다

게이트는 `tracked_ratio_after_initialization` 을 찾는데 v10 은
`tracked_frames` / `frames` / `first_tracked_frame` 으로 준다.
`_tracked_ratio()` 헬퍼로 계산하게 했다.

**`coverage` 를 그대로 쓰지 않았다** — 초기화 전 프레임이 분모에 들어가 과소평가된다.

### 배치 항목 — 영수증을 근거 있게 채웠다

`receipt.json` 이 없어 "배치가 같은 지도를 썼다"가 미판정이었다.
`reports/atlas_selection_orb_only.json` 에 편별 아틀라스 기록이 있고
`atlas_sha256` 이 실제 `map1.osa` 해시와 일치함을 확인한 뒤 옮겨 적었다.

```
후보 아틀라스 1종 · 기록 10bf14e7 · 파일 10bf14e7 · 일치
편별 아틀라스 기록 96 / 96
→ 배치 판정: 통과  지도 1종 · 10bf14e7×96
```

영수증 파일에 **"우리가 잰 값이 아니라 옮겨 적은 것"** 을 명시했다.

### 최종

```
통과 94 · 불합격 2 · 미판정 0 / 전체 96
불합격 사유  파지 개구(편 최소) 28.3mm · 27.6mm — 진짜 불합격
미판정 항목  키프레임 수 · 편당 이동거리 · 카메라 궤적 범위 (전부 권고, v10 에 키 없음)
```

## 4. 빌더가 게이트와 별개로 결손 구간을 강제한다

`ValueError: marker missing run 57 exceeds limit` 로 zarr 빌드가 죽었다.
게이트에서 권고로 내려도 **빌더는 자기 config 로 거부**한다.

근거는 도경 쪽에 있었다 — `atlas_selection_orb_only.json` 의
`thresholds.maximum_gripper_missing_run_frames: 100000`. 도경도 이 프로토콜에선 안 쓴다.

**공식 `dataset.json` 은 건드리지 않고 `dataset_trim3.json` 사본**을 만들어 양쪽을 같은 값으로 맞췄다.
그래야 A/B 차이가 `trim` 하나로 남는다:

```
A/B 차이 키 {'episode_start_trim_s'} / 전체 키 10
```

## 5. 네 갈래 학습

```
        축          trim   편   프레임    train_loss  소요
A  ours  틀림(180)  0.0    94   25,475    0.013       2:06
B  offic 틀림(180)  3.0    94   17,015    0.015       1:04
C  ours  맞음( 90)  0.0    94   25,475    —           도는 중
D  offic 맞음( 90)  3.0    94   17,015    0.015       1:25
```

**프레임 수가 A=C, B=D 로 정확히 일치한다.** `camera_tcp` 는 좌표 변환이라
프레임 수를 안 바꾸는 게 맞다 — 한 변수만 바뀌었다는 검산이다.

`trim` 효과: 17,015 / 25,475 = **66.8%**. trim 3.0 이 33.2% 를 버렸고
그 안에 파지 순간이 들어 있다 (0921 측정).

## 6. 축 매핑 90도 — 오늘의 핵심 🟢

서버에 `camera_tcp` 판이 **둘** 있었다.

```
                 T_lens_tcp   T_camera_lens   T_camera_tcp        status
00955e02 (사용)  90.9762도    180도           90.9762도           cad_estimate_axis_mapping_unvalidated
fc4a6912         90.9762도(동일) 90도         180.0000도          cad_tcp_video_axes_aligned_origin_provisional
```

`T_lens_tcp` 는 **완전히 같다.** 차이는 축 매핑 가정 하나다.
`fc4a6912` 가 스스로 적어놓았다:

```json
"old_saved_jpeg_from_cad_lens": "Rz(+90deg), solved by requiring the CAD gripper_tcp axes
                                 to equal the video-checked v10 canonical axes",
"previous_Rz_180_assumption_rotation_error_deg": 89.99999880178046
```

근거의 무게가 다르다 — 전자는 "180도 돌아갔을 것"이라는 **가정**,
후자는 **5,149프레임 마커-중점 실측**으로 푼 값이다.

```
D = X_00955⁻¹ @ X_fc4a   회전 90.0000도 · 평행이동 [9.56, 37.73, 10.11] mm
```

**0921 의 atlas92 교란(회전 폭 0.749→0.519)의 정체가 이것일 가능성이 크다.**

### 보정 규약 확정 — 재학습 불필요 🟢

액션이 상대 궤적이므로 TCP 정의 변경은 conjugation 으로 표현된다.
합성 SE(3) 로 세 후보를 스윕했다:

```
D⁻¹ A D    최대오차 3.331e-16 · 위치오차 0.0000 mm   ← 정답
D A D⁻¹    최대오차 1.995e-02 · 위치오차 19.9478 mm
보정 없음   최대오차 9.974e-03 · 위치오차  9.9739 mm   ← 판별행
```

정책 출력에 상수 행렬 하나를 곱하면 축이 옮겨간다. **학습을 다시 할 이유가 없다.**
재학습이 필요한 건 절대 좌표로 학습했을 때다.

⚠️ 이 보정을 **아직 코드에 적용하지 않았다.** 적용 지점은 젯슨 러너인데
그 코드를 아직 못 봤다.

## 7. 축 90도가 정책을 바꿨나 — B vs D 🟢

`probe_ckpt_agreement.py` · 같은 94편 · 같은 trim 3.0 · 유일한 차이는 `camera_tcp`.

```
관측 4벌 · within 120쌍 · between 144쌍
위치   within 18.271 mm   between 52.894 mm
회전   within  1.047 deg  between  2.107 deg
개구   within  1.092 mm   between  1.330 mm

판정   구분 가능   52.894 / 18.271 = 2.89   (게이트 1.2)
```

**말하는 것**: 좌표계 규약 하나가 정책을 실제로 바꿨다.
**말하지 않는 것**: 어느 쪽이 맞는지. 그건 `fc4a6912` 의 실측 근거로 판단한 것이지
이 측정으로 판단한 게 아니다. 그리고 **합성 관측**(회색 이미지)이라
실제 장면에서의 차이는 별개다.

`within 18.271mm` 가 작지 않다 — diffusion 샘플링 노이즈다.
1.5배쯤 나왔으면 판정을 못 했을 것이다.

## 8. 젯슨 패키지 — 네 갈래 전부 FAIL

```
통과 10 · 불합격 2 · 미판정 0 / 12   판정: FAIL
[불합격] training_input_status      provisional_camera_tcp
[불합격] physical_deployment_ready  False
```

**두 건이 같은 원인**이다. `camera_tcp.json` 파일 자체가
`"physical_deployment_allowed": false` 를 들고 있고 빌더가 그대로 전파한다.

C·D 도 통과 못 한다 — `fc4a6912` 는 **축은 실측으로 맞췄지만 origin 은 여전히 미실측**
(`..._origin_provisional`)이라 같은 딱지가 붙는다.

파일 자체는 다 만들어졌다 (`encoder.pt` 44.9MB · `denoiser.pt` 95.9MB · tgz 126MB),
TorchScript 기준값 5/5 온전, pc_check 통과.

**목적을 갈라야 한다**: 성능 평가에는 못 쓰고, 젯슨 구동 경로가 뚫리는지 보는 데는 쓸 수 있다.

## 9. 미해결

| 무엇 | 누구 |
|---|---|
| `D⁻¹AD` 보정을 어디에 넣나 (젯슨 러너 코드 미확인) | MW(신현우) |
| `robot_execution_enabled` 의 뜻 | MW |
| 로봇 구동 로그 형식 · 에러별 대응 | MW |
| origin 까지 실측한 `camera_tcp` | HW/트랙 A |
| A vs C 비교 (축 효과가 trim 과 독립인지) | 트랙 B · C 학습 완료 후 |
| VLM 촬영 94장 정답 라벨 | 사용자 |
| `gap` 보간이 학습에 주는 영향 | 트랙 B |
