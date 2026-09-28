#!/usr/bin/env bash
# raw 촬영분 -> SLAM -> zarr -> 학습 -> ckpt 를 한 줄로 돌린다.
# One command from a raw capture folder to a deployable checkpoint.
#
# 실행:
#   cd ~/S15P21A103 && setsid nohup bash AI/tools/run_slam_to_ckpt.sh > out/s2c.log 2>&1 < /dev/null & disown
#
# 완료 감지 문자열: "S2C_FINISHED_MARKER"
#
# 구간 소유
#   현석  run_orbslam_linux.sh · build_dataset.py · train_policy.py   (데이터 생성·학습)
#   준태  gate_raw_capture · gate_slam_batch · audit_umi_zarr · smoke_deploy_ckpt  (판정)
#
# ⚠️ 미확인 2건 — 추측으로 채우지 않는다. 확인 전에는 프리플라이트에서 멈춘다.
#     (a) 시연 배치 localization 호출 형식. 매핑은 ORB_SLAM_SAVE_MAP 인데
#         배치 쪽이 ORB_SLAM_LOAD_MAP 인지 별도 인자인지 확인 필요
#     (b) build_dataset.py 의 zarr 빌드 하위명령 이름. slam-tag 만 알고 있다
#     둘 다 S2C_LOCALIZE_CMD / S2C_ZARR_CMD 로 넘기면 그대로 쓴다.

set -u
REPO=~/S15P21A103
PY=~/envs/handoff312/bin/python
TPY=python                                   # 현석 학습기용 시스템 파이썬 (torch 2.6.0+cu126)
BUNDLE=~/hyeonseok/umi_gpu_bundle
TRAINER=$BUNDLE/03_umi_policy_trainer/train_policy.py
PROFILE=$BUNDLE/03_umi_policy_trainer/configs/policy_resnet18_gpu.yaml

SLAM_ROOT=${SLAM_ROOT:-~/umi-slam}
ORB_SLAM_BIN=${ORB_SLAM_BIN:-$SLAM_ROOT/source/Examples/Monocular-Inertial/gopro_slam}
ORB_SLAM_VOCAB=${ORB_SLAM_VOCAB:-$SLAM_ROOT/source/Vocabulary/ORBvoc.txt}
RUN_SLAM_SH=${RUN_SLAM_SH:-$SLAM_ROOT/run_orbslam_linux.sh}
BUILD_DS=${BUILD_DS:-$SLAM_ROOT/build_dataset.py}
INIT_TAG_ID=${INIT_TAG_ID:-13}
INIT_TAG_SIZE_M=${INIT_TAG_SIZE_M:-0.16}

RAW=${RAW:-~/raw_0921}                       # 촬영분 (rec_* 들)
MAPPING=${MAPPING:-}                         # 매핑 편 rec_* 절대경로. 비면 멈춘다
STAMP=$(date +%Y%m%d_%H%M%S)
OUT=${OUT:-$REPO/out/s2c_$STAMP}
RUN_ID=${RUN_ID:-onsite_$STAMP}
EPOCHS=${EPOCHS:-120}
BATCH=${BATCH:-64}
GPU=${GPU:-1}                                # 1·2·3·4·6 만. 0·5·7·8·9 는 타인 것
RATE_HZ=${RATE_HZ:-30.0}
mkdir -p "$OUT"
STATUS=$OUT/STATUS.txt; : > "$STATUS"

say(){ echo; echo "=================== $* ==================="; }
note(){ echo "[STATUS] $*" | tee -a "$STATUS"; }
die(){ note "!! $*"; note "중단 — 이후 단계는 미실행이다. 통과가 아니다"; echo "S2C_FINISHED_MARKER"; exit 1; }

