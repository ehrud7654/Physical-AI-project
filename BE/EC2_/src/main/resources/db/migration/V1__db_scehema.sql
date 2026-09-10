-- PostgreSQL 17 / DB스키마v1.md (문서 버전 1.0.0)
-- 빈 데이터베이스의 public 스키마에서 한 번 실행하는 초기 DDL.
-- 데이터베이스/계정 자체는 Compose 또는 운영 관리자가 생성한다.
-- 요청된 파일명/경로는 Flyway 기본 탐색 규칙과 다르다.
-- 기본 Flyway 사용 시 db/migration/V1__db_schema.sql로 이동/변경해야 한다.
-- UUID는 호출 주체가 발급한다(특히 GPU가 예약한 EpisodeArchive ID 유지).
-- updated_at 갱신 및 row_version 증가는 애플리케이션 책임이다.
-- FK는 추적성 보존을 위해 ON DELETE RESTRICT. TaskStep도 무조건 CASCADE하지 않는다.
-- 업무 상태 전이/소유자 일치/입력 고정/불변 메타데이터/Outbox 원자성은 서비스 트랜잭션 책임이다.
SET search_path TO public;

-- users
CREATE TABLE users (
    id uuid NOT NULL PRIMARY KEY,
    email varchar(320) NOT NULL,
    password_hash varchar(255) NOT NULL,
    name varchar(100) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CHECK (status IN ('ACTIVE', 'LOCKED', 'WITHDRAWN'))
);

