# Ver1 파츠별 STL

## 출처와 좌표

- 원본: 로컬 handoff에 보관된 `Ver1.blend` (7,288,831 bytes).
- SHA-256: `8830dc217a5f54038fcfb097e1ea8716984b2cb700d976a99216add5863cf89d`.
- `dev/HW`에는 원본 blend가 없었으므로 로컬 원본을 사용했다. 원본은 수정하거나 저장하지 않았다.
- Blender 5.2.1 LTS에서 장면의 mesh 33개를 개별 binary STL로 내보냈다.
- **mm 기준**, 원본 world 좌표와 조립 자세 유지, Y forward / Z up.
  STL 자체에는 단위 정보가 없다. 부품별 원점 이동이나 출력용 바닥 정렬은 하지 않았다.
- 팔의 `source_commit`은 로컬 TheRobotStudio/SO-ARM100 체크아웃의
  `7629d2ad9853d10fb903093a33ef6114099d97e5`와 일치했다.
  원본 메시 경로는 `Simulation/SO101/assets/`이며 Apache-2.0
  [라이선스 사본](../so-arm101/LICENSE)을 포함한다.
- 팔 추출본은 원본 조립 변환이 적용되므로 upstream 파일을 그대로 복사한 것으로 표시하지 않는다.
  원본 STL 전용 `so-arm101/stl/`과 구분해 `so-arm101/ver1-stl/`에 둔다.
- `reference-stl/`의 모터 6개, 혼 1개, 나사 9개는 구매 부품의 참고 형상이다.
  특히 그리퍼 모터는 원본 blend에서 혼 표시 등이 수정된 모델이며 제조용 도면이 아니다.

## 검증과 제한

33개 모두 binary STL 길이, 유한 좌표, 원본 삼각형 수, bounding box 오차
0.001 mm 미만 및 Blender 재가져오기를 검사했다.
파일별 SHA-256, 원본 오브젝트명, 치수 경계와 메시 진단은
[manifest](ver1-stl-manifest.json)에 기록했다.

형상 보존을 위해 자동 메시 수리나 간소화는 적용하지 않았다. 다음 문제를 포함하므로
이 결과를 출력 적합성 또는 실물 조립 검증으로 해석하지 않는다.

- `motor_holder_so101_base_v1.stl`: 재가져온 메시에서 boundary edge 2개,
  non-manifold edge 3개가 검출됨.
- `08_wrist_I_adapter.stl`: STL의 6,410개 삼각형 중 252개는 꼭짓점이 겹친
  퇴화 삼각형이다. Blender 재가져오기에서 해당 252개를 제거하여 6,158개가 된다.
  저장된 STL은 원본 삼각형을 유지한다.
- 재가져온 메시에서 면적 1e-10 mm² 미만인 면은 오른쪽 홀더 1개,
  왼쪽 홀더 11개, 하부 커버 1개, 어댑터 3개다.
- 일관되지 않은 공유 모서리 winding은 33개 모두 0개다.
  자기 교차, 출력 공차, 재료·강도와 실물 적합성은 검사하지 않았다.

## 재생성

빈 출력 경로를 갖는 작업 사본에서 다음 명령을 실행한다. 기존 STL 또는 manifest가
있으면 덮어쓰지 않고 중단한다. 원본 blend는 별도로 제공해야 한다.

```powershell
& 'C:\Program Files\Blender Foundation\Blender 5.2\blender.exe' --background --factory-startup --disable-autoexec 'C:\path\Ver1.blend' --python-exit-code 1 --python 'HW\export_ver1_stl.py'
```

검증 assert가 변환 과정에 포함되어 있으며 실패 시 종료 코드 1을 반환한다.

## 파츠 목록

