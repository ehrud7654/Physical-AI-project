# Project Handoff

> Last updated: 2026-09-18 KST. **Read this section before the older material
> below.** The repository and uncommitted diff are the source of truth. Do not
> reset, clean, pull, switch branches, or bulk-add files before reviewing §0.3.

## 0.0 Latest diagnostic state (2026-09-17; read before the historical §0.1-0.6)

The historical §0.3 Git snapshot and K=2 next-action text below are **not the
current repository state**. The local geometry/contact gate was completed, and
the user authorized diagnostic learned-policy rollout on the local machine.
Shared-server learned-checkpoint inference, policy probes and rollout remain
forbidden; server GPU use is for training. No hardware/deployment approval
follows from these simulation results.

2026-09-17 follow-up: local CPU v10 best-val learned-policy diagnostics now
validate and execute the first four **future** targets (index 0..3) fully,
instead of stopping halfway through the prefix on simulator success. Under the
same K=2 near-contact, provisional-contact, simulation-only 2mm-preload and
fixed-object conditions, 14/14 scene-valid episodes showed bilateral contact
and stable lift. Every run ended after its first four-target prefix, before a
second policy inference; this is **not** far-start or multi-cycle closed-loop
validation. Required time expansion was 2.77–3.86x, so the nominal 0.4s
four-target execution took about 1.1–1.5s. All 14 exceed an illustrative 2x
handoff cap. See `AI/docs/MEASURE_v10_four_target_nearcontact_0917.md` and
`AI/out/v10_policy_k2_execute4_complete_normal14_20260917.json` (generated
report, not Git). `AI/tools/render_relative_chunk_rollout.py` gained an
opt-in `--complete-execute-prefix` local diagnostic mode; the default behaviour
is unchanged. The next gate is policy-free time-parameterised far-start IK and
acceleration feasibility, followed by genuine H=2 four-target reobservation.

2026-09-17 later result **supersedes that next-gate sentence**: a scripted,
policy-free open-jaw approach from one fixed provisional start reached the
predeclared near-contact handoff with valid IK, object visibility and measured
simulator acceleration in 14/14 episodes. At that handoff, the local CPU v10
best-val checkpoint executed future targets 0..3, reobserved a genuinely new
H=2 pair, then executed another 0..3: 14/14 nominal-placement lifts in two
cycles. This is *not* learned far-start navigation or hardware validation.
First-prefix time expansion was about 4x; the caller's 4x cap and the
policy-free warmup's 2x cap are diagnostics, not approved robot limits.
Inference-time mean fill yielded 0/14 accepted lifts (six geometry and eight
timing rejections), whereas additive noise on the source image yielded 13/14.
Crucially, the stricter object-only ±10mm test at a no-contact earlier handoff
found normal first-four-target direction correct in only 20/52 paired shifts,
and no episode correct in all four. One of 14 episodes was excluded because a
shift created *pre-policy* jaw contact. Nominal lift must not be cited as
object-position generalisation. See the follow-up sections of
`AI/docs/MEASURE_v10_four_target_nearcontact_0917.md`, the reports under
`AI/out/v10_far_start*20260917.json`, and the local-only tool
`AI/tools/probe_far_start_object_only.py`. The next substantive work is to
diagnose this localisation failure before more success-rate trials; physical
calibration, collision clearance, transport and supervised robot approval
remain outstanding.

2026-09-17 localisation diagnosis, continuing the object-only check above:
at the later, contact-free handoff (scripted waypoint 18), the same local
v10 checkpoint pointed its first-four-target response toward the shifted
object in only 16/52 ±10mm placements across 13 valid episodes (0/13
correct in all four directions). This is a first-action test, not a shifted
object rollout. Real v10 training data do contain frames of similar apparent
object size/open gap (42/56 episodes), but >10mm lateral fourth-target
corrections occur in only 9/56 episodes within those selected frames. A
same-checkpoint **local CPU** probe on selected real images also compressed
recorded lateral-target variation: predicted fourth-target x standard
deviation ~1.4mm versus target ~26.8mm on training frames, the latter
dominated by a few extreme demonstrations. The held-out image-position bins
make that explicit: 9 central episodes have 1.62mm x MAE, while the sole
off-centre right episode has 73.0mm error. This is too few off-centre cases
for a stable rate claim. Simulated handoff H=2 inputs are
also much more static than comparable-gap human demonstrations (median TCP
motion 1.49mm versus 15.69mm per ~0.1s), and visual packaging/background
detail differs. Thus neither "missing real image signal" nor "sim appearance
alone" is established as the sole cause. These are observational and narrow
sim tests, not hardware evidence. See
`AI/docs/MEASURE_v10_visual_localisation_failure_0917.md` and its ignored
`AI/out/*20260917.json` reports. Next: controlled rare-lateral-correction
training comparison with the same split/checkpoint family, held-out
displacement-stratified error, and the same object-only first-four-target
probe; separately address the 10Hz observation/retiming mismatch. No new
training or server-side checkpoint inference was performed in this diagnosis.
An optional factor-4 train-only lateral-correction sampler is prepared in
`AI/tools/train_relative_chunk_bc.py` (default factor 1 preserves the old
path); its focused local check passes. In the v10 seed-0 split, it selects
198/1,009 training rows by current gap 60–70mm and fourth-target local-x
correction >10mm. The factor-4 treatment was subsequently trained on V100
GPU 2; both its best-val and last-epoch checkpoints were evaluated on the
local CPU with the same object-only probe. Correct-direction signs on the
last of the first four targets changed from baseline 16/52 to best-val
28/52 and last 25/52, but best-val median displacement gain was 0.0011,
and image noise still gave 25/52. Selected held-out real-image local-x MAE
barely changed (8.76 to 8.66mm), with one off-centre held-out episode
still at 67.2mm error. This is a partial diagnostic change, not a
localisation or physical grasp gate. See
`AI/docs/MEASURE_v10_visual_localisation_failure_0917.md` for conditions,
checkpoint hashes, limitations and ignored `AI/out/` report paths.

2026-09-18 follow-up: a local-CPU, inference-time recheck used **all** 28
scene-scale/open-gap matched rows from 10 of the 14 held-out v10 episodes,
instead of choosing one row per episode. Baseline vs factor-4 best-val
fourth-target local-x MAE was 12.71mm vs 8.66mm; episode-equal means were
9.70mm vs 7.39mm. Only five rows from three episodes had recorded lateral
targets above 10mm. Their MAE fell 61.55mm to 37.55mm and correct-sign
counts rose 2/5 to 4/5, but two roughly 70mm targets still elicited only
about 3mm of policy movement. Mean-filled and noisy inference inputs raised
factor-4 overall MAE to 13.22mm and 13.19mm. These correlated, cross-episode
image comparisons do **not** establish measured object-placement
generalisation or robot success. See
`AI/docs/MEASURE_v10_heldout_visual_response_0918.md` and the ignored
`AI/out/v10_heldout_all_matched_visual_response_20260918.json`. A subsequent
paired-episode exploratory bootstrap found baseline-minus-factor-4 mean MAE
improvement +2.31mm across ten episodes, but only 4/10 episodes improved and
the 95% percentile interval was [−0.39,+6.81]mm. The apparent row-average
benefit is not a stable episode-level finding; only three episodes contain
large lateral targets. See the separate ignored
`AI/out/v10_heldout_episode_paired_effect_20260918.json`.

2026-09-18 next controlled training experiment: the diagnostic relative-chunk
image encoder globally averaged its last feature map to `1x1`, which may
attenuate object position. This is a hypothesis, not an established cause. An
opt-in `4x4` spatial-grid encoder config was added at
`AI/configs/train/relative_chunk_bc_spatial4.yaml`; the legacy config and old
checkpoint reconstruction remain `1x1`. Split, seed, optimiser, loss and
sampling factor stay unchanged so the first comparison varies only this model
path (although parameter count rises from 1,031,280 to 2,014,320). Training is
GPU-2/server-only; learned-checkpoint evaluation remains local CPU. The plan
and exact commands are in `AI/docs/PLAN_v10_spatial_encoder_ablation_0918.md`.
No spatial checkpoint has been trained or evaluated yet.

2026-09-18 spatial4 result supersedes the final sentence above. Training ran
on V100 GPU 2 only; all following inference ran on local CPU. Best-val SHA-256
is `3cc516e3e6a3935dead95bc73e8aeda48186f9fed48143d87616db43e64e45fd`
and last-epoch is
`a61f42adc887941ca6e2b2450764a4a2d030b7ef4c2349b82899371b0233943e`.
Best validation L1 fell from the old `0.39769` to `0.32684`, but loss is not
the acceptance criterion. On the same ten-episode/28-row held-out image
diagnostic, best-val normal local-x MAE was 4.15mm versus the old 12.71mm;
the five large-target rows from only three episodes changed from 2/5 correct
directions and 61.55mm MAE to 5/5 and 12.77mm. Mean-fill raised the new MAE to
13.72mm and large-target MAE to 62.17mm. In fixed-pose, object-only +/-10mm
renders, best/last produced 24/28 and 26/28 correct terminal directions versus
the old 14/28; all four directions passed in 3/7 and 5/7 episodes versus 0/7.
With genuinely different moving-H=2 snapshots, best/last produced 15/16 and
16/16 directions over four IK-valid episodes, while mean-fill produced 3/16
and 8/16. **All four histories still exceed the provisional acceleration
limit**, and only three independent held-out demonstrations contain large
lateral targets. This supports spatial-feature preservation as a major
bottleneck but is not closed-loop, hardware or deployment evidence. Keep both
best and last as research candidates until a new-placement episode holdout
resolves best's stronger magnitude/error versus last's stronger direction
consistency. Details: `AI/docs/MEASURE_v10_spatial_encoder_0918.md`.

2026-09-18 smooth moving-H=2 follow-up: the abrupt one-tick history above was
not retained as the execution condition. Before looking at any learned-policy
result, a policy-free quintic duration sweep over `0.2--1.0s` selected the
shortest duration that kept all four IK-valid histories below the provisional
acceleration bound. `0.2s` passed only 1/4 histories (peak `3.401rad/s^2`),
whereas `0.3s` passed 4/4 (peak `1.600rad/s^2`). The observation interval stays
about `0.1s`; `0.3s` is the complete ramp duration, so the H=2 history executes
about 20.99% of the command and has median TCP movement only `0.604mm`. This is
far more static than the real UMI median (~`15.69mm/0.1s`). Under that frozen
condition, best/last normal direction counts were 14/16 and 15/16, all-four
episode counts 2/4 and 3/4, mean-fill counts 2/16 and 8/16, and acceleration
exceedances 0/4 for both. This removes the provisional acceleration violation
from this diagnostic only; it is not hardware, timing-match, closed-loop or
deployment evidence. The local regression including the new analytic profile
check passes 35/35. Details and ignored generated reports are listed in
`AI/docs/MEASURE_v10_spatial_encoder_0918.md`.

2026-09-18 pre-arrival work: HW expects to share a new ORB-SLAM3 + ArUco
ID 11 episode, but no such episode has reached Track A yet. The
`AI/docs/PLAN_orbslam_aruco_umi_intake_0918.md` separates a read-only
archive inventory from still-unverified pose/clock/gap/tracking checks.
`AI/tools/inspect_umi_delivery.py` fingerprints a directory, ZIP or TAR
without extraction and always reports `INVENTORY_ONLY_SCHEMA_UNVERIFIED`;
its four synthetic tests and a read of one older ARCore normalized ZIP pass.
No ORB-specific adapter or new-data quality pass is claimed. The
`AI/umi/offline_real_handoff.py` now copies an accepted H=2 JointState
snapshot, rejects inconsistent 10Hz history and independently checks the
injected IK output with the released URDF FK. Its seven ROS-free fixture
tests pass; so do the existing four joint-chunk, six ROS-adapter and four
installation-limit tests, and the expanded local regression passes 35/35
checks. The real IK solver, camera/TCP calibration,
continuous collision clearance, ROS transport and physical approval remain
unverified. None of this work runs learned inference on the shared server
or sends motor commands. This account-transfer section describes related
source/tests/docs, not generated recordings or media.