-- refresh_sessions
CREATE TABLE refresh_sessions (
    id uuid NOT NULL PRIMARY KEY,
    user_id uuid NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    replaced_by_session_id uuid,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_used_at timestamptz,
    UNIQUE (token_hash),
    CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

-- devices
CREATE TABLE devices (
    id uuid NOT NULL PRIMARY KEY,
    owner_id uuid NOT NULL,
    device_type varchar(20) NOT NULL,
    handle_number varchar(100) NOT NULL,
    name varchar(100) NOT NULL,
    connection_status varchar(20) NOT NULL,
    firmware_version varchar(100),
    runtime_version varchar(100),
    last_seen_at timestamptz,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (handle_number),
    CHECK (device_type IN ('UMI_Camera', 'JETSON')),
    CHECK (connection_status IN ('ONLINE', 'OFFLINE'))
);

-- device_credentials
CREATE TABLE device_credentials (
    device_id uuid NOT NULL PRIMARY KEY,
    device_secret_hash varchar(255) NOT NULL,
    registration_code_hash varchar(255),
    registration_code_expires_at timestamptz,
    secret_rotated_at timestamptz,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- storage_nodes
CREATE TABLE storage_nodes (
    id uuid NOT NULL PRIMARY KEY,
    worker_id varchar(100) NOT NULL,
    node_key varchar(100) NOT NULL,
    name varchar(150) NOT NULL,
    storage_type varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    capacity_bytes bigint NOT NULL,
    available_bytes bigint NOT NULL,
    last_seen_at timestamptz,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    UNIQUE (worker_id),
    UNIQUE (worker_id, node_key),
    CHECK (capacity_bytes >= 0),
    CHECK (available_bytes >= 0 AND available_bytes <= capacity_bytes),
    CHECK (storage_type IN ('LOCAL')),
    CHECK (status IN ('ONLINE', 'OFFLINE', 'DRAINING'))
);

-- retention_policies
CREATE TABLE retention_policies (
    id uuid NOT NULL PRIMARY KEY,
    name varchar(100) NOT NULL,
    default_retention_days integer NOT NULL,
    post_training_grace_days integer NOT NULL,
    max_retention_days integer NOT NULL,
    max_delete_attempts integer NOT NULL,
    allow_manual_extension boolean NOT NULL,
    allow_manual_purge boolean NOT NULL,
    is_default boolean NOT NULL,
    active boolean NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (name),
    CHECK (default_retention_days >= 0),
    CHECK (post_training_grace_days >= 0),
    CHECK (max_retention_days >= default_retention_days),
    CHECK (max_delete_attempts >= 1)
);

-- episodes
CREATE TABLE episodes (
    id uuid NOT NULL PRIMARY KEY,
    owner_id uuid NOT NULL,
    umi_device_id uuid NOT NULL,
    storage_node_id uuid NOT NULL,
    retention_policy_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    session_id varchar(150) NOT NULL,
    source varchar(10) NOT NULL,
    success boolean,
    label_status varchar(20) NOT NULL,
    label_source varchar(20),
    labeled_at timestamptz,
    contract_version varchar(100) NOT NULL,
    meta_schema_version varchar(50) NOT NULL,
    summary_schema_version varchar(50) NOT NULL,
    status varchar(30) NOT NULL,
    started_at timestamptz NOT NULL,
    ended_at timestamptz NOT NULL,
    start_monotonic_ns bigint NOT NULL,
    timestamp_timebase varchar(30) NOT NULL,
    first_timestamp_ns bigint NOT NULL,
    last_timestamp_ns bigint NOT NULL,
    duration_ns bigint NOT NULL,
    frame_count integer NOT NULL,
    pose_sample_count integer NOT NULL,
    imu_sample_count integer NOT NULL,
    gripper_sample_count integer NOT NULL,
    missing_frame_count integer NOT NULL,
    tracking_gap_count integer NOT NULL,
    tracking_lost_duration_ns bigint NOT NULL,
    quality_status varchar(20) NOT NULL,
    capture_metadata jsonb NOT NULL,
    quality_summary jsonb NOT NULL,
    validated_at timestamptz,
    last_used_at timestamptz,
    retention_expires_at timestamptz NOT NULL,
    deleted_at timestamptz,
    error_code varchar(100),
    error_message text,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    UNIQUE (owner_id, umi_device_id, session_id),
    UNIQUE (id, storage_node_id),
    CHECK (source IN ('SIM', 'REAL')),
    CHECK (label_status IN ('UNLABELED', 'LABELED')),
    CHECK ((label_status = 'LABELED' AND success IS NOT NULL AND label_source IS NOT NULL AND labeled_at IS NOT NULL)
    OR (label_status = 'UNLABELED' AND success IS NULL AND label_source IS NULL AND labeled_at IS NULL)),
    CHECK (ended_at >= started_at),
    CHECK (start_monotonic_ns >= 0),
    CHECK (timestamp_timebase = 'MONOTONIC_NS'),
    CHECK (first_timestamp_ns >= start_monotonic_ns),
    CHECK (last_timestamp_ns >= first_timestamp_ns),
    CHECK (duration_ns = last_timestamp_ns - first_timestamp_ns),
    CHECK (frame_count >= 1),
    CHECK (pose_sample_count >= 1),
    CHECK (imu_sample_count >= 1),
    CHECK (gripper_sample_count >= 1),
    CHECK (missing_frame_count >= 0),
    CHECK (tracking_gap_count >= 0),
    CHECK (tracking_lost_duration_ns >= 0),
    CHECK (quality_status IN ('PENDING', 'VALID', 'WARNING', 'INVALID')),
    CHECK (label_source IN ('UMI', 'USER', 'SERVER')),
    CHECK (status IN ('UPLOADING', 'VALIDATING', 'READY', 'INVALID', 'DELETING', 'DELETED', 'DELETE_FAILED'))
);

-- episode_archives
CREATE TABLE episode_archives (
    id uuid NOT NULL PRIMARY KEY,
    episode_id uuid NOT NULL,
    storage_node_id uuid NOT NULL,
    storage_key varchar(500) NOT NULL,
    original_filename varchar(255) NOT NULL,
    content_type varchar(150) NOT NULL,
    archive_format_version varchar(100) NOT NULL,
    compression_method varchar(20) NOT NULL,
    size_bytes bigint NOT NULL,
    uncompressed_size_bytes bigint NOT NULL,
    entry_count integer NOT NULL,
    sha256 varchar(64) NOT NULL,
    manifest_summary jsonb NOT NULL,
    status varchar(30) NOT NULL,
    stored_at timestamptz,
    validated_at timestamptz,
    deleted_at timestamptz,
    error_code varchar(100),
    error_message text,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (episode_id),
    UNIQUE (storage_node_id, storage_key),
    CHECK (content_type = 'application/zip'),
    CHECK (compression_method = 'STORE'),
    CHECK (size_bytes > 0),
    CHECK (uncompressed_size_bytes > 0),
    CHECK (entry_count >= 7),
    CHECK (sha256 ~ '^[0-9a-f]{64}$'),
    CHECK (storage_key !~ '^/'),
    CHECK (storage_key !~ '(^|/)\.\.?(/|$)'),
    CHECK (storage_key !~ '[\\]'),
    CHECK (status IN ('UPLOADING', 'AVAILABLE', 'DELETING', 'DELETED', 'DELETE_FAILED'))
);

-- skills
CREATE TABLE skills (
    id uuid NOT NULL PRIMARY KEY,
    skill_key varchar(100) NOT NULL,
    tier varchar(20) NOT NULL,
    status varchar(30) NOT NULL,
    display_name varchar(150) NOT NULL,
    description text,
    needs_vlm boolean DEFAULT false NOT NULL,
    persona text,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (skill_key),
    CHECK (tier IN ('TUTORIAL', 'DEMO')),
    CHECK (status IN ('PLANNED', 'COLLECTING', 'TRAINING', 'GATE_FAILED', 'DEPLOYED')),
    CHECK (tier <> 'DEMO' OR persona IS NOT NULL),
    CHECK (skill_key ~ '^[a-z0-9][a-z0-9_-]{0,99}$')
);

-- training_profiles
CREATE TABLE training_profiles (
    id uuid NOT NULL PRIMARY KEY,
    profile_key varchar(100) NOT NULL,
    version integer NOT NULL,
    status varchar(20) NOT NULL,
    algorithm varchar(100) NOT NULL,
    hyperparameters jsonb NOT NULL,
    config_sha256 varchar(64) NOT NULL,
    description text,
    activated_at timestamptz,
    retired_at timestamptz,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (profile_key, version),
    UNIQUE (id, config_sha256),
    CHECK (version >= 1),
    CHECK (status IN ('ACTIVE', 'RETIRED')),
    CHECK (config_sha256 ~ '^[0-9a-f]{64}$'),
    CHECK (config_sha256 IN ('algorithm', 'hyperparameters'))
);

-- training_jobs
CREATE TABLE training_jobs (
    id uuid NOT NULL PRIMARY KEY,
    owner_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    umi_device_id uuid NOT NULL,
    training_profile_id uuid NOT NULL,
    assigned_storage_node_id uuid,
    assigned_worker_id varchar(100),
    input_mode varchar(30) NOT NULL,
    status varchar(30) NOT NULL,
    attempt integer DEFAULT 1 NOT NULL,
    lease_token_hash varchar(64),
    lease_expires_at timestamptz,
    training_config_snapshot jsonb NOT NULL,
    training_config_sha256 varchar(64) NOT NULL,
    dataset_query jsonb NOT NULL,
    gpu_index integer,
    git_rev varchar(100),
    expected_episode_count integer DEFAULT 0 NOT NULL,
    received_episode_count integer DEFAULT 0 NOT NULL,
    received_bytes bigint DEFAULT 0 NOT NULL,
    episode_count integer NOT NULL,
    input_snapshot_sha256 varchar(64),
    progress_stage varchar(100),
    progress_percent numeric(5,2) NOT NULL,
    current_epoch integer,
    total_epochs integer,
    result_skill_version_id uuid,
    error_code varchar(100),
    error_message text,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    started_at timestamptz,
    model_completed_at timestamptz,
    finished_at timestamptz,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CHECK (attempt >= 1),
    CHECK (gpu_index IS NULL OR gpu_index >= 0),
    CHECK (training_config_sha256 ~ '^[0-9a-f]{64}$'),
    CHECK (expected_episode_count >= 0),
    CHECK (received_episode_count >= 0),
    CHECK (received_bytes >= 0),
    CHECK (episode_count >= 0),
    CHECK (progress_percent >= 0 AND progress_percent <= 100),
    CHECK (model_completed_at IS NULL OR started_at IS NULL OR model_completed_at >= started_at),
    CHECK (finished_at IS NULL OR model_completed_at IS NULL OR finished_at >= model_completed_at),
    UNIQUE (id, training_profile_id, training_config_sha256),
    CHECK (input_mode IN ('COLLECT_NEW', 'EXISTING_EPISODES')),
    CHECK (status IN ('CREATED', 'COLLECTING', 'INPUT_VALIDATING', 'INPUT_READY', 'QUEUED', 'PREPARING', 'RUNNING', 'MODEL_UPLOADING', 'MODEL_VALIDATING', 'CANCEL_REQUESTED', 'CANCELED', 'SUCCEEDED', 'FAILED')),
    CHECK (lease_token_hash ~ '^[0-9a-f]{64}$'),
    CHECK (input_snapshot_sha256 ~ '^[0-9a-f]{64}$')
);

-- training_job_episodes
CREATE TABLE training_job_episodes (
    training_job_id uuid NOT NULL,
    episode_id uuid NOT NULL,
    input_order integer NOT NULL,
    archive_sha256_snapshot varchar(64) NOT NULL,
    contract_version_snapshot varchar(100) NOT NULL,
    source_snapshot varchar(10) NOT NULL,
    label_status_snapshot varchar(20) NOT NULL,
    success_snapshot boolean,
    quality_status_snapshot varchar(20) NOT NULL,
    attached_at timestamptz NOT NULL,
    PRIMARY KEY (training_job_id, episode_id),
    UNIQUE (training_job_id, input_order),
    CHECK (input_order >= 1),
    CHECK (archive_sha256_snapshot ~ '^[0-9a-f]{64}$'),
    CHECK (source_snapshot IN ('SIM', 'REAL')),
    CHECK (quality_status_snapshot IN ('VALID', 'WARNING')),
    CHECK (label_status_snapshot IN ('UNLABELED', 'LABELED'))
);

-- training_job_events
CREATE TABLE training_job_events (
    id uuid NOT NULL PRIMARY KEY,
    training_job_id uuid NOT NULL,
    attempt integer NOT NULL,
    event_id varchar(150) NOT NULL,
    sequence bigint NOT NULL,
    event_type varchar(100) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    UNIQUE (training_job_id, attempt, event_id),
    UNIQUE (training_job_id, attempt, sequence)
);

-- robot_profiles
CREATE TABLE robot_profiles (
    id uuid NOT NULL PRIMARY KEY,
    name varchar(150) NOT NULL,
    contract_version varchar(100) NOT NULL,
    architecture varchar(100) NOT NULL,
    runtime varchar(100) NOT NULL,
    runtime_version varchar(100) NOT NULL,
    camera_contract jsonb NOT NULL,
    state_contract jsonb NOT NULL,
    action_contract jsonb NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (name, contract_version, runtime, runtime_version)
);

-- skill_versions
CREATE TABLE skill_versions (
    id uuid NOT NULL PRIMARY KEY,
    skill_id uuid NOT NULL,
    training_job_id uuid NOT NULL,
    training_profile_id uuid NOT NULL,
    training_config_sha256 varchar(64) NOT NULL,
    robot_profile_id uuid NOT NULL,
    version integer NOT NULL,
    status varchar(30) NOT NULL,
    checkpoint_bucket varchar(255) NOT NULL,
    checkpoint_key varchar(700) NOT NULL,
    checkpoint_sha256 varchar(64) NOT NULL,
    checkpoint_bytes bigint NOT NULL,
    architecture varchar(100) NOT NULL,
    framework varchar(100) NOT NULL,
    framework_version varchar(100) NOT NULL,
    contract_version varchar(100) NOT NULL,
    action_space varchar(30) NOT NULL,
    n_params bigint NOT NULL,
    control_rate_hz numeric(10,3) NOT NULL,
    camera_names jsonb NOT NULL,
    input_schema jsonb NOT NULL,
    output_schema jsonb NOT NULL,
    gate_metric varchar(100),
    gate_n integer,
    gate_runs integer,
    gate_value double precision,
    gate_ci_low double precision,
    gate_ci_high double precision,
    gate_measured_at timestamptz,
    gate_passed boolean GENERATED ALWAYS AS (
  COALESCE(
    gate_metric = 'rollout_success'
    AND gate_n >= 100
    AND gate_runs >= 3
    AND gate_value > 0.20
    AND gate_ci_low > 0.20,
    false
  )
) STORED NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    validated_at timestamptz,
    archived_at timestamptz,
    UNIQUE (skill_id, version),
    UNIQUE (training_job_id),
    UNIQUE (checkpoint_bucket, checkpoint_key),
    CHECK (version >= 1),
    CHECK (checkpoint_bytes >= 0),
    CHECK (checkpoint_sha256 ~ '^[0-9a-f]{64}$'),
    CHECK (training_config_sha256 ~ '^[0-9a-f]{64}$'),
    CHECK (checkpoint_key !~ '^/'),
    CHECK (octet_length(checkpoint_key) <= 1024),
    CHECK (action_space IN ('JOINT_ABSOLUTE', 'JOINT_DELTA')),
    CHECK (n_params > 0),
    CONSTRAINT ck_skill_versions_gate_complete CHECK (
  (gate_metric IS NULL AND gate_n IS NULL AND gate_runs IS NULL
    AND gate_value IS NULL AND gate_ci_low IS NULL
    AND gate_ci_high IS NULL AND gate_measured_at IS NULL)
  OR
  (gate_metric IS NOT NULL AND gate_n IS NOT NULL AND gate_runs IS NOT NULL
    AND gate_value IS NOT NULL AND gate_ci_low IS NOT NULL
    AND gate_ci_high IS NOT NULL AND gate_measured_at IS NOT NULL)
),
    CHECK (gate_n IS NULL OR gate_n > 0),
    CHECK (gate_runs IS NULL OR gate_runs > 0),
    CHECK (gate_value IS NULL OR gate_value BETWEEN 0 AND 1),
    CHECK (gate_ci_low IS NULL OR gate_ci_low BETWEEN 0 AND 1),
    CHECK (gate_ci_high IS NULL OR gate_ci_high BETWEEN 0 AND 1),
    CHECK (gate_ci_low IS NULL OR gate_ci_high IS NULL OR gate_ci_low <= gate_ci_high),
    UNIQUE (id, gate_passed),
    UNIQUE (id, skill_id),
    CHECK (status IN ('UPLOADING', 'VALIDATING', 'READY', 'FAILED', 'ARCHIVED'))
);

-- skill_destinations
CREATE TABLE skill_destinations (
    skill_id uuid NOT NULL,
    destination_key varchar(100) NOT NULL,
    display_name varchar(150) NOT NULL,
    enabled boolean NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (skill_id, destination_key)
);

-- vlm_model_versions
CREATE TABLE vlm_model_versions (
    id uuid NOT NULL PRIMARY KEY,
    version varchar(100) NOT NULL,
    model_name varchar(150) NOT NULL,
    status varchar(20) NOT NULL,
    model_metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (version),
    CHECK (status IN ('CANDIDATE', 'READY', 'ARCHIVED'))
);

-- vlm_policies
CREATE TABLE vlm_policies (
    id uuid NOT NULL PRIMARY KEY,
    policy_key varchar(100) NOT NULL,
    version integer NOT NULL,
    confidence_threshold numeric(5,4) NOT NULL,
    enabled boolean NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (policy_key, version),
    CHECK (version >= 1),
    CHECK (confidence_threshold >= 0 AND confidence_threshold <= 1)
);

-- vlm_inference_results
CREATE TABLE vlm_inference_results (
    id uuid NOT NULL PRIMARY KEY,
    request_id varchar(150) NOT NULL,
    robot_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    vlm_model_version_id uuid,
    vlm_policy_id uuid NOT NULL,
    destination_key varchar(100) NOT NULL,
    target_description_ko text NOT NULL,
    bbox_x1 numeric(7,6) NOT NULL,
    bbox_y1 numeric(7,6) NOT NULL,
    bbox_x2 numeric(7,6) NOT NULL,
    bbox_y2 numeric(7,6) NOT NULL,
    confidence numeric(5,4) NOT NULL,
    confidence_threshold numeric(5,4) NOT NULL,
    abstain boolean NOT NULL,
    motion_allowed boolean GENERATED ALWAYS AS (
  NOT abstain AND confidence >= confidence_threshold
) STORED NOT NULL,
    raw_output jsonb NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (request_id),
    CHECK (bbox_x1 BETWEEN 0 AND 1 AND bbox_x2 BETWEEN 0 AND 1 AND bbox_x1 < bbox_x2),
    CHECK (bbox_y1 BETWEEN 0 AND 1 AND bbox_y2 BETWEEN 0 AND 1 AND bbox_y1 < bbox_y2),
    CHECK (confidence BETWEEN 0 AND 1),
    CHECK (confidence_threshold BETWEEN 0 AND 1)
);

-- data_purge_jobs
CREATE TABLE data_purge_jobs (
    id uuid NOT NULL PRIMARY KEY,
    episode_id uuid NOT NULL,
    storage_node_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    attempt integer DEFAULT 0 NOT NULL,
    assigned_worker_id varchar(100),
    lease_token_hash varchar(64),
    lease_expires_at timestamptz,
    scheduled_at timestamptz NOT NULL,
    started_at timestamptz,
    finished_at timestamptz,
    error_code varchar(100),
    error_message text,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CHECK (attempt >= 0),
    CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CHECK (lease_token_hash ~ '^[0-9a-f]{64}$')
);

-- data_purge_reports
CREATE TABLE data_purge_reports (
    id uuid NOT NULL PRIMARY KEY,
    data_purge_job_id uuid NOT NULL,
    report_id varchar(150) NOT NULL,
    attempt integer NOT NULL,
    status varchar(20) NOT NULL,
    archive_result jsonb NOT NULL,
    error_code varchar(100),
    error_message text,
    reported_at timestamptz NOT NULL,
    UNIQUE (data_purge_job_id, report_id)
);

-- tasks
CREATE TABLE tasks (
    id uuid NOT NULL PRIMARY KEY,
    owner_id uuid NOT NULL,
    task_key varchar(100) NOT NULL,
    display_name varchar(150) NOT NULL,
    description text,
    latest_version integer,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (owner_id, task_key)
);

-- task_versions
CREATE TABLE task_versions (
    id uuid NOT NULL PRIMARY KEY,
    task_id uuid NOT NULL,
    version integer NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (task_id, version),
    CHECK (version >= 1)
);

-- task_steps
CREATE TABLE task_steps (
    id uuid NOT NULL PRIMARY KEY,
    task_version_id uuid NOT NULL,
    skill_version_id uuid NOT NULL,
    step_order integer NOT NULL,
    parameters jsonb DEFAULT '{}'::jsonb NOT NULL,
    timeout_seconds integer,
    UNIQUE (task_version_id, step_order),
    CHECK (step_order >= 1),
    CHECK (timeout_seconds IS NULL OR timeout_seconds >= 1)
);

-- robots
CREATE TABLE robots (
    id uuid NOT NULL PRIMARY KEY,
    owner_id uuid NOT NULL,
    jetson_device_id uuid NOT NULL,
    robot_profile_id uuid NOT NULL,
    name varchar(150) NOT NULL,
    connection_status varchar(20) NOT NULL,
    current_deployment_id uuid,
    last_seen_at timestamptz,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    UNIQUE (jetson_device_id),
    CHECK (connection_status IN ('ONLINE', 'OFFLINE'))
);

-- deployments
CREATE TABLE deployments (
    id uuid NOT NULL PRIMARY KEY,
    robot_id uuid NOT NULL,
    task_version_id uuid NOT NULL,
    status varchar(30) NOT NULL,
    progress_percent numeric(5,2) NOT NULL,
    last_report_sequence bigint DEFAULT 0 NOT NULL,
    error_code varchar(100),
    error_message text,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    applied_at timestamptz,
    row_version bigint DEFAULT 0 NOT NULL,
    CHECK (progress_percent >= 0 AND progress_percent <= 100),
    CHECK (status IN ('PENDING', 'DOWNLOADING', 'VERIFYING', 'APPLYING', 'SUCCEEDED', 'FAILED', 'CANCELED'))
);

-- deployment_items
CREATE TABLE deployment_items (
    id uuid NOT NULL PRIMARY KEY,
    deployment_id uuid NOT NULL,
    skill_version_id uuid NOT NULL,
    gate_passed boolean NOT NULL,
    vlm_model_version_id uuid,
    status varchar(30) NOT NULL,
    downloaded_at timestamptz,
    verified_at timestamptz,
    error_code varchar(100),
    error_message text,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (deployment_id, skill_version_id),
    CHECK (gate_passed = true),
    CHECK (status IN ('PENDING', 'DOWNLOADING', 'VERIFIED', 'FAILED'))
);

-- deployment_reports
CREATE TABLE deployment_reports (
    id uuid NOT NULL PRIMARY KEY,
    deployment_id uuid NOT NULL,
    report_id varchar(150) NOT NULL,
    sequence bigint NOT NULL,
    status varchar(30) NOT NULL,
    progress_percent numeric(5,2) NOT NULL,
    items jsonb NOT NULL,
    error jsonb,
    reported_at timestamptz NOT NULL,
    UNIQUE (deployment_id, report_id),
    UNIQUE (deployment_id, sequence)
);

-- robot_skill_deployments
CREATE TABLE robot_skill_deployments (
    id uuid NOT NULL PRIMARY KEY,
    robot_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    skill_version_id uuid NOT NULL,
    deployment_id uuid NOT NULL,
    vlm_model_version_id uuid,
    status varchar(20) NOT NULL,
    deployed_at timestamptz NOT NULL,
    retired_at timestamptz,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CHECK (status IN ('ACTIVE', 'RETIRED')),
    CHECK (
  (status = 'ACTIVE' AND retired_at IS NULL)
  OR (status = 'RETIRED' AND retired_at IS NOT NULL)
)
);

-- idempotency_keys
CREATE TABLE idempotency_keys (
    id uuid NOT NULL PRIMARY KEY,
    actor_type varchar(30) NOT NULL,
    actor_id varchar(150) NOT NULL,
    http_method varchar(10) NOT NULL,
    request_path varchar(500) NOT NULL,
    idempotency_key varchar(200) NOT NULL,
    request_hash varchar(64) NOT NULL,
    response_status integer,
    response_body jsonb,
    resource_type varchar(100),
    resource_id varchar(150),
    expires_at timestamptz NOT NULL,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (actor_type, actor_id, http_method, request_path, idempotency_key),
    CHECK (actor_type IN ('USER', 'UMI', 'GPU', 'JETSON', 'INTERNAL')),
    CHECK (request_hash ~ '^[0-9a-f]{64}$')
);

-- outbox_events
CREATE TABLE outbox_events (
    id uuid NOT NULL PRIMARY KEY,
    event_id varchar(150) NOT NULL,
    aggregate_type varchar(100) NOT NULL,
    aggregate_id varchar(150) NOT NULL,
    event_type varchar(150) NOT NULL,
    payload jsonb NOT NULL,
    status varchar(20) NOT NULL,
    attempt integer NOT NULL,
    available_at timestamptz NOT NULL,
    published_at timestamptz,
    last_error text,
    created_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UNIQUE (event_id),
    CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

-- 모든 테이블 생성 후 단일/복합/순환 외래키 추가
ALTER TABLE refresh_sessions ADD CONSTRAINT fk_refresh_sessions_1
    FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE refresh_sessions ADD CONSTRAINT fk_refresh_sessions_2
    FOREIGN KEY (replaced_by_session_id) REFERENCES refresh_sessions (id) ON DELETE RESTRICT;
ALTER TABLE devices ADD CONSTRAINT fk_devices_3
    FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE device_credentials ADD CONSTRAINT fk_device_credentials_4
    FOREIGN KEY (device_id) REFERENCES devices (id) ON DELETE RESTRICT;
ALTER TABLE episodes ADD CONSTRAINT fk_episodes_5
    FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE episodes ADD CONSTRAINT fk_episodes_6
    FOREIGN KEY (umi_device_id) REFERENCES devices (id) ON DELETE RESTRICT;
ALTER TABLE episodes ADD CONSTRAINT fk_episodes_7
    FOREIGN KEY (storage_node_id) REFERENCES storage_nodes (id) ON DELETE RESTRICT;
ALTER TABLE episodes ADD CONSTRAINT fk_episodes_8
    FOREIGN KEY (retention_policy_id) REFERENCES retention_policies (id) ON DELETE RESTRICT;
ALTER TABLE episodes ADD CONSTRAINT fk_episodes_9
    FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT;
ALTER TABLE episode_archives ADD CONSTRAINT fk_episode_archives_10
    FOREIGN KEY (episode_id, storage_node_id)
  REFERENCES episodes (id, storage_node_id) ON DELETE RESTRICT;
ALTER TABLE episode_archives ADD CONSTRAINT fk_episode_archives_11
    FOREIGN KEY (episode_id) REFERENCES episodes (id) ON DELETE RESTRICT;
ALTER TABLE episode_archives ADD CONSTRAINT fk_episode_archives_12
    FOREIGN KEY (storage_node_id) REFERENCES storage_nodes (id) ON DELETE RESTRICT;
ALTER TABLE training_jobs ADD CONSTRAINT fk_training_jobs_13
    FOREIGN KEY (training_profile_id, training_config_sha256)
  REFERENCES training_profiles (id, config_sha256) ON DELETE RESTRICT;
ALTER TABLE training_jobs ADD CONSTRAINT fk_training_jobs_14
    FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE training_jobs ADD CONSTRAINT fk_training_jobs_15
    FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT;
ALTER TABLE training_jobs ADD CONSTRAINT fk_training_jobs_16
    FOREIGN KEY (umi_device_id) REFERENCES devices (id) ON DELETE RESTRICT;
ALTER TABLE training_jobs ADD CONSTRAINT fk_training_jobs_17
    FOREIGN KEY (training_profile_id) REFERENCES training_profiles (id) ON DELETE RESTRICT;
ALTER TABLE training_jobs ADD CONSTRAINT fk_training_jobs_18
    FOREIGN KEY (assigned_storage_node_id) REFERENCES storage_nodes (id) ON DELETE RESTRICT;
ALTER TABLE training_jobs ADD CONSTRAINT fk_training_jobs_19
    FOREIGN KEY (result_skill_version_id) REFERENCES skill_versions (id) ON DELETE RESTRICT;
ALTER TABLE training_job_episodes ADD CONSTRAINT fk_training_job_episodes_20
    FOREIGN KEY (training_job_id) REFERENCES training_jobs (id) ON DELETE RESTRICT;
ALTER TABLE training_job_episodes ADD CONSTRAINT fk_training_job_episodes_21
    FOREIGN KEY (episode_id) REFERENCES episodes (id) ON DELETE RESTRICT;
ALTER TABLE training_job_events ADD CONSTRAINT fk_training_job_events_22
    FOREIGN KEY (training_job_id) REFERENCES training_jobs (id) ON DELETE RESTRICT;
ALTER TABLE skill_versions ADD CONSTRAINT fk_skill_versions_23
    FOREIGN KEY (training_profile_id, training_config_sha256)
  REFERENCES training_profiles (id, config_sha256) ON DELETE RESTRICT;
ALTER TABLE skill_versions ADD CONSTRAINT fk_skill_versions_24
    FOREIGN KEY (training_job_id, training_profile_id, training_config_sha256)
  REFERENCES training_jobs (id, training_profile_id, training_config_sha256) ON DELETE RESTRICT;
ALTER TABLE skill_versions ADD CONSTRAINT fk_skill_versions_25
    FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT;
ALTER TABLE skill_versions ADD CONSTRAINT fk_skill_versions_26
    FOREIGN KEY (training_job_id) REFERENCES training_jobs (id) ON DELETE RESTRICT;
ALTER TABLE skill_versions ADD CONSTRAINT fk_skill_versions_27
    FOREIGN KEY (training_profile_id) REFERENCES training_profiles (id) ON DELETE RESTRICT;
ALTER TABLE skill_versions ADD CONSTRAINT fk_skill_versions_28
    FOREIGN KEY (robot_profile_id) REFERENCES robot_profiles (id) ON DELETE RESTRICT;
ALTER TABLE skill_destinations ADD CONSTRAINT fk_skill_destinations_29
    FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT;
ALTER TABLE vlm_inference_results ADD CONSTRAINT fk_vlm_inference_results_30
    FOREIGN KEY (skill_id, destination_key)
  REFERENCES skill_destinations (skill_id, destination_key) ON DELETE RESTRICT;
ALTER TABLE vlm_inference_results ADD CONSTRAINT fk_vlm_inference_results_31
    FOREIGN KEY (robot_id) REFERENCES robots (id) ON DELETE RESTRICT;
ALTER TABLE vlm_inference_results ADD CONSTRAINT fk_vlm_inference_results_32
    FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT;
ALTER TABLE vlm_inference_results ADD CONSTRAINT fk_vlm_inference_results_33
    FOREIGN KEY (vlm_model_version_id) REFERENCES vlm_model_versions (id) ON DELETE RESTRICT;
ALTER TABLE vlm_inference_results ADD CONSTRAINT fk_vlm_inference_results_34
    FOREIGN KEY (vlm_policy_id) REFERENCES vlm_policies (id) ON DELETE RESTRICT;
ALTER TABLE data_purge_jobs ADD CONSTRAINT fk_data_purge_jobs_35
    FOREIGN KEY (episode_id) REFERENCES episodes (id) ON DELETE RESTRICT;
ALTER TABLE data_purge_jobs ADD CONSTRAINT fk_data_purge_jobs_36
    FOREIGN KEY (storage_node_id) REFERENCES storage_nodes (id) ON DELETE RESTRICT;
ALTER TABLE data_purge_reports ADD CONSTRAINT fk_data_purge_reports_37
    FOREIGN KEY (data_purge_job_id) REFERENCES data_purge_jobs (id) ON DELETE RESTRICT;
ALTER TABLE tasks ADD CONSTRAINT fk_tasks_38
    FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE task_versions ADD CONSTRAINT fk_task_versions_39
    FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE RESTRICT;
ALTER TABLE task_steps ADD CONSTRAINT fk_task_steps_40
    FOREIGN KEY (task_version_id) REFERENCES task_versions (id) ON DELETE RESTRICT;
ALTER TABLE task_steps ADD CONSTRAINT fk_task_steps_41
    FOREIGN KEY (skill_version_id) REFERENCES skill_versions (id) ON DELETE RESTRICT;
ALTER TABLE robots ADD CONSTRAINT fk_robots_42
    FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT;
ALTER TABLE robots ADD CONSTRAINT fk_robots_43
    FOREIGN KEY (jetson_device_id) REFERENCES devices (id) ON DELETE RESTRICT;
ALTER TABLE robots ADD CONSTRAINT fk_robots_44
    FOREIGN KEY (robot_profile_id) REFERENCES robot_profiles (id) ON DELETE RESTRICT;
ALTER TABLE robots ADD CONSTRAINT fk_robots_45
    FOREIGN KEY (current_deployment_id) REFERENCES deployments (id) ON DELETE RESTRICT;
ALTER TABLE deployments ADD CONSTRAINT fk_deployments_46
    FOREIGN KEY (robot_id) REFERENCES robots (id) ON DELETE RESTRICT;
ALTER TABLE deployments ADD CONSTRAINT fk_deployments_47
    FOREIGN KEY (task_version_id) REFERENCES task_versions (id) ON DELETE RESTRICT;
ALTER TABLE deployment_items ADD CONSTRAINT fk_deployment_items_48
    FOREIGN KEY (skill_version_id, gate_passed)
  REFERENCES skill_versions (id, gate_passed) ON DELETE RESTRICT;
ALTER TABLE deployment_items ADD CONSTRAINT fk_deployment_items_49
    FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE RESTRICT;
ALTER TABLE deployment_items ADD CONSTRAINT fk_deployment_items_50
    FOREIGN KEY (skill_version_id) REFERENCES skill_versions (id) ON DELETE RESTRICT;
ALTER TABLE deployment_items ADD CONSTRAINT fk_deployment_items_51
    FOREIGN KEY (vlm_model_version_id) REFERENCES vlm_model_versions (id) ON DELETE RESTRICT;
ALTER TABLE deployment_reports ADD CONSTRAINT fk_deployment_reports_52
    FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE RESTRICT;
ALTER TABLE robot_skill_deployments ADD CONSTRAINT fk_robot_skill_deployments_53
    FOREIGN KEY (skill_version_id, skill_id)
  REFERENCES skill_versions (id, skill_id) ON DELETE RESTRICT;
ALTER TABLE robot_skill_deployments ADD CONSTRAINT fk_robot_skill_deployments_54
    FOREIGN KEY (robot_id) REFERENCES robots (id) ON DELETE RESTRICT;
ALTER TABLE robot_skill_deployments ADD CONSTRAINT fk_robot_skill_deployments_55
    FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT;
ALTER TABLE robot_skill_deployments ADD CONSTRAINT fk_robot_skill_deployments_56
    FOREIGN KEY (skill_version_id) REFERENCES skill_versions (id) ON DELETE RESTRICT;
ALTER TABLE robot_skill_deployments ADD CONSTRAINT fk_robot_skill_deployments_57
    FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE RESTRICT;
ALTER TABLE robot_skill_deployments ADD CONSTRAINT fk_robot_skill_deployments_58
    FOREIGN KEY (vlm_model_version_id) REFERENCES vlm_model_versions (id) ON DELETE RESTRICT;

-- 검색 인덱스, 부분 UNIQUE 인덱스 및 명시된 기본값
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email));
CREATE INDEX ix_users_status ON users (status);
CREATE INDEX ix_refresh_sessions_1 ON refresh_sessions (user_id, expires_at);
CREATE INDEX ix_devices_1 ON devices (owner_id, device_type);
CREATE INDEX ix_devices_2 ON devices (connection_status, last_seen_at);
CREATE INDEX ix_storage_nodes_1 ON storage_nodes (status, last_seen_at);
CREATE UNIQUE INDEX uq_retention_policy_default
ON retention_policies (is_default)
WHERE is_default = true;
CREATE INDEX ix_episodes_1 ON episodes (owner_id, status, started_at DESC);
CREATE INDEX ix_episodes_2 ON episodes (umi_device_id, status, started_at DESC);
CREATE INDEX ix_episodes_3 ON episodes (storage_node_id, status);
CREATE INDEX ix_episodes_4 ON episodes (status, retention_expires_at);
CREATE INDEX ix_episodes_5 ON episodes (skill_id, source, label_status, success, contract_version, status);
CREATE INDEX ix_episodes_6 ON episodes (quality_status, started_at DESC);
CREATE INDEX ix_episode_archives_1 ON episode_archives (storage_node_id, status);
CREATE INDEX ix_episode_archives_2 ON episode_archives (sha256);
CREATE INDEX ix_skills_1 ON skills (tier, status);
CREATE UNIQUE INDEX uq_training_profiles_one_active
ON training_profiles ((1))
WHERE status = 'ACTIVE';
CREATE INDEX ix_training_jobs_1 ON training_jobs (owner_id, status, created_at DESC);
CREATE INDEX ix_training_jobs_2 ON training_jobs (skill_id, status, created_at DESC);
CREATE INDEX ix_training_jobs_3 ON training_jobs (assigned_storage_node_id, status);
CREATE INDEX ix_training_jobs_4 ON training_jobs (assigned_worker_id, status);
CREATE INDEX ix_training_jobs_5 ON training_jobs (training_profile_id, status);
CREATE INDEX ix_training_job_episodes_1 ON training_job_episodes (episode_id, training_job_id);
CREATE INDEX ix_training_job_events_1 ON training_job_events (training_job_id, received_at);
CREATE INDEX ix_skill_versions_1 ON skill_versions (skill_id, status, version DESC);
CREATE INDEX ix_skill_versions_2 ON skill_versions (robot_profile_id, status);
CREATE INDEX ix_skill_versions_3 ON skill_versions (training_profile_id);
CREATE INDEX ix_skill_destinations_1 ON skill_destinations (skill_id, enabled);
CREATE INDEX ix_vlm_model_versions_1 ON vlm_model_versions (status, created_at DESC);
CREATE UNIQUE INDEX uq_vlm_policy_enabled
ON vlm_policies (policy_key)
WHERE enabled = true;
CREATE INDEX ix_vlm_inference_results_1 ON vlm_inference_results (robot_id, created_at DESC);
CREATE INDEX ix_vlm_inference_results_2 ON vlm_inference_results (skill_id, created_at DESC);
CREATE INDEX ix_data_purge_jobs_1 ON data_purge_jobs (storage_node_id, status, scheduled_at);
CREATE INDEX ix_data_purge_jobs_2 ON data_purge_jobs (episode_id, created_at DESC);
CREATE UNIQUE INDEX uq_episode_active_purge_job
ON data_purge_jobs (episode_id)
WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX ix_data_purge_reports_1 ON data_purge_reports (data_purge_job_id, reported_at);
CREATE INDEX ix_tasks_1 ON tasks (owner_id, created_at DESC);
CREATE INDEX ix_task_versions_1 ON task_versions (task_id, version DESC);
CREATE INDEX ix_task_steps_1 ON task_steps (skill_version_id);
CREATE INDEX ix_robots_1 ON robots (owner_id, created_at DESC);
CREATE INDEX ix_robots_2 ON robots (connection_status, last_seen_at);
CREATE INDEX ix_deployments_1 ON deployments (robot_id, created_at DESC);
CREATE INDEX ix_deployments_2 ON deployments (robot_id, status);
CREATE UNIQUE INDEX uq_robot_active_deployment
ON deployments (robot_id)
WHERE status IN ('PENDING', 'DOWNLOADING', 'VERIFYING', 'APPLYING');
ALTER TABLE deployment_items ALTER COLUMN gate_passed SET DEFAULT true;
CREATE INDEX ix_deployment_items_1 ON deployment_items (skill_version_id);
CREATE INDEX ix_deployment_items_2 ON deployment_items (vlm_model_version_id);
CREATE INDEX ix_deployment_reports_1 ON deployment_reports (deployment_id, reported_at);
CREATE INDEX ix_robot_skill_deployments_1 ON robot_skill_deployments (robot_id, deployed_at DESC);
CREATE INDEX ix_robot_skill_deployments_2 ON robot_skill_deployments (skill_version_id);
CREATE UNIQUE INDEX uq_robot_active_skill
ON robot_skill_deployments (robot_id, skill_id)
WHERE status = 'ACTIVE';
CREATE INDEX ix_idempotency_keys_1 ON idempotency_keys (expires_at);
CREATE INDEX ix_outbox_events_1 ON outbox_events (status, available_at);
CREATE INDEX ix_outbox_events_2 ON outbox_events (aggregate_type, aggregate_id, created_at);

-- 배포 자격 검증 (문서에 정의된 READY/VLM 검사)
CREATE FUNCTION enforce_deployable_skill_version()
RETURNS trigger AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM skill_versions sv
    JOIN skills s ON s.id = sv.skill_id
    WHERE sv.id = NEW.skill_version_id
      AND sv.status = 'READY'
      AND sv.gate_passed = true
      AND (NOT s.needs_vlm OR NEW.vlm_model_version_id IS NOT NULL)
      AND (
        NEW.vlm_model_version_id IS NULL
        OR EXISTS (
          SELECT 1 FROM vlm_model_versions vm
          WHERE vm.id = NEW.vlm_model_version_id AND vm.status = 'READY'
        )
      )
  ) THEN
    RAISE EXCEPTION 'SkillVersion is not deployable: %', NEW.skill_version_id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_deployment_item_deployable
