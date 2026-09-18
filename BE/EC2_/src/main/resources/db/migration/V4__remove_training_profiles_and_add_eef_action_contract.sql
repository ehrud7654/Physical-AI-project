-- Training algorithm/profile configuration is owned by the trainer.
-- EC2 keeps orchestration state and treats actionSpec as opaque contract data.

ALTER TABLE skill_versions DROP CONSTRAINT IF EXISTS fk_skill_versions_23;
ALTER TABLE skill_versions DROP CONSTRAINT IF EXISTS fk_skill_versions_24;
ALTER TABLE skill_versions DROP CONSTRAINT IF EXISTS fk_skill_versions_27;
ALTER TABLE training_jobs DROP CONSTRAINT IF EXISTS fk_training_jobs_13;
ALTER TABLE training_jobs DROP CONSTRAINT IF EXISTS fk_training_jobs_17;

DROP INDEX IF EXISTS ix_skill_versions_3;
DROP INDEX IF EXISTS ix_training_jobs_5;
DROP INDEX IF EXISTS ix_training_jobs_claim_candidate;
DROP INDEX IF EXISTS uq_training_profiles_one_active;

ALTER TABLE training_jobs
  DROP CONSTRAINT IF EXISTS training_jobs_id_training_profile_id_training_config_sha256_key,
  DROP CONSTRAINT IF EXISTS training_jobs_training_config_sha256_check,
  DROP COLUMN IF EXISTS training_profile_id,
  DROP COLUMN IF EXISTS training_config_snapshot,
  DROP COLUMN IF EXISTS training_config_sha256;

ALTER TABLE skill_versions
  DROP CONSTRAINT IF EXISTS skill_versions_training_config_sha256_check,
  DROP CONSTRAINT IF EXISTS skill_versions_action_space_check,
  DROP COLUMN IF EXISTS training_profile_id,
  DROP COLUMN IF EXISTS training_config_sha256,
  ADD COLUMN IF NOT EXISTS action_spec jsonb NOT NULL DEFAULT '{}'::jsonb,
  ADD CONSTRAINT skill_versions_action_space_check
    CHECK (action_space IN ('JOINT_ABSOLUTE', 'JOINT_DELTA', 'EEF_RELATIVE_ROT6D'));

CREATE INDEX ix_training_jobs_claim_candidate
  ON training_jobs (created_at)
  WHERE deleted_at IS NULL AND status IN ('CREATED', 'QUEUED');

DROP TABLE IF EXISTS training_profiles;

UPDATE robot_profiles
SET action_contract = jsonb_build_object(
  'actionSpace', 'EEF_RELATIVE_ROT6D',
  'actionSpec', jsonb_build_object(
    'dim', 10,
    'horizon', 8,
    'rateHz', 10,
    'layout', jsonb_build_array('dx','dy','dz','r00','r01','r02','r10','r11','r12','gap_m'),
    'rotation', 'rotmat_first_two_ROWS',
    'compose', 'T_next = T_cur @ A_relative',
    'gapUnit', 'm',
    'execSlice', jsonb_build_array(1, 5)
  )
);

COMMENT ON COLUMN skill_versions.action_spec IS
  'Trainer-defined action tensor contract. EC2 stores and forwards this JSON without numeric validation.';