The EEF-relative-chunk-to-joint-command ownership is now recorded as a
**D-AI-55 proposal, not yet approved**, in
`AI/docs/DECISIONS_0917_relative_chunk_execution_boundary_draft.md`:
Track B owns policy EEF output, Track A owns current-TCP anchoring/decode,
five-axis IK and joint/gap chunk validation, and HW/ROS owns the downstream
joint-trajectory input/control. Existing Track A implementation is offline
and MuJoCo-oriented; the cross-system handoff is not complete. Dataset v10's
alignment/IK `false` flags describe label generation, not the need for this
runtime boundary. Relative targets anchor to `T_base_tcp_current`; this does
not establish an independent physical object/workspace-to-base registration.
The teammate corrected the reported wrist-roll stop to 738 ticks *
360/4096 = +64.86328125deg (the earlier +66.42deg used a rounded tick
size). This is close to the handoff mesh's first sampled bracket intersection
(+65deg), not proof of swept clearance or a directly confirmed safe stop. The raw
URDF limit remains untouched. An AI-side, config-driven +60deg **offline
diagnostic cap** now screens IK through `umi.relative_robot_preflight.arm_ranges`;
it is not a certified safe physical range. Direct HW stop/clearance checks and
full ROS trajectory integration remain outstanding; motor repair alone does
not authorise replay of simulator expert trajectories on hardware.
The +60deg cap was rechecked locally with **policy-free** recorded-oracle
replays on 2026-09-17: both v9 and v10 passed the same 14/14 scene-valid
episodes under the v9 K=2 per-episode approach-start registration. The v10
report is `AI/out/wristroll60_v10_oracle_gate_20260917.json` (v9 comparator:
`AI/out/wristroll60_v9_oracle_gate_20260917.json`). These runs still use the
simulation-only 2mm grip preload and validate one target per cycle, not the
full 8-step chunk. The highest commanded wrist_roll in the v10 replay was
0.05452rad (3.12deg), so this is a regression check, **not a stress test of
the new upper bound**. The registration's common `start_arm_rad` itself has
wrist_roll 1.62073rad and exceeds the +60deg cap; the successful replays use
their separate per-episode approach starts. Do not use that common start for
new executions without re-registration.
An additional torch/ROS-free `AI/umi/joint_chunk_handoff.py` now assembles
exactly eight preflighted arm commands into ordered J1-J5 `(8,5)` radians,
absolute full-gap `(8,)` metres and retimed `(8,)` seconds. It independently
checks the installed wrist-roll cap, gap contract, waypoint range, dynamic
limits and caller-provided maximum time scale; four focused tests pass.
Track B's official-UMI `evaluate.py` runs `absolute[1:5]` with
`action_steps=4`, but v10's eight outputs are *all future targets* and index 0
is t+0.1s, not current state. An offline prefix selector therefore uses
v10 indices 0..3 for a nominal four-step/0.4s observe-execute-reobserve
comparison; retiming can extend the actual interval. Track A's existing local
MuJoCo diagnostic defaults to one executed future target. Do not transfer
Track B's 1..4 slice to v10 or compare the two rollout rates as identical
conditions. The D-AI-55 draft records both indexing conventions.
This is an **offline interface artifact only**. HW's `BACKEND_INTERFACE.md`
lists both `/arm_controller/joint_trajectory` topic and
`/arm_controller/follow_joint_trajectory` action, while the repository has
only a single-point topic helper. Confirm which transport will carry the
eight-point chunk and how the separate gripper command is synchronized before
implementing a sender; do not command physical motors from this handoff.

- Real demonstration data: `umi_real_relative_20260911_v10`, 70 episodes,
  1,248 anchors, observation history 2 and future relative SE(3)+gap chunk 8.
  The task is still the upright 40mm-wide snack case, grasped **from both sides
  of its body**, never top-down.
- The diagnostic registration for this work is
  `AI/out/relative_chunk_v9_width40mm_preload2mm_lookback4_fixedstart_20260916.json`.
  It is **rejected as a global registration**; only the predeclared eight-episode
  subset is used with an explicit diagnostic override. K=4 is a separate
  near-contact test, not a replacement for the historical K=2 oracle gate.
- New policy-free, fixed-start, object-only ±10mm X/Y exact-10Hz suite:
  `AI/tools/eval_sim_relative_10hz_suite.py` produces a resumable 8 episodes ×
  5 placements manifest without deleting partial outputs. The completed local
  run has 35/40 checked candidate conditions and 637 complete H=2/K=8 rows.
  All five rejected conditions belong to short episode `145625` and remain in
  the denominator. `AI/tools/audit_sim_relative_10hz_candidates.py` checks
  fixed-start image/target variation and extrapolation baselines. These are
  **simulator-achieved future states**, not robot commands, human labels or
  approved training data (`training_ready=false`).
- `AI/tools/preflight_sim_relative_10hz_domain.py` keeps all five placements
  of a source episode in one fold and compares distinct source rows at baseline
  object placement only. The eligible source split is five train / two holdout
  episodes; `145625` is excluded from training but not from the 40-condition
  denominator. Forty distinct source rows were paired. `source_row` aligns
  trajectory phase, not physically synchronized camera times after retiming.
- Same-checkpoint **inference-time** RGB replacement was run on the local CPU
  over the same 8 × 5 placements using the v10 seed-0 best-val checkpoint
  (SHA-256 `5bc8a25de054bc29967ebe387d808d5add0e7ad8f791c60e40b20f3440e6adbb`).
  Stable side grasp: normal 35/40, spatial-information-free `mean_fill` 36/40,
  noise 36/40. The 40 placements cluster within only eight source episodes;
  they are not independent demonstrations. Under this narrow K=4, provisional
  2mm-preload/contact-proxy, one-step-validation simulation, **normal RGB has
  no observed advantage**. This does not prove the policy never uses images,
  nor demonstrate real-world visual localisation. Do not equate this 35/40 with
  the policy-free candidate-suite 35/40: the latter validates the full 8-step
  future and has a different denominator meaning.
- A further local CPU paired-input probe used the earliest common source row
  of each of the seven complete source episodes (28 paired offsets). Holding
  proprio fixed while changing candidate RGB changed predicted 8-step
  translation by a median 15.30mm, while the corresponding simulator-achieved
  future target changed by 5.85mm; the episode-median cosine was only 0.011.
  Changing proprio alone moved predictions by 0.077mm. The policy responds to
  RGB, but the response is not aligned with the object-placement target in
  this diagnostic. Candidate RGB also contains robot-pixel differences after
  the first motion, so this is not a pure object-only image intervention.
  Report: `AI/out/sim_relative_visual_position_v10_k4_summary_20260917.json`;
  code/test: `AI/tools/probe_sim_relative_visual_position.py` and
  `AI/tools/check_sim_relative_visual_position.py`.
- The follow-up fixed-pose render removed that robot-pixel-motion confound:
  for seven complete source episodes, the robot joints and wrist-camera pose
  were held identical while only object XY was shifted by 0/±10mm. The same
  v10 best-val checkpoint's final chunk-target displacement pointed in the
  correct half-space in only 14/28 paired offsets, with 0/7 episodes correct
  in all four directions; mean-filled images gave 7/28. This is a static H=2
  first-chunk visual-localisation diagnostic, **not** closed-loop success or
  physical validation. See
  `AI/out/fixed_pose_object_only_v10_full_v2_20260917.json` and montage PNG;
  source/test: `AI/tools/probe_fixed_pose_object_only.py` and
  `AI/tools/check_fixed_pose_object_only.py`.
- A moving-H=2, object-only held-out probe now uses two distinct robot/camera
  snapshots 0.096s apart, then re-renders the exact same snapshots with only
  object XY changed by 0/±10mm. A policy-free backoff IK audit found both
  poses feasible in 3/7 source episodes at 10mm, 2/7 at 20mm, and 0/7 at
  30mm (5mm target step; either 45/65mm open gap). In a narrower 10mm-backoff,
  3mm-target-step probe, 4/7 held-out episodes supplied image pairs; the
  normal checkpoint's terminal target pointed in the intended half-space for
  7/16 paired object shifts, mean-filled images for 0/16, and 0/4 episodes
  were correct in all four directions. Crucially, all four 0.096s approach
  motions exceeded the **provisional acceleration limit** (36.1–36.8 vs
  3.0rad/s²), despite speed being below 1.0rad/s. This is a non-executable,
  near-contact first-chunk diagnostic, not a successful far-start or
  closed-loop rollout. Report/montage:
  `AI/out/moving_h2_object_only_v10_backoff10_step3_gap45_final_20260917.json`
  and `.png`; audit:
  `AI/out/moving_h2_backoff_ik_audit_20260917.json`; code/check:
  `AI/tools/probe_moving_history_object_only.py`,
  `AI/tools/audit_moving_h2_backoff_ik.py`, and
  `AI/tools/check_moving_history_object_only.py`.

Measurement and exact report paths are in
`AI/docs/MEASURE_s22_mujoco_visual_alignment_0917.md`. The new source has
targeted tests `AI/tools/check_sim_relative_10hz_{suite,audit,domain}.py`.
Generated `AI/out` reports and NPZ/media are not source and should stay out of
Git. Next substantive work is to resolve the far-start IK and acceleration
gates with a policy-free, time-parameterised approach, **then** rerun the
episode-separated moving-H=2 object-only test before closed-loop success
claims. The current static and moving diagnostics did not show reliable
four-direction tracking. Also measure real optical
camera↔TCP, robot-base/workspace registration and requested gap↔actual pad
separation, and approve collision/stop limits before any supervised hardware
dry-run. Do not promote synthetic candidates or the current checkpoint to
hardware claims or promise a deployment date.

## 0. Immediate account-transfer state (2026-09-15)

### 0.1 What the project is doing now

The active task is not generic BC training. It is validating whether the real
UMI demonstration of lifting an upright `구운감자` snack case can be reproduced
as a **horizontal side/body grasp** by the SO-101: the two parallel fingers must
close on the left and right sides of the upright package, like lifting a cup.
Top-down/claw-machine grasping is the wrong task even if the object rises.

The active data path is the relative EEF trajectory dataset family, currently
`umi_real_relative_20260911_v10`: observation horizon 2 at 10 Hz and an 8-step
future relative SE(3)+gap target. Robot-base registration and IK are applied at
execution/validation time, not baked into the training label. A learned policy
has been run in narrow local simulation, but useful image-conditioned grasping
and hardware success are **not** established; see §0.0.

### 0.2 Last work completed in the current task

On 2026-09-16 the hardware simulation package's fitted finger contact patches
were adopted as a stricter diagnostic reference. The current TCP, finger axes,
gap range and eight primary ver1 meshes already matched the package, so the full
URDF was not substituted. The old `(5,12,40)mm` local half-size boxes were
replaced by `(3,7.7,13)mm`, which represents the package's `(3,13,7.7)mm`
finger-body patch, and friction was changed to `(1.2,0.01,0.001)`. This is still
not measured collision geometry.

The fitted-patch recorded-oracle results are:

- challenge three: `3/3` stable bilateral lift, although `150833` still has a
  post-lift final-tail IK error of 5.81mm versus the 5mm gate;
- fixed candidate 3 over the 14 validation episodes: side axes `13/14`, table
  margin `10/14`, contact IK `10/14`, fitted-pad centring and full static scene
  `4/14`;
- the four static-valid episodes all dynamically succeeded with no tip or
  displacement, but the overall gate is `4/14 = 28.6%`, therefore rejected.

The dominant next problem is no longer a 90-degree gripper-orientation error.
An offset sweep and a new Cartesian start-pose search have now shown that a
single approach offset or a larger random candidate count does not solve the
14-episode gate. The best result remains the existing start pose at `3/14`
static scenes with `-20mm`; the best new Cartesian candidate reached `2/14`.
The inferred contact-object X positions span `0.372--0.658m` (28.6cm). A probe
of `-0.12/-0.16/-0.20m` inward start-TCP translations found no additional
side-frame IK solutions, so blind expansion of the translation range was
stopped.
Do not enlarge the patch or relax the 8mm gate.

