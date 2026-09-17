# v10 object-position response: real labels vs local sim inputs (2026-09-17)

Related Jira: `S15P21A103-154`, `S15P21A103-171`. All checkpoint inference
below ran on the **local CPU**, never on the shared GPU server or real robot.
The checkpoint is the existing v10 seed-0 best-val model; no retraining was
performed. These are diagnostic correlations/interventions, not success
rates, hardware approval, or causal attribution to a single failure source.

## What the checkpoint actually does

At the same scripted far-start→near-contact handoff used for the 14/14
nominal-placement two-cycle MuJoCo lifts, keep both achieved H=2 robot poses,
camera, proprioception and checkpoint fixed. Shift only the object by ±10 mm
in world X/Y, and re-render the two images. One of 14 episodes (`145657`)
creates **pre-policy jaw contact** under an offset and is excluded. Among the
other 13, the last of the first four executed v10 targets moves in the
correct object-shift half-space for only **16/52** placements; no episode
tracks all four directions. The median direction gain is −0.019. This is a
first-action image test, not a shifted-object rollout result. The exact
handoff report is
`AI/out/v10_far_start_object_only_handoff18_14_metrics_20260917.json`.

For the camera-horizontal shift pair (world Y ±10 mm), the object bbox centre
moves about 0.05 normalized image width each way. The geometric target's
TCP-local x response has a median slope **−0.199 m / normalized image x**,
but the policy's median slope is **−0.00013** (13 episode-level central
differences). The corresponding comparison report is
`AI/out/v10_real_label_vs_sim_image_response_20260917.json`. This supports
the conclusion that the policy is not producing the required lateral
correction under the simulated object-only intervention. It does not prove
which training or domain factor caused that failure.

## Does the real v10 dataset contain the signal?

The real v10 data has 70 episodes / 1,248 anchors; seed-0 split is 56 train /
14 held-out episodes. A yellow/orange connected-component detector finds the
package in 55/56 training episodes' first anchor and 56/56 at the first
anchor with gap ≤45 mm. At the latter phase, the package centre-x p05–p95 is
only **0.488–0.523** (about 8 pixels on a 224-pixel image). The first-anchor
range is much wider, **0.108–0.706**. These are *image positions*, not a
measurement of physical object placements: handheld camera pose and phase
also change. Report:
`AI/out/v10_real_visual_coverage_train56_heldout14_20260917.json`.

Matching the simulator handoff's apparent package width/height within 25%
and requiring an open 60–70 mm gap yields at least one real frame in **42/56
training** and **10/14 held-out** episodes. Thus the relevant-looking real
frames are **not absent**. The matched training set contains 105 rows, of
which 18 have |fourth future target TCP-local x| >10 mm; those rows occur in
only **9/56 training episodes**. Choosing exactly one nearest-size frame per
episode leaves 4/42 training and 1/10 held-out episodes with >10 mm x
correction. These selection-dependent counts are not independent trial
frequencies. Report:
`AI/out/v10_open_gap_sim_scale_visual_match_counts_20260917.json`.

On the selected frames, object centre-x and recorded fourth-target local-x
have exploratory Pearson r=−0.91 (42 train) and −0.98 (10 held-out). **Do not
quote these alone:** the slope is strongly influenced by the few extreme
off-centre demonstrations. Restricting centre-x to [0.3, 0.7] yields r≈−0.48
on 40 training episodes; most central targets are small. The relationship is
observational and confounded by camera pose/phase, although its negative
direction agrees with the simulator's geometric shift direction.

Feeding these *real* frames through the existing checkpoint with proprio held
fixed does not recover that response. The predicted fourth-target local-x
slope is −0.0055 on training frames and **+0.0098** on held-out frames,
versus recorded-label slopes of −0.30 and −0.19 respectively. Original
proprio instead of fixed proprio gives essentially the same result. The
prediction range is compressed: standard deviation ≈1.4 mm (train) and
1.3 mm (held-out), versus selected target standard deviations ≈26.8 mm and
21.3 mm, both heavily affected by rare large corrections. This is
cross-episode image variation, not an object-only real-image intervention.
The displacement-stratified rerun makes the limitation sharper: among 10
selected held-out episodes, nine have package centre-x in [0.45, 0.55] and
their x MAE is **1.62 mm**; the sole right-of-0.55 case has **73.0 mm** x
error and is also the only >10 mm recorded target. In training, the single
left-of-0.45 case has **154.8 mm** error, versus **2.53 mm** on 33 central
cases. Do not average these tiny off-centre groups into a generalisation
rate. Report:
`AI/out/v10_real_image_position_checkpoint_response_stratified_20260917.json`.