say "0. 프리플라이트  $STAMP"
note "시작 $STAMP · RUN_ID=$RUN_ID · GPU=$GPU · EPOCHS=$EPOCHS · RATE=$RATE_HZ"
ok=0; tot=0
chk(){ tot=$((tot+1)); if eval "$2" >/dev/null 2>&1; then ok=$((ok+1)); echo "  [있음] $1"; else echo "  [없음] $1  <- $3"; fi; }
chk "SLAM 바이너리"        "test -x $ORB_SLAM_BIN"   "$ORB_SLAM_BIN"
chk "SLAM 어휘"           "test -f $ORB_SLAM_VOCAB" "$ORB_SLAM_VOCAB"
chk "run_orbslam_linux.sh" "test -f $RUN_SLAM_SH"   "$RUN_SLAM_SH"
chk "build_dataset.py"     "test -f $BUILD_DS"      "$BUILD_DS"
chk "촬영 폴더"            "test -d $RAW"           "$RAW"
chk "매핑 편 지정"          "test -n '$MAPPING' -a -d '$MAPPING'" "MAPPING= 로 넘겨라"
chk "학습기"              "test -f $TRAINER"        "$TRAINER"
chk "학습 프로파일"         "test -f $PROFILE"       "$PROFILE"
chk "촬영 게이트"          "test -f $REPO/AI/tools/gate_raw_capture.py"  "pull 했나"
chk "SLAM 게이트"         "test -f $REPO/AI/tools/gate_slam_batch.py"   "pull 했나"
chk "zarr 감사기"         "test -f $REPO/AI/tools/audit_umi_zarr.py"    "pull 했나"
echo "  프리플라이트 $ok / $tot"
note "프리플라이트 $ok / $tot"
[ "$ok" -lt "$tot" ] && die "필수 구성이 빠졌다. 위 [없음] 줄의 경로를 확인해라"

say "0b. GPU 실연산 (가용성 확인 != 기능 확인)"
CUDA_VISIBLE_DEVICES=$GPU $TPY - > "$OUT/gpu.txt" 2>&1 <<'PYEOF'
import torch
print("torch", torch.__version__, "available", torch.cuda.is_available())
if torch.cuda.is_available():
    a = torch.randn(512, 512, device="cuda")
    print("device", torch.cuda.get_device_name(0), "cc", torch.cuda.get_device_capability(0))
    print("실연산 OK", round((a @ a).sum().item(), 3))
PYEOF
cat "$OUT/gpu.txt"
grep -q "실연산 OK" "$OUT/gpu.txt" || die "GPU 실연산 실패"
note "GPU 실연산 통과"

say "1. 촬영 게이트 (SLAM 돌리기 전에 거른다)"
$PY "$REPO/AI/tools/gate_raw_capture.py" --raw "$RAW" --out "$OUT/capture_gate.json" 2>&1 | tail -30
$PY - "$OUT/capture_gate.json" 2>&1 <<'PYEOF' | tee -a "$STATUS"
import json, sys
d = json.load(open(sys.argv[1])); t = d["tally"]
print("[STATUS] 촬영 게이트 통과 %s · 불합격 %s · 미판정 %s / 전체 %s"
      % (t.get("PASS", 0), t.get("FAIL", 0), t.get("INCOMPLETE", 0), d["total"]))
PYEOF
note "촬영 게이트 결과는 위. **불합격 편은 SLAM 을 돌리지 말고 다시 찍는다**"

say "2. 매핑 편 -> 아틀라스"
PREP_MAP=$OUT/prepared_mapping; mkdir -p "$PREP_MAP"
env ORB_SLAM_BIN="$ORB_SLAM_BIN" ORB_SLAM_VOCAB="$ORB_SLAM_VOCAB" \
    ORB_SLAM_SAVE_MAP="$PREP_MAP/map_atlas.osa" \
    INIT_TAG_ID="$INIT_TAG_ID" INIT_TAG_SIZE_M="$INIT_TAG_SIZE_M" \
    sh "$RUN_SLAM_SH" "$MAPPING" "$PREP_MAP" "$PREP_MAP/slam" 2>&1 | tail -25
