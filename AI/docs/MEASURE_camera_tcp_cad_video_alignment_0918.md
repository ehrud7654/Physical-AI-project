# HW CAD TCP와 v10 canonical/video hand-eye 대조

기준일: 2026-09-18
관련 Jira: `S15P21A103-154`, `S15P21A103-156`

## 결론

HW handoff의 `gripper_tcp` 축은 v10 canonical pinch 축과 같다.

- `+X`: jaw 개폐축
- `+Y`: `custom_hand +Z`, 위쪽
- `+Z`: `custom_hand -Y`, 손끝/수평 접근 방향
- 설계 원점: `custom_hand` 기준
  `(0, -78.018753, +27.000012) mm`

따라서 `T_cad_tcp_v10_canonical`은 항등이다. 별도 90도 child-frame 회전을
적용하면 안 된다. 다만 이 원점은 ver1 URDF/CAD의 **설계 TCP**이며 실제 패드
접촉면 중심과 일치하는지는 독립 실측되지 않았다.

## 발견한 축 불일치

기존 74편 ORB-SLAM3 v10에 복사된
`s22_camera_tcp_cad_provisional_0918.json`은 다음 가정을 사용했다.

```text
T_upright_camera_cad_lens = Rz(180 deg)
```

이 값으로 얻은 CAD TCP와 0911 영상에서 5,149프레임으로 측정한 canonical
marker-midpoint TCP를 같은 축으로 비교하면 회전이 정확히 약 90도 어긋난다.
이는 pad/TCP child 축 차이가 아니라 CAD lens frame과 저장 OpenCV frame의
매핑 오류다.

0911 저장 JPEG는 물리적으로 뒤집힌 화면이었고, 0918 수집 앱의
`upright_v1`은 픽셀에 180도 회전을 이미 적용한다. 이를 명시적으로 합성하면:

```text
old saved JPEG <- CAD lens : Rz(+90 deg)  (영상 canonical 축으로 해를 구함)
upright_v1 <- old JPEG     : Rz(180 deg)
upright_v1 <- CAD lens     : Rz(-90 deg)
```

새 잠정 설정은
`configs/real/s22_camera_tcp_cad_video_aligned_provisional_0918.json`에 분리했다.
기존 74편 산출물을 조용히 덮어쓰지 않는다.

## 0918 raw 영상 직접 축 검증

0911 영상 대조만으로 신규 수집분의 축을 확정하지 않기 위해, 0918 raw에서
현재 선택 목록 전체에 걸쳐 균등하게 뽑은 6개 에피소드의 앞·중간·뒤 프레임을
직접 검사했다. 총 18프레임 모두에서 ArUco ID 0/1이 동시에 검출됐다.

각 프레임에서 다음을 같은 원본 픽셀 위에 투영했다.

- ID 0과 ID 1 중심을 잇는 실제 jaw 개폐축
- 기존 `Rz(180°)` camera→TCP의 `+X`
- 새 `Rz(-90°)` 후보 camera→TCP의 `+X`, `+Z`

원본 픽셀은 회전·반전·재저장하지 않았고 오버레이 출력만 별도로 만들었다.

| 비교 | jaw 축과의 무방향 각도 오차 중앙 | 최대 |
|---|---:|---:|
| 기존 `Rz(180°)` TCP `+X` | 89.866° | 89.907° |
| 새 `Rz(-90°)` TCP `+X` | 0.107° | 0.183° |

따라서 새 후보의 `+X`가 **0918 raw 자체의 실제 좌우 jaw 개폐축과 직접
일치**한다. 이는 더 이상 0911 데이터만을 전제로 한 결론이 아니다. 투영된
새 `+Z`는 18프레임 모두에서 화면 아래의 gripper/TCP에서 위쪽 물체 방향을
가리켜 수평 측면 접근 의미와도 일치한다. 단, `+Z` 결과는 물체 중심을 자동
검출한 수치 게이트가 아니라 오버레이 시각 확인이다.

재현 명령:

```bash
python AI/tools/visualize_camera_tcp_axes_0918.py \
  --selection tmp/raw_0918_full/atlas_selection_reviewed74.json \
  --intrinsics AI/configs/real/s22_orbslam3_mono_inertial_0918.yaml \
  --old-camera-tcp AI/configs/real/s22_camera_tcp_cad_provisional_0918.json \
  --candidate-camera-tcp AI/configs/real/s22_camera_tcp_cad_video_aligned_provisional_0918.json \
  --episodes 6 --frames-per-episode 3 \
  --out AI/out/camera_tcp_axis_overlay_0918
```