The per-episode common-start workspace audit is now implemented. It records
named scene failures and contact displacement for every episode. On the same
14 episodes, start-to-contact displacement spans X `286.2mm`, Y `286.7mm`, Z
`15.4mm`; grasp-height absolute error is median `3.32mm`, p95 `7.91mm`. A
common XY table-placement interval exists, but four episodes lack contact IK
and ten miss fitted-pad centring, so a table translation alone does not justify
a common 5-DoF registration.

The 8mm first-contact diagnostic has now been decomposed rather than relaxed.
The old full-XY norm incorrectly included the expected object half-depth at
first side-surface contact. The code now records signed approach depth and the
planar component perpendicular to the approach axis, and applies the unchanged
8mm tolerance to that lateral component. Recomputing from the existing JSON
gives lateral pass `9/10` detected contacts and predicts `3/14` full static
passes (`150926`, `150943`, `150948`), pending a complete MuJoCo rerun.

That rerun is now complete. With the unchanged 8mm lateral gate and an explicit
80% minimum static-scene fraction, the source registration passes `3/14 =
21.4%` and is correctly reported as `REJECTED_NO_VALID_REGISTRATION`. The
authoritative report is
`AI/out/relative_chunk_v9_workspace_audit_lateral8mm_gate80_offset_minus20mm_20260916.json`
(SHA-256 `73676d8bce873e624001d4c4d656d76520e17a7951ed69ffb8ef5cf68cbf8bda`).

The three passing inferred object positions are not an independent object-pose
measurement: they are derived from the near-close gripper pose. Therefore they
must not be promoted into a physical canonical workspace or collection range.
The current raw data has no table fiducial, independent object pose, or measured
robot-base extrinsic, so a unique physical `T_base_world` is not identifiable.

The required simulation-only canonical replay is now implemented and measured.
It translates every recorded TCP pose and the inferred object by the same
episode-specific vector, re-solves IK for every pose, forbids candidate search
and dynamic rollout, and can never emit a physical-registration status. Two
fixed object centres were tested across the same 14 episodes:

- `(0.30, 0.00, 0.03835)m`: anchor and contact IK `0%`, static scene `0/14`;
- `(0.48, 0.00, 0.03835)m`: anchor IK `40.7%`, contact IK `9/14`, fitted-pad
  centring `4/14`, static scene `0/14`.

Both are `REJECTED_DIAGNOSTIC_CANONICAL_REPLAY`. The reports are
`AI/out/relative_chunk_v9_canonical_replay_x030_20260916.json` (SHA-256
`4bfb496088455686f5cfb8f357b12929e45898ed243bca1ebc30b55c7f323ae4`) and
`AI/out/relative_chunk_v9_canonical_replay_x048_20260916.json` (SHA-256
`858295161bbe9abca174ed47543cee430e48fa16d20b7cccad2396fef507c498`).
This separates the remaining blocker from object-only placement: the selected
14-episode relative-motion support is too broad for one current ver1 5-DoF
side-grasp workspace, and pad centring still fails. It does not identify
physical `T_base_world` because independent object/base measurements remain
absent.

The current ver1 horizontal side-grasp workspace has also been remeasured; the
old top-down/pre-ver1 `15x35cm` map is not reused. A 50mm coarse scan found only
three accepted cells. A 25mm refinement found 18/81 cells and the largest
continuous static rectangle `x=[0.375,0.500]m`, `y=[-0.025,0.025]m` (about
`12.5x5cm`). This requires IK, no table/self collision, fitted-pad vertical
centring, initial camera visibility and table margin, but does not claim
bilateral contact or lift. The report is
`AI/out/ver1_side_workspace_refine_25mm_v2_20260916.json` (SHA-256
`fac0d016a00d84a9663e0d7ab161a560e1870fa470959aeb583cda914a72e323`).
Only 2/14 inferred validation object centres lie in that static rectangle;
because those centres are derived from the gripper pose, this is diagnosis,
not a canonical episode filter or physical placement measurement.

The hardware handoff's predeclared `can_side` simulation envelope was also
captured explicitly as simulation-only evidence: x `[0.385,0.415]m`, y
`[-0.035,0.035]m`. With the current ver1 canonical side pose, 104/105 cells on
a 5mm grid pass the static IK/collision/pad/camera/table checks. The camera part
uses the provisional MuJoCo wrist camera because ver1 has no measured optical
extrinsic. This does not calibrate the real snack-case task because the source
is a YCB tomato-can scene.

At the envelope centre `(0.40,0,0.03835)m`, three fixed-condition 14-episode
replays now separate the blockers. Raw recorded rotation plus XY placement gives
side-frame `0/14` and full scene `0/14`. Explicitly projecting every pose to the
fixed SO-101 side frame gives side-frame `14/14` but scene `0/14`. Additionally
aligning the recorded near-close contact to the object body centre in XYZ gives
side-frame, pad centring and contact IK `14/14`, but first contact only `7/14`
and full scene `3/14`. Every condition is marked diagnostic and rejected; no
dataset or training labels were rewritten.

The remaining first-contact split is strongly associated with recorded gap.
On 2026-09-16 the user physically measured the snack-case body width along the
gripper closing axis as `40mm` (measurement tool/repetitions were not recorded),
so the old provisional 39mm width was replaced. Replaying the unchanged 14
episodes at 40mm improved detected first contact only from `7/14` to `8/14`;
the full static scene gate remained `3/14`. Detected target gaps span
`34.84--39.81mm`; missed episodes span `40.26--41.21mm`. Therefore object width
was only a partial blocker and must not be tuned further to the simulation
result. The remaining physical gate is commanded gap versus actual inner-pad
separation on SO-101/ver1. Dynamic lift and learned-policy rollout remain
blocked until that contract or an explicitly diagnostic execution-time preload
is validated without rewriting the source labels.

That simulation-only preload comparison is now complete on the same fixed 14
episodes, canonical object, side-frame projection and 40mm physical width.
Recorded gaps stayed unchanged and reports store source/executed gaps
separately. First swept contact improved from `8/14` at 0mm to `12/14` at 1mm
and `14/14` at 2mm. The full static scene gate nevertheless stayed `3/14` in
all three conditions because ten episodes still fail the common-start IK and
collision checks (one also misses initial camera visibility). Thus 1mm is the
smallest diagnostic preload meeting the predeclared `>=12/14` contact gate;
2mm establishes that the remaining static failures are not a gap/contact
problem. Neither value is a real SO-101 calibration. The next blocker is to
define a common reachable approach segment/start condition without per-episode
registration or source-label rewriting, then rerun the policy-free dynamic
lift gate before any learned checkpoint rollout.

A fixed contact-relative trimming diagnostic is now implemented. It never
searches for a successful/reachable row per episode: every episode begins at
exactly `contact_row - K`, clamped only at row zero, while all source arrays stay
unchanged. With 1mm preload, K=4/8/12 rows produced static scene fractions
`7/14`, `3/14`, and `3/14`. A follow-up boundary isolation at 2mm preload gave:

- K=0 (contact pose only, not a valid approach task): scene `14/14`, geometry
  `22/27`, contact `14/14`;
- K=2 (0.2s at 10Hz; minimum segment retaining two-frame observation history):
  scene `14/14`, geometry `22/27`, contact `14/14`;
- K=4: scene `8/14`, geometry `16/27`, contact `14/14`, with all six failures
  caused by start IK/collision.

K=2 is therefore the only meaningful static-gate survivor, but remains a
simulation-only canonical replay with an uncalibrated 2mm preload. K=0 is a
diagnostic lower bound and must not be cited as task success. The next action is
to replay from each report's fixed K=2 row and corresponding statically solved
arm state. That policy-free dynamic gate has now been run on all 14 static-valid
episodes. The first report was `11/14`, but visual and trace inspection showed
that the scorer incorrectly counted deliberate horizontal transport **after the
object was lifted** as a tabletop shove. The threshold was not relaxed: the same
19.5mm limit is now applied only while lift is at most 5mm, and total XY travel
remains separately recorded. Unit coverage explicitly checks this distinction.

The corrected result is:

```text
report: AI/out/relative_chunk_v9_width40mm_preload2mm_lookback2_dynamic_gate14_preliftfix_20260916.json
status: PASS_LOCAL_DYNAMIC_ORACLE_GATE
episodes/contact/stable lift: 14/14, 14/14, 14/14
final lift range: 50.8--112.0mm
max pre-lift XY displacement: 17.0mm (< 19.5mm gate)
IK reject / tip / runner error: 0 / 0 / 0
```

The dataset was then regenerated from the preserved 70 normalized canonical
bundles as `umi_real_relative_20260911_v10`.  It has the same 1,248 image,
proprio, action, action-timestamp and source-row values as v9, but correctly
uses schema `umi_relative_chunk/0.2.0-provisional` and stores both history
timestamps as `(N,2)`.  Verification reports zero contract problems.  Archive
SHA-256 is
`c45e2dedf91ec6b067adcf36ab1e3079e93fa78208d52211b4c3201d2625a420`.

Seed-0 diagnostic BC training ran on V100; no checkpoint inference ran on the
shared server.  The 30-epoch best-val checkpoint has SHA-256
`5bc8a25de054bc29967ebe387d808d5add0e7ad8f791c60e40b20f3440e6adbb`
(epoch 27, val `0.39769`).  Local same-checkpoint image replacement materially
changes translation, rotation and gap predictions.

The learned-policy rollout tool now permits the exact predeclared K=2 approach
start used by the oracle gate and can perturb only inference RGB with
`noise`, `blackout` or `freeze`.  The simulator/scorer state is not perturbed.
On the fixed 14 validation episodes, best-val + simulation-camera closed loop
gave normal `14/14`, blackout `0/14`, per-tick Gaussian noise `14/14`, and
first-observation freeze `14/14` stable side-grasp lifts.  All successful
conditions had bilateral contact in `14/14` and zero geometry errors; blackout
had bilateral contact in only `1/14` and geometry errors in `14/14`.  Median
maximum lift was 76.8mm normal, 2.0mm blackout, 52.6mm noise, and 74.9mm freeze.
The 14/14 Wilson 95% interval is `[78.5%,100%]`; blackout is `[0%,21.5%]`.

This result does **not** establish correct visual localisation or use of live
visual feedback.  Freeze matching normal shows that the K=2 near-contact
diagnostic can be solved without time-varying RGB, and noise succeeding shows
that blackout alone is an unsafe proxy for general image removal.  The result
remains single-seed, near-contact K=2, one-step validation/execution,
simulation-only 2mm preload, an unmeasured proxy and retimed execution.  Seeds
1/2, a farther common start or object-position perturbation, and 8-step/8-step
execution remain pending.

A bounded object-only perturbation diagnostic was added without shifting the
registered robot start.  On representative episode `150943`, all
normal/noise/freeze conditions succeeded at +/-15mm X/Y.  At +/-25mm, 10/12
conditions succeeded; normal and freeze failed only at +25mm X while noise
succeeded.  This was not expanded to all episodes because K=2 near-contact
capture and pushing make it an invalid visual-localisation proof, and Gaussian
noise can move this OOD policy toward a conservative mean output.

The older K=4 report lacked fixed start joint/gap values, so its 8/8 runner
errors were tooling errors and are not performance evidence.  Re-running the
same K=4 registration search with current code produced
`AI/out/relative_chunk_v9_width40mm_preload2mm_lookback4_fixedstart_20260916.json`.
Only 8/14 episodes are static-scene valid, so the source remains
`REJECTED_DIAGNOSTIC_CANONICAL_REPLAY`.  The explicit eight-episode subset
passed the policy-free dynamic oracle gate 8/8; the gate now requires both an
explicit episode list and `--allow-rejected-diagnostic-subset` and records that
the source registration was rejected.

