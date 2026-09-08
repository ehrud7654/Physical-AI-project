ALTER TABLE deployments
    ADD CONSTRAINT uq_deployments_identity_robot_task
        UNIQUE (id, robot_id, task_version_id);

CREATE TABLE robot_executions (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL,
    robot_id uuid NOT NULL,
    task_version_id uuid NOT NULL,
    deployment_id uuid NOT NULL,
    status varchar(30) NOT NULL,
    current_step integer,
    total_steps integer NOT NULL,
    progress_percent numeric(5,2) DEFAULT 0 NOT NULL,
    last_report_id varchar(150),
    last_report_sequence bigint DEFAULT 0 NOT NULL,
    last_report_hash char(64),
    error_code varchar(100),
    error_message text,
    recovery_action varchar(40),
    requested_at timestamptz DEFAULT now() NOT NULL,
    started_at timestamptz,
    finished_at timestamptz,
    updated_at timestamptz DEFAULT now() NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT fk_robot_executions_owner FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_robot_executions_robot FOREIGN KEY (robot_id) REFERENCES robots(id) ON DELETE RESTRICT,
    CONSTRAINT fk_robot_executions_task_version FOREIGN KEY (task_version_id) REFERENCES task_versions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_robot_executions_deployment FOREIGN KEY (deployment_id) REFERENCES deployments(id) ON DELETE RESTRICT,
    CONSTRAINT fk_robot_executions_deployment_context
        FOREIGN KEY (deployment_id, robot_id, task_version_id)
        REFERENCES deployments(id, robot_id, task_version_id) ON DELETE RESTRICT,
    CONSTRAINT ck_robot_executions_status CHECK (status IN (
        'REQUESTED', 'STARTING', 'RUNNING', 'CANCEL_REQUESTED',
        'CANCELED', 'SUCCEEDED', 'FAILED', 'STATUS_UNKNOWN'
    )),
    CONSTRAINT ck_robot_executions_current_step CHECK (current_step IS NULL OR current_step >= 1),
    CONSTRAINT ck_robot_executions_total_steps CHECK (total_steps >= 1),
    CONSTRAINT ck_robot_executions_step_range CHECK (current_step IS NULL OR current_step <= total_steps),
    CONSTRAINT ck_robot_executions_progress CHECK (progress_percent >= 0 AND progress_percent <= 100),
    CONSTRAINT ck_robot_executions_report_sequence CHECK (last_report_sequence >= 0),
    CONSTRAINT ck_robot_executions_report_consistency CHECK (
        (last_report_sequence = 0 AND last_report_id IS NULL AND last_report_hash IS NULL)
        OR
        (last_report_sequence > 0 AND last_report_id IS NOT NULL AND last_report_hash ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_robot_executions_error CHECK (
        (status = 'FAILED' AND error_code IS NOT NULL AND error_message IS NOT NULL)
        OR
        (status <> 'FAILED')
    ),
    CONSTRAINT ck_robot_executions_started_at CHECK (
        status = 'REQUESTED' OR started_at IS NOT NULL
    ),
    CONSTRAINT ck_robot_executions_finished_at CHECK (
        (status IN ('CANCELED', 'SUCCEEDED', 'FAILED') AND finished_at IS NOT NULL)
        OR
        (status NOT IN ('CANCELED', 'SUCCEEDED', 'FAILED') AND finished_at IS NULL)
    ),
    CONSTRAINT ck_robot_executions_time_order CHECK (
        (started_at IS NULL OR started_at >= requested_at)
        AND (finished_at IS NULL OR finished_at >= COALESCE(started_at, requested_at))
        AND updated_at >= requested_at
    )
);

CREATE UNIQUE INDEX uq_robot_active_execution
    ON robot_executions (robot_id)
    WHERE status IN ('REQUESTED', 'STARTING', 'RUNNING', 'CANCEL_REQUESTED', 'STATUS_UNKNOWN');

CREATE UNIQUE INDEX uq_robot_execution_report_id
    ON robot_executions (robot_id, last_report_id)
    WHERE last_report_id IS NOT NULL;

CREATE INDEX ix_robot_executions_owner_requested
    ON robot_executions (owner_id, requested_at DESC, id DESC);

CREATE INDEX ix_robot_executions_robot_status
    ON robot_executions (robot_id, status, requested_at DESC);


CREATE INDEX ix_robot_executions_robot_requested
    ON robot_executions (robot_id, requested_at DESC, id DESC);

CREATE INDEX ix_robot_executions_task_version
    ON robot_executions (task_version_id);

CREATE INDEX ix_robot_executions_deployment
    ON robot_executions (deployment_id);

COMMENT ON TABLE robot_executions IS '웹 사용자가 요청한 로봇 TaskVersion 실행과 Jetson 보고의 최종 상태';
COMMENT ON COLUMN robot_executions.last_report_hash IS '동일 reportId/sequence 재전송의 본문 동일성 확인용 SHA-256';
COMMENT ON COLUMN robot_executions.recovery_action IS '오류 발생 시 FE가 안내할 복구 행동 코드';
