# v10 first-four-target near-contact diagnostic (2026-09-17)

Related Jira: `S15P21A103-154`, `S15P21A103-171`. This is a **local CPU
simulation diagnostic**, not a hardware test, deployment gate or independent
object-position generalisation result.

## Fixed conditions

- Dataset: `umi_real_relative_20260911_v10` (70 real UMI episodes, 1,248
  anchors); policy: the existing seed-0 best-val checkpoint, not retrained.
- Task: upright 40 mm snack case, horizontal two-sided body grasp.
- Registration: the predeclared v9 K=2 per-episode near-contact approach start;
  this registration is not a valid global far-start alignment.
- 14 scene-valid episodes, nominal object placement, provisional ver1 jaw
  contact proxy, simulation-only 2 mm grip preload and provisional fitted
  S22/MuJoCo visual domain.
- Policy input: simulation RGB and relative proprio. The first cycle's history
  duplicates its initial frame/state; there is no genuine two-frame moving
  history before the first policy call.
- From the v10 eight **future** targets, validate and execute index `0..3`.
  The Track B official-UMI `1..4` slice has different semantics and was not used.
- The new `--complete-execute-prefix` diagnostic option suppresses the
  simulator's mid-prefix success break, then checks success at the cycle
  boundary. It does not override geometry/IK rejection or change the dataset.

## Result and interpretation

`AI/out/v10_policy_k2_execute4_complete_normal14_20260917.json` records
14/14 stable side-grasp lifts and bilateral contact. Every episode executed
exactly four targets in one cycle, with no geometry or runner error. The
Wilson 95% interval for this narrow 14/14 condition is `[78.5%, 100%]`.

**This does not complete the planned four-target closed-loop evaluation.**
Each episode reached the simulator's success criterion after its first
four-target prefix. It generated an updated observation but stopped before a
second policy call, so execute→reobserve→predict over multiple cycles has not
been tested. It also did not test a far start or object-placement offsets.

The required uniform time expansion was **2.77–3.86×** (per-target scheduled
period about 0.28–0.39 s, rather than the source 0.1 s). Four targets therefore
occupied about 1.1–1.5 s, not 0.4 s. These diagnostics did not apply a
maximum time-expansion rejection. A caller using the illustrative `2.0×`
bound from `check_umi_joint_chunk_handoff.py` would reject all 14. No team-
approved execution bound or guarantee of policy behaviour under retimed
multi-cycle observations follows from this result.

The earlier run without `--complete-execute-prefix` also reported 14/14, but
stopped after only 2–3 executed targets per episode. It is **not** a four-
target execution result. Its report remains at
`AI/out/v10_policy_k2_execute4_normal14_20260917.json` for comparison.

## Follow-up: fixed far-start warmup and genuine H=2 (2026-09-17)

`AI/tools/audit_relative_chunk_far_start.py` implements a **policy-free**
time-parameterised approach from one fixed candidate joint pose to each
episode's predeclared K=2 approach pose. The start is home→the `150943`
approach joint pose at fraction 0.75, with the jaw held open at 65 mm. This
was a bounded diagnostic candidate search, **not** physical robot-base
registration. A 24-waypoint quintic TCP path uses 5-DoF IK; execution stops
at waypoint 18, while the arm is still moving. The last two achieved camera
images and joint states are 0.096 s apart. The policy-free report
`AI/out/v10_far_start_h2_14_20260917.json` has IK, first-view visibility,
actual speed/acceleration, object-stability and diagnostic timing checks at
14/14. Its illustrative 2× cap has **not** been approved as a robot limit.
There is no swept installed-hardware collision certificate.

The local CPU v10 best-val policy then used those achieved H=2 snapshots.
`AI/out/v10_far_start_h2_policy_normal14_retry_20260917.json` records two
policy calls per episode, four **future** points `0..3` per call, and 14/14
nominal-placement simulator lifts with bilateral contact. This is policy-free
navigation to a near-contact handoff followed by two policy cycles, **not**
an autonomous policy from the far start. The first prefix was retimed to
roughly 1.6 s rather than its nominal 0.4 s, so real-time distribution shift
and a team-approved execution cap remain unresolved. Contact still relies on
the provisional proxy and simulation-only 2 mm preload.

At the same checkpoint and handoff, inference-time mean-fill RGB produced
0/14 accepted lifts: six paths failed geometry preflight and eight exceeded
the caller's diagnostic 4× time-scale cap. Additive Gaussian noise (sigma 96
on top of the rendered image) produced 13/14 accepted lifts. Report:
`AI/out/v10_far_start_h2_policy_ablations14_20260917.json`. Mean-fill is a
severe out-of-distribution input, whereas additive noise retains spatial
content. Therefore these outcomes do **not** establish reliable visual
localisation or a normal-vs-information-free success effect.

## Follow-up: object-only ±10 mm first-four-target probe

`AI/tools/probe_far_start_object_only.py` holds each achieved H=2 robot state,
camera pose, proprioception and checkpoint fixed. It re-renders only the object
at nominal and ±10 mm world X/Y, then compares the predicted first four
relative EEF translations against the corresponding TCP-local object shift.
At waypoint 18, one shifted placement made pre-policy jaw contact, so a new
policy-free waypoint-16 audit was made: 14/14 passed the same scripted motion
checks (`AI/out/v10_far_start_h2_handoff16_14_20260917.json`). One of these
14 episodes (`145657`) still made pre-policy contact under ±10 mm and was
**excluded**, not silently counted as a policy failure.

For the other 13 held-out episodes, baseline images reproduce the saved
snapshots exactly and the median shifted-image difference is 4.69 RGB levels.
Across 52 paired offsets, the last of the **executed** four targets moved in
the expected half-space in only 20/52 normal-RGB comparisons; 0/13 episodes
tracked all four directions. Median directional gain is −0.023 (ideal ≈1).
Per-shift channel-mean fill gave 34/52 and additive noise gave 26/52, but
neither control establishes object localisation: the former retains global
colour/area change and the latter retains image structure. Full report:
`AI/out/v10_far_start_object_only_handoff16_14_metrics_20260917.json`.

This is **negative evidence for robust first-four-target localisation under
these ±10 mm simulator shifts** despite nominal-placement lifts. It is not a
closed-loop shifted-object success rate, not a hardware result, and not proof
that all visual information is unused. Do not promote the nominal 14/14 or
this checkpoint to a real-robot claim. Next, isolate the dataset and visual
domain factors behind the wrong-sign/weak spatial response; keep physical
camera↔TCP, base/workspace registration, gap↔pad opening and installation
clearance as separate prerequisites for any supervised hardware dry-run.

The later waypoint-18 object-only comparison and real-data/input-support
diagnosis are recorded in `MEASURE_v10_visual_localisation_failure_0917.md`.
Its 16/52 result uses a **different handoff waypoint** from the 20/52 result
above; neither is a shifted-object closed-loop success rate.