On that same K=4 subset, learned-policy normal and freeze both succeeded 5/8
and failed the same three episodes (`150753`, `150943`, `151043`) at the first
predicted command due to joint-limit violations.  Noise succeeded 8/8.  This
does not show that noise improves perception.  The leading explanation is a
real-JPEG-to-MuJoCo RGB domain mismatch: normal simulated RGB produces
aggressive OOD targets while noise pushes the policy toward conservative mean
outputs.  Blackout is an information-removal control and must not be tuned to
succeed.  The next priority is camera/render domain alignment or simulation
pretraining/domain randomisation followed by real-data fine-tuning, then the
same ablation suite.

Pure black was then retained unchanged and complemented with a less extreme
`mean_fill` control: each history frame keeps its own RGB channel means but all
spatial information is removed.  On the K=4 subset it succeeded 7/8 with one
geometry error (`150943`), versus normal/freeze 5/8, noise 8/8 and same-K4
blackout 0/8 with eight geometry errors.  Therefore the current near-contact
rollout can mostly succeed without object shape or position pixels, and
blackout failure is dominated by a black-specific OOD response.  Do not report
mean-fill or noise success as policy performance.
Future camera/domain work must rerun
normal/blackout/mean_fill/noise/freeze under identical conditions.

This closes the **local policy-free simulation geometry/contact gate only**. It
does not validate the 2mm execution preload on hardware, establish a deployable
world-to-base registration, validate a learned checkpoint, or approve motor
commands. The three inspection GIFs that motivated the scorer correction are
`rec_20260911_145625`, `145657`, and `145951` with suffix
`_lookback2_preload2mm_failure_20260916.gif`; all retain bilateral contact and
lift while carrying the object laterally.

The previous MuJoCo diagnostic falsely reported `3/3` because the scene still
rendered the legacy SO-101 rotary jaw while two separate, effectively invisible
parallel-jaw collision boxes lifted the object. The user caught this by watching
the video. That old `3/3` interpretation is withdrawn.

The current uncommitted implementation now:

- imports the actual ver1 hand/finger/holder OBJ meshes from the supplied
  `handoff.zip` into `AI/sim/mujoco/assets/ver1/`;
- hides the legacy `wrist_roll_follower_so101_v1` and
  `moving_jaw_so101_v1` visual/collision geometry when the ver1 overlay is used;
- attaches each provisional collision pad to the **same moving body** as its
  visible left/right ver1 finger, so visual motion and contact cannot diverge;
- synchronises the two finger bodies from the measured gripper gap curve;
- rejects old collision-proxy payloads that do not also declare the matching
  visible ver1 model (fail closed);
- checks that the pad inner contact plane is the same `custom_hand X=0` plane
  as the supplied finger meshes and that the pad covers the finger contact strip;
- records visible-gripper/collision-alignment metadata in rollout reports;
- corrects the measurement document to retract the misleading old result.

One policy-free recorded-oracle replay was rerendered after the correction:

```text
episode: rec_20260911_150833
registration: candidate_003 from the -30mm/refine8 search
mode: recorded target replay; no learned checkpoint
video: AI/out/ver1_visible_parallel_candidate3_150833_oracle.gif
montage: AI/out/ver1_visible_parallel_candidate3_150833_montage.png
report: AI/out/ver1_visible_parallel_candidate3_150833_oracle.json
```

Visual inspection now shows the real ver1 parallel fingers around the two sides
of the upright red proxy object, rather than the legacy vertical rotary jaw. The
report still uses a **provisional**, non-measured pad size/friction model; this
single replay is only a visual/collision alignment check, not physical validation
and not policy performance.

Verification completed after the changes:

```text
AI/tools/check_ver1_collision_proxy.py: 8/8 PASS
AI/tools/check_relative_chunk_registration_search.py: 18/18 PASS
AI/tools/check_ver1_side_workspace.py: 2/2 PASS
AI/tools/run_umi_regression.py: 27/27 scripts PASS
git diff --check: PASS (line-ending warnings only)
MuJoCo: 3.12.0, local Windows CPU diagnostic
```

### 0.3 Historical Git snapshot (2026-09-16; use current `git status` for staging)

```text
branch: umi
HEAD/origin/umi: 413a96fc4b725ef5aac45e29d3fbae6865bb3493
subject: fix: UMI 상대 청크 timestamp와 gap 계약 정합화
staged files: none
tracked modifications: 11
intentional new source: AI/tools/check_ver1_collision_proxy.py and
                        AI/sim/mujoco/assets/ver1/*.obj (8 files)
AGENTS.md / HANDOFF.md: untracked automatic-continuation instructions
```

Tracked modifications and their purpose:

- `AI/configs/real/so101_ver1_phone_holder_kinematic.json`: handoff-derived
  ver1 visual model declaration, fixed transform, mesh/body mapping and required
  visual/collision alignment.
- `AI/sim/mujoco/build_scene.py`: earlier provisional bilateral proxy work plus
  current actual ver1 visual import, legacy-jaw hiding, same-body pad attachment,
  gap synchronisation.
- `AI/sim/mujoco/env.py`: per-pad/bilateral contact reporting and stable grasp
  semantics.
- `AI/tools/umi_mujoco.py`: validates and applies the kinematic/collision/visual
  overlay; old invisible-proxy payloads are rejected.
- `AI/tools/render_relative_chunk_rollout.py`: recorded-oracle diagnostics,
  displacement/tip/bilateral-contact reporting, visible-model metadata.
- `AI/tools/gate_relative_chunk_oracle.py`: multi-episode gate, failure
  classification and Windows child-output absolute-path fix.
- `AI/tools/search_relative_chunk_registration.py`: resumable candidate progress,
  dynamic finalists, side-grasp-first ranking, and source-rotation-preserving
  Cartesian start-TCP translation candidates.
- `AI/tools/check_relative_chunk_oracle_gate.py` and
  `AI/tools/check_relative_chunk_registration_search.py`: regression coverage.
- `AI/tools/run_umi_regression.py`: includes the new proxy/visual test.
- `AI/docs/MEASURE_umi_relative_chunk_v6_0914.md`: candidate-search history and
  explicit retraction/correction of the false `3/3` interpretation.

New intentional source:

- `AI/tools/check_ver1_collision_proxy.py`: verifies legacy geometry is inactive,
  metric gap tracking, per-pad contacts, shared visible/collision bodies and OBJ
  contact-plane alignment.
- `AI/sim/mujoco/assets/ver1/{adapter,cover_bottom,cover_top,drive_motor,
  finger_left,finger_right,holder_left,holder_right}.obj`: exact assets extracted
  from user-supplied `C:\Users\SSAFY\Downloads\handoff.zip`. The archive was
  previously audited and is explicitly not a validated dynamics package.

There are many unrelated untracked caches, generated files, FE artifacts and
study documents. **Do not use `git add .` and do not delete them.** In particular,
exclude `.local-umi-deps/`, `.pnpm-store/`, `.server-worktree/`, `.tmp*`, `tmp/`,
`FE/.vite/`, `FE/tsconfig.tsbuildinfo`, large Docs media/backups and unrelated
AI study/config files unless the user separately asks to commit them.

### 0.4 Historical P0 (2026-09-16; superseded by §0.0)

1. Review this handoff, `git status`, and the current diff; do not recreate the
   ver1 fix.
2. Treat the new dynamic `14/14` only as a local simulation gate. Preserve the
   fitted tip patch, horizontal side/body axes, fixed K=2 start and source labels;
   do not run more blind registration candidates or tune the 19.5mm threshold.
3. Decide the next evaluation path explicitly: either (a) obtain the independent
   object/table/base reference and measured SO-101 gap-to-inner-pad calibration
   needed for physical transfer, or (b) run a clearly labelled local learned
   policy sanity check. Path (b) may test software integration but cannot claim
   physical registration or deployment readiness, and learned inference/rollout
   must remain off the shared GPU server.
4. For learned-policy evidence, use one unchanged checkpoint and compare normal
   rollout against inference-time noise/blackout/freeze; do not substitute the
   previously misdescribed training-time image corruption experiment.
5. Continue the teammate's official-UMI conversion from the normalized raw
   stream in parallel. Do not reinterpret the v9 6D chunks as raw axis-angle.
6. Commit only the related source/tests/config/handoff update after review. Keep
   generated `AI/out` artifacts out unless explicitly requested.

Useful local test command on this machine:

```powershell
$env:PYTHONPATH='C:\Users\SSAFY\Desktop\S15P21A103\AI'
& 'C:\Users\SSAFY\Desktop\S15P21A103\.uv-cache\archive-v0\jke7XrWxJrWRWqpi\Scripts\python.exe' AI/tools/check_ver1_collision_proxy.py
& 'C:\Users\SSAFY\Desktop\S15P21A103\.uv-cache\archive-v0\jke7XrWxJrWRWqpi\Scripts\python.exe' AI/tools/run_umi_regression.py
```

### 0.5 Side-chat and teammate context captured

The following context was read from the Codex sidebar tasks and is included so
an account switch does not lose it:

- In the side task `내용 확인`, the latest `origin/ai` was separated from Kim
  Juntai's active `origin/test/ai/S15P21A103-27-gripper-crosscheck` branch. As of
  this handoff, `origin/ai` ends at `d465bb040` (2026-09-12 hand-eye measurement)
  and the test branch at `b2fff07ce` (2026-09-14 lead-k target generator).
- That teammate diagnosed the legacy joint single-step target
  `action[t]=state[t+1]` as a rollout shortcut/fixed-point failure. Their reported
  sim comparison was 0/300 for that target and 22/300 for actual `ctrl`; these are
  diagnostics, not project success claims. Their lead-k branch explores whether
  farther future state targets can salvage old data.
- The teammate corrected their later image-ablation description. The reported
  success gap used **noise images during training and normal images during
  evaluation**, not a same-checkpoint inference-time image perturbation. It only
  supports the narrow claim that the task could not be learned from destroyed
  training images. It does not yet prove that a normally trained checkpoint
  consults images at inference time. Those older results also used terminal
  clamping and must be labelled `clamp on`.
- The agreed clean inference-time test keeps the checkpoint fixed and applies
  `noise`, `blackout`, or first-frame `freeze` only during rollout. It must run
  locally rather than on the shared GPU server. The v6/v9 contract uses `H=2`,
  `K=8`, requires `L >= H + K`, and forbids terminal clamp/padding. Pairing
  residuals use the existing `+-10ms` gate; future target horizons are excluded
  from that pairing test.
- Contract direction agreed in discussion: do not invent absent UMI controller
  commands. Real robot/sim data may carry `command`; handheld UMI should keep a
  separately named `relative_target_trajectory`. The current v6/v9 target is EEF
  relative SE(3)+gap `(8,10)`, not legacy joint action `(6,)`.
- The teammate asked to specify 6D rotation row/column convention and
  orthonormalisation, observation timestamps `(N,2)`, and move max gap `0.09m`
  to config. Commit `413a96fc4` is the current UMI-side timestamp/gap contract
  alignment result; verify its exact implementation before further contract edits.
- `sim_pick_v5` is not independent of `sim_pick_v3` (content identical despite
  archive metadata differences). Do not count both as independent evidence.
- The user also owns substantial FE work and expects presentation/PPT work next
  week. The side task `업무 우선순위 요약` records that AI validation remains
  important, but delaying all FE work does not remove that responsibility. Avoid
  unnecessary reruns or blocking the user's machine when a short targeted check
  answers the question.

Latest runtime rule from the main conversation supersedes older text below:

- Shared GPU server: training is allowed; policy-free MuJoCo may be run only if
  team/server policy permits it.
- **Learned-checkpoint inference, image probe, policy preflight and rollout must
  not run on the shared GPU server.** Run those locally. Do not infer that a
  recorded-oracle replay is learned-policy inference.

### 0.6 Hard boundaries for the next Codex

- The supplied handoff explicitly says custom-part inertia, measured collision
  geometry, collision-safe limits and camera optical extrinsic are not validated.
- The older pad proxy half-size `(5,12,40)mm` was found to cover most of the
  visible finger and could over-report contact. It is being replaced by the
  hardware package's fitted tip patch `(3,13,7.7)mm` in the hand/finger body
  frame (represented as `(3,7.7,13)mm` in the local rotated pad frame), with
  friction `(1.2,0.01,0.001)`. The package provides no physical measurement
  provenance, so this remains a provisional diagnostic proxy. All dynamic
  oracle results obtained with the larger boxes must be rerun and not cited.
