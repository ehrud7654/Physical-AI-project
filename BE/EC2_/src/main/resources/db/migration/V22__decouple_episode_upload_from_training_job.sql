ALTER TABLE episode_upload_sessions
    ALTER COLUMN training_job_id DROP NOT NULL;

DROP INDEX IF EXISTS ix_episode_upload_training_job_status;

CREATE INDEX ix_episode_upload_owner_device_status
    ON episode_upload_sessions (owner_id, umi_device_id, status, created_at DESC);

COMMENT ON COLUMN episode_upload_sessions.training_job_id IS
    'Legacy training job reference. New standalone episode uploads leave this column NULL.';
