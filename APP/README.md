# SSAFY Collect

폰을 UMI 그리퍼에 달아서 사람 시연을 찍고 서버로 올리는 앱이다. 초광각 카메라 영상과 IMU 를 같이 기록한다.
로봇이 학습된 정책을 돌릴 때는 같은 폰을 로봇(젯슨)에 USB 로 물려서 카메라 영상을 실시간으로 넘겨준다.
수집할 때와 로봇을 돌릴 때 렌즈와 카메라 설정이 똑같아야 해서, 두 기능을 한 앱에 넣었다.

패키지는 `com.a103.collect`, 현재 버전은 1.0 이다. Android 13 이상에서만 돈다.
서버는 `https://j15a103.p.ssafy.io` 이고 HTTPS 만 받는다.

## 폴더

```
APP/
├─ collect/                 앱 소스
├─ ssafy-collect-1.0.apk    release 빌드 (서명됨)
├─ arpose/                  이전 버전 (ARCore). 참고용으로만 남겨둠
└─ _ref_umi/                원본 UMI 앱(org.umi.collect, org.umi.stream) APK 와 디컴파일 자료
```

## 쓰는 순서

처음 켜면 QR 등록 화면이 나온다. 관리자 웹에서 장치 등록 QR 을 띄워서 찍으면 된다. 등록 없이는 못 들어간다.
카메라 권한을 거부했으면 아래에 "카메라 권한 허용" 버튼이 뜨는데, 누르면 앱 설정으로 간다. 허용하고 돌아오면 카메라가 알아서 켜진다.

등록하고 나면 튜토리얼이 한 번 나온다. 나중에 다시 보고 싶으면 메뉴의 "도움말"로 보면 된다.

촬영 화면에서는 먼저 위쪽 "학습"을 눌러 무슨 동작을 찍을지 고른다. 옆에 `4/50편` 처럼 지금까지 찍은 개수가 나온다.
왼쪽 위 신호가 초록("촬영 가능")일 때 셔터를 누르면 시작, 한 번 더 누르면 끝이다. 끝나면 성공으로 자동 저장된다.
찍다가 망쳤으면 셔터 밑에 뜨는 "↻ 재촬영"으로 방금 것을 지우고 다시 찍으면 된다.

찍은 건 메뉴 → "기록"에서 보인다. 올릴 것들을 고르고 학습을 지정해서 전송한다. 위쪽 칩으로 학습별로 걸러 볼 수 있다.
설정에는 학습별로 몇 편 찍었는지, 남은 저장 공간이 얼마인지가 나온다.

## 촬영 조건

학습 데이터가 매번 같은 조건에서 나와야 해서 카메라 설정은 앱이 고정한다.

- 후면 렌즈 중 화각이 제일 넓은 초광각(90° 이상, 고정초점)만 쓴다. 그런 렌즈가 없으면 메인 렌즈로 대신하지 않고 그냥 오류를 낸다.
- AF, OIS, EIS, 왜곡 보정은 전부 끈다. 밝기(AE)랑 화이트밸런스(AWB)는 자동이고, 대신 프레임마다 노출값을 기록한다.
- 1920×1080, 30fps 고정, H.264 16Mbps, 키프레임 1초마다.
- 영상은 180° 돌려서 저장한다. 회전 정보를 메타에 넣는 게 아니라 픽셀 자체를 돌려서 굽는다(`upright_v1`).
- 영상 PTS 는 카메라 센서 시각을 그대로 쓴다. 그래서 `pts_us == sensor_timestamp_ns / 1000` 이 항상 맞고, IMU 랑도 같은 시계라 따로 맞출 필요가 없다.
- IMU 는 가속도, 자이로 둘 다 200Hz 로 요청한다.

PTS 가 센서 시각이라 `video.mp4` 는 PC 동영상 플레이어에서 안 열릴 수 있다. 파이프라인은 문제없이 읽는다.

## 저장되는 파일

```
/sdcard/Android/data/com.a103.collect/files/sessions/rec_<시작 epoch ms>_<uuid 8자리>/
├─ manifest.json
├─ video.mp4
├─ frames.csv
├─ encoded.csv
├─ accelerometer.csv
└─ gyroscope.csv
```

CSV 헤더는 서버 계약이랑 글자 하나까지 같아야 한다.