- `rec_20260911_150833` currently has a late final-tail IK rejection around
  5.81mm versus a 5mm budget even after stable lift; a pick-only diagnostic must
  not be described as full 8-step executability.
- Do not train again merely because the gripper visual changed; first finish the
  policy-free recorded-oracle geometry/contact gate.
- Do not modify or commit unrelated user files. Do not use destructive Git
  commands.

The remaining numbered sections were written earlier and contain useful broad
architecture/setup context, but some phase descriptions and GPU wording are
historical. When they conflict with §0 or the repository, §0/repository wins.

## 1. Project Overview

S.S.A.F.Y. (Show Simply Arm Follows You)는 사용자가 UMI(Universal Manipulation Interface) 형태의 핸드헬드 그리퍼로 보여 준 동작을 수집하고, 이를 학습 가능한 데이터로 변환하여 SO-101 로봇팔에서 재현하는 플랫폼이다. 웹에서는 데이터·학습·작업 조합·배포 흐름을 제공하고, AI는 시연 영상/pose/gripper 데이터를 공용 계약으로 변환하고 정책을 학습·검증하며, ROS2/하드웨어 계층은 최종 관절 명령을 안전하게 실행하는 것이 목표다.

현재 단계는 **실물 UMI 파일럿 데이터의 수집 형식 호환, 마커 기반 gripper gap 후처리, 카메라→파지점 변환, SO-101 IK/물리 재생을 검증하는 단계**다. 시뮬레이션·공용 계약·UMI 변환 및 명령 어댑터의 상당 부분은 구현되어 있으나, 이번 실물 데이터로 학습을 시작할 수 있는 최종 승인과 실물 로봇 실행은 아직 완료되지 않았다. 현재 브랜치의 FE는 Mock 중심이고 BE는 문서와 Spring Boot 골격 수준이며, HW 디렉터리에는 실제 motor control 소스가 없다.

## 2. Current Architecture

현재 실제 코드 흐름은 다음과 같다.

```text
Galaxy S22 + UMI gripper
  ├─ frames/*.jpg
  ├─ ARCore camera pose (poses.csv)
  └─ magenta marker images
          │
          ├─ AI/tools/gripper_gap.py
          │    └─ frame_index, gap_m, status(D/M/T/X)
          │
          └─ AI/track_a/convert/arpose_delivery.py
               └─ delivered arpose.episode/1 → canonical umi_raw/0.1.0 ZIP
                         │
                         └─ AI/track_a/convert/arcore_bundle.py
                              └─ decoded RawEpisode (robot-frame intermediate)
                                        │
                                        └─ AI/umi/convert.py
                                             └─ contract.Episode 0.3.0-provisional
                                                  cam_wrist + J1~J5 + gripper gap
                                                        │
                                                        ├─ AI/policy/* (BC 학습; 아직 이번 실물 데이터로 미실행)
                                                        └─ UMI action decode / IK / runtime
                                                             ├─ AI/umi/action_decode.py
                                                             ├─ AI/umi/action_ik.py
                                                             ├─ AI/umi/chunk_runtime.py
                                                             ├─ AI/umi/arm_preflight.py
                                                             └─ AI/umi/ros_adapter.py
                                                                  │
                                                                  └─ 상위 ROS2 (이 저장소에는 배포 노드 없음)
                                                                       └─ ros2_control → motor driver → SO-101
```

실물 데이터의 pose는 ARCore/OpenGL 카메라 규약 그대로 저장된다. 로컬 카메라 축은 `+X 오른쪽, +Y 위, -Z 앞`, quaternion은 `xyzw`, pose는 `T_world_camera`다. AI의 임시 카메라→pinch 변환은 OpenCV 카메라 규약으로 받은 값을 ARCore 규약에 맞게 변환하여 `AI/configs/real/umi_s22_pinch_provisional.json`에 기록했다.

배포 경계는 다음과 같이 합의되어 있다.

- AI: 체크포인트 추론 → 상대 EEF pose 복원 → 기존 MuJoCo 기반 `solve_pose_ik` 계열을 사용하여 J1~J5 `[rad]` + gripper 전체 gap `[m]` 생성.
- ROS2: AI 출력의 메시지 포장/발행, 30 Hz 타이머, watchdog, tracking error, E-stop, trajectory/action 제어.
- motor driver: radian↔encoder tick 변환, 패킷/통신 및 하드 범위 검사, 6축 Sync Write.
- AI가 모터를 직접 제어하지 않는다. `AI/umi/ros_adapter.py`도 ROS 메시지 객체 자체가 아니라 ROS 계층이 포장할 순수 데이터 구조를 제공하는 방향이다.

FE는 `services/*`의 비동기 Mock을 통해 `hooks/useAppStore.tsx`와 `app/page.tsx`를 구동한다. 실제 BE/GPU/Jetson 연결은 아직 없다. BE의 `BE/EC2_`는 Spring Boot 시작 클래스와 계약/API/DB 문서가 중심이며 실제 도메인 API 구현은 확인되지 않았다.

## 3. Directory Structure

```text
S15P21A103/
├─ README.md                       # AI 트랙 중심의 루트 설명; 현 전체 제품 상태와 일부 불일치
├─ HANDOFF.md                      # 이 인수인계 문서
├─ AI/
│  ├─ README.md                    # 시뮬/정책 측정·실행 설명(일부 과거 수치)
│  ├─ contract/
│  │  ├─ episode.py                # 학습 계약 0.3.0-provisional, 검증/저장/읽기
│  │  ├─ ids.py                    # 공용 skill 식별자
│  │  └─ skills.py                 # skill registry 및 배포 gate
│  ├─ umi/
│  │  ├─ raw.py                    # 디코딩된 UMI 중간 표현/검증
│  │  ├─ convert.py                # RawEpisode → contract.Episode
│  │  ├─ camera_frames.py          # ARCore/OpenCV 축·강체변환 처리 (미커밋)
│  │  ├─ gripper_gap.py            # 마젠타 마커→gap 계산 핵심 (미커밋)
│  │  ├─ action_decode.py           # UMI 정책 action_pred 의미 복원
│  │  ├─ action_ik.py               # 상대 EEF pose → SO-101 IK 명령
│  │  ├─ chunk_runtime.py           # N-step/horizon 순수 파이썬 런타임
│  │  ├─ arm_preflight.py           # 최종 관절 명령 경계 검사
│  │  ├─ policy_preflight.py        # 체크포인트/인터페이스 사전 검사
│  │  ├─ real_fk.py                 # 실물 URDF 기반 FK
│  │  ├─ ros_adapter.py             # joint_states/명령 경계용 순수 데이터 adapter
│  │  ├─ trajectory.py              # step/속도/가속도 검사
│  │  └─ provenance.py              # raw/tool SHA 출처 추적
│  ├─ track_a/
│  │  ├─ HANDOVER_track_a.md        # 원래 UMI 인수인계(과거 상태 포함)
│  │  ├─ README*.md                 # UMI 온보딩/학습용 문서(미커밋)
│  │  └─ convert/
│  │     ├─ arcore_bundle.py         # canonical UMI ZIP 소비/검증
│  │     ├─ arcore.py                # 공개 to_raw wrapper
│  │     └─ arpose_delivery.py       # 실제 전달 ZIP 호환 adapter (미커밋)
│  ├─ sim/
│  │  ├─ base.py                    # simulator-independent RobotEnv 표면
│  │  └─ mujoco/                    # SO-101 씬, IK/FK, 그리퍼, 환경, 렌더링
│  ├─ policy/                       # baseline, BC 모델/학습
│  ├─ eval/                         # rollout, transfer, determinism, 진단/기록
│  ├─ data/                         # 시뮬 수집, dataset 읽기, 검증
│  ├─ registry/skills/              # skill JSON registry
│  ├─ configs/
│  │  ├─ so101.yaml                 # 하드웨어/시뮬 단일 설정 출처
│  │  ├─ train/bc.yaml              # BC 학습 설정
│  │  └─ real/                      # 실물 URDF, FK, 임시 extrinsic/측정 기록
│  ├─ tools/                        # CLI, 회귀검사, 실물 진단/재생 스크립트
│  ├─ docs/                         # 결정, 측정, 한계, 인수인계, 질문 기록
│  ├─ requirements-sim.txt
│  ├─ requirements-train.txt
│  ├─ runtime_limits.py             # GPU/CPU thread/EGL/중복 실행 규칙
│  ├─ EXP_LOG.jsonl                 # 실험 로그
│  ├─ LIMITS.md                     # 미검증 항목
│  └─ out/                          # 로컬 산출물; 현재 실물 변환/재생 결과 존재, 미추적
├─ FE/
│  ├─ app/page.tsx                  # 대부분의 화면/상호작용이 한 파일에 있음
│  ├─ app/globals.css               # 디자인/반응형 스타일
│  ├─ hooks/useAppStore.tsx          # Mock 앱 상태/학습 draft
│  ├─ services/                     # 현재 Mock 비동기 서비스
│  ├─ mocks/, types/                # Mock 데이터와 타입
│  ├─ package.json, pnpm-lock.yaml
│  ├─ DESIGN_SPEC.md, TEAM_SETUP.md
│  └─ README.md
├─ BE/
│  ├─ EC2_/                         # Gradle/Spring Boot 프로젝트 골격
│  └─ docs/                         # API, DB, 인터페이스 계약 문서
├─ HW/
│  └─ README.md                     # 현재 빈 파일; motor_control 구현은 저장소에 없음
└─ Docs/                            # 팀원별 문서/영상; 일부 큰 로컬 산출물 미추적
```

`.pnpm-store`, `.tmp-umi-deps`, `tmp/`, `FE/.vite`, `FE/.next`, `*.tsbuildinfo`, `AI/out`은 소스가 아니라 로컬 캐시/빌드/검증 산출물로 취급해야 한다.

## 4. Tech Stack

- Language: Python, TypeScript/JavaScript, Java, Bash, YAML/XML/JSON.
- AI/ML: PyTorch(학습 요구사항에서 2.3 이상), NumPy, Behavior Cloning, UMI 상대 EEF pose decode.
- Robot/ROS2: SO-101 5-DoF + gripper, ros2_control 연동 계약. ROS2 노드 자체는 이 브랜치에 구현되어 있지 않다.
- Simulation: MuJoCo `3.12.0`, SO-101 MJCF/URDF, headless EGL 지원.
- Frontend: React `19.2.0`, TypeScript, Vinext `0.0.17`, Vite `7.2.4`, Tailwind Vite plugin, Cloudflare/Vinext 설정.
- Backend: Java 17, Spring Boot `3.5.5`, Gradle, Spring Data JPA, Validation, Security, WebSocket, OAuth2 client, MySQL connector, Lombok, SpringDoc OpenAPI `2.8.9`.
- Infrastructure (문서/설정 기준): MySQL 8 compose, EC2/S3/GPU/Jetson 간 API 계약. 실제 배포 연결 여부는 확인 필요.
- Image processing: Pillow/ImageIO 및 자체 HSV 마젠타 원 검출.
- Android/ARCore: 실제 앱 코드는 현재 체크아웃에 없지만 전달 데이터와 확인된 생산 코드 설명상 Galaxy S22 ARCore `getPose()` 및 Android sensor 로그 사용.

## 5. Environment & Setup

### AI local simulation/verification

- 저장소 기록상 검증 기준: Python 3.11, MuJoCo 3.12.0. 현재 Codex 번들 Python은 3.12이며 임시 `tmp/umi-local-deps`에 MuJoCo 3.12.0을 설치해 최근 검증했다.
- 설치:

```bash
cd AI
python -m pip install -r requirements-sim.txt
```

- 학습 의존성:

```bash
python -m pip install -r requirements-train.txt
```

