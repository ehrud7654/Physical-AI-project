ALTER TABLE device_credentials
    DROP COLUMN registration_code_hash,
    DROP COLUMN registration_code_expires_at,
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN enrolled_at timestamptz,
    ADD COLUMN last_authenticated_at timestamptz,
    ADD CONSTRAINT ck_device_credentials_status
        CHECK (status IN ('ACTIVE', 'REVOKED'));

COMMENT ON COLUMN device_credentials.status IS '장치 인증 정보 상태';
COMMENT ON COLUMN device_credentials.enrolled_at IS 'QR 등록 완료 시각';
COMMENT ON COLUMN device_credentials.last_authenticated_at IS '마지막 Access Token 발급 인증 시각';

CREATE TABLE device_enrollments (
    id uuid PRIMARY KEY,
    device_id uuid NOT NULL,
    registration_token_hash varchar(64) NOT NULL,
    status varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    attempt_count integer NOT NULL DEFAULT 0,
    max_attempts integer NOT NULL DEFAULT 5,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    row_version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_device_enrollments_device
        FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE RESTRICT,
    CONSTRAINT fk_device_enrollments_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uq_device_enrollments_token_hash UNIQUE (registration_token_hash),
    CONSTRAINT ck_device_enrollments_token_hash
        CHECK (registration_token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_device_enrollments_status
        CHECK (status IN ('ACTIVE', 'USED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT ck_device_enrollments_attempts
        CHECK (attempt_count >= 0 AND max_attempts >= 1 AND attempt_count <= max_attempts),
    CONSTRAINT ck_device_enrollments_expiration
        CHECK (expires_at > created_at),
    CONSTRAINT ck_device_enrollments_state
        CHECK (
            (status = 'ACTIVE' AND used_at IS NULL AND revoked_at IS NULL)
            OR (status = 'USED' AND used_at IS NOT NULL AND revoked_at IS NULL)
            OR (status = 'EXPIRED' AND used_at IS NULL AND revoked_at IS NULL)
            OR (status = 'REVOKED' AND used_at IS NULL AND revoked_at IS NOT NULL)
        )
);

CREATE UNIQUE INDEX uq_device_enrollments_active_device
    ON device_enrollments(device_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_device_enrollments_status_expires_at
    ON device_enrollments(status, expires_at);

CREATE INDEX ix_device_enrollments_created_by_created_at
    ON device_enrollments(created_by, created_at DESC);

COMMENT ON TABLE device_enrollments IS 'QR 기반 Device Secret 최초 발급을 위한 일회성 등록 요청';
COMMENT ON COLUMN device_enrollments.registration_token_hash IS 'QR에 포함된 원문 등록 토큰의 SHA-256';
COMMENT ON COLUMN device_enrollments.used_at IS 'Device Secret 발급 완료 시각';
COMMENT ON COLUMN device_enrollments.attempt_count IS '등록 완료 검증 실패 횟수';
COMMENT ON COLUMN device_enrollments.max_attempts IS '허용되는 최대 검증 실패 횟수';
COMMENT ON COLUMN device_enrollments.created_by IS 'QR 등록을 시작한 사용자';
