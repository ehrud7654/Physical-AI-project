# ARPose — ARCore 카메라 프리뷰 + 6DOF pose + 녹화

폰 하나로 **손목 카메라 + 6DOF 추적**을 동시에. 이미지와 pose가 **같은 ARCore 프레임**에서 나오므로
시간 동기화 오차가 구조적으로 0이다. (UMI 방식 데이터 수집의 폰 버전)

---

## 화면

- 전체화면 **카메라 프리뷰**
- 상단 오버레이: tracking 상태 / fps / x,y,z(m) / roll,pitch,yaw(°) / 녹화 중이면 프레임·드롭 수
- 하단 **● REC** 버튼 → 누르면 녹화 시작, 다시 누르면 정지
- 좌하단 **AF** 버튼 → 포커스 모드 전환. **기본값 FIXED 를 그대로 쓸 것** (아래 "알아둘 것" 참고).
  녹화 중에는 잠긴다.

## 저장되는 것

```
/sdcard/Android/data/com.ssafy.arpose/files/rec_YYYYMMDD_HHmmss/
├─ frames/000000.jpg, 000001.jpg, ...    640x480 JPEG (q90)
├─ poses.csv
├─ imu.csv
├─ meta.txt
└─ summary.txt
```

**poses.csv**

| 컬럼 | 뜻 |
|---|---|
| `index` | 프레임 번호 |
| `timestamp_ns` | ARCore frame.timestamp (나노초, `elapsedRealtimeNanos` 기준) |
| `tracking` | TRACKING / PAUSED |
| `x,y,z` | 카메라 위치 (미터) |
| `qx,qy,qz,qw` | 카메라 회전 (쿼터니언) |
| `jump_mm` | 이 프레임에서 추적 점프가 감지됐으면 그 이동량(mm). 아니면 0 |
| `jump_deg` | 같은 프레임의 회전량(°). 아니면 0 |
| `image` | 대응하는 JPEG 파일명. **비어 있으면 그 프레임은 이미지가 드롭됨** (pose는 유효) |

**imu.csv** — 자이로·가속도 200Hz. `timestamp_ns,sensor,x,y,z`

영상과 pose 는 ARCore 카메라 프레임에 묶여 30Hz 가 상한이라, 고주파 신호는 Android 센서
API 로 따로 받는다. 두 스트림은 서로 독립이지만 **타임스탬프 기준이 같아서**(둘 다
`elapsedRealtimeNanos`) 그대로 정렬된다.

`sensor` 는 `accel` 또는 `gyro`. 두 센서는 샘플이 도착하는 시점이 달라서 한 행에 묶지
않는다. 단위는 가속도 m/s², 자이로 rad/s (Android 표준).

S23 실측 206.3Hz. 200 을 요청해도 정확히 200 이 나오지는 않는다 — 센서 칩의 출력 단계가
정해져 있어 가장 가까운 단계로 올라간다. **매 녹화마다 `summary.txt` 의 실측치를 확인할 것.**

**meta.txt** — 기기명, 카메라 설정, **카메라 내부 파라미터(fx, fy, cx, cy)**, `focus_mode`, 시작 시각(wall clock).
내부 파라미터는 나중에 좌표 변환/캘리브레이션에 필요하니 버리지 말 것.

**summary.txt** — 프레임 수, 실제 Hz, 드롭 수, **점프 목록과 사용 가능 구간**. 녹화 끝나고 이것부터 본다.

```
frames_write_failed=0
free_mb_start=122941
free_mb_end=122925
tracking_jumps=0
max_jump_mm=0.0
paused_frames=132
verdict=추적상실 1구간 - 아래 구간으로 분할해서 사용
paused_spans(from_sec,to_sec,frames):
  8.43,12.80,132
usable_segments:
  0.00~8.33
  13.00~16.84
imu_accel_samples=2513
imu_accel_hz=206.3
imu_gyro_samples=2513
imu_gyro_hz=206.3
imu_clock_skew_ms=9.57
```

`imu_clock_skew_ms` 는 센서 샘플이 앱에 도달하기까지의 지연이다. 10ms 안쪽이면 정상이고,
초 단위로 벌어지면 센서 타임스탬프가 ARCore 와 다른 시계를 쓴다는 뜻이니 그때는 데이터를
쓰면 안 된다.

점프가 나도 녹화 전체를 버리지 않는다. `usable_segments` 의 구간을 각각 잘라서 쓰면 된다.