BEFORE INSERT OR UPDATE OF skill_version_id, vlm_model_version_id ON deployment_items
FOR EACH ROW EXECUTE FUNCTION enforce_deployable_skill_version();

-- 컬럼 계약 설명
COMMENT ON COLUMN users.id IS 'PK';
COMMENT ON COLUMN users.email IS '로그인 이메일';
COMMENT ON COLUMN users.password_hash IS '평문 저장 금지';
COMMENT ON COLUMN users.name IS '표시 이름';
COMMENT ON COLUMN users.status IS '`ACTIVE`, `LOCKED`, `WITHDRAWN`';
COMMENT ON COLUMN users.created_at IS '생성 시각';
COMMENT ON COLUMN users.updated_at IS '수정 시각';
COMMENT ON COLUMN refresh_sessions.id IS 'PK';
COMMENT ON COLUMN refresh_sessions.user_id IS 'FK → `users.id`';
COMMENT ON COLUMN refresh_sessions.token_hash IS 'Refresh Token 원문 저장 금지';
COMMENT ON COLUMN refresh_sessions.expires_at IS '만료 시각';
COMMENT ON COLUMN refresh_sessions.revoked_at IS '폐기 시각';
COMMENT ON COLUMN refresh_sessions.replaced_by_session_id IS '회전된 새 세션 FK';
COMMENT ON COLUMN refresh_sessions.created_at IS '생성 시각';
COMMENT ON COLUMN refresh_sessions.last_used_at IS '마지막 갱신 시각';
COMMENT ON COLUMN devices.id IS 'PK';
COMMENT ON COLUMN devices.owner_id IS 'FK → `users.id`';
COMMENT ON COLUMN devices.device_type IS '`UMI_Camera`, `JETSON`';
COMMENT ON COLUMN devices.handle_number IS '외부 장치 식별자';
COMMENT ON COLUMN devices.name IS '사용자 표시 이름';
COMMENT ON COLUMN devices.connection_status IS '`ONLINE`, `OFFLINE`';
COMMENT ON COLUMN devices.firmware_version IS '마지막 heartbeat 값';
COMMENT ON COLUMN devices.runtime_version IS '마지막 heartbeat 값';
COMMENT ON COLUMN devices.last_seen_at IS '서버 수신 기준 시각';
COMMENT ON COLUMN devices.created_at IS '생성 시각';
COMMENT ON COLUMN devices.updated_at IS '수정 시각';
COMMENT ON COLUMN device_credentials.device_id IS 'PK, FK → `devices.id`';
COMMENT ON COLUMN device_credentials.device_secret_hash IS '장치 secret hash';
COMMENT ON COLUMN device_credentials.registration_code_hash IS '최초 소유 등록 코드 hash';
COMMENT ON COLUMN device_credentials.registration_code_expires_at IS '등록 코드 만료';
COMMENT ON COLUMN device_credentials.secret_rotated_at IS 'secret 회전 시각';
COMMENT ON COLUMN device_credentials.created_at IS '생성 시각';
COMMENT ON COLUMN device_credentials.updated_at IS '수정 시각';
COMMENT ON COLUMN storage_nodes.id IS 'PK';
COMMENT ON COLUMN storage_nodes.worker_id IS 'service token의 worker 범위';
COMMENT ON COLUMN storage_nodes.node_key IS 'worker 내부 논리 노드 키';
COMMENT ON COLUMN storage_nodes.name IS '운영 표시 이름';
COMMENT ON COLUMN storage_nodes.storage_type IS '현재 `LOCAL`만 허용';
COMMENT ON COLUMN storage_nodes.status IS '`ONLINE`, `OFFLINE`, `DRAINING`';
COMMENT ON COLUMN storage_nodes.capacity_bytes IS '0 이상';
COMMENT ON COLUMN storage_nodes.available_bytes IS '0 이상, capacity 이하';
COMMENT ON COLUMN storage_nodes.last_seen_at IS 'heartbeat 수신 시각';
COMMENT ON COLUMN storage_nodes.created_at IS '생성 시각';
COMMENT ON COLUMN storage_nodes.updated_at IS '수정 시각';
COMMENT ON COLUMN storage_nodes.row_version IS '낙관적 잠금, 기본 0';
COMMENT ON COLUMN retention_policies.id IS 'PK';
COMMENT ON COLUMN retention_policies.name IS '정책 이름';
COMMENT ON COLUMN retention_policies.default_retention_days IS '검증 완료 후 기본 보관 일수';
COMMENT ON COLUMN retention_policies.post_training_grace_days IS '마지막 학습 종료 후 유예 일수';
COMMENT ON COLUMN retention_policies.max_retention_days IS '사용자 연장 최대 일수';
COMMENT ON COLUMN retention_policies.max_delete_attempts IS '자동 삭제 최대 시도 횟수';
COMMENT ON COLUMN retention_policies.allow_manual_extension IS '사용자 연장 허용';
COMMENT ON COLUMN retention_policies.allow_manual_purge IS '사용자 조기 삭제 허용';
COMMENT ON COLUMN retention_policies.is_default IS '기본 정책 여부';
COMMENT ON COLUMN retention_policies.active IS '적용 가능 여부';
COMMENT ON COLUMN retention_policies.created_at IS '생성 시각';
COMMENT ON COLUMN retention_policies.updated_at IS '수정 시각';
COMMENT ON COLUMN episodes.id IS 'PK';
COMMENT ON COLUMN episodes.owner_id IS 'FK → `users.id`';
COMMENT ON COLUMN episodes.umi_device_id IS 'FK → `devices.id`, UMI만 허용';
COMMENT ON COLUMN episodes.storage_node_id IS 'FK → `storage_nodes.id`';
COMMENT ON COLUMN episodes.retention_policy_id IS 'FK → `retention_policies.id`';
COMMENT ON COLUMN episodes.skill_id IS 'FK → `skills.id`, `meta.json.skill_key`를 서버가 해석한 값';
COMMENT ON COLUMN episodes.session_id IS 'UMI가 생성한 UUID 또는 ULID, ZIP 이름과 일치';
COMMENT ON COLUMN episodes.source IS '`SIM`, `REAL`';
COMMENT ON COLUMN episodes.success IS '수행 성공 여부, 미라벨이면 NULL';
COMMENT ON COLUMN episodes.label_status IS '`UNLABELED`, `LABELED`';
COMMENT ON COLUMN episodes.label_source IS '`UMI`, `USER`, `SERVER`';
COMMENT ON COLUMN episodes.labeled_at IS '라벨 확정 시각';
COMMENT ON COLUMN episodes.contract_version IS '전체 Episode archive 계약 버전';
COMMENT ON COLUMN episodes.meta_schema_version IS '`meta.json.schema_version`';
COMMENT ON COLUMN episodes.summary_schema_version IS '`summary.json.schema_version`';
COMMENT ON COLUMN episodes.status IS 'Episode 상태';
COMMENT ON COLUMN episodes.started_at IS '`meta.json.started_at`';
COMMENT ON COLUMN episodes.ended_at IS '`meta.json.ended_at`';
COMMENT ON COLUMN episodes.start_monotonic_ns IS 'CSV `t_ns`와 UTC를 연결하는 기준';
COMMENT ON COLUMN episodes.timestamp_timebase IS 'MVP는 `MONOTONIC_NS`';
COMMENT ON COLUMN episodes.first_timestamp_ns IS '전체 데이터의 가장 이른 `t_ns`';
COMMENT ON COLUMN episodes.last_timestamp_ns IS '전체 데이터의 가장 늦은 `t_ns`';
COMMENT ON COLUMN episodes.duration_ns IS '`last_timestamp_ns - first_timestamp_ns`';
COMMENT ON COLUMN episodes.frame_count IS '검증된 이미지 프레임 수';
COMMENT ON COLUMN episodes.pose_sample_count IS '`pose.csv` 데이터 행 수';
COMMENT ON COLUMN episodes.imu_sample_count IS '`imu.csv` 데이터 행 수';
COMMENT ON COLUMN episodes.gripper_sample_count IS '`gripper.csv` 데이터 행 수';
COMMENT ON COLUMN episodes.missing_frame_count IS '누락 프레임 수';
COMMENT ON COLUMN episodes.tracking_gap_count IS '추적 중단 구간 수';
COMMENT ON COLUMN episodes.tracking_lost_duration_ns IS '추적 중단 누적 시간';
COMMENT ON COLUMN episodes.quality_status IS '`PENDING`, `VALID`, `WARNING`, `INVALID`';
COMMENT ON COLUMN episodes.capture_metadata IS '장치·ARCore·카메라 내부 파라미터·좌표계·단위 snapshot';
COMMENT ON COLUMN episodes.quality_summary IS '추가 결측·품질 집계 snapshot';
COMMENT ON COLUMN episodes.validated_at IS '서버 archive 검증 완료 시각';
COMMENT ON COLUMN episodes.last_used_at IS '마지막 학습 사용 시각';
COMMENT ON COLUMN episodes.retention_expires_at IS '삭제 가능 기준 시각';
COMMENT ON COLUMN episodes.deleted_at IS '물리 ZIP 삭제 확인 시각';
COMMENT ON COLUMN episodes.error_code IS '최근 오류 코드';
COMMENT ON COLUMN episodes.error_message IS '최근 오류 설명';
COMMENT ON COLUMN episodes.created_at IS '생성 시각';
COMMENT ON COLUMN episodes.updated_at IS '수정 시각';
COMMENT ON COLUMN episodes.row_version IS '선택·삭제 경합 방지용 낙관적 잠금';
COMMENT ON COLUMN episode_archives.id IS 'PK';
COMMENT ON COLUMN episode_archives.episode_id IS 'FK → `episodes.id`, UNIQUE';
COMMENT ON COLUMN episode_archives.storage_node_id IS 'FK → `storage_nodes.id`';
COMMENT ON COLUMN episode_archives.storage_key IS '서버 생성 상대 key, `episodes/{owner_id}/{수집완료UTC}/{archive_id}/archive.zip`';
COMMENT ON COLUMN episode_archives.original_filename IS '감사용 `<session_id>.zip`, 경로 계산에 사용하지 않음';
COMMENT ON COLUMN episode_archives.content_type IS '고정 `application/zip`';
COMMENT ON COLUMN episode_archives.archive_format_version IS '예: `umi-episode-zip-v1`';
COMMENT ON COLUMN episode_archives.compression_method IS '고정 `STORE`';
COMMENT ON COLUMN episode_archives.size_bytes IS 'ZIP 파일 크기';
COMMENT ON COLUMN episode_archives.uncompressed_size_bytes IS 'ZIP entry 원본 크기 합계';
COMMENT ON COLUMN episode_archives.entry_count IS 'ZIP entry 수';
COMMENT ON COLUMN episode_archives.sha256 IS '최종 ZIP 전체 SHA-256';
COMMENT ON COLUMN episode_archives.manifest_summary IS '필수 entry 존재 여부와 주요 내부 파일 SHA-256 snapshot';
COMMENT ON COLUMN episode_archives.status IS 'archive 보관·삭제 상태';
COMMENT ON COLUMN episode_archives.stored_at IS 'atomic rename 완료 시각';
COMMENT ON COLUMN episode_archives.validated_at IS 'ZIP 구조·내용 검증 완료 시각';
COMMENT ON COLUMN episode_archives.deleted_at IS '실제 ZIP 삭제 확인 시각';
COMMENT ON COLUMN episode_archives.error_code IS '최근 archive 오류';
COMMENT ON COLUMN episode_archives.error_message IS '최근 archive 오류 설명';
COMMENT ON COLUMN episode_archives.created_at IS '생성 시각';
COMMENT ON COLUMN episode_archives.updated_at IS '수정 시각';
COMMENT ON COLUMN skills.id IS 'PK';
COMMENT ON COLUMN skills.skill_key IS 'FE·BE·AI·VLM 공통의 전역 불변 key';
COMMENT ON COLUMN skills.tier IS '`TUTORIAL`, `DEMO`';
COMMENT ON COLUMN skills.status IS '스킬 카탈로그 수명주기 상태';
COMMENT ON COLUMN skills.display_name IS '표시 이름';
COMMENT ON COLUMN skills.description IS '설명';
COMMENT ON COLUMN skills.needs_vlm IS 'VLM 추론 필요 여부, 기본 false';
COMMENT ON COLUMN skills.persona IS 'DEMO 스킬 대상 페르소나';
COMMENT ON COLUMN skills.created_at IS '생성 시각';
COMMENT ON COLUMN skills.updated_at IS '수정 시각';
COMMENT ON COLUMN training_profiles.id IS 'PK';
COMMENT ON COLUMN training_profiles.profile_key IS '불변 논리 key, 예: `umi-act-default`';
COMMENT ON COLUMN training_profiles.version IS 'key 범위 증가 버전';
COMMENT ON COLUMN training_profiles.status IS '`ACTIVE`, `RETIRED`';
COMMENT ON COLUMN training_profiles.algorithm IS '예: `ACT`';
COMMENT ON COLUMN training_profiles.hyperparameters IS 'epoch·batch·seed·optimizer·learning rate 등 전체 고정값';
COMMENT ON COLUMN training_profiles.config_sha256 IS '`algorithm`과 `hyperparameters` canonical JSON의 SHA-256';
COMMENT ON COLUMN training_profiles.description IS '변경 목적·운영 메모';
COMMENT ON COLUMN training_profiles.activated_at IS 'ACTIVE 전환 시각';
COMMENT ON COLUMN training_profiles.retired_at IS 'RETIRED 전환 시각';
COMMENT ON COLUMN training_profiles.created_at IS '생성 시각';
COMMENT ON COLUMN training_jobs.id IS 'PK';
COMMENT ON COLUMN training_jobs.owner_id IS 'FK → `users.id`';
COMMENT ON COLUMN training_jobs.skill_id IS 'FK → `skills.id`';
COMMENT ON COLUMN training_jobs.umi_device_id IS 'FK → `devices.id`';
COMMENT ON COLUMN training_jobs.training_profile_id IS 'FK → `training_profiles.id`, 생성 시 백엔드가 결정';
COMMENT ON COLUMN training_jobs.assigned_storage_node_id IS 'FK → `storage_nodes.id`';
COMMENT ON COLUMN training_jobs.assigned_worker_id IS '현재 GPU worker';
COMMENT ON COLUMN training_jobs.input_mode IS '`COLLECT_NEW`, `EXISTING_EPISODES`';
COMMENT ON COLUMN training_jobs.status IS '학습 상태';
COMMENT ON COLUMN training_jobs.attempt IS '기본 1';
COMMENT ON COLUMN training_jobs.lease_token_hash IS '현재 lease token hash';
COMMENT ON COLUMN training_jobs.lease_expires_at IS 'lease 만료 시각';
COMMENT ON COLUMN training_jobs.training_config_snapshot IS '선택된 프로필의 `algorithm`·`hyperparameters` 불변 snapshot';
COMMENT ON COLUMN training_jobs.training_config_sha256 IS 'snapshot canonical JSON SHA-256';
COMMENT ON COLUMN training_jobs.dataset_query IS 'Episode 선택 조건 snapshot';
COMMENT ON COLUMN training_jobs.gpu_index IS '학습에 할당된 GPU 번호, 0 이상';
COMMENT ON COLUMN training_jobs.git_rev IS '학습 코드 revision';
COMMENT ON COLUMN training_jobs.expected_episode_count IS '기본 0';
COMMENT ON COLUMN training_jobs.received_episode_count IS '기본 0';
COMMENT ON COLUMN training_jobs.received_bytes IS '기본 0';
COMMENT ON COLUMN training_jobs.episode_count IS '고정 입력 Episode 수';
COMMENT ON COLUMN training_jobs.input_snapshot_sha256 IS '고정 입력 목록 hash';
COMMENT ON COLUMN training_jobs.progress_stage IS '합의된 stage enum';
COMMENT ON COLUMN training_jobs.progress_percent IS '0~100';
COMMENT ON COLUMN training_jobs.current_epoch IS '현재 epoch';
COMMENT ON COLUMN training_jobs.total_epochs IS '전체 epoch';
COMMENT ON COLUMN training_jobs.result_skill_version_id IS 'FK → `skill_versions.id`, migration 후 추가';
COMMENT ON COLUMN training_jobs.error_code IS '종료 또는 최근 오류';
COMMENT ON COLUMN training_jobs.error_message IS '오류 설명';
COMMENT ON COLUMN training_jobs.created_at IS '생성 시각';
COMMENT ON COLUMN training_jobs.started_at IS '시작 시각';
COMMENT ON COLUMN training_jobs.model_completed_at IS 'FastAPI의 최초 학습 완료 통지 발신 UTC 시각, GPU Spring Boot를 거쳐 전달';
COMMENT ON COLUMN training_jobs.finished_at IS '종료 시각';
COMMENT ON COLUMN training_jobs.updated_at IS '수정 시각';
COMMENT ON COLUMN training_jobs.row_version IS '상태 전이 낙관적 잠금';
COMMENT ON COLUMN training_job_episodes.training_job_id IS 'PK 일부, FK → `training_jobs.id`';
COMMENT ON COLUMN training_job_episodes.episode_id IS 'PK 일부, FK → `episodes.id`';
COMMENT ON COLUMN training_job_episodes.input_order IS '1부터 연속';
COMMENT ON COLUMN training_job_episodes.archive_sha256_snapshot IS '입력 고정 시점의 EpisodeArchive SHA-256';
COMMENT ON COLUMN training_job_episodes.contract_version_snapshot IS '입력 데이터 계약 snapshot';
COMMENT ON COLUMN training_job_episodes.source_snapshot IS '`SIM`, `REAL` snapshot';
COMMENT ON COLUMN training_job_episodes.label_status_snapshot IS '라벨 상태 snapshot';
COMMENT ON COLUMN training_job_episodes.success_snapshot IS '성공 라벨 snapshot';
COMMENT ON COLUMN training_job_episodes.quality_status_snapshot IS '서버 품질 판정 snapshot';
COMMENT ON COLUMN training_job_episodes.attached_at IS '입력 고정 시각';
COMMENT ON COLUMN training_job_events.id IS 'PK';
COMMENT ON COLUMN training_job_events.training_job_id IS 'FK → `training_jobs.id`';
COMMENT ON COLUMN training_job_events.attempt IS '보고 attempt';
COMMENT ON COLUMN training_job_events.event_id IS 'worker 이벤트 ID';
COMMENT ON COLUMN training_job_events.sequence IS 'attempt 안의 순번';
COMMENT ON COLUMN training_job_events.event_type IS '`INPUT_PROGRESS`, `PROGRESS` 등';
COMMENT ON COLUMN training_job_events.payload IS '이벤트 내용';
COMMENT ON COLUMN training_job_events.occurred_at IS 'worker 발생 시각';
COMMENT ON COLUMN training_job_events.received_at IS '서버 수신 시각';
COMMENT ON COLUMN robot_profiles.id IS 'PK';
COMMENT ON COLUMN robot_profiles.name IS '프로파일 이름';
COMMENT ON COLUMN robot_profiles.contract_version IS '입력·출력 계약 버전';
COMMENT ON COLUMN robot_profiles.architecture IS '로봇/모델 구조';
COMMENT ON COLUMN robot_profiles.runtime IS 'PyTorch, TensorRT 등';
COMMENT ON COLUMN robot_profiles.runtime_version IS '런타임 버전';
COMMENT ON COLUMN robot_profiles.camera_contract IS '카메라 이름·입력 규격';
COMMENT ON COLUMN robot_profiles.state_contract IS '관절·상태 규격';
COMMENT ON COLUMN robot_profiles.action_contract IS '출력 행동 규격';
COMMENT ON COLUMN robot_profiles.created_at IS '생성 시각';
COMMENT ON COLUMN robot_profiles.updated_at IS '수정 시각';
COMMENT ON COLUMN skill_versions.id IS 'PK';
COMMENT ON COLUMN skill_versions.skill_id IS 'FK → `skills.id`';
COMMENT ON COLUMN skill_versions.training_job_id IS 'FK → `training_jobs.id`';
COMMENT ON COLUMN skill_versions.training_profile_id IS 'FK → `training_profiles.id`, CKPT 생성 프로필';
COMMENT ON COLUMN skill_versions.training_config_sha256 IS 'TrainingJob 및 CKPT manifest 설정 hash';
COMMENT ON COLUMN skill_versions.robot_profile_id IS 'FK → `robot_profiles.id`';
COMMENT ON COLUMN skill_versions.version IS 'Skill 범위 증가 번호';
COMMENT ON COLUMN skill_versions.status IS '`UPLOADING`, `VALIDATING`, `READY`, `FAILED`, `ARCHIVED`';
COMMENT ON COLUMN skill_versions.checkpoint_bucket IS '내부 S3 bucket';
COMMENT ON COLUMN skill_versions.checkpoint_key IS '서버 생성 불변 S3 key, `ckpt/{owner_id}/{skill_key}/v{version}/{skill_key}_{학습완료UTC}.ckpt`';
COMMENT ON COLUMN skill_versions.checkpoint_sha256 IS 'CKPT SHA-256';
COMMENT ON COLUMN skill_versions.checkpoint_bytes IS 'CKPT 크기';
COMMENT ON COLUMN skill_versions.architecture IS '모델 architecture';
COMMENT ON COLUMN skill_versions.framework IS '프레임워크';
COMMENT ON COLUMN skill_versions.framework_version IS '프레임워크 버전';
COMMENT ON COLUMN skill_versions.contract_version IS '모델 입출력 계약';
COMMENT ON COLUMN skill_versions.action_space IS '`JOINT_ABSOLUTE`, `JOINT_DELTA`';
COMMENT ON COLUMN skill_versions.n_params IS '모델 파라미터 수';
COMMENT ON COLUMN skill_versions.control_rate_hz IS '제어 주기';
COMMENT ON COLUMN skill_versions.camera_names IS '카메라 이름 배열';
COMMENT ON COLUMN skill_versions.input_schema IS '입력 계약';
COMMENT ON COLUMN skill_versions.output_schema IS '출력 계약';
COMMENT ON COLUMN skill_versions.gate_metric IS '현재 `rollout_success`';
COMMENT ON COLUMN skill_versions.gate_n IS '평가 표본 수';
COMMENT ON COLUMN skill_versions.gate_runs IS '독립 학습·평가 반복 수';
COMMENT ON COLUMN skill_versions.gate_value IS '게이트 점추정 값';
COMMENT ON COLUMN skill_versions.gate_ci_low IS '신뢰구간 하한';
COMMENT ON COLUMN skill_versions.gate_ci_high IS '신뢰구간 상한';
COMMENT ON COLUMN skill_versions.gate_measured_at IS '게이트 측정 시각';
COMMENT ON COLUMN skill_versions.gate_passed IS '조건식으로 계산되는 generated column';
COMMENT ON COLUMN skill_versions.created_at IS '버전 생성 시각';
COMMENT ON COLUMN skill_versions.validated_at IS 'READY/FAILED 검증 시각';
COMMENT ON COLUMN skill_versions.archived_at IS '보관 처리 시각';
COMMENT ON COLUMN skill_destinations.skill_id IS 'PK 일부, FK → `skills.id`';
COMMENT ON COLUMN skill_destinations.destination_key IS 'PK 일부, 외부 계약 enum 값';
COMMENT ON COLUMN skill_destinations.display_name IS '사용자 표시 이름';
COMMENT ON COLUMN skill_destinations.enabled IS '현재 선택 가능 여부';
COMMENT ON COLUMN skill_destinations.created_at IS '생성 시각';
COMMENT ON COLUMN skill_destinations.updated_at IS '수정 시각';
COMMENT ON COLUMN vlm_model_versions.id IS 'PK';
COMMENT ON COLUMN vlm_model_versions.version IS '외부 계약에서 사용하는 버전 문자열';
COMMENT ON COLUMN vlm_model_versions.model_name IS '모델 이름';
COMMENT ON COLUMN vlm_model_versions.status IS '`CANDIDATE`, `READY`, `ARCHIVED`';
COMMENT ON COLUMN vlm_model_versions.model_metadata IS '런타임·양자화·revision 등, 기본 `{}`';
COMMENT ON COLUMN vlm_model_versions.created_at IS '생성 시각';
COMMENT ON COLUMN vlm_model_versions.updated_at IS '수정 시각';
COMMENT ON COLUMN vlm_policies.id IS 'PK';
COMMENT ON COLUMN vlm_policies.policy_key IS '정책 그룹 key, 예: `default-motion-gate`';
COMMENT ON COLUMN vlm_policies.version IS '정책 그룹 내 증가 버전';
COMMENT ON COLUMN vlm_policies.confidence_threshold IS '동작 허용 최소 confidence, 초기 0.7000';
COMMENT ON COLUMN vlm_policies.enabled IS '신규 추론에 적용할 활성 버전 여부';
COMMENT ON COLUMN vlm_policies.created_at IS '생성 시각';
COMMENT ON COLUMN vlm_inference_results.id IS 'PK';
COMMENT ON COLUMN vlm_inference_results.request_id IS '호출 측 멱등성·추적 ID';
COMMENT ON COLUMN vlm_inference_results.robot_id IS 'FK → `robots.id`';
COMMENT ON COLUMN vlm_inference_results.skill_id IS 'FK → `skills.id`';
COMMENT ON COLUMN vlm_inference_results.vlm_model_version_id IS 'FK → `vlm_model_versions.id`';
COMMENT ON COLUMN vlm_inference_results.vlm_policy_id IS 'FK → `vlm_policies.id`';
COMMENT ON COLUMN vlm_inference_results.destination_key IS '`skill_destinations` 복합 FK 일부';
COMMENT ON COLUMN vlm_inference_results.target_description_ko IS '한국어 대상 설명';
COMMENT ON COLUMN vlm_inference_results.bbox_x1 IS '정규화 좌상단 x';
COMMENT ON COLUMN vlm_inference_results.bbox_y1 IS '정규화 좌상단 y';
COMMENT ON COLUMN vlm_inference_results.bbox_x2 IS '정규화 우하단 x';
COMMENT ON COLUMN vlm_inference_results.bbox_y2 IS '정규화 우하단 y';
COMMENT ON COLUMN vlm_inference_results.confidence IS 'VLM 응답 confidence';
COMMENT ON COLUMN vlm_inference_results.confidence_threshold IS '판단 당시 정책값 snapshot';
COMMENT ON COLUMN vlm_inference_results.abstain IS 'VLM 동작 보류 여부';
COMMENT ON COLUMN vlm_inference_results.motion_allowed IS '조건식으로 계산되는 generated column';
COMMENT ON COLUMN vlm_inference_results.raw_output IS '계약 검증을 통과한 원본 출력 snapshot';
COMMENT ON COLUMN vlm_inference_results.created_at IS '수신 시각';
COMMENT ON COLUMN data_purge_jobs.id IS 'PK';
COMMENT ON COLUMN data_purge_jobs.episode_id IS 'FK → `episodes.id`';
COMMENT ON COLUMN data_purge_jobs.storage_node_id IS 'FK → `storage_nodes.id`';
COMMENT ON COLUMN data_purge_jobs.status IS '`PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`';
COMMENT ON COLUMN data_purge_jobs.attempt IS '기본 0, claim 시 증가';
COMMENT ON COLUMN data_purge_jobs.assigned_worker_id IS '삭제 worker';
COMMENT ON COLUMN data_purge_jobs.lease_token_hash IS 'lease token hash';
COMMENT ON COLUMN data_purge_jobs.lease_expires_at IS 'lease 만료';
COMMENT ON COLUMN data_purge_jobs.scheduled_at IS '삭제 예정 시각';
COMMENT ON COLUMN data_purge_jobs.started_at IS '실행 시작';
COMMENT ON COLUMN data_purge_jobs.finished_at IS '성공·실패 완료';
COMMENT ON COLUMN data_purge_jobs.error_code IS '삭제 오류';
COMMENT ON COLUMN data_purge_jobs.error_message IS '오류 설명';
COMMENT ON COLUMN data_purge_jobs.created_at IS '생성 시각';
COMMENT ON COLUMN data_purge_jobs.updated_at IS '수정 시각';
COMMENT ON COLUMN data_purge_jobs.row_version IS '상태 전이 낙관적 잠금';
COMMENT ON COLUMN data_purge_reports.id IS 'PK';
COMMENT ON COLUMN data_purge_reports.data_purge_job_id IS 'FK → `data_purge_jobs.id`';
COMMENT ON COLUMN data_purge_reports.report_id IS 'worker 멱등성 ID';
COMMENT ON COLUMN data_purge_reports.attempt IS '삭제 attempt';
COMMENT ON COLUMN data_purge_reports.status IS '보고 상태';
COMMENT ON COLUMN data_purge_reports.archive_result IS 'Episode archive 삭제 결과 snapshot';
COMMENT ON COLUMN data_purge_reports.error_code IS '보고 오류';
COMMENT ON COLUMN data_purge_reports.error_message IS '오류 설명';
COMMENT ON COLUMN data_purge_reports.reported_at IS '서버 수신 시각';
COMMENT ON COLUMN tasks.id IS 'PK';
COMMENT ON COLUMN tasks.owner_id IS 'FK → `users.id`';
COMMENT ON COLUMN tasks.task_key IS '소유자 범위 불변 key';
COMMENT ON COLUMN tasks.display_name IS '표시 이름';
COMMENT ON COLUMN tasks.description IS '설명';
COMMENT ON COLUMN tasks.latest_version IS '목록 표시용 캐시 값';
COMMENT ON COLUMN tasks.created_at IS '생성 시각';
COMMENT ON COLUMN tasks.updated_at IS '수정 시각';
COMMENT ON COLUMN task_versions.id IS 'PK';
COMMENT ON COLUMN task_versions.task_id IS 'FK → `tasks.id`';
COMMENT ON COLUMN task_versions.version IS 'Task 범위 증가 번호';
COMMENT ON COLUMN task_versions.created_at IS '생성 시각';
COMMENT ON COLUMN task_steps.id IS 'PK';
COMMENT ON COLUMN task_steps.task_version_id IS 'FK → `task_versions.id`';
COMMENT ON COLUMN task_steps.skill_version_id IS 'FK → `skill_versions.id`';
COMMENT ON COLUMN task_steps.step_order IS '1부터 연속';
COMMENT ON COLUMN task_steps.parameters IS '기본 `{}`';
COMMENT ON COLUMN task_steps.timeout_seconds IS '1 이상';
COMMENT ON COLUMN robots.id IS 'PK';
COMMENT ON COLUMN robots.owner_id IS 'FK → `users.id`';
COMMENT ON COLUMN robots.jetson_device_id IS 'FK → `devices.id`, JETSON만 허용';
COMMENT ON COLUMN robots.robot_profile_id IS 'FK → `robot_profiles.id`';
COMMENT ON COLUMN robots.name IS '표시 이름';
COMMENT ON COLUMN robots.connection_status IS '`ONLINE`, `OFFLINE`';
COMMENT ON COLUMN robots.current_deployment_id IS 'FK → `deployments.id`, migration 후 추가';
COMMENT ON COLUMN robots.last_seen_at IS '마지막 연결 확인';
COMMENT ON COLUMN robots.created_at IS '생성 시각';
COMMENT ON COLUMN robots.updated_at IS '수정 시각';
COMMENT ON COLUMN robots.row_version IS '배포 적용 낙관적 잠금';
COMMENT ON COLUMN deployments.id IS 'PK';
COMMENT ON COLUMN deployments.robot_id IS 'FK → `robots.id`';
COMMENT ON COLUMN deployments.task_version_id IS 'FK → `task_versions.id`';
COMMENT ON COLUMN deployments.status IS '배포 상태';
COMMENT ON COLUMN deployments.progress_percent IS '0~100';
COMMENT ON COLUMN deployments.last_report_sequence IS '기본 0';
COMMENT ON COLUMN deployments.error_code IS '배포 오류';
COMMENT ON COLUMN deployments.error_message IS '오류 설명';
COMMENT ON COLUMN deployments.created_at IS '생성 시각';
COMMENT ON COLUMN deployments.updated_at IS '수정 시각';
COMMENT ON COLUMN deployments.applied_at IS 'SUCCEEDED 확정 시각';
COMMENT ON COLUMN deployments.row_version IS '상태 전이 낙관적 잠금';
COMMENT ON COLUMN deployment_items.id IS 'PK';
COMMENT ON COLUMN deployment_items.deployment_id IS 'FK → `deployments.id`';
COMMENT ON COLUMN deployment_items.skill_version_id IS 'FK → `skill_versions.id`';
COMMENT ON COLUMN deployment_items.gate_passed IS '기본 true이며 항상 true, 배포 게이트 복합 FK 일부';
COMMENT ON COLUMN deployment_items.vlm_model_version_id IS 'FK → `vlm_model_versions.id`, 해당 스킬의 VLM 버전';
COMMENT ON COLUMN deployment_items.status IS '`PENDING`, `DOWNLOADING`, `VERIFIED`, `FAILED`';
COMMENT ON COLUMN deployment_items.downloaded_at IS '다운로드 완료';
COMMENT ON COLUMN deployment_items.verified_at IS '해시·로드 검증 완료';
COMMENT ON COLUMN deployment_items.error_code IS '항목 오류';
COMMENT ON COLUMN deployment_items.error_message IS '오류 설명';
COMMENT ON COLUMN deployment_items.created_at IS '생성 시각';
COMMENT ON COLUMN deployment_items.updated_at IS '수정 시각';
COMMENT ON COLUMN deployment_reports.id IS 'PK';
COMMENT ON COLUMN deployment_reports.deployment_id IS 'FK → `deployments.id`';
COMMENT ON COLUMN deployment_reports.report_id IS 'Jetson 멱등성 ID';
COMMENT ON COLUMN deployment_reports.sequence IS '증가 순번';
COMMENT ON COLUMN deployment_reports.status IS '보고 Deployment 상태';
COMMENT ON COLUMN deployment_reports.progress_percent IS '보고 진행률';
COMMENT ON COLUMN deployment_reports.items IS '항목별 보고 snapshot';
COMMENT ON COLUMN deployment_reports.error IS '보고 오류';
COMMENT ON COLUMN deployment_reports.reported_at IS '서버 수신 시각';
COMMENT ON COLUMN robot_skill_deployments.id IS 'PK';
COMMENT ON COLUMN robot_skill_deployments.robot_id IS 'FK → `robots.id`';
COMMENT ON COLUMN robot_skill_deployments.skill_id IS 'FK → `skills.id`';
COMMENT ON COLUMN robot_skill_deployments.skill_version_id IS 'FK → `skill_versions.id`';
COMMENT ON COLUMN robot_skill_deployments.deployment_id IS 'FK → `deployments.id`, 이 상태를 만든 작업';
COMMENT ON COLUMN robot_skill_deployments.vlm_model_version_id IS 'FK → `vlm_model_versions.id`';
COMMENT ON COLUMN robot_skill_deployments.status IS '`ACTIVE`, `RETIRED`';
COMMENT ON COLUMN robot_skill_deployments.deployed_at IS '활성화 시각';
COMMENT ON COLUMN robot_skill_deployments.retired_at IS '은퇴 시각';
COMMENT ON COLUMN robot_skill_deployments.created_at IS '생성 시각';
COMMENT ON COLUMN idempotency_keys.id IS 'PK';
COMMENT ON COLUMN idempotency_keys.actor_type IS '`USER`, `UMI`, `GPU`, `JETSON`, `INTERNAL`';
COMMENT ON COLUMN idempotency_keys.actor_id IS '주체 ID';
COMMENT ON COLUMN idempotency_keys.http_method IS '요청 method';
COMMENT ON COLUMN idempotency_keys.request_path IS 'path template 또는 정규화 path';
COMMENT ON COLUMN idempotency_keys.idempotency_key IS '클라이언트 전달 key';
COMMENT ON COLUMN idempotency_keys.request_hash IS '정규화 요청 본문 hash';
COMMENT ON COLUMN idempotency_keys.response_status IS '최초 응답 HTTP status';
COMMENT ON COLUMN idempotency_keys.response_body IS '재사용할 응답';
COMMENT ON COLUMN idempotency_keys.resource_type IS '생성된 리소스 유형';
COMMENT ON COLUMN idempotency_keys.resource_id IS '생성된 리소스 ID';
COMMENT ON COLUMN idempotency_keys.expires_at IS '중복 보장 만료';
COMMENT ON COLUMN idempotency_keys.created_at IS '최초 처리 시각';
COMMENT ON COLUMN outbox_events.id IS 'PK';
COMMENT ON COLUMN outbox_events.event_id IS '외부 중복 제거 ID';
COMMENT ON COLUMN outbox_events.aggregate_type IS '`EPISODE`, `TRAINING_JOB` 등';
COMMENT ON COLUMN outbox_events.aggregate_id IS '대상 리소스 ID';
COMMENT ON COLUMN outbox_events.event_type IS '이벤트 종류';
COMMENT ON COLUMN outbox_events.payload IS 'secret·binary·서명 URL 제외';
COMMENT ON COLUMN outbox_events.status IS '`PENDING`, `PUBLISHED`, `FAILED`';
COMMENT ON COLUMN outbox_events.attempt IS '발행 시도 횟수';
COMMENT ON COLUMN outbox_events.available_at IS '다음 처리 가능 시각';
COMMENT ON COLUMN outbox_events.published_at IS '발행 완료';
COMMENT ON COLUMN outbox_events.last_error IS '마지막 오류';
COMMENT ON COLUMN outbox_events.created_at IS '생성 시각';

