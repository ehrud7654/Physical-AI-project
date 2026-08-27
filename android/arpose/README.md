# ARPose — ARCore 카메라 프리뷰 + 6DOF pose + 녹화

폰 하나로 **손목 카메라 + 6DOF 추적**을 동시에. 이미지와 pose가 **같은 ARCore 프레임**에서 나오므로
시간 동기화 오차가 구조적으로 0이다. (UMI 방식 데이터 수집의 폰 버전)

---

## 화면

- 전체화면 **카메라 프리뷰**
- 상단 오버레이: tracking 상태 / fps / x,y,z(m) / roll,pitch,yaw(°) / 녹화 중이면 프레임·드롭 수
- 하단 **● REC** 버튼 → 누르면 녹화 시작, 다시 누르면 정지

## 저장되는 것

```
/sdcard/Android/data/com.ssafy.arpose/files/rec_YYYYMMDD_HHmmss/
├─ frames/000000.jpg, 000001.jpg, ...    640x480 JPEG (q90)
├─ poses.csv
└─ meta.txt
```

**poses.csv**

| 컬럼 | 뜻 |
|---|---|
| `index` | 프레임 번호 |
| `timestamp_ns` | ARCore frame.timestamp (나노초, `elapsedRealtimeNanos` 기준) |
| `tracking` | TRACKING / PAUSED |
| `x,y,z` | 카메라 위치 (미터) |
| `qx,qy,qz,qw` | 카메라 회전 (쿼터니언) |
| `image` | 대응하는 JPEG 파일명. **비어 있으면 그 프레임은 이미지가 드롭됨** (pose는 유효) |

**meta.txt** — 기기명, 카메라 설정, **카메라 내부 파라미터(fx, fy, cx, cy)**, 시작 시각(wall clock).
내부 파라미터는 나중에 좌표 변환/캘리브레이션에 필요하니 버리지 말 것.

---

## 빌드 · 설치 · 실행

PowerShell (매번 새 창이면 이 줄들 먼저):

```powershell
cd C:\dev\arpose
$env:Path = "$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:Path"
```

```powershell
.\gradlew.bat installDebug
adb shell am start -n com.ssafy.arpose/.MainActivity
```

> JDK는 `gradle.properties` 의 `org.gradle.java.home` 으로 고정돼 있음 (JDK 21).

## 녹화 → PC로 가져오기

1. 앱에서 `TRACKING` 뜬 거 확인
2. **● REC** 탭 → 시연 → **■ STOP** 탭
3. 폴더 확인 후 pull:

```powershell
# 녹화 폴더 목록
adb shell ls /sdcard/Android/data/com.ssafy.arpose/files/

# 통째로 가져오기 (폴더명은 위에서 확인한 것으로)
adb pull /sdcard/Android/data/com.ssafy.arpose/files/rec_20260825_143000 .\data\
```

4. 검사:

```powershell
python tools\check_recording.py .\data\rec_20260825_143000
```

프레임 수, 실제 Hz, TRACKING 비율, 드롭률, 이동 거리, JPEG 총 용량을 뽑아준다.

## 저장소 정리

```powershell
# 특정 녹화 삭제
adb shell rm -rf /sdcard/Android/data/com.ssafy.arpose/files/rec_20260825_143000

# 전부 삭제
adb shell rm -rf /sdcard/Android/data/com.ssafy.arpose/files/rec_*
```

용량 감: 640x480 q90 JPEG ≈ 40~70KB/장 → **30fps 기준 분당 약 70~120MB**.

---

## 알아둘 것

- **프레임 드롭**: JPEG 인코딩이 30fps를 못 따라가면 이미지가 드롭된다. pose는 항상 전부 기록되고,
  `image` 컬럼이 빈 행이 드롭된 프레임이다. 드롭률이 높으면 `Recorder.JPEG_QUALITY` 를 낮추거나
  해상도를 줄일 것.
- **세션 초기 3~5초는 버려라.** ARCore 좌표가 안정화되는 데 그 정도 걸린다 (실측 10mm 드리프트).
- **카메라가 텍스처 있는 씬을 봐야 한다.** 흰 벽/근접 표면이면 `PAUSED` 로 떨어진다.
  그리퍼 거치 각도를 잡을 때 이걸 먼저 확인할 것.
- **좌표계**: 세션 시작 시점 카메라 위치가 원점. Y가 위(중력 반대), -Z가 카메라가 보던 방향.
  녹화마다 원점이 다르므로, 학습 시엔 t₀ 기준 상대 궤적으로 변환해서 쓴다.
- **앱을 백그라운드로 보내면 녹화가 자동 정지**된다 (파일은 정상적으로 닫힘).

## 자주 나는 에러

| 증상 | 해결 |
|---|---|
| `SDK location not found` | `local.properties` 에 `sdk.dir=...` |
| `adb: no devices` | USB 디버깅 ON, USB 모드를 "파일 전송"으로 |
| 앱 즉시 종료 | `adb logcat -b crash` |
| tracking 계속 PAUSED | 조명/특징점. 화면의 실패 이유 확인 |
| `adb pull` 권한 거부 | 경로 오타 확인. `adb shell ls` 로 먼저 존재 확인 |
| 드롭 프레임 많음 | JPEG 품질↓ 또는 해상도↓ |

## 다음 단계

- 그리퍼 개폐 폭 신호 추가 (손가락 마커 or 포텐셔미터)
- hand-eye 캘리브레이션 (카메라 pose → 그리퍼 TCP pose)
- 학습 포맷으로 변환 (t₀ 기준 상대 SE(3) 궤적)
- 필요해지면 실시간 스트리밍 (`adb forward tcp:9000 tcp:9000` 로 USB부터)