**Therefore simulator appearance alone cannot explain the failure.** The
baseline BC checkpoint already under-reacts to the rare large lateral
corrections in its native real-image domain. The current per-step,
per-component standardized L1 loss averages all 8×10 outputs uniformly; a
near-zero median prediction can score well on common centred examples. That
is a plausible mechanism, not a proven causal ablation.

## Additional input shifts at the scripted handoff

Within the same 60–70 mm open-gap band, the 50 training episodes that have
eligible rows contribute 575 *correlated* H=2 rows. Their median 0.1 s TCP
translation is **15.69 mm**, rotation **0.54°**, and two-frame RGB MAE
**18.41/255**. The 14 scripted simulator handoff observations have medians
**1.49 mm**, **0.071°** and **1.60/255**, with actual frame spacing 0.096 s.
The earlier waypoint-16 handoff still has only 2.06 mm and RGB MAE 2.26.
The robot-feasible scripted approach thus yields much more static policy
input than the human demonstrations. The samples are not phase matched, so
this is an input-support warning, not a measured causal effect. Reports:
`AI/out/v10_handoff18_observation_support_20260917.json` and
`AI/out/v10_handoff16_observation_support_20260917.json`.

The K=2 scene-matched appearance check sees the target on all 70/70 rays
over 14 episodes, but held-out real images have edge fraction 0.1346 while
the visual-domain proxy has 0.0307 (threshold: 20 gray levels). The render
still lacks packaging print and busy workshop detail, even when object
visibility is correct. Report:
`AI/out/v10_k2_real_sim_visual_domain_20260917.json`.

## Decision for the next experiment

Do **not** call the nominal 14/14 lift a visual-generalisation pass or send
this checkpoint to real motors. A single camera texture tweak is not a
complete remedy. The next controlled training comparison should keep the
same episode split, architecture, seed(s), image pipeline and object-only
evaluation, and vary only the treatment of rare lateral-correction examples
(for example, episode-balanced sampling or an explicit lateral-error weight).
Report held-out local-x error by object-centre displacement, not just mean
8×10 L1. Separately, construct robot-feasible 10 Hz observation/target
sequences rather than slowing commands while leaving the training-time
observation clock implicit. Only after those factors are isolated should
visual-domain refinement and simulator pretraining be judged by the same
object-only first-four-target test and episode-separated closed-loop rollout.

Real camera↔TCP, base/workspace registration, actual pad-gap mapping,
installed collision clearance and supervised hardware approval are separate
unmet prerequisites. None is bypassed by these local diagnostics.

## Reproducible next training comparison (prepared, not yet run)

`AI/tools/train_relative_chunk_bc.py` now has an optional
`--lateral-sampling-factor`; its default `1` keeps the historical shuffled
training path. The diagnostic treatment uses factor `4` for **training-only**
rows with current gap 60–70 mm and absolute fourth future target local-x
correction >10 mm. The seed-0 v10 split contains 198 such rows among 1,009
training rows, so the treatment's expected draw fraction is 49.4% instead
of 19.6%. Validation remains all 239 held-out rows once, unchanged. This
rule selects by gap and target but **not** by simulator-size image match; it
does not manufacture counterfactual object positions or prove that the
selected labels are causal visual supervision.

Use the same v10 data, split, model, hyperparameters and epoch count for a
factor-1 baseline and factor-4 treatment, with separate checkpoint names.
Compare held-out fourth-target local-x error stratified by object-centre
offset and rerun the *same* local-CPU object-only ±10 mm first-four-target
probe for both checkpoints. Do not compare **training** losses directly:
the treatment changes its draw distribution. Checkpoint validation losses
can be compared as diagnostics, but lift and localisation gates remain
separate. The new sampler and its deterministic selection checks passed
locally; **no treatment training or rollout result exists yet**.
