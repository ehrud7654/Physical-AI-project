# Hardware

This project uses the open-source SO-ARM101 design as its base robot arm.
The original SO-ARM101 frame and arm geometry are used without modification.
Only the gripper/end-effector is custom-designed for this project.

Keeping the upstream arm and the custom end-effector in separate directories
makes their origin and ownership explicit.

## Directory structure

- `so-arm101/` — attribution and unmodified STL files from the upstream
  SO-ARM101 project.
- `gripper/` — custom gripper/end-effector CAD files and project images.
- `assembly/` — documentation and images of the complete SO-ARM101 and custom
  gripper assembly.

Do not place custom gripper files in `so-arm101/`, and do not present upstream
SO-ARM101 geometry as original work from this project.

## Ver1 part exports

`Ver1.blend`에서 추출한 STL 33개: [커스텀 그리퍼 8개](gripper/stl/),
[로봇팔 프레임 9개](so-arm101/ver1-stl/),
[모터·혼·나사 참고 모델 16개](assembly/reference-stl/).
단위는 mm이며 원본 조립 위치와 방향을 유지합니다.
출처, 재생성 방법, 부품 목록 및 메시 검사 결과는
[Ver1 내보내기 기록](assembly/VER1_STL.md)을 확인하세요.

## URDF

[SO-101 + Ver1 URDF](urdf/so101_ver1.urdf)와 필요한 링크 좌표 메시를
`urdf/`에 포함했습니다. 단위, 그리퍼 연동, 검증 범위는
[URDF 사용 안내](urdf/README.md)를 확인하세요.