구간을 깎아먹는 원인은 두 가지다. **추적 점프**(순간)와 **추적 상실**(`tracking=PAUSED` 구간).
둘 다 앞뒤로 여유를 두고 잘라낸다 — 경계 프레임의 pose 는 믿을 수 없기 때문이다.
남은 조각이 2초 미만이면 버린다.

`paused_frames` 가 0 이 아니면 그 녹화는 중간에 추적을 놓쳤다는 뜻이다. 녹화 시작 직후에
몰려 있다면 REC 를 너무 빨리 누른 것이니, 다음부터는 `TRACKING` 이 뜬 뒤에 시작할 것.

---

## 빌드 · 설치 · 실행

PowerShell (매번 새 창이면 이 줄들 먼저):

```powershell
cd <저장소>\APP\arpose
$env:Path = "$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:Path"
```

```powershell
.\gradlew.bat installDebug
adb shell am start -n com.ssafy.arpose/.MainActivity
```

> JDK는 `gradle.properties` 의 `org.gradle.java.home` 으로 고정돼 있음 (JDK 21).
> 그 경로에 JDK 21이 없는 PC에서는 빌드가 깨진다. 경로가 다르면 이 줄을 지우고
> 개인 설정(`~/.gradle/gradle.properties`)으로 옮길 것.
>
> 저장소 경로에 한글이 섞여 있으면 AGP가 `non-ASCII characters` 로 빌드를 막는다.
> `gradle.properties` 의 `android.overridePathCheck=true` 로 풀어놨고, 빌드·설치는 정상 동작한다.

## 녹화 → PC로 가져오기

1. 앱에서 `TRACKING` 뜬 거 확인
2. **● REC** 탭 → 시연 → **■ STOP** 탭
3. 폴더 확인 후 pull:

```powershell
# 녹화 폴더 목록
adb shell ls /sdcard/Android/data/com.ssafy.arpose/files/

# 통째로 가져오기 (폴더명은 위에서 확인한 것으로)
adb pull /sdcard/Android/data/com.ssafy.arpose/files/rec_20260825_143000 .\data\rec_20260825_143000
```

> **목적지 경로에 한글이 있으면 폴더 pull 이 조용히 실패한다.** 에러 없이 `building file list...`
> 만 찍고 0장이 받아진다. 파일 하나씩은 되는데 폴더째는 안 된다. 이 저장소는 경로에 한글이
> 들어갈 수 있으니, 안 받아지면 ASCII 경로(예: `C:\tmp\rec_xxx`)로 먼저 받아서 옮길 것.
>
> 목적지 폴더명을 끝까지 적을 것. 생략하면 폴더가 아니라 내용물만 풀린다.

4. 검사:

```powershell
python tools\check_recording.py .\data\rec_20260825_143000
```

프레임 수, 실제 Hz, TRACKING 비율, 드롭률, 이동 거리, JPEG 총 용량을 뽑아준다.

5. 궤적 그림으로 보기:

```powershell
# 한 회차: top-down 궤적 + 위치 그래프 + 프레임간 이동량 (점프는 X 로 표시)
python tools\plot_poses.py .\data\rec_20260825_143000

# 여러 회차를 focus_mode 별로 비교
python tools\plot_poses.py --compare .\data
```

matplotlib 이 필요하다 (`pip install matplotlib`).

## 저장소 정리

```powershell
# 특정 녹화 삭제
adb shell rm -rf /sdcard/Android/data/com.ssafy.arpose/files/rec_20260825_143000

# 전부 삭제
adb shell rm -rf /sdcard/Android/data/com.ssafy.arpose/files/rec_*
```

용량 감: 640x480 q90 JPEG ≈ 39~47KB/장 → **분당 약 85MB** (IMU 포함, 실측).

앱이 저장 공간을 지킨다. 여유가 **1GB 미만이면 녹화가 시작되지 않고**, 녹화 중 **300MB 아래로
떨어지면 자동 정지**한다. 정지 시점까지의 데이터는 정상이며 `summary.txt` 에 경고가 남는다.

쓰기가 실패한 프레임은 `frames_write_failed` 로 집계된다. 이 값이 0 이 아니거나
`WARNING=` 줄이 있으면 그 녹화는 구멍이 있다는 뜻이다.

---

## 추적 점프

ARCore 가 `tracking=TRACKING` 을 유지한 채로 좌표계를 재보정(재로컬라이제이션)하는 일이 있다.
겉보기엔 정상인데 궤적만 조용히 끊기므로, 그대로 학습에 넣으면 데이터가 오염된다.
앱이 이걸 자동 검출해서 `poses.csv` 와 `summary.txt` 에 남긴다.

