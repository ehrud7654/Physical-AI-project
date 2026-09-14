ALTER TABLE episode_upload_sessions
    DROP CONSTRAINT ck_episode_upload_status,
    DROP CONSTRAINT ck_episode_upload_completion,
    ADD COLUMN canceled_at timestamptz,
    ADD CONSTRAINT ck_episode_upload_status
        CHECK (status IN ('PENDING', 'VALIDATING', 'EXPIRED', 'CANCELLED')),
    ADD CONSTRAINT ck_episode_upload_completion
        CHECK (
            (status = 'VALIDATING' AND completed_at IS NOT NULL AND canceled_at IS NULL)
            OR (status IN ('PENDING', 'EXPIRED') AND completed_at IS NULL AND canceled_at IS NULL)
            OR (status = 'CANCELLED' AND completed_at IS NULL AND canceled_at IS NOT NULL)
        );

COMMENT ON COLUMN episode_upload_sessions.canceled_at IS '미완료 업로드 세션 취소 시각';
