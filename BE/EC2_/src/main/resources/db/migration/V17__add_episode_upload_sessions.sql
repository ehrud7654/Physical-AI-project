CREATE TABLE episode_upload_sessions (
    id uuid PRIMARY KEY,
    episode_id uuid NOT NULL UNIQUE,
    training_job_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    umi_device_id uuid NOT NULL,
    session_id uuid NOT NULL,
    content_type varchar(150) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 varchar(64) NOT NULL,
    metadata jsonb NOT NULL,
    summary jsonb NOT NULL,
    bucket_name varchar(255) NOT NULL,
    object_key varchar(500) NOT NULL,
    status varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    completed_at timestamptz,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT fk_episode_upload_training_job FOREIGN KEY (training_job_id)
        REFERENCES training_jobs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_episode_upload_owner FOREIGN KEY (owner_id)
        REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_episode_upload_device FOREIGN KEY (umi_device_id)
        REFERENCES devices(id) ON DELETE RESTRICT,
    CONSTRAINT uq_episode_upload_identity UNIQUE (owner_id, umi_device_id, session_id),
    CONSTRAINT uq_episode_upload_object UNIQUE (bucket_name, object_key),
    CONSTRAINT ck_episode_upload_content_type CHECK (content_type = 'application/zip'),
    CONSTRAINT ck_episode_upload_size CHECK (size_bytes > 0),
    CONSTRAINT ck_episode_upload_sha256 CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_episode_upload_status CHECK (status IN ('PENDING', 'VALIDATING', 'EXPIRED')),
    CONSTRAINT ck_episode_upload_completion CHECK (
        (status = 'VALIDATING' AND completed_at IS NOT NULL)
        OR (status IN ('PENDING', 'EXPIRED') AND completed_at IS NULL)
    )
);

CREATE INDEX ix_episode_upload_training_job_status
    ON episode_upload_sessions(training_job_id, status);
CREATE INDEX ix_episode_upload_expiration
    ON episode_upload_sessions(status, expires_at);
