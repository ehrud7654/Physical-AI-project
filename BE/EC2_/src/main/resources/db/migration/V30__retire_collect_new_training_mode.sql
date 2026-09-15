-- Episode는 UMI가 S3에 선행 업로드하며 학습 작업은 준비된 Episode만 참조한다.
-- 기존 COLLECT_NEW 작업은 더 이상 재개할 수 없으므로 명시적으로 종료한다.
UPDATE training_jobs
SET status = 'FAILED',
    error_code = 'COLLECT_NEW_RETIRED',
    error_message = 'S3에 업로드된 기존 Episode만 사용하는 정책으로 변경되었습니다.',
    finished_at = COALESCE(finished_at, CURRENT_TIMESTAMP),
    input_mode = 'EXISTING_EPISODES',
    assigned_worker_id = NULL,
    assigned_storage_node_id = NULL,
    gpu_index = NULL,
    git_rev = NULL,
    lease_token_hash = NULL,
    lease_expires_at = NULL
WHERE input_mode = 'COLLECT_NEW';

ALTER TABLE training_jobs
    DROP CONSTRAINT training_jobs_input_mode_check;

ALTER TABLE training_jobs
    ADD CONSTRAINT training_jobs_input_mode_check
    CHECK (input_mode = 'EXISTING_EPISODES');

COMMENT ON COLUMN training_jobs.input_mode IS
    '고정값 EXISTING_EPISODES. UMI가 S3에 업로드하고 검증 완료된 Episode만 학습 입력으로 사용';

COMMENT ON COLUMN training_job_events.event_type IS
    'STARTED, PROGRESS, TRAINING_FINISHED, FAILED, CANCELED';
