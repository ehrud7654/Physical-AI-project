ALTER TABLE outbox_events DROP CONSTRAINT outbox_events_status_check;

ALTER TABLE outbox_events
    ADD COLUMN locked_by varchar(100),
    ADD COLUMN locked_at timestamptz,
    ADD COLUMN lease_expires_at timestamptz,
    ADD COLUMN aggregate_sequence bigint,
    ADD COLUMN row_version bigint DEFAULT 0 NOT NULL,
    ADD CONSTRAINT ck_outbox_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD')),
    ADD CONSTRAINT ck_outbox_claim_consistency CHECK (
        (status = 'PROCESSING' AND locked_by IS NOT NULL AND locked_at IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR
        (status <> 'PROCESSING' AND locked_by IS NULL AND locked_at IS NULL AND lease_expires_at IS NULL)
    ),
    ADD CONSTRAINT ck_outbox_attempt CHECK (attempt >= 0),
    ADD CONSTRAINT ck_outbox_sequence CHECK (aggregate_sequence IS NULL OR aggregate_sequence >= 1),
    ADD CONSTRAINT uq_outbox_aggregate_sequence
        UNIQUE (aggregate_type, aggregate_id, aggregate_sequence);

CREATE INDEX ix_outbox_publishable
    ON outbox_events (available_at, created_at)
    WHERE status IN ('PENDING', 'FAILED');

CREATE INDEX ix_outbox_expired_processing
    ON outbox_events (lease_expires_at)
    WHERE status = 'PROCESSING';

CREATE INDEX ix_outbox_published_cleanup
    ON outbox_events (published_at)
    WHERE status = 'PUBLISHED';