[ -f "$PREP_MAP/map_atlas.osa" ] || die "아틀라스가 안 만들어졌다. 매핑 편 촬영을 먼저 의심해라 (태그 ID $INIT_TAG_ID · 한 변 ${INIT_TAG_SIZE_M}m 이 계속 보였나)"
note "아틀라스 $(stat -c%s "$PREP_MAP/map_atlas.osa") B"

say "3. tx_slam_tag.json"
$TPY "$BUILD_DS" slam-tag "$MAPPING" "$PREP_MAP" 2>&1 | tail -15
[ -f "$PREP_MAP/tx_slam_tag.json" ] || die "tx_slam_tag.json 이 안 나왔다. 마커 검출 실패다"
note "tx_slam_tag OK"

say "4. 시연 배치 localization"
if [ -z "${S2C_LOCALIZE_CMD:-}" ]; then
  die "시연 배치 호출 형식이 **미확인**이다. 추측해서 돌리지 않는다.
     현석에게 확인한 뒤 S2C_LOCALIZE_CMD 로 넘겨라. 예)
       S2C_LOCALIZE_CMD='env ORB_SLAM_BIN=@BIN@ ORB_SLAM_VOCAB=@VOCAB@ ORB_SLAM_LOAD_MAP=@MAP@ sh @SH@ @SESSION@ @PREP@ @PREP@/slam'
     치환자: @BIN@ @VOCAB@ @MAP@ @SH@ @SESSION@ @PREP@"
fi
PROC=$OUT/processed; mkdir -p "$PROC"
n=0; fail=0
for d in "$RAW"/rec_*; do
  [ -d "$d" ] || continue
  [ "$d" = "$MAPPING" ] && continue
  n=$((n+1)); nm=$(basename "$d"); pp="$PROC/$nm"; mkdir -p "$pp"
  cmd=$(printf '%s' "$S2C_LOCALIZE_CMD" \
        | sed -e "s#@BIN@#$ORB_SLAM_BIN#g" -e "s#@VOCAB@#$ORB_SLAM_VOCAB#g" \
              -e "s#@MAP@#$PREP_MAP/map_atlas.osa#g" -e "s#@SH@#$RUN_SLAM_SH#g" \
              -e "s#@SESSION@#$d#g" -e "s#@PREP@#$pp#g")
  eval "$cmd" > "$pp/slam_run.log" 2>&1 || fail=$((fail+1))
  [ -f "$pp/slam/camera_trajectory.csv" ] || fail=$((fail+1))
done
note "localization 시도 $n편 · 실패 $fail편"
[ "$n" -eq 0 ] && die "시연 편이 0개다. RAW 경로와 MAPPING 제외가 맞나"

say "5. SLAM 게이트"
$PY "$REPO/AI/tools/gate_slam_batch.py" --processed "$PROC" --out "$OUT/slam_gate.json" 2>&1 | tail -25
$PY - "$OUT/slam_gate.json" 2>&1 <<'PYEOF' | tee -a "$STATUS"
import json, sys
d = json.load(open(sys.argv[1])); t = d["tally"]
print("[STATUS] SLAM 게이트 통과 %s · 불합격 %s · 미판정 %s / 전체 %s"
      % (t.get("PASS", 0), t.get("FAIL", 0), t.get("INCOMPLETE", 0), d["total"]))
for r in d["batch"]["rows"]:
    print("[STATUS]   %s: %s" % (r["name"], r["got"]))
PYEOF

say "6. zarr 빌드"
if [ -z "${S2C_ZARR_CMD:-}" ]; then
  die "zarr 빌드 하위명령이 **미확인**이다. slam-tag 만 알고 있다.
     현석에게 확인한 뒤 S2C_ZARR_CMD 로 넘겨라. 치환자: @BUILD@ @RAW@ @PROC@ @PREP@ @OUT@"
fi
ZBASE=$OUT/ds_$STAMP
cmd=$(printf '%s' "$S2C_ZARR_CMD" \
      | sed -e "s#@BUILD@#$BUILD_DS#g" -e "s#@RAW@#$RAW#g" -e "s#@PROC@#$PROC#g" \
            -e "s#@PREP@#$PREP_MAP#g" -e "s#@OUT@#$ZBASE#g")