| STL | 원본 오브젝트 | 삼각형 수 |
|---|---|---:|
| [assembly/reference-stl/GEO-grip-motor-screw-PAN2x3-1.stl](../assembly/reference-stl/GEO-grip-motor-screw-PAN2x3-1.stl) | GEO-grip-motor-screw-PAN2x3-1 | 1660 |
| [assembly/reference-stl/GEO-grip-motor-screw-PAN2x3-2.stl](../assembly/reference-stl/GEO-grip-motor-screw-PAN2x3-2.stl) | GEO-grip-motor-screw-PAN2x3-2 | 1660 |
| [assembly/reference-stl/GEO-grip-motor-screw-PAN2x3-3.stl](../assembly/reference-stl/GEO-grip-motor-screw-PAN2x3-3.stl) | GEO-grip-motor-screw-PAN2x3-3 | 1660 |
| [assembly/reference-stl/GEO-grip-motor-screw-PAN2x3-4.stl](../assembly/reference-stl/GEO-grip-motor-screw-PAN2x3-4.stl) | GEO-grip-motor-screw-PAN2x3-4 | 1660 |
| [assembly/reference-stl/GEO-SER0070-gear-screw-M3x10-1.stl](../assembly/reference-stl/GEO-SER0070-gear-screw-M3x10-1.stl) | GEO-SER0070-gear-screw-M3x10-1 | 532 |
| [assembly/reference-stl/GEO-SER0070-gear-screw-M3x10-2.stl](../assembly/reference-stl/GEO-SER0070-gear-screw-M3x10-2.stl) | GEO-SER0070-gear-screw-M3x10-2 | 532 |
| [assembly/reference-stl/GEO-SER0070-gear-screw-M3x10-3.stl](../assembly/reference-stl/GEO-SER0070-gear-screw-M3x10-3.stl) | GEO-SER0070-gear-screw-M3x10-3 | 532 |
| [assembly/reference-stl/GEO-SER0070-gear-screw-M3x10-4.stl](../assembly/reference-stl/GEO-SER0070-gear-screw-M3x10-4.stl) | GEO-SER0070-gear-screw-M3x10-4 | 532 |
| [assembly/reference-stl/GEO-SER0070-horn-screw-M3x6.stl](../assembly/reference-stl/GEO-SER0070-horn-screw-M3x6.stl) | GEO-SER0070-horn-screw-M3x6 | 532 |
| [assembly/reference-stl/GEO-SER0070-stock-round-horn-reference.stl](../assembly/reference-stl/GEO-SER0070-stock-round-horn-reference.stl) | GEO-SER0070-stock-round-horn-reference | 3344 |
| [assembly/reference-stl/GEO-SO101-base-sts3215_03a_v1.stl](../assembly/reference-stl/GEO-SO101-base-sts3215_03a_v1.stl) | GEO-SO101-base-sts3215_03a_v1 | 19080 |
| [assembly/reference-stl/GEO-SO101-gripper-sts3215_03a_v1.stl](../assembly/reference-stl/GEO-SO101-gripper-sts3215_03a_v1.stl) | GEO-SO101-gripper-sts3215_03a_v1 | 14928 |
| [assembly/reference-stl/GEO-SO101-lower_arm-sts3215_03a_v1.stl](../assembly/reference-stl/GEO-SO101-lower_arm-sts3215_03a_v1.stl) | GEO-SO101-lower_arm-sts3215_03a_v1 | 19080 |
| [assembly/reference-stl/GEO-SO101-shoulder-sts3215_03a_v1.stl](../assembly/reference-stl/GEO-SO101-shoulder-sts3215_03a_v1.stl) | GEO-SO101-shoulder-sts3215_03a_v1 | 19080 |
| [assembly/reference-stl/GEO-SO101-upper_arm-sts3215_03a_v1.stl](../assembly/reference-stl/GEO-SO101-upper_arm-sts3215_03a_v1.stl) | GEO-SO101-upper_arm-sts3215_03a_v1 | 19080 |
| [assembly/reference-stl/GEO-SO101-wrist-sts3215_03a_no_horn_v1.stl](../assembly/reference-stl/GEO-SO101-wrist-sts3215_03a_no_horn_v1.stl) | GEO-SO101-wrist-sts3215_03a_no_horn_v1 | 17316 |
| [gripper/stl/01_finger_holder_right.stl](../gripper/stl/01_finger_holder_right.stl) | GEO-finger-holder-U | 3674 |
| [gripper/stl/02_finger_holder_left.stl](../gripper/stl/02_finger_holder_left.stl) | GEO-finger-holder-U-opposite | 4098 |
| [gripper/stl/03_soft_finger_right.stl](../gripper/stl/03_soft_finger_right.stl) | soft_gripper_finger_60pct | 2692 |
| [gripper/stl/04_soft_finger_left.stl](../gripper/stl/04_soft_finger_left.stl) | soft_gripper_finger_60pct_opposite | 2692 |
| [gripper/stl/05_center_pinion.stl](../gripper/stl/05_center_pinion.stl) | GEO-center-pinion | 5542 |
| [gripper/stl/06_guide_cover_top.stl](../gripper/stl/06_guide_cover_top.stl) | GEO-guide-cover-top | 4892 |
| [gripper/stl/07_guide_cover_bottom.stl](../gripper/stl/07_guide_cover_bottom.stl) | GEO-guide-cover-bottom | 10436 |
| [gripper/stl/08_wrist_I_adapter.stl](../gripper/stl/08_wrist_I_adapter.stl) | GEO-wrist-I-adapter | 6410 |
| [so-arm101/ver1-stl/base_motor_holder_so101_v1.stl](../so-arm101/ver1-stl/base_motor_holder_so101_v1.stl) | GEO-SO101-base-base_motor_holder_so101_v1 | 37540 |
| [so-arm101/ver1-stl/base_so101_v2.stl](../so-arm101/ver1-stl/base_so101_v2.stl) | GEO-SO101-base-base_so101_v2 | 9430 |
| [so-arm101/ver1-stl/motor_holder_so101_base_v1.stl](../so-arm101/ver1-stl/motor_holder_so101_base_v1.stl) | GEO-SO101-shoulder-motor_holder_so101_base_v1 | 22585 |
| [so-arm101/ver1-stl/motor_holder_so101_wrist_v1.stl](../so-arm101/ver1-stl/motor_holder_so101_wrist_v1.stl) | GEO-SO101-lower_arm-motor_holder_so101_wrist_v1 | 21042 |
| [so-arm101/ver1-stl/rotation_pitch_so101_v1.stl](../so-arm101/ver1-stl/rotation_pitch_so101_v1.stl) | GEO-SO101-shoulder-rotation_pitch_so101_v1 | 17672 |
| [so-arm101/ver1-stl/under_arm_so101_v1.stl](../so-arm101/ver1-stl/under_arm_so101_v1.stl) | GEO-SO101-lower_arm-under_arm_so101_v1 | 39516 |
| [so-arm101/ver1-stl/upper_arm_so101_v1.stl](../so-arm101/ver1-stl/upper_arm_so101_v1.stl) | GEO-SO101-upper_arm-upper_arm_so101_v1 | 26068 |
| [so-arm101/ver1-stl/waveshare_mounting_plate_so101_v2.stl](../so-arm101/ver1-stl/waveshare_mounting_plate_so101_v2.stl) | GEO-SO101-base-waveshare_mounting_plate_so101_v2 | 1254 |
| [so-arm101/ver1-stl/wrist_roll_pitch_so101_v2.stl](../so-arm101/ver1-stl/wrist_roll_pitch_so101_v2.stl) | GEO-SO101-wrist-wrist_roll_pitch_so101_v2 | 53994 |
