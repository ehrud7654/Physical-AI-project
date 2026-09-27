# RUNBOOK — 로봇 확보 시 즉시 실행 (2026-09-22 확정 결함 2건 교정)

작성자: 김준태(트랙 B) · 근거 `MEASURE_root_cause_settle_0922.md` · `DECISIONS_AI_0922_branch_and_failclosed.md`

> **모든 판정 기준은 결과 보기 전에 확정했다.** 빗나가면 방향까지 기록한다.
> 참조 집단: 오늘 실물 7개 런 (z 처짐 −2.80 ~ −10.29mm · 닫기비 0.140 ~ 0.465)

---

## 0. 준비 (매 세션 1회)

```bash
# [젯슨] 새 창이면 반드시 먼저 — 없으면 torch import 가 죽는다
cd ~/Workspace/stanford_umi_demo && export LD_LIBRARY_PATH="$PWD/.venv/lib/python3.10/site-packages/cusparselt/lib:/usr/local/cuda/lib64:${LD_LIBRARY_PATH:-}"
```

```bash
# [젯슨] runtime_jt 존재 확인 (없으면 재빌드)
ls -d ~/Workspace/stanford_umi_demo/runtime_jt/umi_checkpoint_executor || (cd ~ && python3 patch_runtime_jt.py)
```

```bash
# [젯슨] 토크 + 자세 + 상태. 팔 받치고 실행
cd ~/Shanks && python3 torque.py on --all && python3 pose.py go grasp_start_0922 --arm; echo "rc=$?"
```

**물체 배치**: 오늘 실패 조건과 같게 — **중앙 10cm 이격**. 런 1~4 내내 **바꾸지 않는다.**
(5cm 는 런 5에서 별도. 배치를 바꾸면 변인이 둘이 된다.)

## 1. 안전 중단 조건 (전 런 공통)

```
목표 z 가 20mm 아래   → 즉시 Ctrl+C
servo 2 체크섬/0x20   → 재시도 금지. 케이블·버스 경합부터 확인
servo 2 온도 45°C 초과 → 중단, 식힌다
로봇을 다른 사람이 쓰는 중 → 시작하지 않는다 (시리얼 버스 경합)
```

## 2. 런 1 — settle 단독

**변인**: `--settle-timeout 0.5 --settle-tolerance 15` 만 추가. 나머지 오늘과 동일.

```bash
# [젯슨]
cd ~/Workspace/stanford_umi_demo && .venv/bin/python -m runtime_jt.umi_checkpoint_executor --model $(ls -d ~/models/pv_E/*_jetson) --out runs/jt1_settle_$(date +%H%M%S) --cycles 15 --max-resamples 10 --move --enable-gripper --gripper-close-offset-ticks 25 --speed 100 --acceleration 30 --settle-timeout 0.5 --settle-tolerance 15 2>&1 | tail -25
```

**판정 대상은 z 처짐 하나다. 파지 성공은 부수 관찰.**

```
통과   z 처짐 중앙 |3|mm 이내 · settle_status 가 arrived / settled_with_residual
실패   z 처짐 −5mm 이하 유지 → settle 이 원인이 아니다. 원인 후보를 다시 연다
부작용 관측 간격(100±15ms) 이탈 발생 → 기록만 하고 계속. 사이클 시간 증가 탓
```

## 3. 런 2 — 시작 그리퍼 단독

**변인**: 시작 그리퍼 개구만. settle 은 **끈다**(오늘과 동일 조건으로 되돌림).

시작 개구를 시연 중앙에 맞춘다:

```
현재 실측   90.1 ~ 91.5 mm (틱 102~129)  ← 학습 정의역 0.09m 밖
목표        70.7 mm = 틱 480             (시연 중앙)
허용        69.5 ~ 72.8 mm = 틱 502 ~ 442 (시연 q1~q3)
```

```bash
# [젯슨] 그리퍼만 틱 480 으로
cd ~/Shanks && python3 ticks.py 6 480; echo "rc=$?"      # ← 인자는 --help 로 먼저 대조할 것
```