- Linux headless rendering에서 `MUJOCO_GL=egl` 필요. 로컬 viewer에서는 EGL 강제를 제거할 수 있다.
- 주요 환경변수 이름만 기록: `CUDA_VISIBLE_DEVICES`, `AI_THREADS`, `OMP_NUM_THREADS`, `MKL_NUM_THREADS`, `OPENBLAS_NUM_THREADS`, `NUMEXPR_NUM_THREADS`, `VECLIB_MAXIMUM_THREADS`, `MUJOCO_GL`, `PYTHONUNBUFFERED`, `PYTHONPATH`.
- 비ASCII 경로에서 MuJoCo XML C++ 로더 문제가 있어 `AI/paths.py`가 ASCII-safe 경로를 처리한다.

### GPU server policy (latest conversation rule)

- GPU 서버 `device 2`에서 학습할 수 있다.
- 팀/서버 정책이 허용하면 학습 정책을 불러오지 않는 recorded-oracle MuJoCo도
  실행할 수 있으나 결과 환경을 반드시 기록한다.
- **학습 체크포인트 추론, image probe, policy preflight와 rollout은 GPU 서버에서
  실행하지 않는다.** 해당 검증은 로컬 PC에서 수행한다.
- IK/FK, action round-trip과 작은 MuJoCo 물리 진단은 기본적으로 로컬 PC에서 수행한다.
- 학습도 데이터 로딩 때문에 CPU를 사용하므로 CPU thread 수를 제한한다. 기본 `AI_THREADS=2`이며 `runtime_limits.py`를 NumPy/PyTorch보다 먼저 import하는 도구가 있다.
- 과거 `runtime_limits.py`에는 할당 가능 GPU가 여러 개 기록돼 있으나 최신 대화의
  device 2 및 추론 금지 규칙을 우선한다.

### Frontend

- Node.js 22.13 이상, pnpm 11 권장.

```bash
cd FE
pnpm install
pnpm dev
```

### Backend

- Java 17. `application.properties`는 MySQL localhost 설정을 포함한다. 비밀값은 이 문서에 기록하지 않았다.

```bash
cd BE/EC2_
docker compose up -d
./gradlew bootRun          # Windows: .\gradlew.bat bootRun
```

### Hardware dependencies

- SO-101, STS3215 servo IDs J1~J5=`1..5`, gripper=`6`, Jetson/serial bus, ROS2 controller manager가 필요하다.
- 별도 전달받은 최신 `motor_control`이 하드웨어 기준이지만 현재 저장소에는 포함되지 않는다. 새 Codex는 이 저장소만 보고 motor driver가 구현되었다고 가정하면 안 된다.

## 6. How to Run

### 가장 먼저 실행할 UMI 로컬 회귀검사

```bash
cd AI
python tools/run_umi_regression.py --list
python tools/run_umi_regression.py
```

현재 runner는 20개 검사 스크립트를 순서대로 실행한다. 공유 Jupyter 서버에서는 의도적으로 거부한다.

### 시뮬레이션 기본 검사

```bash
cd AI
python tools/check_angle_contract.py
python tools/check_interface.py
python tools/render_check.py
python tools/view.py
```

### 시뮬 데이터/BC 학습(학습은 GPU 서버 device 2에서만)

```bash
cd AI
python tools/collect_sim.py --episodes 100 --skill-id pick_place --out datasets/sim_pick_v2 --log
python tools/verify_dataset.py datasets/sim_pick_v2 --write-index --log
CUDA_VISIBLE_DEVICES=2 AI_THREADS=2 python tools/train_bc.py --data datasets/sim_pick_v2 --device cuda --log
```

정책 추론/rollout은 서버가 아니라 로컬에서 수행해야 한다.

### 실제 전달물 후처리 및 정규화

원본 전달 ZIP 경로, 후처리 출력 폴더, 새 출력 폴더를 지정한다. 원본 ZIP을 수정하지 않는다.

```bash
cd AI
python tools/gripper_gap.py --bundle <delivery.zip> --out <gripper-output-dir>
python tools/normalize_arpose_delivery.py \
  --delivery <delivery.zip> \
  --gripper-root <gripper-output-dir> \
  --out <normalized-output-dir> \
  --pre-stabilized
```

`--pre-stabilized`는 촬영자가 녹화 시작 전에 ARCore/물리 안정화를 완료했다는 명시적 근거가 있을 때만 사용한다. 이번 전달물은 촬영 담당에게 그렇게 확인했다.

### 선택 episode 진단 재생

`AI/tools/replay_real_umi_trajectory.py --help`를 먼저 확인한다. 현재 예시는 `rec_20260911_150920`과 임시 컵 프록시용이며 물체 3D 라벨이 없어 성공 판정용이 아니다.

```bash
cd AI
python tools/replay_real_umi_trajectory.py \
  --bundle out/real_20260911_normalized/rec_20260911_150920.zip \
  --quality out/real_20260911_normalized/rec_20260911_150920.quality.json \
  --extrinsic configs/real/umi_s22_pinch_provisional.json \
  --placement out/real_20260911_placement_study.json \
  --out out/real_20260911_replay/<new-name> \
  --max-arm-step-rad 0.05 --max-gripper-step-rad 0.05 --time-scale
```

### Frontend

```bash
cd FE
pnpm dev
pnpm lint
pnpm build
```

기본 주소는 `http://localhost:3000`. 현재 서비스는 Mock이다.

## 7. Implemented Features

### Committed at HEAD

- 공용 학습 계약 `0.3.0-provisional`: 단일 논리 카메라 `cam_wrist`, J1~J5 + 전체 gripper gap 6채널, `action[t] = state[t+1]`, 마지막 raw tick은 학습 행에서 제외. 구 계약 데이터는 진단용 읽기는 가능하지만 신규 학습 검증에서 거부한다. 관련: `AI/contract/episode.py`.
- UMI raw/intermediate 스키마, 변환, IK protocol 및 MuJoCo adapter.
- UMI 체크포인트 action decode: 상대 EEF position, rotation 6D, gripper width 의미 복원.
- 상대 pose를 현재 EEF pose에서 목표 pose로 복원하고 기존 SO-101 IK에 연결하는 adapter. 새 IK solver 자체를 만드는 작업은 아니다.
- J1~J5 최종 관절 한계 및 ROS 경계 검사, `/joint_states → J1~J5 → base_link 기준 gripper_tcp FK` adapter.
- SO-101 실물 기준: J3 하한 `-1.57079632679`, gripper 외부 의미 전체 gap `[m]`.
- N-step 순수 Python runtime과 체크포인트 preflight.
- MuJoCo SO-101 scene/IK/FK/gripper pads 및 기존 시뮬 수집·BC·rollout 계측.
- 데이터 provenance: raw path/SHA/tool SHA 및 번들 내부 파일별 SHA 기록 경로.
- 실물 데이터 전 preflight/회귀 경계 테스트가 Jira S15P21A103-128까지 merge됨.

### Current uncommitted implementation

- 실제 `arpose.episode/1` 다중 녹화 전달 ZIP을 canonical `umi_raw/0.1.0` episode ZIP들로 변환하는 adapter.
- 녹화 전 안정화가 완료된 경우 `warmup_s=0`을 허용하는 명시적 `pre_stabilized` 계약과 테스트. 문자열 등 비-boolean은 거부한다.
- 마젠타 원 2개 HSV 검출 및 15mm 원 지름 기반 frame별 거리 보정, marker 중심간 거리→gripper gap 선형 변환.
- 제공된 gripper 캘리브레이션:
  - marker 원 지름 15mm.
  - 닫힘 marker 중심 37.6mm ↔ 접촉면 gap 0mm.
  - 열림 marker 중심 134mm ↔ gap 70mm.
  - `gap_mm = (marker_gap_mm - 37.6) * 70 / (134 - 37.6)`, `[0,70]` clamp.
  - 30mm 접촉면 gap 단일 확인 이미지가 추가로 제공됐으나, 완전한 다점 실측 검증은 아니다.
- D/M/T/X 출력 계약. 현재 구현의 실제 전달물 결과는 D와 X만 발생했고 M/T는 0이다. D/M만 gap 값이 유효하며 T/X는 빈 값이어야 한다.
- ARCore/OpenGL↔OpenCV 카메라 축 변환과 임시 `T_camera→UMI_pinch`.
- 실물 71개 녹화 후처리/정규화, 상대 궤적 감사, 공통 배치 IK 수용률, position-only 진단, axis projection sweep.
- 학습 없이 실제 상대 궤적을 SO-101 MuJoCo 명령으로 재생하는 진단 도구. full-pose IK 실패 시 position-only fallback을 명시적으로 기록하며 이 결과는 절대 학습-ready로 간주하지 않는다.
- causal rate limiting과 원 경로를 보존하는 linear time scaling. `0.05 rad/tick`은 실물 확정 한계가 아니라 provisional simulation 값이다.
- 임시 물체 크기/파지 높이 override, 물체 등록, 충돌/접촉/들기 측정.
- 위 새 도구들이 `run_umi_regression.py`의 20개 검사 목록에 추가됨.

## 8. Current Work In Progress

직전 작업은 실제 UMI 데이터 `rec_20260911_150920`을 학습 없이 MuJoCo에서 최대한 재생하여 데이터→pose→IK→관절 명령→물리 경로가 이어지는지 확인하는 것이었다.

### 실제 전달물 처리 결과

- 원본: 사용자 Desktop의 `새 폴더 (2).zip` (저장소 밖). 이전 세션에서 기록한 SHA-256: `1493c...5021bf`(문맥에 축약값만 남아 있으므로 새 Codex가 사용 전 전체 SHA를 다시 계산할 것).
- 71개 녹화, 총 6,087 JPEG.
- 마커 gap 상태: D=5,759, X=328, M=0, T=0. 직접 검출률 약 94.6%.
- canonical 정규화는 70개 성공, `rec_20260911_145737` 1개 거부로 기록됐다. `AI/out/real_20260911_normalized`에 결과가 있으나 전체 summary 파일은 현재 없다.
- 각 녹화는 약 1.8~4.6초로 짧다. 촬영자가 녹화 전에 안정화를 완료했다고 확인했으므로 고정 3초 warmup을 적용하지 않고 `pre_stabilized=True`, `warmup_s=0`으로 처리하는 변경을 작성했다.

### IK/placement 진단

- 공통 start placement, stride 15: 469 frame 중 full pose 127개 수용 = 27.08%.
- position-only 5mm 이내: 445/469 = 94.88%.
- full-position 5mm 이내: 27.51%, full-axis 5도 이내: 33.26%.
- 결론: 주된 손실은 translation reach보다 SO-101 5-DoF와 UMI 접근축/자세의 호환성이다.
- position-only fallback의 접근축 손실 p50 25.9°, p95 88.2°, max 144.4°이므로 이를 학습 라벨로 사용하면 안 된다.
- 원래 axis를 start axis 방향으로 0/25/50/75/100% 단순 보간한 sweep도 수용률 약 27.3~28.0%로 개선되지 않았다.

### 실제 궤적 replay

- episode `rec_20260911_150920`, 변환 후 75 source frame.
- 일반 time scaling replay: 75→124 frame, 2.5→4.13초, max command step ≤ 약 0.05, tracking max 약 0.022rad, waypoint TCP position error p95 3.35mm.
- causal clipping은 tracking은 낮췄지만 TCP p95 약 65.8mm의 큰 lag를 만들어 기각. 원 경로를 보존하는 waypoint 삽입(time scaling)을 사용한다.
- 실제 close 프레임 이미지를 확인하니 대상은 1.5~2.5cm cube가 아니라 노란 컵이었다. 따라서 기존 20mm MuJoCo box와 gap≈40mm 불일치는 gripper calibration 오류 증거가 아니며 물체 모델 불일치였다.
- 닫힘 등록 기준을 `가장 큰 단일 감소`에서 `닫힘 transition 이후 최종 plateau 10% 이내에 처음 도달한 frame`으로 수정했다. 선택 episode의 anchor는 source index 41.
- 임시 컵 proxy box half-size `[0.02,0.02,0.05]m`, grasp height 80mm, x=0.25, y=0.10, yaw=90° replay:
  - time-scaled 117 frame, full-pose 52/75=69.3%, position-only fallback 23.
  - command position error p95 약 0.01mm, axis error p95 약 35.7°(fallback 포함).
  - table/self collision 0, object contact 0, pick failure.