판정 기준 — 아래 둘 중 **한 줄이라도** 그 줄의 조건을 **전부** 만족하면 점프로 본다:

| | 기준 |
|---|---|
| 위치 | 한 프레임 이동 40mm 초과 **&&** 2m/s 초과 **&&** 직전 3프레임 최대값의 5배 초과 |
| 회전 | 한 프레임 회전 12° 초과 **&&** 직전 3프레임 최대값의 5배 초과 |

마지막 "직전 대비 5배"가 핵심이다. 크기만 보면 **빠른 손동작이 그대로 오탐된다** — 실측으로
164mm / 64.5° / 4.93m/s 로 휘두른 녹화에서 14회 오탐이 났다. 진짜 재로컬라이제이션은 앞뒤
프레임과 단절되지만(직전 대비 68~204배) 빠른 손동작은 연속적이라(0.5~2.6배) 이 조건으로 갈린다.

녹화 직후·추적 복귀 직후·AF 토글 직후처럼 직전 3프레임이 아직 없으면 연속성 조건은 건너뛴다.

기준값은 `MainActivity.kt` 의 `JUMP_*` 상수에 모여 있다.

## 알아둘 것

- **프레임 드롭**: JPEG 인코딩이 30fps를 못 따라가면 이미지가 드롭된다. pose는 항상 전부 기록되고,
  `image` 컬럼이 빈 행이 드롭된 프레임이다. 드롭률이 높으면 `Recorder.JPEG_QUALITY` 를 낮추거나
  해상도를 줄일 것.
- **포커스는 FIXED 로 둔다.** AUTO 와 각 5~7회씩 비교한 결과, FIXED 는 15mm 초과 프레임이
  2721프레임 중 0회(최대 12.9mm)인 반면 AUTO 는 2417프레임 중 14회(최대 36.6mm)였다.
  배경 선명도도 FIXED 가 4.9배 높다(ARCore 가 특징점을 뽑는 영역이다). 대신 근거리 물체는
  AUTO 가 22% 선명하니, 그 정도는 감수한다.
- **세션 초기 3~5초는 버려라.** ARCore 좌표가 안정화되는 데 그 정도 걸린다 (실측 10mm 드리프트).
- **카메라가 텍스처 있는 씬을 봐야 한다.** 흰 벽/근접 표면이면 `PAUSED` 로 떨어진다.
  그리퍼 거치 각도를 잡을 때 이걸 먼저 확인할 것.
- **좌표계**: 세션 시작 시점 카메라 위치가 원점. Y가 위(중력 반대), -Z가 카메라가 보던 방향.
  녹화마다 원점이 다르므로, 학습 시엔 t₀ 기준 상대 궤적으로 변환해서 쓴다.
- **앱을 백그라운드로 보내면 녹화가 자동 정지**된다 (파일은 정상적으로 닫힘).
- **IMU 는 녹화 중에만 파일에 쓰지만, 센서는 앱이 떠 있는 동안 계속 듣는다.** REC 를 누른
  순간부터 샘플 스트림이 이미 안정돼 있어야 하기 때문이다.

## 자주 나는 에러

| 증상 | 해결 |
|---|---|
| `SDK location not found` | `local.properties` 에 `sdk.dir=...` |
| `adb: no devices` | USB 디버깅 ON, USB 모드를 "파일 전송"으로 |
| 앱 즉시 종료 | `adb logcat -b crash` |
| tracking 계속 PAUSED | 조명/특징점. 화면의 실패 이유 확인 |
| `adb pull` 권한 거부 | 경로 오타 확인. `adb shell ls` 로 먼저 존재 확인 |
| 드롭 프레임 많음 | JPEG 품질↓ 또는 해상도↓ |
| `adb pull` 이 0장 받고 끝남 | 목적지 경로에 한글. ASCII 경로로 받을 것 |
| 빌드 시 `non-ASCII characters` | `gradle.properties` 에 `android.overridePathCheck=true` |
| 빌드 시 JDK 관련 실패 | `gradle.properties` 의 `org.gradle.java.home` 경로 확인 |

## 다음 단계

- 그리퍼 개폐 폭 신호 추가 (손가락 마커 or 포텐셔미터)
- hand-eye 캘리브레이션 (카메라 pose → 그리퍼 TCP pose)
- 학습 포맷으로 변환 (t₀ 기준 상대 SE(3) 궤적)
- 필요해지면 실시간 스트리밍 (`adb forward tcp:9000 tcp:9000` 로 USB부터)