```bash
# [젯슨] 런
cd ~/Workspace/stanford_umi_demo && .venv/bin/python -m runtime_jt.umi_checkpoint_executor --model $(ls -d ~/models/pv_E/*_jetson) --out runs/jt2_grip_$(date +%H%M%S) --cycles 15 --max-resamples 10 --move --enable-gripper --gripper-close-offset-ticks 25 --speed 100 --acceleration 30 2>&1 | tail -25
```

```
통과   닫기 시작 비율 0.60 이상 (시연 최소 0.690 에 접근)
실패   0.50 이하 유지 → 시작 개구가 원인이 아니다. 조기 폐쇄 원인 재개방
```

## 4. 런 3 — 둘 다

```bash
# [젯슨] 그리퍼 틱 480 세팅 후
cd ~/Workspace/stanford_umi_demo && .venv/bin/python -m runtime_jt.umi_checkpoint_executor --model $(ls -d ~/models/pv_E/*_jetson) --out runs/jt3_both_$(date +%H%M%S) --cycles 20 --max-resamples 10 --move --enable-gripper --gripper-close-offset-ticks 25 --speed 100 --acceleration 30 --settle-timeout 0.5 --settle-tolerance 15 2>&1 | tail -25
```

```
통과   10cm 에서 파지 도달 (개구 정체) + z 가 파지 후 +20mm 이상 상승
부분   파지는 하는데 안 든다 → 리프트는 별개 문제. 사이클 수를 올려 재시도
실패   여전히 바닥까지 하강 → 두 결함 밖에 원인이 더 있다
```

## 5. 런 4 — fail-closed 추가

```bash
# [젯슨]
cd ~/Workspace/stanford_umi_demo && .venv/bin/python -m runtime_jt.umi_checkpoint_executor --model $(ls -d ~/models/pv_E/*_jetson) --out runs/jt4_failclosed_$(date +%H%M%S) --cycles 20 --max-resamples 10 --move --enable-gripper --gripper-close-offset-ticks 25 --speed 100 --acceleration 30 --settle-timeout 0.5 --settle-tolerance 15 --fail-closed 2:>&1 | tail -25
```

⚠️ 위 줄의 `2:>&1` 은 오타다. **`2>&1` 로 고쳐서 낼 것.**

```
예측(되돌려보기 근거)  첫 샘플 통과율 약 75.7% · 사이클당 기대 샘플 1.32회
                       재샘플 소진으로 죽을 확률 1.4e-7
통과   런이 완주하고, attempt > 0 인 사이클이 실제로 생긴다
실패   "failed a safety gate after all resamples" 로 죽으면 임계가 과하다.
       그때는 workspace 경계값을 재검토한다 (게이트를 낮추는 게 아니라 값의 타당성을 잰다)
```

## 6. 런 5 — 반복 (n 올리기)

런 3 설정 그대로, **5cm 배치와 10cm 배치를 각 3회씩.** 오늘까지 조건당 n=1 이라
성공률을 낼 수 없다. 게이트를 낮추지 말고 n 을 올린다.

## 7. 매 런 직후 분석

```bash
# [젯슨] z 처짐 · 닫기비 · 거부율 한 번에
cd ~/Workspace/stanford_umi_demo && D=$(ls -dt runs/jt*_* | head -1) && echo "$D" && cat $D/args.json | head -20 && python3 ~/analyze_live_log.py --run $D 2>&1 | tail -20
```

`args.json` 이 생겼는지 반드시 확인한다 — **[JT-PATCH args-dump] 가 붙은 증거**이고,
앞으로 실행 조건 복원의 유일한 근거다.

## 8. 예상 소요

```
런 1개  15~20사이클 · 사이클당 약 0.45초 추론 + settle 0.5초 + 실행 → 약 30~60초
세팅·리셋 포함  런당 약 5분
런 1~4  약 20분 · 런 5(6회) 약 30분
```

## 9. 이 런북이 다루지 않는 것

- **실행 스텝 4 vs 설정 8** (`n_action_steps`) — 영향 미측정. 별도 실험
- **v10 RelativeChunkBC 비교** — 오늘 94편의 v10 판이 없다. 트랙 A 요청 필요
- **camera_tcp 손목 장착 재검증** — 핸들 조건에서만 검증됐다
- `workspace_x/y/z` 경계값의 타당성 — 지금 값이 맞는지 측정한 적 없다