- 낮은 50mm grasp height에서는 object contact 2 tick이 있었으나 table contact 78, 최종 lift 실패. 80/110mm는 table collision은 없어졌지만 물체 접촉이 없었다.
- **해석:** 파이프라인 motion plumbing은 확인됐으나, 영상에는 물체의 정확한 3D 위치/크기/파지 높이 라벨이 없으므로 임시 box로 pick success를 판정할 수 없다. 이번 실물 데이터로 학습을 시작하기 전 데이터 라벨/태스크 구성 확인이 필요하다.

### 직전 수정 파일

- `AI/tools/replay_real_umi_trajectory.py`: completed-close anchor, time scaling, provisional object size/height override, 물리 metric.
- `AI/tools/check_umi_trajectory_rate_limit.py`: limiter/time scaling/registration 단위검사.
- `AI/tools/search_real_umi_grasp_registration.py`: x/y/yaw 후보 collision search 및 오래된 2cm 오류 문구 수정.

최근 단위검사는 `check_umi_trajectory_rate_limit.py` PASS, 위 파일들의 `py_compile` PASS였다. 2026-09-12에 정상 PyYAML 6.0.3·MuJoCo 3.12.0 로컬 환경에서 전체 회귀검사도 **20/20 PASS**했다. 최초 실행의 2개 실패는 소스 결함이 아니라 권한 때문에 불완전한 `yaml` namespace package가 로드된 임시 의존성 환경 문제였고, 새 임시 환경을 설치한 동일 Windows 권한으로 실행해 해소했다.

## 9. Git State

- Branch: `umi`
- HEAD: `4a7865f94bf9ed0ca5c214a75b5b37c2f50bcb73`
- Remote tracking at inspection: `origin/umi` also `4a7865f9`.
- Staged files: 없음.
- 이 문서 작성 전부터 worktree가 매우 dirty였으며, **정리/삭제/커밋하지 않았다**.

### Recent important commits

- `4a7865f9` Merge S15P21A103-128 UMI real-data preflight.
- `d2738edd` UMI 파일럿 수용 경계 검증 보강.
- `993ee7a8` UMI 실데이터 파일럿 검증 보강.
- `8141f5e5` S15P21A103-153 UMI 데이터 계약 및 단일 카메라 확정.
- `bf1bc255` S15P21A103-31 ARCore raw 변환 검증 보강.
- `ea87010c` S15P21A103-155 실측 FK 및 ROS adapter 연결.
- `707af12a` S15P21A103-113 실행가능성 계측 및 실물 FK 경계.
- `d9c311b3` S15P21A103-156 실물 명령 경계 및 N-step runtime.

### Modified tracked files (unstaged)

- `AI/tools/check_umi_ingestion.py`: `pre_stabilized=True`이면 warmup 0을 허용하고 타입 오류를 검사.
- `AI/tools/run_umi_regression.py`: 실물 adapter/gap/camera/placement/IK/replay 관련 검사 8개 추가.
- `AI/tools/umi_ingest_arcore.py`: quality 입력 도움말에 `pre_stabilized` 추가.
- `AI/track_a/convert/arcore.py`: `pre_stabilized` 인자를 bundle decoder로 전달.
- `AI/track_a/convert/arcore_bundle.py`: 명시적 boolean 검증, 근거 있는 warmup 0, meta 기록.

### Important untracked source/config/docs

- `AI/configs/real/j2_velocity_trials_20260910.json`: J2 하중/신전 속도 시험 메타데이터.
- `AI/configs/real/umi_s22_pinch_provisional.json`: provisional ARCore camera→UMI pinch transform.
- `AI/docs/MEASURE_j2_velocity_0910.md`: J2 시험 해석.
- `AI/docs/UMI_SO101_TECH_SPEC_README.md`: UMI/SO-101 공부용 문서. 사용자가 공부 README는 커밋 대상에서 제외한다고 과거에 말했으므로 커밋 전 확인할 것.
- `AI/docs/UMI_조사_및_진행방향_공유.md`: 진행방향 공유 문서.
- `AI/tools/audit_real_umi_relative.py`
- `AI/tools/check_arpose_delivery_adapter.py`
- `AI/tools/check_gripper_gap.py`
- `AI/tools/check_umi_axis_projection.py`
- `AI/tools/check_umi_camera_frames.py`
- `AI/tools/check_umi_real_ik_diagnosis.py`
- `AI/tools/check_umi_real_placement.py`
- `AI/tools/check_umi_relative_audit.py`
- `AI/tools/check_umi_trajectory_rate_limit.py`
- `AI/tools/diagnose_real_umi_ik.py`
- `AI/tools/gripper_gap.py`
- `AI/tools/normalize_arpose_delivery.py`
- `AI/tools/replay_real_umi_trajectory.py`
- `AI/tools/search_real_umi_grasp_registration.py`
- `AI/tools/sweep_real_umi_axis_projection.py`
- `AI/tools/umi_real_placement_study.py`
- `AI/track_a/README.md`, `AI/track_a/README_UMI_ONBOARDING.md`: 온보딩/공부 문서로 보이며 커밋 포함 여부 확인 필요.
- `AI/track_a/convert/arpose_delivery.py`
- `AI/umi/camera_frames.py`
- `AI/umi/gripper_gap.py`

### Untracked generated/local artifacts (do not blindly commit)

- `.pnpm-store/`, `.tmp-umi-deps/`, `tmp/`: package/Python 임시 의존성 및 작업 산출물.
- `FE/.vite/`, `FE/tsconfig.tsbuildinfo`: FE build cache.
- `Docs/video-inspection/`, `Docs/backups/`, `Docs/20260831-header-shift-recording.mp4`: 로컬 검사/백업/대용량 파일. 소유자 확인 없이 커밋하지 말 것.
- `AI/out/**`: `git status`에는 상위 `tmp/`만 표시되고 `.gitignore`가 충분하지 않다. 실물 gripper/normalized ZIP, JSON, GIF 등이 다수 존재한다. 결과를 커밋하려면 필요한 작은 report만 선별하고 원본 이미지/ZIP/GIF는 별도 보관 정책을 확인한다.
- `HANDOFF.md`: 이번 요청으로 새로 생성된 유일한 파일.

Git이 사용자 홈 global ignore를 읽지 못한다는 permission warning이 발생했다. 따라서 `git status`에서 보이는 캐시는 실제로는 개인 global ignore에 의존했을 가능성이 있으며, 새 계정에서 결과가 달라질 수 있다.

## 10. Important Design Decisions

Decision: 학습 계약은 `0.3.0-provisional`, 단일 `cam_wrist`, 6번째 채널은 hinge가 아니라 전체 gripper gap 정규화값이다.
Reason: 실제 학습은 UMI 카메라 하나, 실행은 동일한 물리 카메라를 로봇으로 옮겨 사용한다. 구 hinge 의미와 숫자 범위가 겹치므로 버전으로 구 계약 학습을 차단한다.
Affected: `AI/contract/episode.py`, `AI/umi/convert.py`, dataset validators.

Decision: `action[t] = state[t+1]`, 마지막 raw state는 직전 action 목표로만 쓰고 학습 행은 삭제한다.
Reason: 다음 상태 예측 계약과 배열/timestamp 정합성을 명확히 한다.
Affected: `AI/contract/episode.py`, `AI/umi/convert.py`, ingestion tests.

Decision: 논리 카메라 채널은 `cam_wrist` 하나다. 과거 `cam_front`는 로봇 카메라, `cam_wrist`는 UMI 카메라로 시뮬에 존재했지만 실제 구성은 하나의 동일 카메라를 UMI에서 떼어 로봇에 옮긴다.
Reason: 학습·실행의 물리 카메라를 동일하게 유지한다.
Affected: contract, converter, policy input. MuJoCo의 과거 2-camera 설정은 별도이며 신규 실물 계약의 필수 입력이 아니다.

Decision: raw bundle은 무손실 보관하고, `RawEpisode`는 디코딩된 robot-frame 중간 표현으로 분리한다.
Reason: 원본을 보존해 임계값/캘리브레이션 변경 시 재처리 가능하게 한다.
Affected: `AI/docs/SPEC_umi_raw_bundle.md`, `AI/umi/raw.py`, `AI/track_a/convert/*`.

Decision: `gripper.csv`의 gap은 AI 후처리로 생산하되 원본 episode bundle에 결합하고 producer/version/hash와 source hashes를 추적한다.
Reason: 별도 CSV 전달은 어느 raw에서 나온 값인지 provenance를 끊는다.
Affected: `AI/tools/gripper_gap.py`, `AI/umi/gripper_gap.py`, normalizer, provenance.

Decision: D/M은 gap 필수, T/X는 빈 gap이며 0으로 채우지 않는다.
Reason: 0m는 결측이 아니라 완전 닫힘이라는 실제 명령이다.
Affected: raw decoder/validator, gripper postprocessor.

Decision: 실제 짧은 녹화는 `pre_stabilized=True`라는 명시적 증거가 있을 때만 warmup 0을 허용한다.
Reason: 이번 촬영은 녹화 버튼 전에 안정화됐고 고정 3초를 자르면 거의 모든 데이터가 사라지지만, 근거 없이 warmup을 제거하면 불안정 pose가 학습된다.
Affected: `arcore.py`, `arcore_bundle.py`, ingestion CLI/tests.

Decision: UMI action→SO-101 변환은 순수 Python이며 기존 IK를 재사용하고 ROS/rclpy 및 모터 제어를 포함하지 않는다.
Reason: 단독 테스트/이식 가능성과 AI/ROS/HW 책임 분리.
Affected: `AI/umi/action_ik.py`, `chunk_runtime.py`, `ros_adapter.py`.

Decision: 경로 안전은 계층별로 나눈다.
Reason: motor package는 단일 명령 hard range만 알며 전체 로봇 경로를 판단할 정보가 없다.
Affected: bus=tick/packet, SO101System=rad/gap/hard limit, upper ROS=collision/speed/acceleration/tracking/watchdog/E-stop, AI=명령 전 provisional preflight.

Decision: GPU 서버 device 2에서는 학습 체크포인트 추론을 금지한다. 학습은 허용하며,
정책 없는 MuJoCo는 팀/서버 정책이 허용할 때만 환경을 기록하고 실행할 수 있다.
Reason: 최신 대화에서 팀원이 GPU MuJoCo를 허용했지만 사용자는 학습 정책 추론은
금지했다. 이전의 "학습만" 문구보다 이 최신 경계를 우선한다.
Affected: `run_umi_regression.py`, `normalize_arpose_delivery.py`, runtime workflow. `runtime_limits.py`의 과거 다중 GPU 자동 선택보다 우선한다.

## 11. Known Issues / Bugs

### 1. 실제 full-pose IK 수용률이 낮다

- 증상: 공통 placement에서 full acceptance 27.08%, position-only reach 94.88%.
- 관련: `AI/tools/umi_real_placement_study.py`, `diagnose_real_umi_ik.py`, 결과 JSON.
- 원인: 측정상 UMI 자세/접근축과 SO-101 5-DoF 기구학 호환성이 주된 손실. 카메라 extrinsic도 아직 provisional.
- 시도: 단순 axis projection sweep은 개선 없음.
- 다음: 실제 태스크별 기준 pose/접근축 retargeting 정책을 정의하고, fallback을 학습 라벨로 쓰지 않은 채 수용률 재측정.

### 2. 물리 pick replay를 확정할 물체 3D 라벨이 없다

- 증상: 컵 proxy는 높이에 따라 table collision 또는 object no-contact가 발생하고 pick 실패.
- 관련: `replay_real_umi_trajectory.py`, `search_real_umi_grasp_registration.py`.
- 원인: 영상의 실제 컵 크기·world 위치·파지 높이를 모르는 상태에서 임시 box를 등록함.
- 시도: 20mm cube, 컵 proxy 크기/높이 50/80/110mm, x/y/yaw search, completed-close anchor.
- 다음: episode별 object identity/실측 크기/테이블 기준 파지 높이를 확보하거나, 물체 성공 판정 없이 kinematic replay로 범위를 명확히 제한.

