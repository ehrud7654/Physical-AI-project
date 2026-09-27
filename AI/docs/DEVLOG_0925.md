# DEVLOG 2026-09-25 (김준태 · 트랙 B) — d0924 실물 구동 (사무실, 젯슨)

전부 실물 · SO-101 · 젯슨 runtime_jt · 모델 d0924_90_b64_204402 (best ep119, sha a58e6a09) · 물체 구운감자(폭 48mm) · n=1 씩.

## 환경
- 젯슨 Wi-Fi 192.168.45.57 · 모터 UART /dev/ttyTHS1 1Mbaud (/dev/ttyACM0 은 S22 폰) · 카메라 adb forward tcp:8765
- 보조 도구: ~/safe_on.py (goal:=현재 후 토크 ON) · ~/jog.py (status/조그, 자체검사 3/3) · ~/look.py (런 요약)

## 런 기록
| 런 | 변경 | 결과 |
|---|---|---|
| r1·r2 | 기본 | 하강하며 접근, 감자 넘어뜨림. 시작 자세 손목이 아래로 기울어 있었음(사용자 확인) |
| r3 | 수평 시작 자세 grasp_start_level | c9–10 닫힘, 폭 47–49mm = 감자에 닿기만. 들지 않음 |
| **r4** | squeeze 145틱 | **파지 c5(38.5mm) → 리프트 c6–7 z 136→239 (+103mm) → 운반 c8–19. 20사이클 제한으로 종료, 정책 예측 폭 40.8mm(놓기 전)** 🟢 |
| r5 | 새 시작자세·cycles 40·release 250·floor 250 | 잡기 전에 상승 → 감자 위에서 닫음 (floor 게이트 오류, 아래) |
| r6 | floor 를 관측폭≤45mm 로 게이트 | c6 파지(37.3mm) · c7 servo 2 0x20. floor 가 c7 첫 waypoint +45mm |
| r7 | ramp 15mm | 감자 넘어뜨림 |
| r9 | grasp_start_level 복귀 | servo 2 0x20 (쥔 직후) |
| r10 | floor 끔(raised 0.0) | c7 servo 2 0x20 (쥔 직후). 시작 z 127 (r4 149) |
| r11 | 36°C 로 식힌 뒤 동일 | servo 2 0x20 (settle 중 read_diagnostics) |
| r12 | speed 60 acc 15 · 감자 3cm 당김 요청 | 쥐고 들다 종료(사용자 보고, 로그 미확인) |

온도: r10 직후 id2 45°C · id3 40 · id4 39 → 15분 후 36°C.

## 확인된 것
- 약한 파지가 리프트를 막았다 🟢(r3 vs r4, n=1). squeeze 145틱으로 리프트 성공
- 20사이클 제한이 운반을 끊었다 🟢
- 정책은 감자가 화면에 보여도(r8 입력 확인) 옆으로 치우치면 보정하지 않고 직진 🟡 — 시연 좌우 분포 미계측
- **쥔 직후 servo 2 과부하(0x20)가 4~5회 연속. floor 꺼도(r10), 식혀도(r11) 재현** 🟢
  - 같은 저장자세에서 c0 z 149(r4) → 127(r10): 같은 명령에 22mm 더 처짐
  - 쥐는 위치 r4 x≈365/z 136 vs r10 x 386/z 99 — 더 뻗고 낮게 쥠
  - 사용자: servo 2 는 이미 한 번 교체한 모터. → 단일 불량보다 어깨 부하 구조(손목 S22 ≈170g 🔵) 의심 🟡

## 추가한 패치 (runtime_jt/umi_checkpoint_executor, 전부 기본 off · 멱등 · 실제 소스 사본에 2회 적용 검증)
- patch_runtime_jt_release.py — squeeze 래치 후 정책이 수렴틱보다 250틱 더 열면 2청크 연속 시 해제, 이후 재조임 없음. 자체검사 16/16
- patch_runtime_jt_holdfloor.py — 파지 후 table z 바닥 · ramp. 자체검사 14/14
- patch_runtime_jt_holdfloor_grip.py — floor 를 '래치 AND 관측폭 ≤45mm' 로 게이트. 자체검사 9/9
- 백업: *.bak0925, *.bak_grip, *.bak_release

## 어시스턴트 오류 (정정)
1. squeeze 패치 자체검사에 "정책이 열어도 안 열림" 을 PASS 로 넣었다 — 놓기를 설계로 막음. r4 에서 드러남
2. holdfloor v1 을 squeeze 래치에 걸었다. 래치는 '정책 폭이 안정' 이면 열린 채로도 걸린다 → r5 잡기 전 상승
3. floor ramp 50mm 로 쥐자마자 +45mm 명령 → r6 과부하 기여
4. r9 후 "floor 가 원인, 확정" 이라 썼다 — r10(floor 0) 에서 같은 0x20. 틀림
5. 패치 경로를 runtime_jt/ 로 줌 (실제 runtime_jt/umi_checkpoint_executor/)
6. --help 확인 명령에 LD_LIBRARY_PATH 누락 → torch import 실패
7. --help grep 기대값 3 이라 씀 — usage 줄 포함 6 이 정상
8. r4 이후 서보 온도를 안 보고 연속 실행
9. safe_on 후 "팔은 안 움직이고 버틴다" — P제어 정상편차로 처진다. 틀린 설명
10. 로컬/젯슨 창 구분 없이 scp 를 줘서 젯슨에서 실행됨
(오전: ttyACM0 하드코딩 오진단 · `| tail` 로 MOVE 프롬프트 가림 · 젯슨 IP 분실)

## 거절한 것
- servo 2 0x20 을 무시하도록 servo_bus 수정 — 세션 권한 판정에서 안전장치 우회로 차단. 코드 제공도 하지 않음. 과부하 시 서보가 힘을 풀어 팔 낙하·서보 손상 위험

## 다음
- 2·3번 전압(reg62)·토크리밋(reg48)·정지 부하(reg60) 읽기 — 미실행
- HW(현석): servo 2 반복 과부하 근거 전달, 무게보상(스프링)·전원·모델(12V 1/345) 확인
- 감자는 r4 위치(쥐는 x≈365)에 · 시연 좌우 분포 계측(서버)
