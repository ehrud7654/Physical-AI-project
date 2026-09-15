CREATE UNIQUE INDEX uq_training_jobs_active_worker_gpu
    ON training_jobs (assigned_worker_id, gpu_index)
    WHERE assigned_worker_id IS NOT NULL
      AND gpu_index IS NOT NULL
      AND status IN (
          'COLLECTING', 'PREPARING', 'RUNNING',
          'MODEL_UPLOADING', 'MODEL_VALIDATING', 'CANCEL_REQUESTED'
      );

ALTER TABLE training_jobs
    ADD CONSTRAINT ck_training_jobs_gpu_index_parallel_limit
    CHECK (gpu_index IS NULL OR gpu_index BETWEEN 0 AND 29);

CREATE INDEX ix_training_jobs_claim_candidate
    ON training_jobs (created_at, training_profile_id)
    WHERE deleted_at IS NULL AND status IN ('CREATED', 'QUEUED');

CREATE INDEX ix_training_jobs_expired_lease
    ON training_jobs (lease_expires_at)
    WHERE lease_expires_at IS NOT NULL
      AND status IN (
          'COLLECTING', 'PREPARING', 'RUNNING',
          'MODEL_UPLOADING', 'MODEL_VALIDATING', 'CANCEL_REQUESTED'
      );

COMMENT ON INDEX uq_training_jobs_active_worker_gpu
    IS 'worker의 GPU 슬롯별 활성 학습 작업 하나, gpuIndex 0~29로 최대 30개 병렬 실행';

COMMENT ON CONSTRAINT ck_training_jobs_gpu_index_parallel_limit ON training_jobs
    IS '등록 GPU worker의 최대 병렬 학습 자원 30개에 대응하는 gpuIndex 범위';