```
frames.csv         frame_number,sensor_timestamp_ns,exposure_ns,frame_duration_ns,iso,rolling_shutter_skew_ns,focus_diopters,af_mode,eis_mode,ois_mode,crop_left,crop_top,crop_right,crop_bottom
encoded.csv        sample_index,pts_us,size_bytes,flags
accelerometer.csv  timestamp_ns,x_m_s2,y_m_s2,z_m_s2,accuracy
gyroscope.csv      timestamp_ns,x_rad_s,y_rad_s,z_rad_s,accuracy
```

`frames.csv` 와 `encoded.csv` 는 한 줄씩 짝이 맞는다. 카메라 프레임 정보와 인코더 출력이 따로 들어와서 원래는 개수가 안 맞는데,
인코딩된 프레임이 나올 때 센서 시각으로 카메라 프레임을 찾아서 두 파일에 같은 번호(0부터)로 같이 쓴다.

`manifest.json` 의 `start_elapsed_ns`, `stop_elapsed_ns` 는 녹화 버튼 누른 시각이 아니라 첫 프레임, 마지막 프레임 시각이다.
IMU 는 녹화 내내 들어오니까 이 범위 밖에 찍힌 샘플은 저장할 때 잘라낸다. 서버가 범위 밖 샘플을 거부하기 때문이다.

## 서버 전송

기록 화면에서 학습을 지정해서 전송한다. 학습이 미분류인 촬영은 안 올라간다. 전송은 WorkManager 로 돌아서 앱을 꺼도 계속되고,
실패하면 30초부터 간격을 늘려가며 다시 시도한다.

1. `POST /api/v1/episode/data-upload-request` 로 업로드 세션을 받는다 (metadata, summary, ZIP 크기와 sha256 포함)
2. 받은 presigned URL 로 S3 에 PUT 한다. 응답에 온 `requiredHeaders` 는 이름, 값 그대로 실어야 한다
3. `POST /api/v1/episode/data-upload-request/{uploadSessionId}/complete` 로 끝났다고 알린다

인증은 QR 등록 때 받은 장치 ID/secret(Keystore 에 암호화해서 저장)으로 토큰을 받아 Bearer 로 붙인다.
재시도해도 중복으로 안 올라가게 `Idempotency-Key` 는 세션 폴더에 저장해 두고 계속 같은 걸 쓴다.
ZIP 은 파일 6개를 폴더 없이 루트에 무압축(STORED)으로 넣는다.

`complete` 가 202 를 주면 서버가 "받았고 이제 검사할게" 라는 뜻이다. 검사까지 통과해야 웹에 등록된다.
앱의 "전송됨" 은 이 202 기준이라서, 웹에 실제로 올라갔는지는 웹에서 확인해야 한다.

## 로봇 연결 (스트리밍)

메뉴 → "로봇 연결"로 들어가면 카메라가 켜지고 로봇 접속을 기다린다. 셔터로 영상 송출을 켜고 끈다.
받는 쪽은 ROS 런타임의 `camera_stream.py` 다. 원래 쓰던 UMI USB Camera2(`org.umi.stream`)와 같은 방식이라 받는 쪽 코드는 안 바꿔도 된다.

로봇(또는 PC)에서 먼저 포트를 연결한다.

```bash
adb forward tcp:8765 tcp:8765
```

받는 쪽이 프레임마다 `0x01` 한 바이트를 보내면 폰이 최신 프레임 한 장을 돌려준다. 응답은 빅엔디안으로 이렇게 생겼다.

```
"UMI1" | sequence(u64) | sensor_timestamp_ns(u64) | width(u32) | height(u32) | jpeg_size(u32) | JPEG
```

영상은 1920×1080 JPEG(품질 90), 최대 30fps 다. 촬영 때와 달리 회전하지 않고 센서 방향 그대로 보낸다.
받는 쪽이 180° 돌려서 쓰기 때문에, 여기서 돌려 보내면 두 번 돌아가서 뒤집힌다.

화면 위쪽에 상태가 나온다.

- 로봇 연결 대기: 폰은 준비됐는데 로봇이 아직 안 붙음
- 로봇 연결됨 — 셔터를 눌러 시작: 로봇이 붙었고 아직 안 보내는 중
- 스트리밍 중 · 로봇 연결됨 · N fps: 보내는 중
- 스트리밍 중 · 로봇 연결 대기: 송출은 켰는데 로봇이 아직 안 붙음

