ALTER TABLE storage_nodes DROP CONSTRAINT storage_nodes_storage_type_check;
ALTER TABLE storage_nodes
    ADD CONSTRAINT storage_nodes_storage_type_check CHECK (storage_type IN ('LOCAL', 'S3'));

INSERT INTO storage_nodes (
    id, worker_id, node_key, name, storage_type, status,
    capacity_bytes, available_bytes, last_seen_at
) VALUES (
    '18000000-0000-4000-8000-000000000001',
    'ec2-episode-storage',
    'episode-s3',
    'Episode S3 Storage',
    'S3',
    'ONLINE',
    9223372036854775807,
    9223372036854775807,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

ALTER TABLE episode_upload_sessions
    DROP CONSTRAINT ck_episode_upload_status,
    DROP CONSTRAINT ck_episode_upload_completion,
    ADD COLUMN quality_status varchar(20),
    ADD COLUMN validation_error_code varchar(100),
    ADD COLUMN validation_error_message text,
    ADD COLUMN validated_at timestamptz;

UPDATE episode_upload_sessions SET status = 'UPLOAD_PENDING' WHERE status = 'PENDING';

ALTER TABLE episode_upload_sessions
    ADD CONSTRAINT ck_episode_upload_status
        CHECK (status IN (
            'UPLOAD_PENDING', 'VALIDATING', 'READY', 'INVALID', 'FAILED', 'EXPIRED', 'CANCELLED'
        )),
    ADD CONSTRAINT ck_episode_upload_completion
        CHECK (
            (status = 'UPLOAD_PENDING'
                AND completed_at IS NULL AND canceled_at IS NULL AND validated_at IS NULL)
            OR (status = 'VALIDATING'
                AND completed_at IS NOT NULL AND canceled_at IS NULL AND validated_at IS NULL)
            OR (status = 'READY'
                AND completed_at IS NOT NULL AND canceled_at IS NULL AND validated_at IS NOT NULL
                AND quality_status IN ('VALID', 'WARNING')
                AND validation_error_code IS NULL AND validation_error_message IS NULL)
            OR (status IN ('INVALID', 'FAILED')
                AND completed_at IS NOT NULL AND canceled_at IS NULL AND validated_at IS NOT NULL
                AND validation_error_code IS NOT NULL AND validation_error_message IS NOT NULL)
            OR (status = 'EXPIRED'
                AND completed_at IS NULL AND canceled_at IS NULL AND validated_at IS NULL)
            OR (status = 'CANCELLED'
                AND completed_at IS NULL AND canceled_at IS NOT NULL AND validated_at IS NULL)
        );

COMMENT ON COLUMN episode_upload_sessions.quality_status IS 'ZIP 검증으로 확정한 VALID, WARNING, INVALID';
COMMENT ON COLUMN episode_upload_sessions.validation_error_code IS '검증 또는 처리 실패 코드';
COMMENT ON COLUMN episode_upload_sessions.validation_error_message IS '검증 또는 처리 실패 설명';
COMMENT ON COLUMN episode_upload_sessions.validated_at IS '검증 결과 확정 시각';
