ALTER TABLE training_jobs ADD COLUMN deleted_at timestamptz;
ALTER TABLE tasks ADD COLUMN deleted_at timestamptz;

CREATE INDEX ix_training_jobs_owner_active_created
    ON training_jobs (owner_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX ix_tasks_owner_active_updated
    ON tasks (owner_id, updated_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON COLUMN training_jobs.deleted_at IS '사용자 목록에서 제외된 soft delete 시각';
COMMENT ON COLUMN tasks.deleted_at IS '사용자 목록에서 제외된 soft delete 시각';