결과는 `AI/out/camera_tcp_axis_overlay_0918/summary.json`과
`contact_sheet.jpg`에 생성된다. 이 검사는 TCP **축 방향**을 검증하며, CAD
TCP 원점과 실제 패드 접촉 중심이 일치하는지는 검증하지 않는다.

## 동일 좌표계 수치 대조

모든 값은 신규 `upright_v1` OpenCV camera frame으로 표현했다.

| 기준점 | x mm | y mm | z mm |
|---|---:|---:|---:|
| 0911 영상 marker midpoint | +1.400 | +40.100 | +149.700 |
| HW CAD nominal `gripper_tcp` | -14.750 | +24.311 | +173.657 |
| CAD - marker | -16.150 | -15.789 | +23.957 |

두 frame의 회전 잔차는 수치 정밀도 안에서 `0°`다. 원점 차이는 camera frame
L2 `32.92mm`이며, marker canonical frame에서는
`(+16.150, +9.050, +27.227)mm`다.

이 translation을 곧바로 실제 marker→pad 보정값이라고 확정하지 않는다.
차이에는 다음 두 효과가 함께 들어 있다.

1. 영상값은 jaw marker midpoint이고 CAD값은 nominal `gripper_tcp`다.
2. CAD lens 기준점과 실제 optical entrance pupil의 차이가 실측되지 않았다.

특히 이전 HW fingertip 원점 추정과 비교할 때 lateral 방향 약 `14.75mm`가
남는다. 이 값은 CAD 행렬의 lens lateral 항과 같아 optical-origin 정의 차이일
가능성이 있으나, 현 자료만으로 어느 쪽을 수정해야 하는지는 확정할 수 없다.

## 재현 검사

```bash
python AI/tools/check_camera_tcp_cad_video_alignment.py
```

통과 조건:

- CAD TCP→v10 canonical이 항등
- 새 축 매핑과 영상 canonical 회전 잔차 `<= 0.001°`
- 기존 `Rz(180°)` 가정은 약 90° 불일치로 검출

## 데이터셋 영향

기존 `umi_real_relative_20260918_v10_orbslam_provisional_74ep`는
`Rz(180°)` 가정의 `camera_tcp.json`을 포함한다. 계약 shape·timestamp·gap 검증
결과는 그대로 유효하지만 TCP 축 의미는 이번 대조와 맞지 않는다. 따라서 트랙 B가
본 학습 기준으로 사용하기 전에 새 설정으로 74편을 재생성하고, 새 dataset 이름과
SHA-256으로 다시 인계해야 한다.

재생성 및 계약 검증을 완료했다.

- 새 데이터셋:
  `AI/datasets/umi_real_relative_20260918_v10_orbslam_cadtcp_video_aligned_provisional_74ep`
- 구성: 74 episodes / 4,714 anchors / 계약 문제 0건
- tar: 같은 이름에 `.tar` 확장자
- tar size: `956,860,928 bytes`
- tar SHA-256:
  `3b355a7a42e00fda475890a2c8d1e554f9bf9ecc8192043f90fa81e2fbecc0d1`
- `dataset.json` SHA-256:
  `f9e7253572e439b456601f5fa55e5f6425657f52e00d5fcd03527bbccbe1dc17`
- `verification_report.json` SHA-256:
  `15b86795fb6e98cbd0bd405361f259989f46189cc4993747cb7dce5b54b874d4`
- `camera_tcp.json` SHA-256:
  `fc4a69124b198c7953c54d1d686f23fd473720612a67a031c997fbc56f7a6a85`

기존 74편과 비교하면 이미지·timestamp·gap은 완전히 동일하다. 새 TCP frame으로
라벨을 재표현한 결과 37,712개 미래 target이 강체변환의 예상 conjugation과
위치 최대 `6.71e-8m`, 회전 최대 `1.71e-6°` 안에서 일치했다. 즉 임의로 궤적을
수정한 것이 아니라 동일 물리 궤적의 TCP frame 정의를 바로잡은 것이다.

새 설정 역시 physical deployment 승인값은 아니다. 실제 optical origin과 physical
pad centre를 독립적으로 확인하기 전에는 실물 모터 명령에 사용하지 않는다.