주의할 게 하나 있다. `camera_stream.py` 는 접속하고 5초 안에 프레임이 안 오면 에러로 끊는다.
그래서 셔터를 먼저 눌러 두고 로봇을 실행하는 게 제일 편하다. 연결을 먼저 했다면 "로봇 연결됨"이 뜨자마자 바로 누를 것.

로봇이 끊기면 폰은 0.5초 안에 "로봇 연결 대기"로 돌아간다.
UMI USB Camera2 가 켜져 있어서 8765 포트를 잡고 있으면 "포트 8765 사용 중"이라고 뜬다. 그 앱을 끄면 된다.
로봇이 안 붙어 있을 때는 JPEG 변환을 쉬어서 폰이 덜 뜨거워진다. 로봇에 달아두는 용도라 이 화면에서는 화면이 꺼지지 않는다.

## 빌드, 설치

PowerShell 기준.

```powershell
cd <저장소>\APP\collect
$env:Path = "$env:LOCALAPPDATA\Android\Sdk\platform-tools;$env:Path"
.\gradlew.bat installDebug
```

> JDK 는 `gradle.properties` 의 `org.gradle.java.home` 에 JDK 21 경로로 박혀 있다. 경로가 다른 PC 면 이 줄을 지우고 `~/.gradle/gradle.properties` 로 옮길 것.
>
> 저장소 경로에 한글(특화)이 있으면 AGP 가 빌드를 막아서 `android.overridePathCheck=true` 를 켜 두었다. 빌드랑 설치는 잘 된다.

### release 빌드

서명 키는 저장소 밖에 따로 둔다. `APP/collect/keystore.properties` 를 만들어서 아래처럼 적으면 release 가 서명된다. 이 파일은 git 에 안 올라간다.

```properties
storeFile=<keystore 절대 경로>
storePassword=...
keyAlias=ssafy-collect
keyPassword=...
```

```powershell
.\gradlew.bat assembleRelease
# app\build\outputs\apk\release\app-release.apk
```

debug 랑 release 는 서명이 달라서 위에 덮어 깔 수가 없다. 기존 앱을 지워야 하는데, 지우면 폰에 있던 촬영이랑 장치 등록도 같이 날아간다.
안 올린 촬영이 있으면 먼저 빼 둘 것.

```powershell
adb pull /sdcard/Android/data/com.a103.collect/files/sessions C:\tmp\collect_backup
```

> 받는 경로에 한글이 있으면 폴더 pull 이 에러도 없이 0개 받고 끝난다. 영어 경로로 먼저 받은 다음 옮기면 된다.

## 로그 보기

```powershell
adb logcat -s UPLOAD:* AUTH:* PACKAGE:*
```

`UPLOAD` 에는 전송 단계별 HTTP 코드랑 `complete` 응답 본문이, `AUTH` 에는 장치 인증과 학습 목록 동기화가,
`PACKAGE` 에는 전송 전에 걸러진 이유(학습 미지정 같은 것)가 찍힌다.

## 자주 나는 문제

| 증상 | 해결 |
|---|---|
| 전송이 다 실패하고 로그에 `토큰 발급 HTTP 401` | 서버에서 장치가 지워진 경우다. 설정에서 앱 버전을 1초 안에 7번 연달아 누르면 장치 초기화가 뜬다. 초기화하고 QR 로 다시 등록 |
| 기록이 계속 "전송 대기" | `adb logcat -s UPLOAD:*` 로 어느 단계에서 막혔는지 본다. 미분류면 학습 지정 후 다시 전송 |
| "카메라 준비 중…"이 잠깐 걸림 | 이전 화면이 카메라를 놓는 중이다. 0.7초 간격으로 5번까지 알아서 다시 시도한다 |
| 로봇 연결에서 "포트 8765 사용 중" | UMI USB Camera2 처럼 같은 포트를 쓰는 앱을 끈다 |
| 로봇이 붙자마자 끊김 | 받는 쪽 5초 제한 때문. 셔터를 먼저 눌러 둔다 |
| `adb pull` 이 0개 받고 끝남 | 받는 경로에 한글. 영어 경로로 받기 |
| release 설치 때 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 서명이 다른 기존 앱이 깔려 있다. 백업하고 지운 뒤 설치 |
| 빌드 때 `non-ASCII characters` | `gradle.properties` 에 `android.overridePathCheck=true` 가 있는지 확인 |
