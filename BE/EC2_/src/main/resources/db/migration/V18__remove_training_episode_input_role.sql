DROP INDEX IF EXISTS uq_training_job_one_mapping_episode;

ALTER TABLE training_job_episodes
    DROP CONSTRAINT IF EXISTS ck_training_job_episode_input_role,
    DROP COLUMN IF EXISTS input_role;