-- 확정된 공통 스킬 seed. 개인 사용자/장치/모델 데이터는 생성하지 않는다.
INSERT INTO skills (id, skill_key, tier, status, display_name, needs_vlm) VALUES
('10000000-0000-4000-8000-000000000001', 'pick_place', 'TUTORIAL', 'PLANNED', '집어 옮기기', false),
('10000000-0000-4000-8000-000000000002', 'sort_two', 'TUTORIAL', 'PLANNED', '두 곳으로 분류', true),
('10000000-0000-4000-8000-000000000003', 'align_fixture', 'TUTORIAL', 'PLANNED', '지그에 정렬', false),
('10000000-0000-4000-8000-000000000004', 'present_inspect', 'TUTORIAL', 'PLANNED', '검사 자세 제시', false),
('10000000-0000-4000-8000-000000000005', 'line_up', 'TUTORIAL', 'PLANNED', '순서대로 늘어놓기', false);

-- 문서에 명시된 초기 confidence 0.7000을 사용한다. policy_key는 문서의 예시 key를 채택한다.
INSERT INTO vlm_policies (id, policy_key, version, confidence_threshold, enabled)
VALUES ('20000000-0000-4000-8000-000000000001', 'default-motion-gate', 1, 0.7000, true);

-- 보류: TrainingProfile 예시 하이퍼파라미터는 최종값이 아니므로 seed하지 않는다.
-- 보류: RetentionPolicy의 실제 보관 일수 및 VLM destination은 합의 후 별도 migration으로 등록한다.
-- 위 정책과 실제 RobotProfile/StorageNode를 등록하기 전에는 학습 요청을 허용하지 않는다.
-- Redis key/TTL, ZIP 내부 검증, 파일명 생성 및 S3 접근 정책은 SQL 스키마가 아닌 애플리케이션/인프라 계약이다.
-- 문서 10절의 잠금·상태 전이·Outbox 기록은 서비스에서 동일 트랜잭션으로 구현한다.
