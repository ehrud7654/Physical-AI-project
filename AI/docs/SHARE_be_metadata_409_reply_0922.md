# SHARE — BE ckpt metadata 409 회신 (8건 확정 · 최종)

작성자: 김준태(트랙 B) · 2026-09-22 · 확신도 🟢 manifest 원문 + 러너 소스 대조

BE(최은찬) `CHECKPOINT_METADATA_MISMATCH` 미통과 8건 처리 결과. **전부 AI 쪽에서 해결. EC2 수정 요청 없음.**

---

## 최종 처리

| 구분 | 건수 | 항목 |
|---|---|---|
| AI가 고쳐서 재업로드 | 7 | contractVersion · robotProfileId · layout 이름 · note 3종 · 숫자 표현 · **execSliceAppliesTo** |
| 차단 아님 · P2 요청 | 1 | `provenance` |

업로드 대상 파일: **`~/metadata_ec2.json`** (서버). 원본 `~/manifest_pv_E.json` 은 불변.
전수 검사 **통과 8 / 전체 8** 🟢

## ⚠️ 정정 — `execSliceAppliesTo` 요청 철회 (같은 날, 같은 건)

**처음 낸 판단(틀림)**: "`raw` 가 맞다. EC2 RobotProfile 쪽을 `raw` 로 바꿔 달라."
근거로 `motion_planner.py:67` 이 원본 배열에 슬라이스를 건다는 점만 들었다.

**정정 후(맞음)**: `converted` 를 수용한다. EC2 수정 불필요.

근거는 **우리 manifest 안에 이미 있었다.** `runtimeSpec` 이 converted 를 정의하고 있다:

```
consumerMustConvert : true
convertedDim        : 7
convertedLayout     : ["x","y","z","rx","ry","rz","gap_m"]
convertedRotation   : "AXIS_ANGLE_ROTVEC"
convertedFrame      : "ABSOLUTE"
execSlice           : {startInclusive: 1, endExclusive: 5}
```

10차원 상대 rot6d → 7차원 절대 축각. **행 단위 1:1 이라 행 수·순서가 보존된다(16행 → 16행).**
따라서 슬라이스 `[1,5)` 를 변환 전/후 어디에 걸어도 같은 4행이고, index 0 은 변환 후에도
"현재 절대 포즈" 라 건너뛰는 것이 맞다. → **raw 와 converted 는 실행상 동치.**

**어시스턴트 오류 유형**: 대상 파일을 열어보기 전에 상대 트랙에 수정 요청부터 냈다.
조건부 수용안을 같이 달아둔 덕에 실제 라운드트립은 발생하지 않았지만,
`json.tool` 한 번이면 요청 자체가 불필요했다. → *계측기 규칙: 대조할 원문이 손에 있는데
추론으로 먼저 결론 내지 않는다.*

**동치가 깨지는 조건 (BE에 기록 요청)**: 변환 단계에 리샘플링(10Hz→30Hz 보간)이나
관절공간 IK 가 들어가면 행 수가 바뀌어 등식이 **조용히** 깨진다. 그때는 `raw` 로 되돌려야 한다.

## 실제 적용 로그 🟢

```
+ contractVersion: (absent) -> umi-policy-v1
+ robotProfileId: (absent) -> 52000000-0000-4000-8000-000000000001
~ actionSpec.layout[0:3]: ['x','y','z'] -> ['dx','dy','dz']
- actionSpec.layoutNote 삭제
- runtimeSpec.execSliceNote 삭제
- runtimeSpec.reobserveRateNote 삭제
= runtimeSpec.rateHz 없음
~ runtimeSpec.actionPointRateHz: 10.0 -> 10
~ actionSpec.rateHz: 10.0 -> 10
= actionSpec.actionPointRateHz 없음
- provenance 분리
변경 9 / 검사 11
~ runtimeSpec.execSliceAppliesTo: raw -> converted   (별도 적용)
```

주의: `rateHz` 는 `runtimeSpec` 이 아니라 **`actionSpec`** 아래에 있다. BE 메시지의 표기와 부모가 다르다.

## 대상 파일 탐색 — 이름으로 고르면 틀린다 🟢

```
서버 metadata.json    1057개
서버 manifest.json     231개
actionSpec 포함 json    10개
execSliceAppliesTo 포함  1개  <- ~/manifest_pv_E.json
```

젯슨 패키지의 `metadata.json` 은 EC2 metadata 가 **아니다**
(`dataset_sha256`/`shape_meta`/`action_scale` 를 담은 다른 문서).
계약 고유 키(`execSliceAppliesTo`)로 찾아야 유일하게 특정된다.

## 도구

`patch_metadata_ec2.py` — 원본 불변 · 멱등 · 자체검사 **12/12** · 판별행 3개
(layout 없음 / 이름이 x,y,z도 dx,dy,dz도 아님 / Hz 비정수 → 전부 `ContractError` 로 죽는다).
엉뚱한 파일에 돌렸을 때 "괜찮음"이 나오지 않게 만든 것이 요점.

## provenance (차단 아님)

`checkpointSha256` 은 업로드 요청 최상위 `sha256` 과 동일 값이라 체크포인트 추적은 유지된다.
실제 유실은 `configSha256` 하나. `~/provenance_sidecar.json` 으로 분리 보관했고 대조표는 AI가 유지.
→ P2: metadata DTO 에 `provenance` 추가 (configSha256 · checkpointSha256 · nativeHz · downSampleSteps).

## 다음 계약 버전 제안 (미반영)

주석 필드를 못 넣는 대신 의미를 값으로 박는다.

```
actionSpec.layoutFrame     = "current_eef"
runtimeSpec.execSliceIndex = [1, 5]
```

`dx` 라는 이름은 월드 증분으로 오독될 수 있다. 오늘 `pose_rep` `'rel'`(월드 차이) vs
`'relative'`(TCP 프레임) 혼동이 정확히 같은 모양이었다.