### 3. 일부 replay가 position-only fallback을 사용한다

- 증상: 선택 episode 일반 replay는 40/75, 컵 등록 replay는 23/75 fallback.
- 원인: full pose IK budget(5mm/5°) 실패.
- 위험: position-only는 orientation을 바꾸므로 학습 라벨 또는 실물 안전 명령으로 승인할 수 없다.
- 다음: 실패 frame 분류 및 태스크별 retargeting/수집 프로토콜 조정.

### 4. 카메라 extrinsic이 provisional이다

- 값: OpenCV 기준으로 받은 pinch 위치 `(0,24.6,176.3)mm`와 회전행렬을 ARCore 규약으로 변환해 기록.
- 문제: 측정/도면 출처와 실제 반복 측정 오차가 독립 검증되지 않았다.
- 다음: 실물 checkerboard/hand-eye 또는 치수 재측정으로 검증하고 calibration ID/version을 확정.

### 5. gripper gap은 영상 추정값이다

- 증상: 약 94.6% D, 5.4% X. 실물 encoder 정답과 전 프레임 대조하지 않았다.
- 원인: 2점 affine calibration과 HSV 원 검출.
- 시도: 30mm 중간 상태 원본 이미지 1장을 받음. 체계적 다점/반복/hysteresis 측정은 없음.
- 다음: 가능하면 실제 gap 중간점/열림·닫힘 반복 측정으로 오차를 계측. X frame을 임의 보간하거나 0으로 채우지 말 것.

### 6. 하드웨어 동적 안전값과 ROS safety interface 미확정

- 미확정: 6축 순차 READ 평균/p95/max, J1↔J6 sample skew, loop/joint_states 주기, 실물 joint/gripper speed·acceleration, tracking error, watchdog, E-stop/복구.
- motor_control 현재 알려진 동작: 개별 READ, Sync Write, UART 최대 3회 retry, 명령 단절 시 마지막 위치 hold, 별도 watchdog/stop/E-stop 없음.
- 다음: 실물/상위 ROS 담당이 계측·인터페이스를 확정하기 전 provisional 0.05rad/tick을 실제 제한으로 쓰지 않는다.

### 7. 실제 학습 준비 여부가 확정되지 않았다

- 이번 70개 canonical bundle은 형식 변환 성공과 학습 승인이라는 뜻이 다르다.
- 짧은 episode, task/object label 일관성, X 구간 처리, full-pose IK 수용률, 카메라 calibration을 검토해야 한다.
- 실제 데이터로 GPU 학습은 아직 실행하지 않았다.

### 8. 저장소 위생 문제

- 캐시/대용량 로컬 파일이 untracked로 다수 보인다. `.gitignore`가 거의 비어 있고 global ignore 접근 warning이 있다.
- 해결을 시도하지 않았다(이번 요청은 문서 작성만 허용).
- 다음: 커밋 전에 소스/작은 report/공부 문서/생성물을 분류하고 사용자 승인 후 별도 커밋.

## 12. TODO

### P0 - Next

1. **미커밋 실물 UMI 변경을 코드 리뷰하고 전체 20개 로컬 회귀검사 결과를 유지한다.** 특히 `arpose_delivery.py`, `gripper_gap.py`, `camera_frames.py`, `replay_real_umi_trajectory.py`와 새 tests를 검토한다. 현재 `python tools/run_umi_regression.py`는 20/20 PASS다.
2. **71개 전달물의 품질/태스크 라벨을 episode별로 정리한다.** object/동작 종류가 섞였는지 확인하고 `rec_20260911_145737` 거부 이유와 모든 X 연속 구간을 기록한다.
3. **학습 승인 gate를 결정한다.** canonical 변환 성공만으로 학습하지 말고 camera extrinsic 상태, gripper valid rate, full-pose IK/retargeting 정책, task label 일관성을 근거로 trainable/rejected를 분리한다.
4. **미커밋 파일을 Jira 단위로 나눌 계획을 만든다.** 캐시/원본/대용량 output과 공부 README는 제외한다. 사용자 과거 지시상 공부 README는 커밋하지 않는다.

### P1 - Important

1. 실제 camera→pinch calibration을 독립 검증하고 provisional 해제 또는 오차 budget 명시.
2. UMI 접근축을 SO-101 5-DoF에 맞추는 태스크별 retargeting 규칙 연구 및 full-pose 수용률 재측정.
3. 물리 pick 검증을 계속하려면 episode별 실제 물체 크기/파지 높이/등록 근거 추가.
4. 하드웨어/ROS의 READ latency, control period, dynamic limits, tracking/watchdog/E-stop 인터페이스 수신 후 `trajectory.py`/preflight 설정에 `verified` 값 반영.
5. 실제 승인 dataset 생성 후에만 GPU device 2에서 CPU threads 제한을 걸어 학습. 학습 후 추론/rollout은 로컬에서 수행.

### P2 - Later

1. FE Mock service를 실제 `/api/v1` HTTP/SSE로 교체하고 삭제/취소/저장 등 최근 UI 계약을 BE와 합의.
2. BE Spring Boot의 실제 인증·dataset·training·deployment·execution API 구현/검증.
3. ROS2 node skeleton, QoS/clock/freshness/error handling과 실제 robot integration.
4. `.gitignore`와 generated artifact 보관 정책 정리.
5. 오래된 README/결정 문서의 실물 데이터 0편, cam_front 등 낡은 서술 정합성 갱신.

## 13. Testing / Verification

### UMI regression

```bash
cd AI
python tools/run_umi_regression.py
```

- Expected: 마지막에 `PASS: 20/20 checks`.
- 서버 hostname이 `jupyter*`이면 exit 2가 정상이며 로컬에서 다시 실행해야 한다.

### Focused latest test

```bash
cd AI
python tools/check_umi_trajectory_rate_limit.py
```

- Expected: `PASS: causal limiting, path-preserving time scaling, and invalid rejection`.
- 직전 세션에서 PASS 확인.

### Dataset validation

```bash
cd AI
python tools/verify_dataset.py <dataset-dir> --write-index
```

- Expected: contract 위반 0. 구 버전/카메라 불일치/action shift 오류는 거부돼야 한다.

### Real delivery checks

```bash
cd AI
python tools/check_gripper_gap.py
python tools/check_arpose_delivery_adapter.py
python tools/check_umi_camera_frames.py
python tools/check_umi_relative_audit.py
python tools/check_umi_real_placement.py
python tools/check_umi_real_ik_diagnosis.py
python tools/check_umi_axis_projection.py
```

- Expected: 각 스크립트 PASS. 생성된 `quality.json`에서 status 분포와 reject 이유를 별도 확인.

### Syntax/whitespace

```bash
python -m py_compile tools/replay_real_umi_trajectory.py tools/search_real_umi_grasp_registration.py
git diff --check
```

직전 세션에서 위 py_compile 및 diff check는 PASS했다.

### Frontend

```bash
cd FE
pnpm lint
pnpm build
```

현재 FE 관련 변경은 이번 UMI 작업 범위가 아니며, 로컬 `.vite`/`tsbuildinfo`를 커밋하지 않는다.

## 14. Context for the Next Codex

- 이 문서는 이전 대화가 없는 Codex를 위한 현재 스냅샷이다. 오래된 `AI/README.md`, `track_a/HANDOVER_track_a.md`, `REVIEW_umi_handover_0910.md`에는 당시 “실물 데이터 0편”, `cam_front` 필요, ARCore adapter 미구현 등의 과거 상태가 남아 있다. 현재 미커밋 구현과 이 문서를 우선 대조하라.
- 사용자가 맡은 범위는 AI UMI다. FE UI 변경은 한동안 이 채팅에서 실수로 진행됐으나 이후 FE 별도 채팅으로 넘기기로 했다. 현재 요청 없이 FE를 수정하지 말 것.
- 실제 카메라는 두 대가 아니라 **하나의 동일한 장치**를 UMI에서 떼어 로봇에 옮겨 장착한다. 학습에는 UMI 장착 영상, 실행에는 로봇 장착 영상을 쓰지만 논리 채널은 둘 다 `cam_wrist` 하나다.
- 카메라 pose는 ARCore/OpenGL 그대로다. `getDisplayOrientedPose`나 OpenCV 변환이 적용되지 않았다. IMU는 이번 변환/안정화에 사용하지 않는다. 나중에 fusion하면 Android device↔camera extrinsic이 별도로 필요하다.
- 제공된 S22→pinch 값은 OpenCV식 축으로 왔고, 현재 config가 이를 ARCore식으로 변환한 provisional 값이다. 실제 배포 전 검증 필요.
- 실물 그리퍼 외부 계약은 전체 접촉면 간 gap `[m]`. 하드웨어 nominal은 0~0.09m였으나 UMI 수집 리그 후처리는 실효 최대 0.07m로 캘리브레이션됐다. 두 범위를 섞지 말고 UMI 측 측정값을 SO-101 가능한 범위로 검증/변환한다.
- `gripper.csv`는 제대로 생성됐지만 하드웨어 encoder 정답이 아니라 영상 마커 기반 추정값이다. 팀원에게 전달할 때 단독 CSV보다 canonical episode ZIP과 `.quality.json`을 함께 주는 것이 provenance상 안전하다.
- 선택 episode 150920은 노란 컵이다. 이를 2cm cube로 가정한 초기 물리 실패 해석은 폐기됐다. 최신 컵 proxy도 실측 geometry가 아니므로 pick failure를 정책/파이프라인 실패로 단정하지 말 것.
- 학습 없이 deterministic trajectory replay는 가능하다. 이는 데이터/좌표/IK/명령/물리 배선을 검증하는 것이며, 이미지로 새로운 상황에 대응하는 policy rollout은 학습이 필요하다.
- position-only fallback은 관찰용 진단 수단일 뿐 training label이나 실물 배포 승인 경로가 아니다.
- local PC와 GPU server 책임을 반드시 지킨다. 새 Codex가 편의상 서버에서 회귀검사/추론하지 말 것.
- 하드웨어 확정값: J1~J5 direction 모두 +1; zero ticks `1990,2048,1024,2048,2126`; J3 range `[-1.570796,1.69]rad`; FK `base_link→gripper_tcp`; gripper는 전체 gap; `/joint_states` timestamp는 ros2_control 발행 시각이며 servo timestamp가 아니다.
- 하드웨어 미확정값은 절대로 추정해 verified로 승격하지 않는다. 문서상 `verified/provisional/unverified` 구분을 유지한다.

## 15. Recommended Next Action

새 계정에서 가장 먼저 할 일은 **현재 미커밋 UMI 변경을 보존한 채 20/20 회귀 통과 상태에서 변경 파일을 리뷰하고 Jira 단위 커밋 후보로 분류하는 것**이다.

1. `git status --short`를 저장하고 위 Git State 목록과 대조한다.
2. 다음 핵심 파일부터 리뷰한다:
   - `AI/track_a/convert/arpose_delivery.py`
   - `AI/umi/gripper_gap.py`, `AI/tools/gripper_gap.py`
   - `AI/umi/camera_frames.py`
   - `AI/tools/replay_real_umi_trajectory.py`
   - `AI/tools/run_umi_regression.py`
3. 필요 시 로컬 PC에서 `cd AI && python tools/run_umi_regression.py`를 재실행한다. GPU 서버에서는 실행하지 않는다. 2026-09-12 기준 20/20 PASS가 확인됐다.
4. 완료 기준:
   - 20/20 PASS.
   - `git diff --check` PASS.
   - `rec_20260911_145737` reject 이유와 70개 accepted episode의 quality summary가 재현됨.
   - generated/cache/study README를 제외한 실제 소스·테스트·작은 config/report만 Jira 단위 커밋 후보로 명확히 분류됨.
5. 위 검증 전에는 학습을 시작하거나 미커밋 변경을 한꺼번에 커밋하지 않는다.