eval "$cmd" 2>&1 | tail -25
ZARR=$(ls -1 "$ZBASE"*.zarr.zip 2>/dev/null | head -1)
[ -n "$ZARR" ] || die "zarr 가 안 만들어졌다"
note "zarr $ZARR ($(stat -c%s "$ZARR") B)"

say "7. zarr 감사 + 게이트"
$PY "$REPO/AI/tools/audit_umi_zarr.py" --zarr "$ZARR" --label "$RUN_ID" --gate \
    --rate-hz "$RATE_HZ" --out "$OUT/zarr_gate.json" 2>&1 | tail -25
$PY - "$OUT/zarr_gate.json" 2>&1 <<'PYEOF' | tee -a "$STATUS"
import json, sys
g = json.load(open(sys.argv[1])).get("gates", {})
print("[STATUS] zarr 게이트 %s · 통과 %s · 불합격 %s · 미판정 %s / 전체 %s"
      % (g.get("verdict"), g.get("passed"), g.get("failed"), g.get("unknown"), g.get("total")))
for r in g.get("rows", []):
    if r.get("ok") is not True:
        print("[STATUS]   미통과 %s: %s  기준 %s" % (r["name"], r["got"], r["want"]))
PYEOF

say "8. 학습  EPOCHS=$EPOCHS  BATCH=$BATCH  GPU=$GPU"
cd "$BUNDLE" || die "번들 경로 없음"
export UMI_ROOT=$BUNDLE/third_party/umi WANDB_MODE=disabled HYDRA_FULL_ERROR=1
CUDA_VISIBLE_DEVICES=$GPU $TPY "$TRAINER" check "$ZARR" > "$OUT/dataset_check.json" 2>&1
grep -q '"episodes"' "$OUT/dataset_check.json" || { tail -20 "$OUT/dataset_check.json"; die "dataset check 실패 — 학습 미실행"; }
note "dataset check 통과"
CUDA_VISIBLE_DEVICES=$GPU $TPY "$TRAINER" train "$ZARR" --run-id "$RUN_ID" \
    --runs-dir "$OUT/runs" --epochs "$EPOCHS" --batch "$BATCH" --eval-batch "$BATCH" \
    --profile "$PROFILE" 2>&1 | tail -50
MAN=$OUT/runs/$RUN_ID/manifest.json
[ -f "$MAN" ] || die "manifest 없음 — 학습 실패 또는 중단"
$PY - "$MAN" 2>&1 <<'PYEOF' | tee -a "$STATUS"
import json, sys
m = json.load(open(sys.argv[1])); b = m.get("best_checkpoint", {})
p, h = b.get("policy", {}), b.get("hold_current_pose_and_width", {})
print("[STATUS] epoch %s · baseline 이김 %s" % (b.get("epoch"), b.get("beats_hold_baseline")))
for k in ("position_component_rmse_mm", "position_distance_rmse_mm",
          "position_distance_p95_mm", "rotation_mean_deg", "width_rmse_mm"):
    pv, hv = p.get(k), h.get(k)
    r = ("%.2fx" % (hv / pv)) if pv and hv else "-"
    print("[STATUS]   %-32s 정책 %-10.10s hold %-10.10s %s" % (k, str(pv), str(hv), r))
print("[STATUS] ** 이건 개루프 예측 오차다. 롤아웃 성공률이 아니다 **")
PYEOF

say "9. ckpt 배포 검사"
CK=$OUT/runs/$RUN_ID/checkpoints/best.ckpt
cd "$REPO" || exit 1
$PY AI/tools/smoke_deploy_ckpt.py --checkpoint "$CK" --manifest "$MAN" 2>&1 | tail -20
note "ckpt $CK"
note "종료 $(date +%Y%m%d_%H%M%S)"
say "요약"; cat "$STATUS"
echo "S2C_FINISHED_MARKER"
