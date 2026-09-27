-- Consolidated baseline from migration_legacy_v2/V1 through V22.
-- PostgreSQL 17; includes final schema and seed data; excludes Flyway history.
-- For a NEW empty database only. Do not apply on an existing V1-V22 installation.

--
-- PostgreSQL database dump
--


-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--



--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: enforce_deployable_skill_version(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.enforce_deployable_skill_version() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM skill_versions sv
    WHERE sv.id = NEW.skill_version_id
      AND sv.status = 'READY'
      AND sv.gate_passed = true
  ) THEN
    RAISE EXCEPTION 'SkillVersion is not deployable: %', NEW.skill_version_id;
  END IF;
  RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: data_purge_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_purge_jobs (
    id uuid NOT NULL,
    episode_id uuid NOT NULL,
    storage_node_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    attempt integer DEFAULT 0 NOT NULL,
    assigned_worker_id character varying(100),
    lease_token_hash character varying(64),
    lease_expires_at timestamp with time zone,
    scheduled_at timestamp with time zone NOT NULL,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    error_code character varying(100),
    error_message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT data_purge_jobs_attempt_check CHECK ((attempt >= 0)),
    CONSTRAINT data_purge_jobs_lease_token_hash_check CHECK (((lease_token_hash)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT data_purge_jobs_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('RUNNING'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text])))
);


--
-- Name: COLUMN data_purge_jobs.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.id IS 'PK';


--
-- Name: COLUMN data_purge_jobs.episode_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.episode_id IS 'FK → `episodes.id`';


--
-- Name: COLUMN data_purge_jobs.storage_node_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.storage_node_id IS 'FK → `storage_nodes.id`';


--
-- Name: COLUMN data_purge_jobs.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.status IS '`PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`';


--
-- Name: COLUMN data_purge_jobs.attempt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.attempt IS '기본 0, claim 시 증가';


--
-- Name: COLUMN data_purge_jobs.assigned_worker_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.assigned_worker_id IS '삭제 worker';


--
-- Name: COLUMN data_purge_jobs.lease_token_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.lease_token_hash IS 'lease token hash';


--
-- Name: COLUMN data_purge_jobs.lease_expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.lease_expires_at IS 'lease 만료';


--
-- Name: COLUMN data_purge_jobs.scheduled_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.scheduled_at IS '삭제 예정 시각';


--
-- Name: COLUMN data_purge_jobs.started_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.started_at IS '실행 시작';


--
-- Name: COLUMN data_purge_jobs.finished_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.finished_at IS '성공·실패 완료';


--
-- Name: COLUMN data_purge_jobs.error_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.error_code IS '삭제 오류';


--
-- Name: COLUMN data_purge_jobs.error_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.error_message IS '오류 설명';


--
-- Name: COLUMN data_purge_jobs.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.created_at IS '생성 시각';


--
-- Name: COLUMN data_purge_jobs.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.updated_at IS '수정 시각';


--
-- Name: COLUMN data_purge_jobs.row_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_jobs.row_version IS '상태 전이 낙관적 잠금';


--
-- Name: data_purge_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_purge_reports (
    id uuid NOT NULL,
    data_purge_job_id uuid NOT NULL,
    report_id character varying(150) NOT NULL,
    attempt integer NOT NULL,
    status character varying(20) NOT NULL,
    archive_result jsonb NOT NULL,
    error_code character varying(100),
    error_message text,
    reported_at timestamp with time zone NOT NULL
);


--
-- Name: COLUMN data_purge_reports.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_reports.id IS 'PK';


--
-- Name: COLUMN data_purge_reports.data_purge_job_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_reports.data_purge_job_id IS 'FK → `data_purge_jobs.id`';


--
-- Name: COLUMN data_purge_reports.report_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_reports.report_id IS 'worker 멱등성 ID';


--
-- Name: COLUMN data_purge_reports.attempt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_reports.attempt IS '삭제 attempt';


--
-- Name: COLUMN data_purge_reports.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_reports.status IS '보고 상태';


--
-- Name: COLUMN data_purge_reports.archive_result; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_reports.archive_result IS 'Episode archive 삭제 결과 snapshot';


--
-- Name: COLUMN data_purge_reports.error_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_reports.error_code IS '보고 오류';


--
-- Name: COLUMN data_purge_reports.error_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_reports.error_message IS '오류 설명';


--
-- Name: COLUMN data_purge_reports.reported_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.data_purge_reports.reported_at IS '서버 수신 시각';


--
-- Name: deployment_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.deployment_items (
    id uuid NOT NULL,
    deployment_id uuid NOT NULL,
    skill_version_id uuid NOT NULL,
    gate_passed boolean DEFAULT true NOT NULL,
    status character varying(30) NOT NULL,
    downloaded_at timestamp with time zone,
    verified_at timestamp with time zone,
    error_code character varying(100),
    error_message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT deployment_items_gate_passed_check CHECK ((gate_passed = true)),
    CONSTRAINT deployment_items_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('DOWNLOADING'::character varying)::text, ('VERIFIED'::character varying)::text, ('FAILED'::character varying)::text])))
);


--
-- Name: COLUMN deployment_items.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.id IS 'PK';


--
-- Name: COLUMN deployment_items.deployment_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.deployment_id IS 'FK → `deployments.id`';


--
-- Name: COLUMN deployment_items.skill_version_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.skill_version_id IS 'FK → `skill_versions.id`';


--
-- Name: COLUMN deployment_items.gate_passed; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.gate_passed IS '기본 true이며 항상 true, 배포 게이트 복합 FK 일부';


--
-- Name: COLUMN deployment_items.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.status IS '`PENDING`, `DOWNLOADING`, `VERIFIED`, `FAILED`';


--
-- Name: COLUMN deployment_items.downloaded_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.downloaded_at IS '다운로드 완료';


--
-- Name: COLUMN deployment_items.verified_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.verified_at IS '해시·로드 검증 완료';


--
-- Name: COLUMN deployment_items.error_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.error_code IS '항목 오류';


--
-- Name: COLUMN deployment_items.error_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.error_message IS '오류 설명';


--
-- Name: COLUMN deployment_items.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.created_at IS '생성 시각';


--
-- Name: COLUMN deployment_items.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_items.updated_at IS '수정 시각';


--
-- Name: deployment_reports; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.deployment_reports (
    id uuid NOT NULL,
    deployment_id uuid NOT NULL,
    report_id character varying(150) NOT NULL,
    sequence bigint NOT NULL,
    status character varying(30) NOT NULL,
    progress_percent numeric(5,2) NOT NULL,
    items jsonb NOT NULL,
    error jsonb,
    reported_at timestamp with time zone NOT NULL
);


--
-- Name: COLUMN deployment_reports.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_reports.id IS 'PK';


--
-- Name: COLUMN deployment_reports.deployment_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_reports.deployment_id IS 'FK → `deployments.id`';


--
-- Name: COLUMN deployment_reports.report_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_reports.report_id IS 'Jetson 멱등성 ID';


--
-- Name: COLUMN deployment_reports.sequence; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_reports.sequence IS '증가 순번';


--
-- Name: COLUMN deployment_reports.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_reports.status IS '보고 Deployment 상태';


--
-- Name: COLUMN deployment_reports.progress_percent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_reports.progress_percent IS '보고 진행률';


--
-- Name: COLUMN deployment_reports.items; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_reports.items IS '항목별 보고 snapshot';


--
-- Name: COLUMN deployment_reports.error; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_reports.error IS '보고 오류';


--
-- Name: COLUMN deployment_reports.reported_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployment_reports.reported_at IS '서버 수신 시각';


--
-- Name: deployments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.deployments (
    id uuid NOT NULL,
    robot_id uuid NOT NULL,
    task_version_id uuid NOT NULL,
    status character varying(30) NOT NULL,
    progress_percent numeric(5,2) NOT NULL,
    last_report_sequence bigint DEFAULT 0 NOT NULL,
    error_code character varying(100),
    error_message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    applied_at timestamp with time zone,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT deployments_progress_percent_check CHECK (((progress_percent >= (0)::numeric) AND (progress_percent <= (100)::numeric))),
    CONSTRAINT deployments_status_check CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('DOWNLOADING'::character varying)::text, ('VERIFYING'::character varying)::text, ('APPLYING'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text, ('CANCELED'::character varying)::text])))
);


--
-- Name: COLUMN deployments.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.id IS 'PK';


--
-- Name: COLUMN deployments.robot_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.robot_id IS 'FK → `robots.id`';


--
-- Name: COLUMN deployments.task_version_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.task_version_id IS 'FK → `task_versions.id`';


--
-- Name: COLUMN deployments.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.status IS '배포 상태';


--
-- Name: COLUMN deployments.progress_percent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.progress_percent IS '0~100';


--
-- Name: COLUMN deployments.last_report_sequence; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.last_report_sequence IS '기본 0';


--
-- Name: COLUMN deployments.error_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.error_code IS '배포 오류';


--
-- Name: COLUMN deployments.error_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.error_message IS '오류 설명';


--
-- Name: COLUMN deployments.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.created_at IS '생성 시각';


--
-- Name: COLUMN deployments.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.updated_at IS '수정 시각';


--
-- Name: COLUMN deployments.applied_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.applied_at IS 'SUCCEEDED 확정 시각';


--
-- Name: COLUMN deployments.row_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.deployments.row_version IS '상태 전이 낙관적 잠금';


--
-- Name: device_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_credentials (
    device_id uuid NOT NULL,
    device_secret_hash character varying(255) NOT NULL,
    secret_rotated_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    enrolled_at timestamp with time zone,
    last_authenticated_at timestamp with time zone,
    CONSTRAINT ck_device_credentials_status CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('REVOKED'::character varying)::text])))
);


--
-- Name: COLUMN device_credentials.device_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_credentials.device_id IS 'PK, FK → `devices.id`';


--
-- Name: COLUMN device_credentials.device_secret_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_credentials.device_secret_hash IS '장치 secret hash';


--
-- Name: COLUMN device_credentials.secret_rotated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_credentials.secret_rotated_at IS 'secret 회전 시각';


--
-- Name: COLUMN device_credentials.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_credentials.created_at IS '생성 시각';


--
-- Name: COLUMN device_credentials.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_credentials.updated_at IS '수정 시각';


--
-- Name: COLUMN device_credentials.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_credentials.status IS '장치 인증 정보 상태';


--
-- Name: COLUMN device_credentials.enrolled_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_credentials.enrolled_at IS 'QR 등록 완료 시각';


--
-- Name: COLUMN device_credentials.last_authenticated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_credentials.last_authenticated_at IS '마지막 Access Token 발급 인증 시각';


--
-- Name: device_enrollments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_enrollments (
    id uuid NOT NULL,
    device_id uuid NOT NULL,
    registration_token_hash character varying(64) NOT NULL,
    status character varying(20) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    revoked_at timestamp with time zone,
    attempt_count integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 5 NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_device_enrollments_attempts CHECK (((attempt_count >= 0) AND (max_attempts >= 1) AND (attempt_count <= max_attempts))),
    CONSTRAINT ck_device_enrollments_expiration CHECK ((expires_at > created_at)),
    CONSTRAINT ck_device_enrollments_state CHECK (((((status)::text = 'ACTIVE'::text) AND (used_at IS NULL) AND (revoked_at IS NULL)) OR (((status)::text = 'USED'::text) AND (used_at IS NOT NULL) AND (revoked_at IS NULL)) OR (((status)::text = 'EXPIRED'::text) AND (used_at IS NULL) AND (revoked_at IS NULL)) OR (((status)::text = 'REVOKED'::text) AND (used_at IS NULL) AND (revoked_at IS NOT NULL)))),
    CONSTRAINT ck_device_enrollments_status CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('USED'::character varying)::text, ('EXPIRED'::character varying)::text, ('REVOKED'::character varying)::text]))),
    CONSTRAINT ck_device_enrollments_token_hash CHECK (((registration_token_hash)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: TABLE device_enrollments; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.device_enrollments IS 'QR 기반 Device Secret 최초 발급을 위한 일회성 등록 요청';


--
-- Name: COLUMN device_enrollments.registration_token_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_enrollments.registration_token_hash IS 'QR에 포함된 원문 등록 토큰의 SHA-256';


--
-- Name: COLUMN device_enrollments.used_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_enrollments.used_at IS 'Device Secret 발급 완료 시각';


--
-- Name: COLUMN device_enrollments.attempt_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_enrollments.attempt_count IS '등록 완료 검증 실패 횟수';


--
-- Name: COLUMN device_enrollments.max_attempts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_enrollments.max_attempts IS '허용되는 최대 검증 실패 횟수';


--
-- Name: COLUMN device_enrollments.created_by; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.device_enrollments.created_by IS 'QR 등록을 시작한 사용자';


--
-- Name: devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.devices (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    device_type character varying(20) NOT NULL,
    handle_number character varying(100) NOT NULL,
    name character varying(100) NOT NULL,
    connection_status character varying(20) NOT NULL,
    firmware_version character varying(100),
    runtime_version character varying(100),
    last_seen_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    location character varying(200),
    registration_status character varying(20) DEFAULT 'REGISTERED'::character varying NOT NULL,
    CONSTRAINT ck_devices_device_type CHECK (((device_type)::text = ANY (ARRAY[('UMI_CAMERA'::character varying)::text, ('JETSON'::character varying)::text]))),
    CONSTRAINT ck_devices_registration_status CHECK (((registration_status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('REGISTERED'::character varying)::text]))),
    CONSTRAINT devices_connection_status_check CHECK (((connection_status)::text = ANY (ARRAY[('ONLINE'::character varying)::text, ('OFFLINE'::character varying)::text])))
);


--
-- Name: COLUMN devices.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.id IS 'PK';


--
-- Name: COLUMN devices.owner_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.owner_id IS 'FK → `users.id`';


--
-- Name: COLUMN devices.device_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.device_type IS '`UMI_CAMERA`, `JETSON`';


--
-- Name: COLUMN devices.handle_number; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.handle_number IS '외부 장치 식별자';


--
-- Name: COLUMN devices.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.name IS '사용자 표시 이름';


--
-- Name: COLUMN devices.connection_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.connection_status IS '`ONLINE`, `OFFLINE`';


--
-- Name: COLUMN devices.firmware_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.firmware_version IS '마지막 heartbeat 값';


--
-- Name: COLUMN devices.runtime_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.runtime_version IS '마지막 heartbeat 값';


--
-- Name: COLUMN devices.last_seen_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.last_seen_at IS '서버 수신 기준 시각';


--
-- Name: COLUMN devices.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.created_at IS '생성 시각';


--
-- Name: COLUMN devices.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.updated_at IS '수정 시각';


--
-- Name: COLUMN devices.location; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.location IS '장치 설치 위치. 미지정 가능';


--
-- Name: COLUMN devices.registration_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.devices.registration_status IS 'UMI 장치 등록 상태. QR 등록 완료 전 PENDING, Device Secret 발급 완료 후 REGISTERED';


--
-- Name: episode_archives; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.episode_archives (
    id uuid NOT NULL,
    episode_id uuid NOT NULL,
    storage_node_id uuid NOT NULL,
    storage_key character varying(500) NOT NULL,
    original_filename character varying(255) NOT NULL,
    content_type character varying(150) NOT NULL,
    archive_format_version character varying(100) NOT NULL,
    compression_method character varying(20) NOT NULL,
    size_bytes bigint NOT NULL,
    uncompressed_size_bytes bigint NOT NULL,
    entry_count integer NOT NULL,
    sha256 character varying(64) NOT NULL,
    manifest_summary jsonb NOT NULL,
    status character varying(30) NOT NULL,
    stored_at timestamp with time zone,
    validated_at timestamp with time zone,
    deleted_at timestamp with time zone,
    error_code character varying(100),
    error_message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_episode_archives_entry_count CHECK ((entry_count >= 6)),
    CONSTRAINT episode_archives_compression_method_check CHECK (((compression_method)::text = 'STORE'::text)),
    CONSTRAINT episode_archives_content_type_check CHECK (((content_type)::text = 'application/zip'::text)),
    CONSTRAINT episode_archives_sha256_check CHECK (((sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT episode_archives_size_bytes_check CHECK ((size_bytes > 0)),
    CONSTRAINT episode_archives_status_check CHECK (((status)::text = ANY (ARRAY[('UPLOADING'::character varying)::text, ('AVAILABLE'::character varying)::text, ('DELETING'::character varying)::text, ('DELETED'::character varying)::text, ('DELETE_FAILED'::character varying)::text]))),
    CONSTRAINT episode_archives_storage_key_check CHECK (((storage_key)::text !~ '^/'::text)),
    CONSTRAINT episode_archives_storage_key_check1 CHECK (((storage_key)::text !~ '(^|/)\.\.?(/|$)'::text)),
    CONSTRAINT episode_archives_storage_key_check2 CHECK (((storage_key)::text !~ '[\\]'::text)),
    CONSTRAINT episode_archives_uncompressed_size_bytes_check CHECK ((uncompressed_size_bytes > 0))
);


--
-- Name: COLUMN episode_archives.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.id IS 'PK';


--
-- Name: COLUMN episode_archives.episode_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.episode_id IS 'FK → `episodes.id`, UNIQUE';


--
-- Name: COLUMN episode_archives.storage_node_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.storage_node_id IS 'FK → `storage_nodes.id`';


--
-- Name: COLUMN episode_archives.storage_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.storage_key IS '서버 생성 S3 object key: episodes/{owner UUID 소문자}/{yyyyMMddTHHmmssZ}/{session UUID v4 소문자}.zip';


--
-- Name: COLUMN episode_archives.original_filename; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.original_filename IS '감사용 원본 파일명 {session UUID v4 소문자}.zip';


--
-- Name: COLUMN episode_archives.content_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.content_type IS '고정 `application/zip`';


--
-- Name: COLUMN episode_archives.archive_format_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.archive_format_version IS '예: `umi-episode-zip-v1`';


--
-- Name: COLUMN episode_archives.compression_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.compression_method IS '고정 `STORE`';


--
-- Name: COLUMN episode_archives.size_bytes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.size_bytes IS 'ZIP 파일 크기';


--
-- Name: COLUMN episode_archives.uncompressed_size_bytes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.uncompressed_size_bytes IS 'ZIP entry 원본 크기 합계';


--
-- Name: COLUMN episode_archives.entry_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.entry_count IS 'ZIP entry 수';


--
-- Name: COLUMN episode_archives.sha256; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.sha256 IS '최종 ZIP 전체 SHA-256';


--
-- Name: COLUMN episode_archives.manifest_summary; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.manifest_summary IS '필수 entry 존재 여부와 주요 내부 파일 SHA-256 snapshot';


--
-- Name: COLUMN episode_archives.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.status IS 'archive 보관·삭제 상태';


--
-- Name: COLUMN episode_archives.stored_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.stored_at IS 'atomic rename 완료 시각';


--
-- Name: COLUMN episode_archives.validated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.validated_at IS 'ZIP 구조·내용 검증 완료 시각';


--
-- Name: COLUMN episode_archives.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.deleted_at IS '실제 ZIP 삭제 확인 시각';


--
-- Name: COLUMN episode_archives.error_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.error_code IS '최근 archive 오류';


--
-- Name: COLUMN episode_archives.error_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.error_message IS '최근 archive 오류 설명';


--
-- Name: COLUMN episode_archives.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.created_at IS '생성 시각';


--
-- Name: COLUMN episode_archives.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_archives.updated_at IS '수정 시각';


--
-- Name: episode_upload_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.episode_upload_sessions (
    id uuid NOT NULL,
    episode_id uuid NOT NULL,
    training_job_id uuid,
    owner_id uuid NOT NULL,
    umi_device_id uuid NOT NULL,
    session_id uuid NOT NULL,
    content_type character varying(150) NOT NULL,
    size_bytes bigint NOT NULL,
    sha256 character varying(64) NOT NULL,
    metadata jsonb NOT NULL,
    summary jsonb NOT NULL,
    bucket_name character varying(255) NOT NULL,
    object_key character varying(500) NOT NULL,
    status character varying(20) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    canceled_at timestamp with time zone,
    quality_status character varying(20),
    validation_error_code character varying(100),
    validation_error_message text,
    validated_at timestamp with time zone,
    CONSTRAINT ck_episode_upload_completion CHECK (((((status)::text = 'UPLOAD_PENDING'::text) AND (completed_at IS NULL) AND (canceled_at IS NULL) AND (validated_at IS NULL)) OR (((status)::text = 'VALIDATING'::text) AND (completed_at IS NOT NULL) AND (canceled_at IS NULL) AND (validated_at IS NULL)) OR (((status)::text = 'READY'::text) AND (completed_at IS NOT NULL) AND (canceled_at IS NULL) AND (validated_at IS NOT NULL) AND ((quality_status)::text = ANY (ARRAY[('VALID'::character varying)::text, ('WARNING'::character varying)::text])) AND (validation_error_code IS NULL) AND (validation_error_message IS NULL)) OR (((status)::text = ANY (ARRAY[('INVALID'::character varying)::text, ('FAILED'::character varying)::text])) AND (completed_at IS NOT NULL) AND (canceled_at IS NULL) AND (validated_at IS NOT NULL) AND (validation_error_code IS NOT NULL) AND (validation_error_message IS NOT NULL)) OR (((status)::text = 'EXPIRED'::text) AND (completed_at IS NULL) AND (canceled_at IS NULL) AND (validated_at IS NULL)) OR (((status)::text = 'CANCELLED'::text) AND (completed_at IS NULL) AND (canceled_at IS NOT NULL) AND (validated_at IS NULL)))),
    CONSTRAINT ck_episode_upload_content_type CHECK (((content_type)::text = 'application/zip'::text)),
    CONSTRAINT ck_episode_upload_sha256 CHECK (((sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT ck_episode_upload_size CHECK ((size_bytes > 0)),
    CONSTRAINT ck_episode_upload_status CHECK (((status)::text = ANY (ARRAY[('UPLOAD_PENDING'::character varying)::text, ('VALIDATING'::character varying)::text, ('READY'::character varying)::text, ('INVALID'::character varying)::text, ('FAILED'::character varying)::text, ('EXPIRED'::character varying)::text, ('CANCELLED'::character varying)::text])))
);


--
-- Name: COLUMN episode_upload_sessions.training_job_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_upload_sessions.training_job_id IS 'Legacy training job reference. New standalone episode uploads leave this column NULL.';


--
-- Name: COLUMN episode_upload_sessions.canceled_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_upload_sessions.canceled_at IS '미완료 업로드 세션 취소 시각';


--
-- Name: COLUMN episode_upload_sessions.quality_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_upload_sessions.quality_status IS 'ZIP 검증으로 확정한 VALID, WARNING, INVALID';


--
-- Name: COLUMN episode_upload_sessions.validation_error_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_upload_sessions.validation_error_code IS '검증 또는 처리 실패 코드';


--
-- Name: COLUMN episode_upload_sessions.validation_error_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_upload_sessions.validation_error_message IS '검증 또는 처리 실패 설명';


--
-- Name: COLUMN episode_upload_sessions.validated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episode_upload_sessions.validated_at IS '검증 결과 확정 시각';


--
-- Name: episodes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.episodes (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    umi_device_id uuid NOT NULL,
    storage_node_id uuid NOT NULL,
    retention_policy_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    session_id character varying(150) NOT NULL,
    source character varying(10) NOT NULL,
    success boolean,
    label_status character varying(20) NOT NULL,
    label_source character varying(20),
    labeled_at timestamp with time zone,
    contract_version character varying(100) NOT NULL,
    meta_schema_version character varying(50) NOT NULL,
    summary_schema_version character varying(50) NOT NULL,
    status character varying(30) NOT NULL,
    started_at timestamp with time zone NOT NULL,
    ended_at timestamp with time zone NOT NULL,
    start_monotonic_ns bigint NOT NULL,
    timestamp_timebase character varying(30) NOT NULL,
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
    quality_status character varying(20) NOT NULL,
    capture_metadata jsonb NOT NULL,
    quality_summary jsonb NOT NULL,
    validated_at timestamp with time zone,
    last_used_at timestamp with time zone,
    retention_expires_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    error_code character varying(100),
    error_message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_episodes_gripper_sample_count CHECK ((gripper_sample_count >= 0)),
    CONSTRAINT episodes_check CHECK (((((label_status)::text = 'LABELED'::text) AND (success IS NOT NULL) AND (label_source IS NOT NULL) AND (labeled_at IS NOT NULL)) OR (((label_status)::text = 'UNLABELED'::text) AND (success IS NULL) AND (label_source IS NULL) AND (labeled_at IS NULL)))),
    CONSTRAINT episodes_check1 CHECK ((ended_at >= started_at)),
    CONSTRAINT episodes_check2 CHECK ((first_timestamp_ns >= start_monotonic_ns)),
    CONSTRAINT episodes_check3 CHECK ((last_timestamp_ns >= first_timestamp_ns)),
    CONSTRAINT episodes_check4 CHECK ((duration_ns = (last_timestamp_ns - first_timestamp_ns))),
    CONSTRAINT episodes_frame_count_check CHECK ((frame_count >= 1)),
    CONSTRAINT episodes_imu_sample_count_check CHECK ((imu_sample_count >= 1)),
    CONSTRAINT episodes_label_source_check CHECK (((label_source)::text = ANY (ARRAY[('UMI'::character varying)::text, ('USER'::character varying)::text, ('SERVER'::character varying)::text]))),
    CONSTRAINT episodes_label_status_check CHECK (((label_status)::text = ANY (ARRAY[('UNLABELED'::character varying)::text, ('LABELED'::character varying)::text]))),
    CONSTRAINT episodes_missing_frame_count_check CHECK ((missing_frame_count >= 0)),
    CONSTRAINT episodes_pose_sample_count_check CHECK ((pose_sample_count >= 0)),
    CONSTRAINT episodes_quality_status_check CHECK (((quality_status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('VALID'::character varying)::text, ('WARNING'::character varying)::text, ('INVALID'::character varying)::text]))),
    CONSTRAINT episodes_source_check CHECK (((source)::text = ANY (ARRAY[('SIM'::character varying)::text, ('REAL'::character varying)::text]))),
    CONSTRAINT episodes_start_monotonic_ns_check CHECK ((start_monotonic_ns >= 0)),
    CONSTRAINT episodes_status_check CHECK (((status)::text = ANY (ARRAY[('UPLOADING'::character varying)::text, ('VALIDATING'::character varying)::text, ('READY'::character varying)::text, ('INVALID'::character varying)::text, ('DELETING'::character varying)::text, ('DELETED'::character varying)::text, ('DELETE_FAILED'::character varying)::text]))),
    CONSTRAINT episodes_timestamp_timebase_check CHECK (((timestamp_timebase)::text = 'MONOTONIC_NS'::text)),
    CONSTRAINT episodes_tracking_gap_count_check CHECK ((tracking_gap_count >= 0)),
    CONSTRAINT episodes_tracking_lost_duration_ns_check CHECK ((tracking_lost_duration_ns >= 0))
);


--
-- Name: COLUMN episodes.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.id IS 'PK';


--
-- Name: COLUMN episodes.owner_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.owner_id IS 'FK → `users.id`';


--
-- Name: COLUMN episodes.umi_device_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.umi_device_id IS 'FK → `devices.id`, UMI만 허용';


--
-- Name: COLUMN episodes.storage_node_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.storage_node_id IS 'FK → `storage_nodes.id`';


--
-- Name: COLUMN episodes.retention_policy_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.retention_policy_id IS 'FK → `retention_policies.id`';


--
-- Name: COLUMN episodes.skill_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.skill_id IS 'FK → `skills.id`, `meta.json.skill_key`를 서버가 해석한 값';


--
-- Name: COLUMN episodes.session_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.session_id IS 'UMI가 생성한 소문자 UUID v4. ZIP 파일명의 확장자를 제외한 값과 일치';


--
-- Name: COLUMN episodes.source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.source IS '`SIM`, `REAL`';


--
-- Name: COLUMN episodes.success; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.success IS '수행 성공 여부, 미라벨이면 NULL';


--
-- Name: COLUMN episodes.label_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.label_status IS '`UNLABELED`, `LABELED`';


--
-- Name: COLUMN episodes.label_source; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.label_source IS '`UMI`, `USER`, `SERVER`';


--
-- Name: COLUMN episodes.labeled_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.labeled_at IS '라벨 확정 시각';


--
-- Name: COLUMN episodes.contract_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.contract_version IS '전체 Episode archive 계약 버전';


--
-- Name: COLUMN episodes.meta_schema_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.meta_schema_version IS '`meta.json.schema_version`';


--
-- Name: COLUMN episodes.summary_schema_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.summary_schema_version IS '`summary.json.schema_version`';


--
-- Name: COLUMN episodes.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.status IS 'Episode 상태';


--
-- Name: COLUMN episodes.started_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.started_at IS '`meta.json.started_at`';


--
-- Name: COLUMN episodes.ended_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.ended_at IS '`meta.json.ended_at`';


--
-- Name: COLUMN episodes.start_monotonic_ns; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.start_monotonic_ns IS 'CSV `t_ns`와 UTC를 연결하는 기준';


--
-- Name: COLUMN episodes.timestamp_timebase; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.timestamp_timebase IS 'MVP는 `MONOTONIC_NS`';


--
-- Name: COLUMN episodes.first_timestamp_ns; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.first_timestamp_ns IS '전체 데이터의 가장 이른 `t_ns`';


--
-- Name: COLUMN episodes.last_timestamp_ns; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.last_timestamp_ns IS '전체 데이터의 가장 늦은 `t_ns`';


--
-- Name: COLUMN episodes.duration_ns; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.duration_ns IS '`last_timestamp_ns - first_timestamp_ns`';


--
-- Name: COLUMN episodes.frame_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.frame_count IS '검증된 이미지 프레임 수';


--
-- Name: COLUMN episodes.pose_sample_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.pose_sample_count IS 'Raw UMI archive has no pose.csv; zero until SLAM preprocessing produces poses';


--
-- Name: COLUMN episodes.imu_sample_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.imu_sample_count IS 'accelerometer.csv and gyroscope.csv data row count sum';


--
-- Name: COLUMN episodes.gripper_sample_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.gripper_sample_count IS 'Raw UMI archive has no gripper.csv; zero until marker preprocessing produces the channel';


--
-- Name: COLUMN episodes.missing_frame_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.missing_frame_count IS '누락 프레임 수';


--
-- Name: COLUMN episodes.tracking_gap_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.tracking_gap_count IS '추적 중단 구간 수';


--
-- Name: COLUMN episodes.tracking_lost_duration_ns; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.tracking_lost_duration_ns IS '추적 중단 누적 시간';


--
-- Name: COLUMN episodes.quality_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.quality_status IS '`PENDING`, `VALID`, `WARNING`, `INVALID`';


--
-- Name: COLUMN episodes.capture_metadata; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.capture_metadata IS '장치·ARCore·카메라 내부 파라미터·좌표계·단위 snapshot';


--
-- Name: COLUMN episodes.quality_summary; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.quality_summary IS '추가 결측·품질 집계 snapshot';


--
-- Name: COLUMN episodes.validated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.validated_at IS '서버 archive 검증 완료 시각';


--
-- Name: COLUMN episodes.last_used_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.last_used_at IS '마지막 학습 사용 시각';


--
-- Name: COLUMN episodes.retention_expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.retention_expires_at IS '삭제 가능 기준 시각';


--
-- Name: COLUMN episodes.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.deleted_at IS '물리 ZIP 삭제 확인 시각';


--
-- Name: COLUMN episodes.error_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.error_code IS '최근 오류 코드';


--
-- Name: COLUMN episodes.error_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.error_message IS '최근 오류 설명';


--
-- Name: COLUMN episodes.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.created_at IS '생성 시각';


--
-- Name: COLUMN episodes.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.updated_at IS '수정 시각';


--
-- Name: COLUMN episodes.row_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.episodes.row_version IS '선택·삭제 경합 방지용 낙관적 잠금';


--
-- Name: idempotency_keys; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotency_keys (
    id uuid NOT NULL,
    actor_type character varying(30) NOT NULL,
    actor_id character varying(150) NOT NULL,
    http_method character varying(10) NOT NULL,
    request_path character varying(500) NOT NULL,
    idempotency_key character varying(200) NOT NULL,
    request_hash character varying(64) NOT NULL,
    response_status integer,
    response_body jsonb,
    resource_type character varying(100),
    resource_id character varying(150),
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT idempotency_keys_actor_type_check CHECK (((actor_type)::text = ANY (ARRAY[('USER'::character varying)::text, ('DEVICE'::character varying)::text, ('UMI'::character varying)::text, ('GPU'::character varying)::text, ('JETSON'::character varying)::text, ('INTERNAL'::character varying)::text]))),
    CONSTRAINT idempotency_keys_request_hash_check CHECK (((request_hash)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: COLUMN idempotency_keys.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.id IS 'PK';


--
-- Name: COLUMN idempotency_keys.actor_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.actor_type IS '`USER`, `UMI`, `GPU`, `JETSON`, `INTERNAL`';


--
-- Name: COLUMN idempotency_keys.actor_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.actor_id IS '주체 ID';


--
-- Name: COLUMN idempotency_keys.http_method; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.http_method IS '요청 method';


--
-- Name: COLUMN idempotency_keys.request_path; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.request_path IS 'path template 또는 정규화 path';


--
-- Name: COLUMN idempotency_keys.idempotency_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.idempotency_key IS '클라이언트 전달 key';


--
-- Name: COLUMN idempotency_keys.request_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.request_hash IS '정규화 요청 본문 hash';


--
-- Name: COLUMN idempotency_keys.response_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.response_status IS '최초 응답 HTTP status';


--
-- Name: COLUMN idempotency_keys.response_body; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.response_body IS '재사용할 응답';


--
-- Name: COLUMN idempotency_keys.resource_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.resource_type IS '생성된 리소스 유형';


--
-- Name: COLUMN idempotency_keys.resource_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.resource_id IS '생성된 리소스 ID';


--
-- Name: COLUMN idempotency_keys.expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.expires_at IS '중복 보장 만료';


--
-- Name: COLUMN idempotency_keys.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.idempotency_keys.created_at IS '최초 처리 시각';


--
-- Name: outbox_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox_events (
    id uuid NOT NULL,
    event_id character varying(150) NOT NULL,
    aggregate_type character varying(100) NOT NULL,
    aggregate_id character varying(150) NOT NULL,
    event_type character varying(150) NOT NULL,
    payload jsonb NOT NULL,
    status character varying(20) NOT NULL,
    attempt integer NOT NULL,
    available_at timestamp with time zone NOT NULL,
    published_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    locked_by character varying(100),
    locked_at timestamp with time zone,
    lease_expires_at timestamp with time zone,
    aggregate_sequence bigint,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_outbox_attempt CHECK ((attempt >= 0)),
    CONSTRAINT ck_outbox_claim_consistency CHECK (((((status)::text = 'PROCESSING'::text) AND (locked_by IS NOT NULL) AND (locked_at IS NOT NULL) AND (lease_expires_at IS NOT NULL)) OR (((status)::text <> 'PROCESSING'::text) AND (locked_by IS NULL) AND (locked_at IS NULL) AND (lease_expires_at IS NULL)))),
    CONSTRAINT ck_outbox_sequence CHECK (((aggregate_sequence IS NULL) OR (aggregate_sequence >= 1))),
    CONSTRAINT ck_outbox_status CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PROCESSING'::character varying)::text, ('PUBLISHED'::character varying)::text, ('FAILED'::character varying)::text, ('DEAD'::character varying)::text])))
);


--
-- Name: COLUMN outbox_events.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.id IS 'PK';


--
-- Name: COLUMN outbox_events.event_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.event_id IS '외부 중복 제거 ID';


--
-- Name: COLUMN outbox_events.aggregate_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.aggregate_type IS '`EPISODE`, `TRAINING_JOB` 등';


--
-- Name: COLUMN outbox_events.aggregate_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.aggregate_id IS '대상 리소스 ID';


--
-- Name: COLUMN outbox_events.event_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.event_type IS '이벤트 종류';


--
-- Name: COLUMN outbox_events.payload; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.payload IS 'secret·binary·서명 URL 제외';


--
-- Name: COLUMN outbox_events.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.status IS '`PENDING`, `PUBLISHED`, `FAILED`';


--
-- Name: COLUMN outbox_events.attempt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.attempt IS '발행 시도 횟수';


--
-- Name: COLUMN outbox_events.available_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.available_at IS '다음 처리 가능 시각';


--
-- Name: COLUMN outbox_events.published_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.published_at IS '발행 완료';


--
-- Name: COLUMN outbox_events.last_error; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.last_error IS '마지막 오류';


--
-- Name: COLUMN outbox_events.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.outbox_events.created_at IS '생성 시각';


--
-- Name: refresh_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.refresh_sessions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_hash character varying(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    replaced_by_session_id uuid,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_used_at timestamp with time zone,
    CONSTRAINT refresh_sessions_token_hash_check CHECK (((token_hash)::text ~ '^[0-9a-f]{64}$'::text))
);


--
-- Name: COLUMN refresh_sessions.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_sessions.id IS 'PK';


--
-- Name: COLUMN refresh_sessions.user_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_sessions.user_id IS 'FK → `users.id`';


--
-- Name: COLUMN refresh_sessions.token_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_sessions.token_hash IS 'Refresh Token 원문 저장 금지';


--
-- Name: COLUMN refresh_sessions.expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_sessions.expires_at IS '만료 시각';


--
-- Name: COLUMN refresh_sessions.revoked_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_sessions.revoked_at IS '폐기 시각';


--
-- Name: COLUMN refresh_sessions.replaced_by_session_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_sessions.replaced_by_session_id IS '회전된 새 세션 FK';


--
-- Name: COLUMN refresh_sessions.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_sessions.created_at IS '생성 시각';


--
-- Name: COLUMN refresh_sessions.last_used_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.refresh_sessions.last_used_at IS '마지막 갱신 시각';


--
-- Name: retention_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.retention_policies (
    id uuid NOT NULL,
    name character varying(100) NOT NULL,
    default_retention_days integer NOT NULL,
    post_training_grace_days integer NOT NULL,
    max_retention_days integer NOT NULL,
    max_delete_attempts integer NOT NULL,
    allow_manual_extension boolean NOT NULL,
    allow_manual_purge boolean NOT NULL,
    is_default boolean NOT NULL,
    active boolean NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT retention_policies_check CHECK ((max_retention_days >= default_retention_days)),
    CONSTRAINT retention_policies_default_retention_days_check CHECK ((default_retention_days >= 0)),
    CONSTRAINT retention_policies_max_delete_attempts_check CHECK ((max_delete_attempts >= 1)),
    CONSTRAINT retention_policies_post_training_grace_days_check CHECK ((post_training_grace_days >= 0))
);


--
-- Name: COLUMN retention_policies.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.id IS 'PK';


--
-- Name: COLUMN retention_policies.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.name IS '정책 이름';


--
-- Name: COLUMN retention_policies.default_retention_days; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.default_retention_days IS '검증 완료 후 기본 보관 일수';


--
-- Name: COLUMN retention_policies.post_training_grace_days; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.post_training_grace_days IS '마지막 학습 종료 후 유예 일수';


--
-- Name: COLUMN retention_policies.max_retention_days; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.max_retention_days IS '사용자 연장 최대 일수';


--
-- Name: COLUMN retention_policies.max_delete_attempts; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.max_delete_attempts IS '자동 삭제 최대 시도 횟수';


--
-- Name: COLUMN retention_policies.allow_manual_extension; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.allow_manual_extension IS '사용자 연장 허용';


--
-- Name: COLUMN retention_policies.allow_manual_purge; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.allow_manual_purge IS '사용자 조기 삭제 허용';


--
-- Name: COLUMN retention_policies.is_default; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.is_default IS '기본 정책 여부';


--
-- Name: COLUMN retention_policies.active; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.active IS '적용 가능 여부';


--
-- Name: COLUMN retention_policies.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.created_at IS '생성 시각';


--
-- Name: COLUMN retention_policies.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.retention_policies.updated_at IS '수정 시각';


--
-- Name: robot_executions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.robot_executions (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    robot_id uuid NOT NULL,
    task_version_id uuid NOT NULL,
    deployment_id uuid NOT NULL,
    status character varying(30) NOT NULL,
    current_step integer,
    total_steps integer NOT NULL,
    progress_percent numeric(5,2) DEFAULT 0 NOT NULL,
    last_report_id character varying(150),
    last_report_sequence bigint DEFAULT 0 NOT NULL,
    last_report_hash character varying(64),
    error_code character varying(100),
    error_message text,
    recovery_action character varying(40),
    requested_at timestamp with time zone DEFAULT now() NOT NULL,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_robot_executions_current_step CHECK (((current_step IS NULL) OR (current_step >= 1))),
    CONSTRAINT ck_robot_executions_error CHECK (((((status)::text = 'FAILED'::text) AND (error_code IS NOT NULL) AND (error_message IS NOT NULL)) OR ((status)::text <> 'FAILED'::text))),
    CONSTRAINT ck_robot_executions_finished_at CHECK (((((status)::text = ANY (ARRAY[('CANCELED'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text])) AND (finished_at IS NOT NULL)) OR (((status)::text <> ALL (ARRAY[('CANCELED'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text])) AND (finished_at IS NULL)))),
    CONSTRAINT ck_robot_executions_progress CHECK (((progress_percent >= (0)::numeric) AND (progress_percent <= (100)::numeric))),
    CONSTRAINT ck_robot_executions_report_consistency CHECK ((((last_report_sequence = 0) AND (last_report_id IS NULL) AND (last_report_hash IS NULL)) OR ((last_report_sequence > 0) AND (last_report_id IS NOT NULL) AND ((last_report_hash)::text ~ '^[0-9a-f]{64}$'::text)))),
    CONSTRAINT ck_robot_executions_report_sequence CHECK ((last_report_sequence >= 0)),
    CONSTRAINT ck_robot_executions_started_at CHECK ((((status)::text = ANY (ARRAY[('REQUESTED'::character varying)::text, ('CANCELED'::character varying)::text])) OR (started_at IS NOT NULL))),
    CONSTRAINT ck_robot_executions_status CHECK (((status)::text = ANY (ARRAY[('REQUESTED'::character varying)::text, ('STARTING'::character varying)::text, ('RUNNING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text, ('CANCELED'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text, ('STATUS_UNKNOWN'::character varying)::text]))),
    CONSTRAINT ck_robot_executions_step_range CHECK (((current_step IS NULL) OR (current_step <= total_steps))),
    CONSTRAINT ck_robot_executions_time_order CHECK ((((started_at IS NULL) OR (started_at >= requested_at)) AND ((finished_at IS NULL) OR (finished_at >= COALESCE(started_at, requested_at))) AND (updated_at >= requested_at))),
    CONSTRAINT ck_robot_executions_total_steps CHECK ((total_steps >= 1))
);


--
-- Name: TABLE robot_executions; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE public.robot_executions IS '웹 사용자가 요청한 로봇 TaskVersion 실행과 Jetson 보고의 최종 상태';


--
-- Name: COLUMN robot_executions.last_report_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_executions.last_report_hash IS '동일 reportId/sequence 재전송의 본문 동일성 확인용 SHA-256';


--
-- Name: COLUMN robot_executions.recovery_action; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_executions.recovery_action IS '오류 발생 시 FE가 안내할 복구 행동 코드';


--
-- Name: robot_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.robot_profiles (
    id uuid NOT NULL,
    name character varying(150) NOT NULL,
    contract_version character varying(100) NOT NULL,
    architecture character varying(100) NOT NULL,
    runtime character varying(100) NOT NULL,
    runtime_version character varying(100) NOT NULL,
    camera_contract jsonb NOT NULL,
    state_contract jsonb NOT NULL,
    action_contract jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: COLUMN robot_profiles.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.id IS 'PK';


--
-- Name: COLUMN robot_profiles.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.name IS '프로파일 이름';


--
-- Name: COLUMN robot_profiles.contract_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.contract_version IS '입력·출력 계약 버전';


--
-- Name: COLUMN robot_profiles.architecture; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.architecture IS '로봇/모델 구조';


--
-- Name: COLUMN robot_profiles.runtime; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.runtime IS 'PyTorch, TensorRT 등';


--
-- Name: COLUMN robot_profiles.runtime_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.runtime_version IS '런타임 버전';


--
-- Name: COLUMN robot_profiles.camera_contract; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.camera_contract IS '카메라 이름·입력 규격';


--
-- Name: COLUMN robot_profiles.state_contract; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.state_contract IS 'Trainer-defined observation schema validated against CKPT inputSchema.';


--
-- Name: COLUMN robot_profiles.action_contract; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.action_contract IS 'Trainer-defined actionSpec, runtimeSpec, and outputSchema validated against CKPT metadata.';


--
-- Name: COLUMN robot_profiles.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.created_at IS '생성 시각';


--
-- Name: COLUMN robot_profiles.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_profiles.updated_at IS '수정 시각';


--
-- Name: robot_skill_deployments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.robot_skill_deployments (
    id uuid NOT NULL,
    robot_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    skill_version_id uuid NOT NULL,
    deployment_id uuid NOT NULL,
    status character varying(20) NOT NULL,
    deployed_at timestamp with time zone NOT NULL,
    retired_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT robot_skill_deployments_check CHECK (((((status)::text = 'ACTIVE'::text) AND (retired_at IS NULL)) OR (((status)::text = 'RETIRED'::text) AND (retired_at IS NOT NULL)))),
    CONSTRAINT robot_skill_deployments_status_check CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('RETIRED'::character varying)::text])))
);


--
-- Name: COLUMN robot_skill_deployments.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_skill_deployments.id IS 'PK';


--
-- Name: COLUMN robot_skill_deployments.robot_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_skill_deployments.robot_id IS 'FK → `robots.id`';


--
-- Name: COLUMN robot_skill_deployments.skill_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_skill_deployments.skill_id IS 'FK → `skills.id`';


--
-- Name: COLUMN robot_skill_deployments.skill_version_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_skill_deployments.skill_version_id IS 'FK → `skill_versions.id`';


--
-- Name: COLUMN robot_skill_deployments.deployment_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_skill_deployments.deployment_id IS 'FK → `deployments.id`, 이 상태를 만든 작업';


--
-- Name: COLUMN robot_skill_deployments.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_skill_deployments.status IS '`ACTIVE`, `RETIRED`';


--
-- Name: COLUMN robot_skill_deployments.deployed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_skill_deployments.deployed_at IS '활성화 시각';


--
-- Name: COLUMN robot_skill_deployments.retired_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_skill_deployments.retired_at IS '은퇴 시각';


--
-- Name: COLUMN robot_skill_deployments.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robot_skill_deployments.created_at IS '생성 시각';


--
-- Name: robots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.robots (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    jetson_device_id uuid NOT NULL,
    robot_profile_id uuid NOT NULL,
    name character varying(150) NOT NULL,
    connection_status character varying(20) NOT NULL,
    current_deployment_id uuid,
    last_seen_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT robots_connection_status_check CHECK (((connection_status)::text = ANY (ARRAY[('ONLINE'::character varying)::text, ('OFFLINE'::character varying)::text])))
);


--
-- Name: COLUMN robots.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.id IS 'PK';


--
-- Name: COLUMN robots.owner_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.owner_id IS 'FK → `users.id`';


--
-- Name: COLUMN robots.jetson_device_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.jetson_device_id IS 'FK → `devices.id`, JETSON만 허용';


--
-- Name: COLUMN robots.robot_profile_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.robot_profile_id IS 'FK → `robot_profiles.id`';


--
-- Name: COLUMN robots.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.name IS '표시 이름';


--
-- Name: COLUMN robots.connection_status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.connection_status IS '`ONLINE`, `OFFLINE`';


--
-- Name: COLUMN robots.current_deployment_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.current_deployment_id IS 'FK → `deployments.id`, migration 후 추가';


--
-- Name: COLUMN robots.last_seen_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.last_seen_at IS '마지막 연결 확인';


--
-- Name: COLUMN robots.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.created_at IS '생성 시각';


--
-- Name: COLUMN robots.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.updated_at IS '수정 시각';


--
-- Name: COLUMN robots.row_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.robots.row_version IS '배포 적용 낙관적 잠금';


--
-- Name: skill_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.skill_versions (
    id uuid NOT NULL,
    skill_id uuid NOT NULL,
    training_job_id uuid NOT NULL,
    robot_profile_id uuid NOT NULL,
    version integer NOT NULL,
    status character varying(30) NOT NULL,
    checkpoint_bucket character varying(255) NOT NULL,
    checkpoint_key character varying(700) NOT NULL,
    checkpoint_sha256 character varying(64) NOT NULL,
    checkpoint_bytes bigint NOT NULL,
    architecture character varying(100) NOT NULL,
    framework character varying(100) NOT NULL,
    framework_version character varying(100) NOT NULL,
    contract_version character varying(100) NOT NULL,
    action_space character varying(30) NOT NULL,
    n_params bigint NOT NULL,
    control_rate_hz numeric(10,3) NOT NULL,
    camera_names jsonb NOT NULL,
    input_schema jsonb NOT NULL,
    output_schema jsonb NOT NULL,
    gate_metric character varying(100),
    gate_n integer,
    gate_runs integer,
    gate_value double precision,
    gate_ci_low double precision,
    gate_ci_high double precision,
    gate_measured_at timestamp with time zone,
    gate_passed boolean GENERATED ALWAYS AS (COALESCE((((gate_metric)::text = 'rollout_success'::text) AND (gate_n >= 100) AND (gate_runs >= 3) AND (gate_value > (0.20)::double precision) AND (gate_ci_low > (0.20)::double precision)), false)) STORED NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    validated_at timestamp with time zone,
    archived_at timestamp with time zone,
    parent_skill_version_id uuid,
    action_spec jsonb DEFAULT '{}'::jsonb NOT NULL,
    runtime_spec jsonb DEFAULT '{}'::jsonb NOT NULL,
    CONSTRAINT ck_skill_versions_gate_complete CHECK ((((gate_metric IS NULL) AND (gate_n IS NULL) AND (gate_runs IS NULL) AND (gate_value IS NULL) AND (gate_ci_low IS NULL) AND (gate_ci_high IS NULL) AND (gate_measured_at IS NULL)) OR ((gate_metric IS NOT NULL) AND (gate_n IS NOT NULL) AND (gate_runs IS NOT NULL) AND (gate_value IS NOT NULL) AND (gate_ci_low IS NOT NULL) AND (gate_ci_high IS NOT NULL) AND (gate_measured_at IS NOT NULL)))),
    CONSTRAINT skill_versions_action_space_check CHECK (((action_space)::text = ANY ((ARRAY['JOINT_ABSOLUTE'::character varying, 'JOINT_DELTA'::character varying, 'EEF_RELATIVE_ROT6D'::character varying])::text[]))),
    CONSTRAINT skill_versions_check CHECK (((gate_ci_low IS NULL) OR (gate_ci_high IS NULL) OR (gate_ci_low <= gate_ci_high))),
    CONSTRAINT skill_versions_checkpoint_bytes_check CHECK ((checkpoint_bytes >= 0)),
    CONSTRAINT skill_versions_checkpoint_key_check CHECK (((checkpoint_key)::text !~ '^/'::text)),
    CONSTRAINT skill_versions_checkpoint_key_check1 CHECK ((octet_length((checkpoint_key)::text) <= 1024)),
    CONSTRAINT skill_versions_checkpoint_sha256_check CHECK (((checkpoint_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT skill_versions_gate_ci_high_check CHECK (((gate_ci_high IS NULL) OR ((gate_ci_high >= (0)::double precision) AND (gate_ci_high <= (1)::double precision)))),
    CONSTRAINT skill_versions_gate_ci_low_check CHECK (((gate_ci_low IS NULL) OR ((gate_ci_low >= (0)::double precision) AND (gate_ci_low <= (1)::double precision)))),
    CONSTRAINT skill_versions_gate_n_check CHECK (((gate_n IS NULL) OR (gate_n > 0))),
    CONSTRAINT skill_versions_gate_runs_check CHECK (((gate_runs IS NULL) OR (gate_runs > 0))),
    CONSTRAINT skill_versions_gate_value_check CHECK (((gate_value IS NULL) OR ((gate_value >= (0)::double precision) AND (gate_value <= (1)::double precision)))),
    CONSTRAINT skill_versions_n_params_check CHECK ((n_params > 0)),
    CONSTRAINT skill_versions_status_check CHECK (((status)::text = ANY (ARRAY[('UPLOADING'::character varying)::text, ('VALIDATING'::character varying)::text, ('READY'::character varying)::text, ('FAILED'::character varying)::text, ('ARCHIVED'::character varying)::text]))),
    CONSTRAINT skill_versions_version_check CHECK ((version >= 1))
);


--
-- Name: COLUMN skill_versions.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.id IS 'PK';


--
-- Name: COLUMN skill_versions.skill_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.skill_id IS 'FK → `skills.id`';


--
-- Name: COLUMN skill_versions.training_job_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.training_job_id IS 'FK → `training_jobs.id`';


--
-- Name: COLUMN skill_versions.robot_profile_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.robot_profile_id IS 'FK → `robot_profiles.id`';


--
-- Name: COLUMN skill_versions.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.version IS 'Skill 범위 증가 번호';


--
-- Name: COLUMN skill_versions.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.status IS '`UPLOADING`, `VALIDATING`, `READY`, `FAILED`, `ARCHIVED`';


--
-- Name: COLUMN skill_versions.checkpoint_bucket; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.checkpoint_bucket IS '내부 S3 bucket';


--
-- Name: COLUMN skill_versions.checkpoint_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.checkpoint_key IS '서버 생성 불변 S3 key, `ckpt/{owner_id}/{skill_key}/v{version}/{skill_key}_{학습완료UTC}.ckpt`';


--
-- Name: COLUMN skill_versions.checkpoint_sha256; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.checkpoint_sha256 IS 'CKPT SHA-256';


--
-- Name: COLUMN skill_versions.checkpoint_bytes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.checkpoint_bytes IS 'CKPT 크기';


--
-- Name: COLUMN skill_versions.architecture; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.architecture IS '모델 architecture';


--
-- Name: COLUMN skill_versions.framework; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.framework IS '프레임워크';


--
-- Name: COLUMN skill_versions.framework_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.framework_version IS '프레임워크 버전';


--
-- Name: COLUMN skill_versions.contract_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.contract_version IS '모델 입출력 계약';


--
-- Name: COLUMN skill_versions.action_space; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.action_space IS '`JOINT_ABSOLUTE`, `JOINT_DELTA`';


--
-- Name: COLUMN skill_versions.n_params; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.n_params IS '모델 파라미터 수';


--
-- Name: COLUMN skill_versions.control_rate_hz; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.control_rate_hz IS '제어 주기';


--
-- Name: COLUMN skill_versions.camera_names; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.camera_names IS '카메라 이름 배열';


--
-- Name: COLUMN skill_versions.input_schema; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.input_schema IS '입력 계약';


--
-- Name: COLUMN skill_versions.output_schema; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.output_schema IS '출력 계약';


--
-- Name: COLUMN skill_versions.gate_metric; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.gate_metric IS '현재 `rollout_success`';


--
-- Name: COLUMN skill_versions.gate_n; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.gate_n IS '평가 표본 수';


--
-- Name: COLUMN skill_versions.gate_runs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.gate_runs IS '독립 학습·평가 반복 수';


--
-- Name: COLUMN skill_versions.gate_value; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.gate_value IS '게이트 점추정 값';


--
-- Name: COLUMN skill_versions.gate_ci_low; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.gate_ci_low IS '신뢰구간 하한';


--
-- Name: COLUMN skill_versions.gate_ci_high; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.gate_ci_high IS '신뢰구간 상한';


--
-- Name: COLUMN skill_versions.gate_measured_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.gate_measured_at IS '게이트 측정 시각';


--
-- Name: COLUMN skill_versions.gate_passed; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.gate_passed IS '조건식으로 계산되는 generated column';


--
-- Name: COLUMN skill_versions.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.created_at IS '버전 생성 시각';


--
-- Name: COLUMN skill_versions.validated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.validated_at IS 'READY/FAILED 검증 시각';


--
-- Name: COLUMN skill_versions.archived_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.archived_at IS '보관 처리 시각';


--
-- Name: COLUMN skill_versions.action_spec; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.action_spec IS 'Trainer-defined action tensor contract. EC2 stores and forwards this JSON without numeric validation.';


--
-- Name: COLUMN skill_versions.runtime_spec; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skill_versions.runtime_spec IS 'Runtime conversion and execution contract. EC2 stores and forwards this JSON without interpreting its fields.';


--
-- Name: skills; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.skills (
    id uuid NOT NULL,
    skill_key character varying(100) NOT NULL,
    tier character varying(20) NOT NULL,
    status character varying(30) NOT NULL,
    display_name character varying(150) NOT NULL,
    description text,
    persona text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    owner_id uuid,
    CONSTRAINT ck_skills_owner_scope CHECK ((((owner_id IS NULL) AND ((tier)::text = ANY (ARRAY[('TUTORIAL'::character varying)::text, ('DEMO'::character varying)::text]))) OR ((owner_id IS NOT NULL) AND ((tier)::text = 'CUSTOM'::text)))),
    CONSTRAINT ck_skills_tier CHECK (((tier)::text = ANY (ARRAY[('TUTORIAL'::character varying)::text, ('DEMO'::character varying)::text, ('CUSTOM'::character varying)::text]))),
    CONSTRAINT skills_check CHECK ((((tier)::text <> 'DEMO'::text) OR (persona IS NOT NULL))),
    CONSTRAINT skills_skill_key_check CHECK (((skill_key)::text ~ '^[a-z0-9][a-z0-9_-]{0,99}$'::text)),
    CONSTRAINT skills_status_check CHECK (((status)::text = ANY (ARRAY[('PLANNED'::character varying)::text, ('COLLECTING'::character varying)::text, ('TRAINING'::character varying)::text, ('GATE_FAILED'::character varying)::text, ('DEPLOYED'::character varying)::text])))
);


--
-- Name: COLUMN skills.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.id IS 'PK';


--
-- Name: COLUMN skills.skill_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.skill_key IS 'FE·BE·AI 공통의 전역 불변 key';


--
-- Name: COLUMN skills.tier; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.tier IS '`TUTORIAL`, `DEMO`';


--
-- Name: COLUMN skills.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.status IS '스킬 카탈로그 수명주기 상태';


--
-- Name: COLUMN skills.display_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.display_name IS '표시 이름';


--
-- Name: COLUMN skills.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.description IS '설명';


--
-- Name: COLUMN skills.persona; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.persona IS 'DEMO 스킬 대상 페르소나';


--
-- Name: COLUMN skills.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.created_at IS '생성 시각';


--
-- Name: COLUMN skills.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.updated_at IS '수정 시각';


--
-- Name: COLUMN skills.owner_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.skills.owner_id IS '커스텀 스킬 소유 사용자. NULL이면 시스템 공통 스킬';


--
-- Name: storage_nodes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.storage_nodes (
    id uuid NOT NULL,
    worker_id character varying(100) NOT NULL,
    node_key character varying(100) NOT NULL,
    name character varying(150) NOT NULL,
    storage_type character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    capacity_bytes bigint NOT NULL,
    available_bytes bigint NOT NULL,
    last_seen_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT storage_nodes_capacity_bytes_check CHECK ((capacity_bytes >= 0)),
    CONSTRAINT storage_nodes_check CHECK (((available_bytes >= 0) AND (available_bytes <= capacity_bytes))),
    CONSTRAINT storage_nodes_status_check CHECK (((status)::text = ANY (ARRAY[('ONLINE'::character varying)::text, ('OFFLINE'::character varying)::text, ('DRAINING'::character varying)::text]))),
    CONSTRAINT storage_nodes_storage_type_check CHECK (((storage_type)::text = ANY (ARRAY[('LOCAL'::character varying)::text, ('S3'::character varying)::text])))
);


--
-- Name: COLUMN storage_nodes.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.id IS 'PK';


--
-- Name: COLUMN storage_nodes.worker_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.worker_id IS 'service token의 worker 범위';


--
-- Name: COLUMN storage_nodes.node_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.node_key IS 'worker 내부 논리 노드 키';


--
-- Name: COLUMN storage_nodes.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.name IS '운영 표시 이름';


--
-- Name: COLUMN storage_nodes.storage_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.storage_type IS '현재 `LOCAL`만 허용';


--
-- Name: COLUMN storage_nodes.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.status IS '`ONLINE`, `OFFLINE`, `DRAINING`';


--
-- Name: COLUMN storage_nodes.capacity_bytes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.capacity_bytes IS '0 이상';


--
-- Name: COLUMN storage_nodes.available_bytes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.available_bytes IS '0 이상, capacity 이하';


--
-- Name: COLUMN storage_nodes.last_seen_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.last_seen_at IS 'heartbeat 수신 시각';


--
-- Name: COLUMN storage_nodes.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.created_at IS '생성 시각';


--
-- Name: COLUMN storage_nodes.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.updated_at IS '수정 시각';


--
-- Name: COLUMN storage_nodes.row_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.storage_nodes.row_version IS '낙관적 잠금, 기본 0';


--
-- Name: task_steps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_steps (
    id uuid NOT NULL,
    task_version_id uuid NOT NULL,
    skill_version_id uuid,
    step_order integer NOT NULL,
    parameters jsonb DEFAULT '{}'::jsonb NOT NULL,
    timeout_seconds integer,
    parent_step_id uuid,
    step_type character varying(20) DEFAULT 'SKILL'::character varying NOT NULL,
    repeat_count integer,
    CONSTRAINT ck_task_steps_not_self_parent CHECK (((parent_step_id IS NULL) OR (parent_step_id <> id))),
    CONSTRAINT ck_task_steps_payload CHECK (((((step_type)::text = 'SKILL'::text) AND (skill_version_id IS NOT NULL) AND (repeat_count IS NULL)) OR (((step_type)::text = 'MOTOR'::text) AND (skill_version_id IS NULL) AND (repeat_count IS NULL)) OR (((step_type)::text = 'REPEAT'::text) AND (skill_version_id IS NULL) AND (repeat_count >= 1)))),
    CONSTRAINT ck_task_steps_type CHECK (((step_type)::text = ANY (ARRAY[('SKILL'::character varying)::text, ('MOTOR'::character varying)::text, ('REPEAT'::character varying)::text]))),
    CONSTRAINT task_steps_step_order_check CHECK ((step_order >= 1)),
    CONSTRAINT task_steps_timeout_seconds_check CHECK (((timeout_seconds IS NULL) OR (timeout_seconds >= 1)))
);


--
-- Name: COLUMN task_steps.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_steps.id IS 'PK';


--
-- Name: COLUMN task_steps.task_version_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_steps.task_version_id IS 'FK → `task_versions.id`';


--
-- Name: COLUMN task_steps.skill_version_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_steps.skill_version_id IS 'FK → `skill_versions.id`';


--
-- Name: COLUMN task_steps.step_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_steps.step_order IS '1부터 연속';


--
-- Name: COLUMN task_steps.parameters; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_steps.parameters IS '기본 `{}`';


--
-- Name: COLUMN task_steps.timeout_seconds; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_steps.timeout_seconds IS '1 이상';


--
-- Name: COLUMN task_steps.parent_step_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_steps.parent_step_id IS 'REPEAT 단계에 포함된 경우 부모 task_steps.id';


--
-- Name: COLUMN task_steps.step_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_steps.step_type IS 'SKILL, MOTOR, REPEAT';


--
-- Name: COLUMN task_steps.repeat_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_steps.repeat_count IS 'REPEAT 단계 반복 횟수';


--
-- Name: task_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.task_versions (
    id uuid NOT NULL,
    task_id uuid NOT NULL,
    version integer NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT task_versions_version_check CHECK ((version >= 1))
);


--
-- Name: COLUMN task_versions.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_versions.id IS 'PK';


--
-- Name: COLUMN task_versions.task_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_versions.task_id IS 'FK → `tasks.id`';


--
-- Name: COLUMN task_versions.version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_versions.version IS 'Task 범위 증가 번호';


--
-- Name: COLUMN task_versions.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.task_versions.created_at IS '생성 시각';


--
-- Name: tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tasks (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    task_key character varying(100) NOT NULL,
    display_name character varying(150) NOT NULL,
    description text,
    latest_version integer,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp with time zone
);


--
-- Name: COLUMN tasks.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.id IS 'PK';


--
-- Name: COLUMN tasks.owner_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.owner_id IS 'FK → `users.id`';


--
-- Name: COLUMN tasks.task_key; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.task_key IS '소유자 범위 불변 key';


--
-- Name: COLUMN tasks.display_name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.display_name IS '표시 이름';


--
-- Name: COLUMN tasks.description; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.description IS '설명';


--
-- Name: COLUMN tasks.latest_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.latest_version IS '목록 표시용 캐시 값';


--
-- Name: COLUMN tasks.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.created_at IS '생성 시각';


--
-- Name: COLUMN tasks.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.updated_at IS '수정 시각';


--
-- Name: COLUMN tasks.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.tasks.deleted_at IS '사용자 목록에서 제외된 soft delete 시각';


--
-- Name: training_job_episodes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.training_job_episodes (
    training_job_id uuid NOT NULL,
    episode_id uuid NOT NULL,
    input_order integer NOT NULL,
    archive_sha256_snapshot character varying(64) NOT NULL,
    contract_version_snapshot character varying(100) NOT NULL,
    source_snapshot character varying(10) NOT NULL,
    label_status_snapshot character varying(20) NOT NULL,
    success_snapshot boolean,
    quality_status_snapshot character varying(20) NOT NULL,
    attached_at timestamp with time zone NOT NULL,
    input_role character varying(20) NOT NULL,
    CONSTRAINT ck_training_job_episode_input_role CHECK (((input_role)::text = ANY ((ARRAY['DEMONSTRATION'::character varying, 'MAPPING'::character varying])::text[]))),
    CONSTRAINT training_job_episodes_archive_sha256_snapshot_check CHECK (((archive_sha256_snapshot)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT training_job_episodes_input_order_check CHECK ((input_order >= 1)),
    CONSTRAINT training_job_episodes_label_status_snapshot_check CHECK (((label_status_snapshot)::text = ANY (ARRAY[('UNLABELED'::character varying)::text, ('LABELED'::character varying)::text]))),
    CONSTRAINT training_job_episodes_quality_status_snapshot_check CHECK (((quality_status_snapshot)::text = ANY (ARRAY[('VALID'::character varying)::text, ('WARNING'::character varying)::text]))),
    CONSTRAINT training_job_episodes_source_snapshot_check CHECK (((source_snapshot)::text = ANY (ARRAY[('SIM'::character varying)::text, ('REAL'::character varying)::text])))
);


--
-- Name: COLUMN training_job_episodes.training_job_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.training_job_id IS 'PK 일부, FK → `training_jobs.id`';


--
-- Name: COLUMN training_job_episodes.episode_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.episode_id IS 'PK 일부, FK → `episodes.id`';


--
-- Name: COLUMN training_job_episodes.input_order; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.input_order IS '1부터 연속';


--
-- Name: COLUMN training_job_episodes.archive_sha256_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.archive_sha256_snapshot IS '입력 고정 시점의 EpisodeArchive SHA-256';


--
-- Name: COLUMN training_job_episodes.contract_version_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.contract_version_snapshot IS '입력 데이터 계약 snapshot';


--
-- Name: COLUMN training_job_episodes.source_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.source_snapshot IS '`SIM`, `REAL` snapshot';


--
-- Name: COLUMN training_job_episodes.label_status_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.label_status_snapshot IS '라벨 상태 snapshot';


--
-- Name: COLUMN training_job_episodes.success_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.success_snapshot IS '성공 라벨 snapshot';


--
-- Name: COLUMN training_job_episodes.quality_status_snapshot; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.quality_status_snapshot IS '서버 품질 판정 snapshot';


--
-- Name: COLUMN training_job_episodes.attached_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_episodes.attached_at IS '입력 고정 시각';


--
-- Name: training_job_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.training_job_events (
    id uuid NOT NULL,
    training_job_id uuid NOT NULL,
    attempt integer NOT NULL,
    event_id character varying(150) NOT NULL,
    sequence bigint NOT NULL,
    event_type character varying(100) NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    received_at timestamp with time zone NOT NULL
);


--
-- Name: COLUMN training_job_events.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_events.id IS 'PK';


--
-- Name: COLUMN training_job_events.training_job_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_events.training_job_id IS 'FK → `training_jobs.id`';


--
-- Name: COLUMN training_job_events.attempt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_events.attempt IS '보고 attempt';


--
-- Name: COLUMN training_job_events.event_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_events.event_id IS 'worker 이벤트 ID';


--
-- Name: COLUMN training_job_events.sequence; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_events.sequence IS 'attempt 안의 순번';


--
-- Name: COLUMN training_job_events.event_type; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_events.event_type IS 'STARTED, PROGRESS, TRAINING_FINISHED, FAILED, CANCELED';


--
-- Name: COLUMN training_job_events.payload; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_events.payload IS '이벤트 내용';


--
-- Name: COLUMN training_job_events.occurred_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_events.occurred_at IS 'worker 발생 시각';


--
-- Name: COLUMN training_job_events.received_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_job_events.received_at IS '서버 수신 시각';


--
-- Name: training_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.training_jobs (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    skill_id uuid NOT NULL,
    umi_device_id uuid NOT NULL,
    assigned_storage_node_id uuid,
    assigned_worker_id character varying(100),
    input_mode character varying(30) NOT NULL,
    status character varying(30) NOT NULL,
    attempt integer DEFAULT 1 NOT NULL,
    lease_token_hash character varying(64),
    lease_expires_at timestamp with time zone,
    dataset_query jsonb NOT NULL,
    gpu_index integer,
    git_rev character varying(100),
    expected_episode_count integer DEFAULT 0 NOT NULL,
    received_episode_count integer DEFAULT 0 NOT NULL,
    received_bytes bigint DEFAULT 0 NOT NULL,
    episode_count integer NOT NULL,
    input_snapshot_sha256 character varying(64),
    progress_stage character varying(100),
    progress_percent numeric(5,2) NOT NULL,
    current_epoch integer,
    total_epochs integer,
    result_skill_version_id uuid,
    error_code character varying(100),
    error_message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    started_at timestamp with time zone,
    model_completed_at timestamp with time zone,
    finished_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    row_version bigint DEFAULT 0 NOT NULL,
    deleted_at timestamp with time zone,
    training_mode character varying(30) DEFAULT 'FROM_SCRATCH'::character varying NOT NULL,
    base_skill_version_id uuid,
    CONSTRAINT ck_training_jobs_base_model CHECK (((((training_mode)::text = 'FROM_SCRATCH'::text) AND (base_skill_version_id IS NULL)) OR (((training_mode)::text = 'FINE_TUNE'::text) AND (base_skill_version_id IS NOT NULL)))),
    CONSTRAINT ck_training_jobs_gpu_index_parallel_limit CHECK (((gpu_index IS NULL) OR ((gpu_index >= 0) AND (gpu_index <= 29)))),
    CONSTRAINT ck_training_jobs_training_mode CHECK (((training_mode)::text = ANY (ARRAY[('FROM_SCRATCH'::character varying)::text, ('FINE_TUNE'::character varying)::text]))),
    CONSTRAINT training_jobs_attempt_check CHECK ((attempt >= 1)),
    CONSTRAINT training_jobs_check CHECK (((model_completed_at IS NULL) OR (started_at IS NULL) OR (model_completed_at >= started_at))),
    CONSTRAINT training_jobs_check1 CHECK (((finished_at IS NULL) OR (model_completed_at IS NULL) OR (finished_at >= model_completed_at))),
    CONSTRAINT training_jobs_episode_count_check CHECK ((episode_count >= 0)),
    CONSTRAINT training_jobs_expected_episode_count_check CHECK ((expected_episode_count >= 0)),
    CONSTRAINT training_jobs_gpu_index_check CHECK (((gpu_index IS NULL) OR (gpu_index >= 0))),
    CONSTRAINT training_jobs_input_mode_check CHECK (((input_mode)::text = 'EXISTING_EPISODES'::text)),
    CONSTRAINT training_jobs_input_snapshot_sha256_check CHECK (((input_snapshot_sha256)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT training_jobs_lease_token_hash_check CHECK (((lease_token_hash)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT training_jobs_progress_percent_check CHECK (((progress_percent >= (0)::numeric) AND (progress_percent <= (100)::numeric))),
    CONSTRAINT training_jobs_received_bytes_check CHECK ((received_bytes >= 0)),
    CONSTRAINT training_jobs_received_episode_count_check CHECK ((received_episode_count >= 0)),
    CONSTRAINT training_jobs_status_check CHECK (((status)::text = ANY (ARRAY[('CREATED'::character varying)::text, ('COLLECTING'::character varying)::text, ('INPUT_VALIDATING'::character varying)::text, ('INPUT_READY'::character varying)::text, ('QUEUED'::character varying)::text, ('PREPARING'::character varying)::text, ('RUNNING'::character varying)::text, ('MODEL_UPLOADING'::character varying)::text, ('MODEL_VALIDATING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text, ('CANCELED'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text])))
);


--
-- Name: COLUMN training_jobs.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.id IS 'PK';


--
-- Name: COLUMN training_jobs.owner_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.owner_id IS 'FK → `users.id`';


--
-- Name: COLUMN training_jobs.skill_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.skill_id IS 'FK → `skills.id`';


--
-- Name: COLUMN training_jobs.umi_device_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.umi_device_id IS 'FK → `devices.id`';


--
-- Name: COLUMN training_jobs.assigned_storage_node_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.assigned_storage_node_id IS 'FK → `storage_nodes.id`';


--
-- Name: COLUMN training_jobs.assigned_worker_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.assigned_worker_id IS '현재 GPU worker';


--
-- Name: COLUMN training_jobs.input_mode; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.input_mode IS '고정값 EXISTING_EPISODES. UMI가 S3에 업로드하고 검증 완료된 Episode만 학습 입력으로 사용';


--
-- Name: COLUMN training_jobs.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.status IS '학습 상태';


--
-- Name: COLUMN training_jobs.attempt; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.attempt IS '기본 1';


--
-- Name: COLUMN training_jobs.lease_token_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.lease_token_hash IS '현재 lease token hash';


--
-- Name: COLUMN training_jobs.lease_expires_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.lease_expires_at IS 'lease 만료 시각';


--
-- Name: COLUMN training_jobs.dataset_query; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.dataset_query IS 'Episode 선택 조건 snapshot';


--
-- Name: COLUMN training_jobs.gpu_index; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.gpu_index IS '학습에 할당된 GPU 번호, 0 이상';


--
-- Name: COLUMN training_jobs.git_rev; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.git_rev IS '학습 코드 revision';


--
-- Name: COLUMN training_jobs.expected_episode_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.expected_episode_count IS '기본 0';


--
-- Name: COLUMN training_jobs.received_episode_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.received_episode_count IS '기본 0';


--
-- Name: COLUMN training_jobs.received_bytes; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.received_bytes IS '기본 0';


--
-- Name: COLUMN training_jobs.episode_count; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.episode_count IS '고정 입력 Episode 수';


--
-- Name: COLUMN training_jobs.input_snapshot_sha256; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.input_snapshot_sha256 IS '고정 입력 목록 hash';


--
-- Name: COLUMN training_jobs.progress_stage; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.progress_stage IS '합의된 stage enum';


--
-- Name: COLUMN training_jobs.progress_percent; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.progress_percent IS '0~100';


--
-- Name: COLUMN training_jobs.current_epoch; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.current_epoch IS '현재 epoch';


--
-- Name: COLUMN training_jobs.total_epochs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.total_epochs IS '전체 epoch';


--
-- Name: COLUMN training_jobs.result_skill_version_id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.result_skill_version_id IS 'FK → `skill_versions.id`, migration 후 추가';


--
-- Name: COLUMN training_jobs.error_code; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.error_code IS '종료 또는 최근 오류';


--
-- Name: COLUMN training_jobs.error_message; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.error_message IS '오류 설명';


--
-- Name: COLUMN training_jobs.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.created_at IS '생성 시각';


--
-- Name: COLUMN training_jobs.started_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.started_at IS '시작 시각';


--
-- Name: COLUMN training_jobs.model_completed_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.model_completed_at IS 'FastAPI의 최초 학습 완료 통지 발신 UTC 시각, GPU Spring Boot를 거쳐 전달';


--
-- Name: COLUMN training_jobs.finished_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.finished_at IS '종료 시각';


--
-- Name: COLUMN training_jobs.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.updated_at IS '수정 시각';


--
-- Name: COLUMN training_jobs.row_version; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.row_version IS '상태 전이 낙관적 잠금';


--
-- Name: COLUMN training_jobs.deleted_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.training_jobs.deleted_at IS '사용자 목록에서 제외된 soft delete 시각';


--
-- Name: CONSTRAINT ck_training_jobs_gpu_index_parallel_limit ON training_jobs; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON CONSTRAINT ck_training_jobs_gpu_index_parallel_limit ON public.training_jobs IS '등록 GPU worker의 최대 병렬 학습 자원 30개에 대응하는 gpuIndex 범위';


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    email character varying(320) NOT NULL,
    password_hash character varying(255) NOT NULL,
    name character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT users_status_check CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('LOCKED'::character varying)::text, ('WITHDRAWN'::character varying)::text])))
);


--
-- Name: COLUMN users.id; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.id IS 'PK';


--
-- Name: COLUMN users.email; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.email IS '로그인 이메일';


--
-- Name: COLUMN users.password_hash; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.password_hash IS '평문 저장 금지';


--
-- Name: COLUMN users.name; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.name IS '표시 이름';


--
-- Name: COLUMN users.status; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.status IS '`ACTIVE`, `LOCKED`, `WITHDRAWN`';


--
-- Name: COLUMN users.created_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.created_at IS '생성 시각';


--
-- Name: COLUMN users.updated_at; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON COLUMN public.users.updated_at IS '수정 시각';


--
-- Data for Name: data_purge_jobs; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: data_purge_reports; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: deployment_items; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: deployment_reports; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: deployments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: device_credentials; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.device_credentials VALUES ('51000000-0000-4000-8000-000000000001', '$2b$10$hk7nZpCKkvK4L9ottyvN/OYf1hCEa8tB91XorGdvjoUBnapCazdWa', '2026-09-18 02:02:43.478697+00', '2026-09-18 02:02:43.478697+00', '2026-09-18 02:02:43.478697+00', 'ACTIVE', '2026-09-18 02:02:43.478697+00', NULL);
INSERT INTO public.device_credentials VALUES ('51000000-0000-4000-8000-000000000002', '$2a$10$.y2GoOBYYemXP84Lpx713OnxK6yy.5BF4ajYzaTIz7xLnVNzHrs86', '2026-09-18 02:02:43.493058+00', '2026-09-18 02:02:43.493058+00', '2026-09-18 02:02:43.493058+00', 'ACTIVE', '2026-09-18 02:02:43.493058+00', NULL);


--
-- Data for Name: device_enrollments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: devices; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.devices VALUES ('51000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', 'UMI_CAMERA', 'UMI-001', 'shanks umi', 'ONLINE', '1.0.0', '1.0.0', '2026-09-09 00:30:00+00', '2026-09-09 00:00:00+00', '2026-09-09 00:30:00+00', '801호', 'REGISTERED');
INSERT INTO public.devices VALUES ('51000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000001', 'JETSON', 'JETSON-001', 'Shanks', 'ONLINE', '1.0.0', 'ros2-humble', '2026-09-09 00:30:00+00', '2026-09-09 00:00:00+00', '2026-09-09 00:30:00+00', '801호', 'REGISTERED');


--
-- Data for Name: episode_archives; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.episode_archives VALUES ('663e79f0-260b-44ea-ad51-8a095eac4d40', 'a14071bf-3677-46bf-aa2b-e32bea650982', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260921T090000Z/5cb1d732-404a-42c1-840c-53d449fd9ab5.zip', 'rec_full.zip', 'application/zip', 'umi-raw-v2', 'STORE', 9628714, 9627874, 6, 'cf763cd242c964b92f95681e9deac8d8e7cdadf1ab7795850cf7daba3907ed42', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "checksumType": "FULL_OBJECT", "schemaVersion": 2, "sourceObjectKey": "rec_full.zip", "compressionMethod": "STORE", "s3ChecksumSha256Base64": "z3Y80kLJZLkvlWgenerI2OfNrfGrd5WFDPfaujkH7UI="}, "fileSha256": {"video.mp4": "91c13159b29046efb0efcc0b09fbb6b8a1544fb8c66d10aad75f6879c2f76a22", "frames.csv": "6b0b54ee5877b2eebd34fa2a2f0e303d034e5ed2ffa555793173c7872309853e", "encoded.csv": "38e821ba9a8c22d39aefffd715f45c6306ea7a7678b33d3dfefac6cc964de0ce", "gyroscope.csv": "6a1fa448b5f30338336bd8937286c79ace5be61cc875ca413193e4cec0778ed8", "manifest.json": "cb5ec7d5f24d00bd7240f8d87b4b6d497fc6aa331d1f5f82e7cd5bb95cf81b61", "accelerometer.csv": "de4818327baf4d1cddac3e8c9a5ee2741bc39773bf518a87840af9219e6f280c"}, "frameEntryCount": 145}', 'AVAILABLE', '2026-09-21 09:05:15+00', '2026-09-21 09:05:15+00', NULL, NULL, NULL, '2026-09-21 09:05:15+00', '2026-09-21 09:05:15+00');
INSERT INTO public.episode_archives VALUES ('bf0bd4cf-909b-5f1e-a08c-6eaf8f7744c9', '851988a6-3e73-559c-bcf2-68546fbc60ec', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010103Z/6968e1b0-c9e9-525a-8b2b-5ac359a264d6.zip', '6968e1b0-c9e9-525a-8b2b-5ac359a264d6.zip', 'application/zip', 'umi-episode-v2', 'STORE', 126204666, 126204042, 6, '5a27e6afc3f9ba0d65d25b9a08340ba099b463f69f46cd34c88d70756efeff93', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "MAPPING", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "78bc7ca8fabddb6e2c077219a88b56585d4531e6b54b7189b7fa17d42b65fdf1", "frames.csv": "be7d9a160319ad4f7b59c94b6f4dccfdc109653fb0f590c3f4b281f4b200d50b", "encoded.csv": "92e5f8319eaf3b6b5eb01291100ea92b922cc041707b8bd9d07f2cd9e3cb20b9", "gyroscope.csv": "f6a1270d1747ecd3362ecca02fe3b2f5b9ba7d6a1050dbb0e7a87f45d872fb72", "manifest.json": "b1ffe0490edcbe04311320cfcd1d8231ef0d843fee7a646e4a623726c1206174", "accelerometer.csv": "781844bf46fc87ac3215e57c012df372b889b51277db7c9bc969fa3c6761dd46"}, "frameEntryCount": 1867}', 'AVAILABLE', '2026-09-22 01:01:03.122+00', '2026-09-22 01:01:03.122+00', NULL, NULL, NULL, '2026-09-22 01:01:03.122+00', '2026-09-22 01:01:03.122+00');
INSERT INTO public.episode_archives VALUES ('3d68baec-d61b-5b8a-b65d-2edc3f51244a', 'ec7f4cf0-41cf-589d-9763-b5c3af853281', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010113Z/1a4ae096-f735-5343-98ab-483f82ea6e12.zip', '1a4ae096-f735-5343-98ab-483f82ea6e12.zip', 'application/zip', 'umi-episode-v2', 'STORE', 17646348, 17645724, 6, '63e0a349945a9747bb1d5dafa5f105ab345e8a2d8e5f63f4ba7f2d68efa4887f', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "CALIBRATION", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "ad0b198f808659ce1af4c257e84e44456c8515592393dd7ae773a9c80ef5aae8", "frames.csv": "c4fc2234c10bea3cbcd0721d77c33ecd92633cd4a67ffd461358e15066ab2421", "encoded.csv": "ab6f4ac3d99aa04fc2f99fbdbbec9fc0edd84ef3b9b75f0ef700331ae13370cb", "gyroscope.csv": "d03dc35039a14517a0c71e194a30c3ece02bb834f833abd121cff090cf8156c9", "manifest.json": "1d10788338fc9cd267b929071cd192abb1970b4d6494818e4f1f433a908cf524", "accelerometer.csv": "3d1633853678de5972028b8ef1839d75e5607a08c557a040fe99075aca872dfa"}, "frameEntryCount": 260}', 'AVAILABLE', '2026-09-22 01:01:13.376+00', '2026-09-22 01:01:13.376+00', NULL, NULL, NULL, '2026-09-22 01:01:13.376+00', '2026-09-22 01:01:13.376+00');
INSERT INTO public.episode_archives VALUES ('89f3afcf-ce89-5308-b936-b74c5190c9cb', '027140cd-9fed-54c4-aca4-bc41679416df', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010125Z/0cf912e1-381f-5044-a948-4e0aa24bbf0f.zip', '0cf912e1-381f-5044-a948-4e0aa24bbf0f.zip', 'application/zip', 'umi-episode-v2', 'STORE', 21644796, 21644172, 6, 'e3501633409d7bb5033e4550a1c5e14ec08b34040fd77ebeffdb69464cedd7b4', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "DEMONSTRATION", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "f76943a252fcd3133e4752c10d687c5cb1a0553f1ac87cf8607959d4b1f9e0c7", "frames.csv": "ccb9afd89d8fcf851ca761d8180b161ca445045362d5dc0983841399a8a951da", "encoded.csv": "82a0694b4318ae5fd99f4046de51c0088eebf40c74fcdacdb80b1e65c2867bd7", "gyroscope.csv": "d27b99a909fb6d3e5b35fa2717ebd0a0ff0f1dd795bcdc4dc96eb44238ff3961", "manifest.json": "0f76b8d33bbeff2ac9f6bf8cc65d7471220b076faceb79046afd3da9c4651f44", "accelerometer.csv": "cc8b34cbbc9806ea549388643a922a703c9f6f650d1e291a70bd2c4e5c9a3bf1"}, "frameEntryCount": 317}', 'AVAILABLE', '2026-09-22 01:01:25.573+00', '2026-09-22 01:01:25.573+00', NULL, NULL, NULL, '2026-09-22 01:01:25.573+00', '2026-09-22 01:01:25.573+00');
INSERT INTO public.episode_archives VALUES ('5c9cd476-c621-5c8a-b602-30008e6f6f1d', 'b13ab2ab-50ed-5f4a-9ec4-5fc0402b6eac', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010137Z/c59d3bb6-0134-5cdf-a614-25d9738cbb51.zip', 'c59d3bb6-0134-5cdf-a614-25d9738cbb51.zip', 'application/zip', 'umi-episode-v2', 'STORE', 20271309, 20270685, 6, 'd8ff83a14798c7af53f7eaaed229ba56b7b9f87604517a35180b0bbefc9b97ab', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "DEMONSTRATION", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "d427cef81219f6304859596a52f04ca76cedb227dbb2c27e9f20d0ab1dcedf0b", "frames.csv": "f2b3c7950afe52694b69e4ea4a3decc4a9fbe9c5c5439f0e74c3ff7ba27b6df5", "encoded.csv": "274426f134d42aed78e191867343bfdd23a40ab954733e85b4978cc03eb6962a", "gyroscope.csv": "6e092c9241a7fde90cc215b21f3fecfd421f301213f62e8f46de7ad9b286f748", "manifest.json": "70eafdf94d4be55453423adf611e14192ffdd25d28ca5aab22474f23d14e13ac", "accelerometer.csv": "12020caaeba4612a999c021914be9dd6ff4cb29c482e97cc2e9894c11923b3a1"}, "frameEntryCount": 298}', 'AVAILABLE', '2026-09-22 01:01:37.132+00', '2026-09-22 01:01:37.132+00', NULL, NULL, NULL, '2026-09-22 01:01:37.132+00', '2026-09-22 01:01:37.132+00');
INSERT INTO public.episode_archives VALUES ('11580696-6108-54a2-9798-a3ccc6e40f53', 'f3174fcd-977f-5caa-806f-2c965ec3b709', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010149Z/134e9e04-0cf1-58ce-aa94-64d1d8c12b58.zip', '134e9e04-0cf1-58ce-aa94-64d1d8c12b58.zip', 'application/zip', 'umi-episode-v2', 'STORE', 21158038, 21157414, 6, '4c498b0275abd11728b6470c37abe87bb5ca6b912b9ba9dc52e558d13e98fbe4', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "DEMONSTRATION", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "ea389eb7da0816af75d2438ff0e509e7f63e17a589260b2d0a66a5aeee0031b8", "frames.csv": "597892087c955e42c18498e2d3487c1c7ef0d468068f0718d2cef2d8c5399c72", "encoded.csv": "653814a62c3ecea2fcfda880713ce68e8d4799505c8da19d5ab1637e6046f026", "gyroscope.csv": "646eea288e564981e2837b34dc8e8ec3505cd3af8458170c7393bfce0c78d698", "manifest.json": "a106aced3da305df6b6d21d24a2750457ec69204a1663281f1870fb54d50bd8c", "accelerometer.csv": "953ec5ff2a38674bd6645427eef0f4215665ba52eeae55a8cd91dabf725ac232"}, "frameEntryCount": 309}', 'AVAILABLE', '2026-09-22 01:01:49.049+00', '2026-09-22 01:01:49.049+00', NULL, NULL, NULL, '2026-09-22 01:01:49.049+00', '2026-09-22 01:01:49.049+00');
INSERT INTO public.episode_archives VALUES ('0aa38a50-ed2c-5a04-80ea-d9dd860aae88', 'ab71d02a-cdeb-5250-86fb-1f7699b92d90', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010159Z/00b26c4f-4b74-5a7c-b968-46769cafd5db.zip', '00b26c4f-4b74-5a7c-b968-46769cafd5db.zip', 'application/zip', 'umi-episode-v2', 'STORE', 18285167, 18284543, 6, 'f7c6e860dfac0d72a87171b75032725a8c761e0a3f10539dc274845ef1044063', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "DEMONSTRATION", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "d5e10622befc1c6b67736d6fcf05f06ec76ee5861376208e9f46038aa7bde8d5", "frames.csv": "8f25e94b3484147c2fabf5e097baba985237c94f0bcf227225164295df39148d", "encoded.csv": "6dac550b7ca1928356850346d567673ceb3263965b438c30bb805e1f30195db5", "gyroscope.csv": "d255d8d3a13e35e0d707bbbac27db3790f18352a2c14a045bd9ebc67fe0e495a", "manifest.json": "67a41df7f816062ce7dc61a05b69b05b78978f5f05114117d426d085a00d61c1", "accelerometer.csv": "e22e69043b268b4486d3795cdde8a394ab09a3c33eb999f7ebcab6d4cd6cdda2"}, "frameEntryCount": 269}', 'AVAILABLE', '2026-09-22 01:01:59.639+00', '2026-09-22 01:01:59.639+00', NULL, NULL, NULL, '2026-09-22 01:01:59.639+00', '2026-09-22 01:01:59.639+00');
INSERT INTO public.episode_archives VALUES ('dc44e389-f714-54e2-8d29-e11839ad4f33', 'aa955a2a-a519-509f-a50d-135cb8ddf9cb', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010210Z/eb4d6141-b153-51e7-950b-b7288dad6a20.zip', 'eb4d6141-b153-51e7-950b-b7288dad6a20.zip', 'application/zip', 'umi-episode-v2', 'STORE', 19097847, 19097223, 6, '4f9ddb090cfb5f86764a605eaa44b13c434f67e702589672a44fff75892fe9ae', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "DEMONSTRATION", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "5c8fc6adaa91cd3048400a5e55f18e65a87ddd404cdc1888c3b5c560d83c433a", "frames.csv": "644128b6bae13d31b4da7ba018aa0b9bcd38deae6fd211a7ec90c394af0575bc", "encoded.csv": "deec73377a18c20ea0c707558ccf7de00d7c8728f774a58d4b91b5c3e7e3b93e", "gyroscope.csv": "4664e8a718c1c2a7fdc979b59c9e0fd9c804516bfa655e5341d637e8ff7fad77", "manifest.json": "6b7e7b68c98583593fffd76917534d2f34fc0ec945c12f2c55e38e388a7579f3", "accelerometer.csv": "75885d4c4937bc5074ef17c181a2565f1ac12ba56814d6788f79ef9df96ca301"}, "frameEntryCount": 279}', 'AVAILABLE', '2026-09-22 01:02:10.569+00', '2026-09-22 01:02:10.569+00', NULL, NULL, NULL, '2026-09-22 01:02:10.569+00', '2026-09-22 01:02:10.569+00');
INSERT INTO public.episode_archives VALUES ('3babd1b6-55c5-5ede-a8b3-2f6d1dc28f02', 'c7e97607-df48-5a44-a6c4-9150553e9752', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010219Z/fed8aef2-cfe8-5f51-92b1-6ad123f726f1.zip', 'fed8aef2-cfe8-5f51-92b1-6ad123f726f1.zip', 'application/zip', 'umi-episode-v2', 'STORE', 15454515, 15453891, 6, 'aab5fda92909c4c99b7af012b5e19c5fb0c99ee06a484f1b8a59dd8d053b85eb', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "DEMONSTRATION", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "bcdd3f30bfc3a373b6d33dd3795b0c78b56be2b48d3cc4453e857562afac7370", "frames.csv": "a42ace97ef8b94aa170a11ea5d1f0b5da583751dfcf7ac65ffdc89c9f144614c", "encoded.csv": "06ed9a194cd7264cf77f11e99159a49ede6f0cedfd2e8954c295029ab66dc574", "gyroscope.csv": "bddbe0488878131d581d0e4c359980d6d785c87034ae2f88f86cbca6ce5e62ad", "manifest.json": "7d859d1eb85e7555e03ee62c20ea2822d41e9736e370919a8f1bb6d08368e60b", "accelerometer.csv": "caeb95d40f3e2dc9d7e89638a098a6cb8a09d35a473c3e7dc5c3f19d82282319"}, "frameEntryCount": 228}', 'AVAILABLE', '2026-09-22 01:02:19.767+00', '2026-09-22 01:02:19.767+00', NULL, NULL, NULL, '2026-09-22 01:02:19.767+00', '2026-09-22 01:02:19.767+00');
INSERT INTO public.episode_archives VALUES ('16a55ffa-fae4-508b-9d00-7f3f2237f1fd', '59cad5e1-c1a7-545f-9d62-66303f1289da', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010227Z/11dac813-8cb3-5392-8560-dbeaa52d19bc.zip', '11dac813-8cb3-5392-8560-dbeaa52d19bc.zip', 'application/zip', 'umi-episode-v2', 'STORE', 13414338, 13413714, 6, '9438f2e189cb4f3054a4e42326997bf58ab6a919942509a7a5203222e660b7d3', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "DEMONSTRATION", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "be2818fdb9b51274e21f0935985af82f1aa7c33d1da4d2a7066daadd7977d117", "frames.csv": "177001ac554d332ce5c3b0ad57de5349da55dd8ae82d130c1e70eec669b9f64d", "encoded.csv": "6d494bf545e9da64c370edffc373d813b9988694855151dd089622c3488d792a", "gyroscope.csv": "6ea71fc470e1fe247be62d9c11dbc50e8ea3457ea61e2383863c21aa040f5d67", "manifest.json": "65222db18b71de10b6a2e2fa1d9b6b6dbaf28bd161a2c614faf5be55df72f2fa", "accelerometer.csv": "991e1d552226c879961ed873a286dfad21e79ab76bcb58643249ba8c2a1e2c53"}, "frameEntryCount": 196}', 'AVAILABLE', '2026-09-22 01:02:27.946+00', '2026-09-22 01:02:27.946+00', NULL, NULL, NULL, '2026-09-22 01:02:27.946+00', '2026-09-22 01:02:27.946+00');
INSERT INTO public.episode_archives VALUES ('f0388a86-3bb8-5237-9c89-98c21f1c3069', 'b89a2578-2db6-5321-ae39-ad16c29749ed', '18000000-0000-4000-8000-000000000001', 'episodes/50000000-0000-4000-8000-000000000001/20260922T010237Z/16f06c3e-9fc2-508c-80cc-16fa9bf03234.zip', '16f06c3e-9fc2-508c-80cc-16fa9bf03234.zip', 'application/zip', 'umi-episode-v2', 'STORE', 15968062, 15967438, 6, 'bf6879e2e63764a582833acbd178c863ec5cb5ae4b8eaac1f5c3bf529a15a2f7', '{"details": {"bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an", "datasetRole": "DEMONSTRATION", "schemaVersion": 2, "compressionMethod": "STORE"}, "fileSha256": {"video.mp4": "17a959e3429949fcca23b05b530f16bd652b039091a99c82da30cbf0da8b5bed", "frames.csv": "3475f75ae863577794cfbb620e0eba4ec21cadb07dc4b0f311e5f98c22302769", "encoded.csv": "03f114d8e9fcebb37889b37cbe7870a23118f204e1cb08d2b9a2469f99261ed9", "gyroscope.csv": "465c31148c58e2eb3f760364db4b0a886df8fa7da237bff574babf23f425f6f0", "manifest.json": "537e5b2ca3bfd2aeefbd83782d32f7fd4dd7695e6a3d635357ec67779ac058f7", "accelerometer.csv": "16af0d8e4a0fc19d4cedaf520ea13244f9923331580bd9ef00a7f5e8b49436c4"}, "frameEntryCount": 235}', 'AVAILABLE', '2026-09-22 01:02:37.417+00', '2026-09-22 01:02:37.417+00', NULL, NULL, NULL, '2026-09-22 01:02:37.417+00', '2026-09-22 01:02:37.417+00');


--
-- Data for Name: episode_upload_sessions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: episodes; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.episodes VALUES ('a14071bf-3677-46bf-aa2b-e32bea650982', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000001', '5cb1d732-404a-42c1-840c-53d449fd9ab5', 'REAL', true, 'LABELED', 'UMI', '2026-09-21 09:00:05.684578+00', 'umi-raw-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-21 09:00:00+00', '2026-09-21 09:00:05.684578+00', 1014759411269965, 'MONOTONIC_NS', 1014760010155241, 1014764843195501, 4833040260, 145, 0, 2395, 0, 0, 0, 0, 'WARNING', '{"units": {"time": "ns", "gyroscope": "rad/s", "markerSide": "mm", "accelerometer": "m/s^2"}, "camera": {"id": "2", "width": 1920, "height": 1080, "focalLengthsMm": [2.2], "timestampSource": 1, "sensorOrientation": 90, "encoderTimestampBase": "SENSOR", "imageRotationDegrees": 180, "minimumFocusDistance": 0, "horizontalFovEstimate": 104.12870803319929, "decoderRotationDegrees": 0, "rotationBakedIntoPixels": true, "imageOrientationContract": "upright_v1"}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "imuRotationApplied": false}}', '{"values": {"task": "grasp_lift_hold", "outcome": "failure", "framesRows": 145, "encodedRows": 145, "calibrationId": null, "gyroscopeRows": 1190, "trainingReady": false, "warningReason": "Manifest marks this recording as a failed, non-training-ready demonstration", "manifestStatus": "recorded", "accelerometerRows": 1205, "ptsMatchesSensorTimestamp": true, "markerBlackSquareSizeVerified": false}}', '2026-09-21 09:05:15+00', NULL, '2026-09-28 09:00:00+00', NULL, NULL, NULL, '2026-09-21 09:05:15+00', '2026-09-26 03:39:30.15721+00', 0);
INSERT INTO public.episodes VALUES ('851988a6-3e73-559c-bcf2-68546fbc60ec', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', '6968e1b0-c9e9-525a-8b2b-5ac359a264d6', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:01:03.122+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:00:00+00', '2026-09-22 01:01:03.122+00', 1041922384410622, 'MONOTONIC_NS', 1041923018435084, 1041985246210191, 62227775107, 1867, 0, 26998, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 1867, "datasetRole": "MAPPING", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "unrated", "imuSampleCount": 26998, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:01:03.122+00', NULL, '2026-10-22 01:01:03.122+00', NULL, NULL, NULL, '2026-09-22 01:01:03.122+00', '2026-09-26 03:39:30.811757+00', 0);
INSERT INTO public.episodes VALUES ('ec7f4cf0-41cf-589d-9763-b5c3af853281', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', '1a4ae096-f735-5343-98ab-483f82ea6e12', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:01:13.376+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:01:04.122+00', '2026-09-22 01:01:13.376+00', 1043450721535404, 'MONOTONIC_NS', 1043451084265152, 1043459749530659, 8665265507, 260, 0, 3949, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 260, "datasetRole": "CALIBRATION", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "success", "imuSampleCount": 3949, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:01:13.376+00', NULL, '2026-10-22 01:01:13.376+00', NULL, NULL, NULL, '2026-09-22 01:01:13.376+00', '2026-09-26 03:39:30.811757+00', 0);
INSERT INTO public.episodes VALUES ('027140cd-9fed-54c4-aca4-bc41679416df', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', '0cf912e1-381f-5044-a948-4e0aa24bbf0f', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:01:25.573+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:01:14.376+00', '2026-09-22 01:01:25.573+00', 1042085631602956, 'MONOTONIC_NS', 1042086010694602, 1042096575394618, 10564700016, 317, 0, 4775, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 317, "datasetRole": "DEMONSTRATION", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "success", "imuSampleCount": 4775, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:01:25.573+00', NULL, '2026-10-22 01:01:25.573+00', NULL, NULL, NULL, '2026-09-22 01:01:25.573+00', '2026-09-26 03:39:30.811757+00', 0);
INSERT INTO public.episodes VALUES ('b13ab2ab-50ed-5f4a-9ec4-5fc0402b6eac', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', 'c59d3bb6-0134-5cdf-a614-25d9738cbb51', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:01:37.132+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:01:26.573+00', '2026-09-22 01:01:37.132+00', 1042104852570032, 'MONOTONIC_NS', 1042105224509669, 1042115155229566, 9930719897, 298, 0, 4500, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 298, "datasetRole": "DEMONSTRATION", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "success", "imuSampleCount": 4500, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:01:37.132+00', NULL, '2026-10-22 01:01:37.132+00', NULL, NULL, NULL, '2026-09-22 01:01:37.132+00', '2026-09-26 03:39:30.811757+00', 0);
INSERT INTO public.episodes VALUES ('f3174fcd-977f-5caa-806f-2c965ec3b709', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', '134e9e04-0cf1-58ce-aa94-64d1d8c12b58', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:01:49.049+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:01:38.132+00', '2026-09-22 01:01:49.049+00', 1042120034474089, 'MONOTONIC_NS', 1042120414805871, 1042130712119097, 10297313226, 309, 0, 4653, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 309, "datasetRole": "DEMONSTRATION", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "success", "imuSampleCount": 4653, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:01:49.049+00', NULL, '2026-10-22 01:01:49.049+00', NULL, NULL, NULL, '2026-09-22 01:01:49.049+00', '2026-09-26 03:39:30.811757+00', 0);
INSERT INTO public.episodes VALUES ('ab71d02a-cdeb-5250-86fb-1f7699b92d90', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', '00b26c4f-4b74-5a7c-b968-46769cafd5db', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:01:59.639+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:01:50.049+00', '2026-09-22 01:01:59.639+00', 1042135587848874, 'MONOTONIC_NS', 1042135982648904, 1042144947203576, 8964554672, 269, 0, 4088, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 269, "datasetRole": "DEMONSTRATION", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "success", "imuSampleCount": 4088, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:01:59.639+00', NULL, '2026-10-22 01:01:59.639+00', NULL, NULL, NULL, '2026-09-22 01:01:59.639+00', '2026-09-26 03:39:30.811757+00', 0);
INSERT INTO public.episodes VALUES ('aa955a2a-a519-509f-a50d-135cb8ddf9cb', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', 'eb4d6141-b153-51e7-950b-b7288dad6a20', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:02:10.569+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:02:00.639+00', '2026-09-22 01:02:10.569+00', 1042152119552410, 'MONOTONIC_NS', 1042152504640860, 1042161802083003, 9297442143, 279, 0, 4232, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 279, "datasetRole": "DEMONSTRATION", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "success", "imuSampleCount": 4232, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:02:10.569+00', NULL, '2026-10-22 01:02:10.569+00', NULL, NULL, NULL, '2026-09-22 01:02:10.569+00', '2026-09-26 03:39:30.811757+00', 0);
INSERT INTO public.episodes VALUES ('c7e97607-df48-5a44-a6c4-9150553e9752', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', 'fed8aef2-cfe8-5f51-92b1-6ad123f726f1', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:02:19.767+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:02:11.569+00', '2026-09-22 01:02:19.767+00', 1042172841450110, 'MONOTONIC_NS', 1042173215701308, 1042180813385347, 7597684039, 228, 0, 3490, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 228, "datasetRole": "DEMONSTRATION", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "success", "imuSampleCount": 3490, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:02:19.767+00', NULL, '2026-10-22 01:02:19.767+00', NULL, NULL, NULL, '2026-09-22 01:02:19.767+00', '2026-09-26 03:39:30.811757+00', 0);
INSERT INTO public.episodes VALUES ('59cad5e1-c1a7-545f-9d62-66303f1289da', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', '11dac813-8cb3-5392-8560-dbeaa52d19bc', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:02:27.946+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:02:20.767+00', '2026-09-22 01:02:27.946+00', 1042188620753750, 'MONOTONIC_NS', 1042189018948003, 1042195550225920, 6531277917, 196, 0, 3051, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 196, "datasetRole": "DEMONSTRATION", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "success", "imuSampleCount": 3051, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:02:27.946+00', NULL, '2026-10-22 01:02:27.946+00', NULL, NULL, NULL, '2026-09-22 01:02:27.946+00', '2026-09-26 03:39:30.811757+00', 0);
INSERT INTO public.episodes VALUES ('b89a2578-2db6-5321-ae39-ad16c29749ed', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', '18000000-0000-4000-8000-000000000001', '40000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', '16f06c3e-9fc2-508c-80cc-16fa9bf03234', 'REAL', true, 'LABELED', 'SERVER', '2026-09-22 01:02:37.417+00', 'umi-episode-v2', '2', 'manifest-derived-v1', 'READY', '2026-09-22 01:02:28.946+00', '2026-09-22 01:02:37.417+00', 1042204133029682, 'MONOTONIC_NS', 1042204524331753, 1042212355658524, 7831326771, 235, 0, 3602, 0, 0, 0, 0, 'VALID', '{"units": {"time": "ns", "gyroscope": "rad/s", "accelerometer": "m/s^2"}, "camera": {"id": "2", "candidates": [{"id": "0", "focal_mm": 5.400000095367432, "horizontal_fov_estimate": 74.14614334943072}, {"id": "2", "focal_mm": 2.200000047683716, "horizontal_fov_estimate": 104.12870803319929}], "focal_lengths_mm": [2.2], "timestamp_source": "SENSOR", "sensor_orientation": 90, "minimum_focus_distance": 0, "horizontal_fov_estimate": 104.12870803319929}, "device": {"model": "SM-S901N", "android": "16", "deviceId": "51000000-0000-4000-8000-000000000001", "appVersion": "0.3", "handleNumber": "UMI-001"}, "coordinateSystem": {"imuAxes": "Android device axes; acceleration includes gravity", "timestampBase": "elapsedRealtimeNanos", "imageOrientation": "upright_v1", "imuRotationApplied": false}}', '{"values": {"frameCount": 235, "datasetRole": "DEMONSTRATION", "calibrationId": "calibration-81c59aa8", "captureStatus": "recorded", "trainingReady": true, "captureOutcome": "success", "imuSampleCount": 3602, "serverSeedVerified": true, "markerBlackSquareSizeVerified": true}}', '2026-09-22 01:02:37.417+00', NULL, '2026-10-22 01:02:37.417+00', NULL, NULL, NULL, '2026-09-22 01:02:37.417+00', '2026-09-26 03:39:30.811757+00', 0);


--
-- Data for Name: idempotency_keys; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: outbox_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: refresh_sessions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: retention_policies; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.retention_policies VALUES ('40000000-0000-4000-8000-000000000001', 'dev-provisional-retention', 7, 3, 30, 3, false, false, true, true, '2026-09-18 02:02:43.324112+00', '2026-09-18 02:02:43.324112+00');


--
-- Data for Name: robot_executions; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: robot_profiles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.robot_profiles VALUES ('52000000-0000-4000-8000-000000000001', 'demo-robot-arm', 'umi-policy-v1', 'jetson-orin-nano', 'ros2', 'humble', '{"cameraNames": ["camera0_rgb"]}', '{"camera0_rgb": {"shape": [2, 3, 224, 224]}, "robot0_eef_pos": {"shape": [2, 3]}, "robot0_gripper_width": {"shape": [2, 1]}, "robot0_eef_rot_axis_angle": {"shape": [2, 6]}, "robot0_eef_rot_axis_angle_wrt_start": {"shape": [2, 6]}}', '{"actionSpec": {"dim": 10, "layout": ["dx", "dy", "dz", "r00", "r01", "r02", "r10", "r11", "r12", "gap_m"], "rateHz": 10, "compose": "T_next = T_cur @ A_relative", "gapUnit": "m", "horizon": 16, "gapRange": [0, 0.09], "rotation": "ROTATION_MATRIX_ROWS_0_1", "nActionSteps": 8, "schemaVersion": 1}, "actionSpace": "EEF_RELATIVE_ROT6D", "runtimeSpec": {"execSlice": {"endExclusive": 5, "startInclusive": 1}, "convertedDim": 7, "convertedFrame": "ABSOLUTE", "convertedLayout": ["x", "y", "z", "rx", "ry", "rz", "gap_m"], "reobserveRateHz": 2.5, "actionPointRateHz": 10, "convertedRotation": "AXIS_ANGLE_ROTVEC", "execSliceAppliesTo": "raw", "consumerMustConvert": true}, "outputSchema": {"actionShape": [16, 10]}}', '2026-09-09 00:00:00+00', '2026-09-26 03:39:31.457789+00');


--
-- Data for Name: robot_skill_deployments; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: robots; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.robots VALUES ('53000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000002', '52000000-0000-4000-8000-000000000001', 'SHANKS', 'ONLINE', NULL, '2026-09-09 00:30:00+00', '2026-09-09 00:00:00+00', '2026-09-09 00:30:00+00', 0);


--
-- Data for Name: skill_versions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.skill_versions VALUES ('56000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000001', '55000000-0000-4000-8000-000000000001', '52000000-0000-4000-8000-000000000001', 1, 'READY', 'pjt2-shanks-ssafy-720223570179-ap-northeast-2-an', 'ckpt/50000000-0000-4000-8000-000000000001/grab_blue_flag/v1/grab_blue_flag_20260909T001000Z.ckpt', 'e9682288c1a5b34e31977d484cc433d8e03fc202fd84e0a19977cd5143289e0e', 71, 'ACT', 'PyTorch', '2.4.0', 'umi-policy-v1', 'JOINT_ABSOLUTE', 45000000, 20.000, '["front"]', '{"state": "JOINT_POSITION", "camera": "RGB"}', '{"action": "JOINT_ABSOLUTE", "includesGripper": true}', 'rollout_success', 100, 3, 0.9, 0.82, 0.96, '2026-09-09 00:10:00+00', DEFAULT, '2026-09-09 00:10:00+00', '2026-09-09 00:10:00+00', NULL, NULL, '{}', '{}');
INSERT INTO public.skill_versions VALUES ('56000000-0000-4000-8000-000000000002', '54000000-0000-4000-8000-000000000002', '55000000-0000-4000-8000-000000000002', '52000000-0000-4000-8000-000000000001', 1, 'READY', 'pjt2-shanks-ssafy-720223570179-ap-northeast-2-an', 'ckpt/50000000-0000-4000-8000-000000000001/raise_arm/v1/raise_arm_20260909T002000Z.ckpt', 'c3c7269a691a71abbb5d7012a85981a0e016f8ddf544ff91ff9e2b627a7ea5b1', 70, 'ACT', 'PyTorch', '2.4.0', 'umi-policy-v1', 'JOINT_ABSOLUTE', 45000000, 20.000, '["front"]', '{"state": "JOINT_POSITION", "camera": "RGB"}', '{"action": "JOINT_ABSOLUTE", "includesGripper": true}', 'rollout_success', 100, 3, 0.87, 0.78, 0.93, '2026-09-09 00:20:00+00', DEFAULT, '2026-09-09 00:20:00+00', '2026-09-09 00:20:00+00', NULL, NULL, '{}', '{}');
INSERT INTO public.skill_versions VALUES ('56000000-0000-4000-8000-000000000003', '54000000-0000-4000-8000-000000000003', '55000000-0000-4000-8000-000000000003', '52000000-0000-4000-8000-000000000001', 1, 'READY', 'pjt2-shanks-ssafy-720223570179-ap-northeast-2-an', 'ckpt/50000000-0000-4000-8000-000000000001/custom_00000000000040008000000000000003/v1/custom_00000000000040008000000000000003_20260923T000000Z.tgz', '90d12f77cb6eed129381c868c2b58904e88e6c90e8b74f8dba9bb27b03222e67', 131570882, 'DiffusionPolicy', 'PyTorch', '2.x', 'umi-policy-v1', 'EEF_RELATIVE_ROT6D', 2, 10.000, '["camera0_rgb"]', '{"camera0_rgb": {"shape": [2, 3, 224, 224]}, "robot0_eef_pos": {"shape": [2, 3]}, "robot0_gripper_width": {"shape": [2, 1]}, "robot0_eef_rot_axis_angle": {"shape": [2, 6]}, "robot0_eef_rot_axis_angle_wrt_start": {"shape": [2, 6]}}', '{"actionShape": [16, 10]}', 'rollout_success', 100, 3, 1, 0.9, 1, '2026-09-23 00:00:00+00', DEFAULT, '2026-09-23 00:00:00+00', '2026-09-23 00:00:00+00', NULL, NULL, '{"dim": 10, "layout": ["dx", "dy", "dz", "r00", "r01", "r02", "r10", "r11", "r12", "gap_m"], "rateHz": 10, "compose": "T_next = T_cur @ A_relative", "gapUnit": "m", "horizon": 16, "gapRange": [0, 0.09], "rotation": "ROTATION_MATRIX_ROWS_0_1", "nActionSteps": 8, "schemaVersion": 1}', '{"execSlice": {"endExclusive": 5, "startInclusive": 1}, "convertedDim": 7, "convertedFrame": "ABSOLUTE", "convertedLayout": ["x", "y", "z", "rx", "ry", "rz", "gap_m"], "reobserveRateHz": 2.5, "actionPointRateHz": 10, "convertedRotation": "AXIS_ANGLE_ROTVEC", "execSliceAppliesTo": "raw", "consumerMustConvert": true}');


--
-- Data for Name: skills; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.skills VALUES ('54000000-0000-4000-8000-000000000001', 'grab_blue_flag', 'TUTORIAL', 'DEPLOYED', '청기잡기', '청기를 잡는 학습 동작', NULL, '2026-09-09 00:00:00+00', '2026-09-09 00:10:00+00', NULL);
INSERT INTO public.skills VALUES ('54000000-0000-4000-8000-000000000002', 'raise_arm', 'TUTORIAL', 'DEPLOYED', '팔 올리기', '로봇팔을 위로 올리는 학습 동작', NULL, '2026-09-09 00:10:00+00', '2026-09-09 00:20:00+00', NULL);
INSERT INTO public.skills VALUES ('54000000-0000-4000-8000-000000000003', 'custom_00000000000040008000000000000003', 'CUSTOM', 'DEPLOYED', '구운감자 옮기기', '구운감자 포장용 동작', NULL, '2026-09-26 03:39:30.370211+00', '2026-09-26 03:39:32.787266+00', '50000000-0000-4000-8000-000000000001');


--
-- Data for Name: storage_nodes; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.storage_nodes VALUES ('18000000-0000-4000-8000-000000000001', 'ec2-episode-storage', 'episode-s3', 'Episode S3 Storage', 'S3', 'ONLINE', 9223372036854775807, 9223372036854775807, '2026-09-18 02:02:43.47003+00', '2026-09-18 02:02:43.47003+00', '2026-09-18 02:02:43.47003+00', 0);
INSERT INTO public.storage_nodes VALUES ('18000000-0000-4000-8000-000000000002', 'ssafy_gpu_01', 'gpu-01-local-cache', 'SSAFY GPU Worker', 'LOCAL', 'ONLINE', 1099511627776, 1099511627776, '2026-09-18 02:02:43.497596+00', '2026-09-18 02:02:43.497596+00', '2026-09-18 02:02:43.497596+00', 0);


--
-- Data for Name: task_steps; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.task_steps VALUES ('57200000-0000-4000-8000-000000000001', '57100000-0000-4000-8000-000000000001', '56000000-0000-4000-8000-000000000001', 1, '{}', 30, NULL, 'SKILL', NULL);
INSERT INTO public.task_steps VALUES ('57200000-0000-4000-8000-000000000002', '57100000-0000-4000-8000-000000000001', '56000000-0000-4000-8000-000000000002', 2, '{}', 30, NULL, 'SKILL', NULL);
INSERT INTO public.task_steps VALUES ('57200000-0000-4000-8000-000000000003', '57100000-0000-4000-8000-000000000002', '56000000-0000-4000-8000-000000000003', 1, '{}', 30, NULL, 'SKILL', NULL);


--
-- Data for Name: task_versions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.task_versions VALUES ('57100000-0000-4000-8000-000000000001', '57000000-0000-4000-8000-000000000001', 1, '2026-09-09 00:25:00+00');
INSERT INTO public.task_versions VALUES ('57100000-0000-4000-8000-000000000002', '57000000-0000-4000-8000-000000000002', 1, '2026-09-23 00:25:00+00');


--
-- Data for Name: tasks; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.tasks VALUES ('57000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', 'raise_blue_flag', '청기 들기', '청기를 잡고 팔을 올린 뒤 종료하는 작업', 1, '2026-09-09 00:25:00+00', '2026-09-09 00:25:00+00', NULL);
INSERT INTO public.tasks VALUES ('57000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000001', 'move_roasted_potato_one_time', '구운감자 옮기기 1회', '구운감자 옮기기 동작을 1회 수행 후 종료하는 작업', 1, '2026-09-23 00:25:00+00', '2026-09-23 00:25:00+00', NULL);


--
-- Data for Name: training_job_episodes; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: training_job_events; Type: TABLE DATA; Schema: public; Owner: -
--



--
-- Data for Name: training_jobs; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.training_jobs VALUES ('55000000-0000-4000-8000-000000000001', '50000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000001', '51000000-0000-4000-8000-000000000001', NULL, NULL, 'EXISTING_EPISODES', 'SUCCEEDED', 1, NULL, NULL, '{"source": "DEVELOPMENT_SEED", "seedFixture": true}', NULL, NULL, 0, 0, 0, 0, '1111111111111111111111111111111111111111111111111111111111111111', 'COMPLETED', 100.00, 100, 100, '56000000-0000-4000-8000-000000000001', NULL, NULL, '2026-09-09 00:01:00+00', '2026-09-09 00:02:00+00', '2026-09-09 00:10:00+00', '2026-09-09 00:10:00+00', '2026-09-09 00:10:00+00', 0, NULL, 'FROM_SCRATCH', NULL);
INSERT INTO public.training_jobs VALUES ('55000000-0000-4000-8000-000000000002', '50000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000002', '51000000-0000-4000-8000-000000000001', NULL, NULL, 'EXISTING_EPISODES', 'SUCCEEDED', 1, NULL, NULL, '{"source": "DEVELOPMENT_SEED", "seedFixture": true}', NULL, NULL, 0, 0, 0, 0, '2222222222222222222222222222222222222222222222222222222222222222', 'COMPLETED', 100.00, 100, 100, '56000000-0000-4000-8000-000000000002', NULL, NULL, '2026-09-09 00:11:00+00', '2026-09-09 00:12:00+00', '2026-09-09 00:20:00+00', '2026-09-09 00:20:00+00', '2026-09-09 00:20:00+00', 0, NULL, 'FROM_SCRATCH', NULL);
INSERT INTO public.training_jobs VALUES ('55000000-0000-4000-8000-000000000003', '50000000-0000-4000-8000-000000000001', '54000000-0000-4000-8000-000000000003', '51000000-0000-4000-8000-000000000001', NULL, NULL, 'EXISTING_EPISODES', 'SUCCEEDED', 1, NULL, NULL, '{"source": "DEVELOPMENT_SEED", "seedFixture": true}', NULL, NULL, 0, 0, 0, 0, '3333333333333333333333333333333333333333333333333333333333333333', 'MODEL_VALIDATED', 100.00, 100, 100, '56000000-0000-4000-8000-000000000003', NULL, NULL, '2026-09-23 00:00:00+00', '2026-09-23 00:00:00+00', '2026-09-23 00:00:00+00', '2026-09-23 00:00:00+00', '2026-09-26 03:39:32.56199+00', 0, NULL, 'FROM_SCRATCH', NULL);


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.users VALUES ('50000000-0000-4000-8000-000000000001', 'admin01@ssafy.local', '$2a$10$ylKVWybCimE9yO2EWUnV4OesA4liqYxdzWmWeCyb4Mo65MpxSrQCu', 'admin01', 'ACTIVE', '2026-09-09 00:00:00+00', '2026-09-09 00:00:00+00');


--
-- Name: data_purge_jobs data_purge_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_purge_jobs
    ADD CONSTRAINT data_purge_jobs_pkey PRIMARY KEY (id);


--
-- Name: data_purge_reports data_purge_reports_data_purge_job_id_report_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_purge_reports
    ADD CONSTRAINT data_purge_reports_data_purge_job_id_report_id_key UNIQUE (data_purge_job_id, report_id);


--
-- Name: data_purge_reports data_purge_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_purge_reports
    ADD CONSTRAINT data_purge_reports_pkey PRIMARY KEY (id);


--
-- Name: deployment_items deployment_items_deployment_id_skill_version_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployment_items
    ADD CONSTRAINT deployment_items_deployment_id_skill_version_id_key UNIQUE (deployment_id, skill_version_id);


--
-- Name: deployment_items deployment_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployment_items
    ADD CONSTRAINT deployment_items_pkey PRIMARY KEY (id);


--
-- Name: deployment_reports deployment_reports_deployment_id_report_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployment_reports
    ADD CONSTRAINT deployment_reports_deployment_id_report_id_key UNIQUE (deployment_id, report_id);


--
-- Name: deployment_reports deployment_reports_deployment_id_sequence_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployment_reports
    ADD CONSTRAINT deployment_reports_deployment_id_sequence_key UNIQUE (deployment_id, sequence);


--
-- Name: deployment_reports deployment_reports_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployment_reports
    ADD CONSTRAINT deployment_reports_pkey PRIMARY KEY (id);


--
-- Name: deployments deployments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployments
    ADD CONSTRAINT deployments_pkey PRIMARY KEY (id);


--
-- Name: device_credentials device_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_credentials
    ADD CONSTRAINT device_credentials_pkey PRIMARY KEY (device_id);


--
-- Name: device_enrollments device_enrollments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_enrollments
    ADD CONSTRAINT device_enrollments_pkey PRIMARY KEY (id);


--
-- Name: devices devices_handle_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_handle_number_key UNIQUE (handle_number);


--
-- Name: devices devices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_pkey PRIMARY KEY (id);


--
-- Name: episode_archives episode_archives_episode_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_archives
    ADD CONSTRAINT episode_archives_episode_id_key UNIQUE (episode_id);


--
-- Name: episode_archives episode_archives_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_archives
    ADD CONSTRAINT episode_archives_pkey PRIMARY KEY (id);


--
-- Name: episode_archives episode_archives_storage_node_id_storage_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_archives
    ADD CONSTRAINT episode_archives_storage_node_id_storage_key_key UNIQUE (storage_node_id, storage_key);


--
-- Name: episode_upload_sessions episode_upload_sessions_episode_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_upload_sessions
    ADD CONSTRAINT episode_upload_sessions_episode_id_key UNIQUE (episode_id);


--
-- Name: episode_upload_sessions episode_upload_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_upload_sessions
    ADD CONSTRAINT episode_upload_sessions_pkey PRIMARY KEY (id);


--
-- Name: episodes episodes_id_storage_node_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episodes
    ADD CONSTRAINT episodes_id_storage_node_id_key UNIQUE (id, storage_node_id);


--
-- Name: episodes episodes_owner_id_umi_device_id_session_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episodes
    ADD CONSTRAINT episodes_owner_id_umi_device_id_session_id_key UNIQUE (owner_id, umi_device_id, session_id);


--
-- Name: episodes episodes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episodes
    ADD CONSTRAINT episodes_pkey PRIMARY KEY (id);


--
-- Name: idempotency_keys idempotency_keys_actor_type_actor_id_http_method_request_pa_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_keys
    ADD CONSTRAINT idempotency_keys_actor_type_actor_id_http_method_request_pa_key UNIQUE (actor_type, actor_id, http_method, request_path, idempotency_key);


--
-- Name: idempotency_keys idempotency_keys_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_keys
    ADD CONSTRAINT idempotency_keys_pkey PRIMARY KEY (id);


--
-- Name: outbox_events outbox_events_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_event_id_key UNIQUE (event_id);


--
-- Name: outbox_events outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (id);


--
-- Name: refresh_sessions refresh_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_sessions
    ADD CONSTRAINT refresh_sessions_pkey PRIMARY KEY (id);


--
-- Name: refresh_sessions refresh_sessions_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_sessions
    ADD CONSTRAINT refresh_sessions_token_hash_key UNIQUE (token_hash);


--
-- Name: retention_policies retention_policies_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.retention_policies
    ADD CONSTRAINT retention_policies_name_key UNIQUE (name);


--
-- Name: retention_policies retention_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.retention_policies
    ADD CONSTRAINT retention_policies_pkey PRIMARY KEY (id);


--
-- Name: robot_executions robot_executions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_executions
    ADD CONSTRAINT robot_executions_pkey PRIMARY KEY (id);


--
-- Name: robot_profiles robot_profiles_name_contract_version_runtime_runtime_versio_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_profiles
    ADD CONSTRAINT robot_profiles_name_contract_version_runtime_runtime_versio_key UNIQUE (name, contract_version, runtime, runtime_version);


--
-- Name: robot_profiles robot_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_profiles
    ADD CONSTRAINT robot_profiles_pkey PRIMARY KEY (id);


--
-- Name: robot_skill_deployments robot_skill_deployments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_skill_deployments
    ADD CONSTRAINT robot_skill_deployments_pkey PRIMARY KEY (id);


--
-- Name: robots robots_jetson_device_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robots
    ADD CONSTRAINT robots_jetson_device_id_key UNIQUE (jetson_device_id);


--
-- Name: robots robots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robots
    ADD CONSTRAINT robots_pkey PRIMARY KEY (id);


--
-- Name: skill_versions skill_versions_checkpoint_bucket_checkpoint_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT skill_versions_checkpoint_bucket_checkpoint_key_key UNIQUE (checkpoint_bucket, checkpoint_key);


--
-- Name: skill_versions skill_versions_id_gate_passed_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT skill_versions_id_gate_passed_key UNIQUE (id, gate_passed);


--
-- Name: skill_versions skill_versions_id_skill_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT skill_versions_id_skill_id_key UNIQUE (id, skill_id);


--
-- Name: skill_versions skill_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT skill_versions_pkey PRIMARY KEY (id);


--
-- Name: skill_versions skill_versions_skill_id_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT skill_versions_skill_id_version_key UNIQUE (skill_id, version);


--
-- Name: skill_versions skill_versions_training_job_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT skill_versions_training_job_id_key UNIQUE (training_job_id);


--
-- Name: skills skills_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skills
    ADD CONSTRAINT skills_pkey PRIMARY KEY (id);


--
-- Name: skills skills_skill_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skills
    ADD CONSTRAINT skills_skill_key_key UNIQUE (skill_key);


--
-- Name: storage_nodes storage_nodes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_nodes
    ADD CONSTRAINT storage_nodes_pkey PRIMARY KEY (id);


--
-- Name: storage_nodes storage_nodes_worker_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_nodes
    ADD CONSTRAINT storage_nodes_worker_id_key UNIQUE (worker_id);


--
-- Name: storage_nodes storage_nodes_worker_id_node_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.storage_nodes
    ADD CONSTRAINT storage_nodes_worker_id_node_key_key UNIQUE (worker_id, node_key);


--
-- Name: task_steps task_steps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_steps
    ADD CONSTRAINT task_steps_pkey PRIMARY KEY (id);


--
-- Name: task_versions task_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_versions
    ADD CONSTRAINT task_versions_pkey PRIMARY KEY (id);


--
-- Name: task_versions task_versions_task_id_version_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_versions
    ADD CONSTRAINT task_versions_task_id_version_key UNIQUE (task_id, version);


--
-- Name: tasks tasks_owner_id_task_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_owner_id_task_key_key UNIQUE (owner_id, task_key);


--
-- Name: tasks tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);


--
-- Name: training_job_episodes training_job_episodes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_job_episodes
    ADD CONSTRAINT training_job_episodes_pkey PRIMARY KEY (training_job_id, episode_id);


--
-- Name: training_job_episodes training_job_episodes_training_job_id_input_order_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_job_episodes
    ADD CONSTRAINT training_job_episodes_training_job_id_input_order_key UNIQUE (training_job_id, input_order);


--
-- Name: training_job_events training_job_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_job_events
    ADD CONSTRAINT training_job_events_pkey PRIMARY KEY (id);


--
-- Name: training_job_events training_job_events_training_job_id_attempt_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_job_events
    ADD CONSTRAINT training_job_events_training_job_id_attempt_event_id_key UNIQUE (training_job_id, attempt, event_id);


--
-- Name: training_job_events training_job_events_training_job_id_attempt_sequence_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_job_events
    ADD CONSTRAINT training_job_events_training_job_id_attempt_sequence_key UNIQUE (training_job_id, attempt, sequence);


--
-- Name: training_jobs training_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_jobs
    ADD CONSTRAINT training_jobs_pkey PRIMARY KEY (id);


--
-- Name: deployments uq_deployments_identity_robot_task; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployments
    ADD CONSTRAINT uq_deployments_identity_robot_task UNIQUE (id, robot_id, task_version_id);


--
-- Name: device_enrollments uq_device_enrollments_token_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_enrollments
    ADD CONSTRAINT uq_device_enrollments_token_hash UNIQUE (registration_token_hash);


--
-- Name: episode_upload_sessions uq_episode_upload_identity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_upload_sessions
    ADD CONSTRAINT uq_episode_upload_identity UNIQUE (owner_id, umi_device_id, session_id);


--
-- Name: episode_upload_sessions uq_episode_upload_object; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_upload_sessions
    ADD CONSTRAINT uq_episode_upload_object UNIQUE (bucket_name, object_key);


--
-- Name: outbox_events uq_outbox_aggregate_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT uq_outbox_aggregate_sequence UNIQUE (aggregate_type, aggregate_id, aggregate_sequence);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: ix_data_purge_jobs_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_data_purge_jobs_1 ON public.data_purge_jobs USING btree (storage_node_id, status, scheduled_at);


--
-- Name: ix_data_purge_jobs_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_data_purge_jobs_2 ON public.data_purge_jobs USING btree (episode_id, created_at DESC);


--
-- Name: ix_data_purge_reports_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_data_purge_reports_1 ON public.data_purge_reports USING btree (data_purge_job_id, reported_at);


--
-- Name: ix_deployment_items_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_deployment_items_1 ON public.deployment_items USING btree (skill_version_id);


--
-- Name: ix_deployment_reports_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_deployment_reports_1 ON public.deployment_reports USING btree (deployment_id, reported_at);


--
-- Name: ix_deployments_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_deployments_1 ON public.deployments USING btree (robot_id, created_at DESC);


--
-- Name: ix_deployments_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_deployments_2 ON public.deployments USING btree (robot_id, status);


--
-- Name: ix_device_enrollments_created_by_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_device_enrollments_created_by_created_at ON public.device_enrollments USING btree (created_by, created_at DESC);


--
-- Name: ix_device_enrollments_status_expires_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_device_enrollments_status_expires_at ON public.device_enrollments USING btree (status, expires_at);


--
-- Name: ix_devices_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_devices_1 ON public.devices USING btree (owner_id, device_type);


--
-- Name: ix_devices_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_devices_2 ON public.devices USING btree (connection_status, last_seen_at);


--
-- Name: ix_episode_archives_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episode_archives_1 ON public.episode_archives USING btree (storage_node_id, status);


--
-- Name: ix_episode_archives_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episode_archives_2 ON public.episode_archives USING btree (sha256);


--
-- Name: ix_episode_upload_expiration; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episode_upload_expiration ON public.episode_upload_sessions USING btree (status, expires_at);


--
-- Name: ix_episode_upload_owner_device_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episode_upload_owner_device_status ON public.episode_upload_sessions USING btree (owner_id, umi_device_id, status, created_at DESC);


--
-- Name: ix_episodes_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episodes_1 ON public.episodes USING btree (owner_id, status, started_at DESC);


--
-- Name: ix_episodes_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episodes_2 ON public.episodes USING btree (umi_device_id, status, started_at DESC);


--
-- Name: ix_episodes_3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episodes_3 ON public.episodes USING btree (storage_node_id, status);


--
-- Name: ix_episodes_4; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episodes_4 ON public.episodes USING btree (status, retention_expires_at);


--
-- Name: ix_episodes_5; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episodes_5 ON public.episodes USING btree (skill_id, source, label_status, success, contract_version, status);


--
-- Name: ix_episodes_6; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_episodes_6 ON public.episodes USING btree (quality_status, started_at DESC);


--
-- Name: ix_idempotency_keys_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_idempotency_keys_1 ON public.idempotency_keys USING btree (expires_at);


--
-- Name: ix_outbox_events_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_outbox_events_1 ON public.outbox_events USING btree (status, available_at);


--
-- Name: ix_outbox_events_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_outbox_events_2 ON public.outbox_events USING btree (aggregate_type, aggregate_id, created_at);


--
-- Name: ix_outbox_expired_processing; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_outbox_expired_processing ON public.outbox_events USING btree (lease_expires_at) WHERE ((status)::text = 'PROCESSING'::text);


--
-- Name: ix_outbox_publishable; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_outbox_publishable ON public.outbox_events USING btree (available_at, created_at) WHERE ((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('FAILED'::character varying)::text]));


--
-- Name: ix_outbox_published_cleanup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_outbox_published_cleanup ON public.outbox_events USING btree (published_at) WHERE ((status)::text = 'PUBLISHED'::text);


--
-- Name: ix_refresh_sessions_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_refresh_sessions_1 ON public.refresh_sessions USING btree (user_id, expires_at);


--
-- Name: ix_robot_executions_deployment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_robot_executions_deployment ON public.robot_executions USING btree (deployment_id);


--
-- Name: ix_robot_executions_owner_requested; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_robot_executions_owner_requested ON public.robot_executions USING btree (owner_id, requested_at DESC, id DESC);


--
-- Name: ix_robot_executions_robot_requested; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_robot_executions_robot_requested ON public.robot_executions USING btree (robot_id, requested_at DESC, id DESC);


--
-- Name: ix_robot_executions_robot_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_robot_executions_robot_status ON public.robot_executions USING btree (robot_id, status, requested_at DESC);


--
-- Name: ix_robot_executions_task_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_robot_executions_task_version ON public.robot_executions USING btree (task_version_id);


--
-- Name: ix_robot_skill_deployments_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_robot_skill_deployments_1 ON public.robot_skill_deployments USING btree (robot_id, deployed_at DESC);


--
-- Name: ix_robot_skill_deployments_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_robot_skill_deployments_2 ON public.robot_skill_deployments USING btree (skill_version_id);


--
-- Name: ix_robots_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_robots_1 ON public.robots USING btree (owner_id, created_at DESC);


--
-- Name: ix_robots_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_robots_2 ON public.robots USING btree (connection_status, last_seen_at);


--
-- Name: ix_skill_versions_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_skill_versions_1 ON public.skill_versions USING btree (skill_id, status, version DESC);


--
-- Name: ix_skill_versions_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_skill_versions_2 ON public.skill_versions USING btree (robot_profile_id, status);


--
-- Name: ix_skill_versions_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_skill_versions_parent ON public.skill_versions USING btree (parent_skill_version_id) WHERE (parent_skill_version_id IS NOT NULL);


--
-- Name: ix_skills_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_skills_1 ON public.skills USING btree (tier, status);


--
-- Name: ix_skills_owner_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_skills_owner_created ON public.skills USING btree (owner_id, created_at DESC) WHERE (owner_id IS NOT NULL);


--
-- Name: ix_storage_nodes_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_storage_nodes_1 ON public.storage_nodes USING btree (status, last_seen_at);


--
-- Name: ix_task_steps_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_task_steps_1 ON public.task_steps USING btree (skill_version_id);


--
-- Name: ix_task_steps_parent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_task_steps_parent ON public.task_steps USING btree (parent_step_id);


--
-- Name: ix_task_versions_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_task_versions_1 ON public.task_versions USING btree (task_id, version DESC);


--
-- Name: ix_tasks_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_tasks_1 ON public.tasks USING btree (owner_id, created_at DESC);


--
-- Name: ix_tasks_owner_active_updated; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_tasks_owner_active_updated ON public.tasks USING btree (owner_id, updated_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: ix_training_job_episodes_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_job_episodes_1 ON public.training_job_episodes USING btree (episode_id, training_job_id);


--
-- Name: ix_training_job_events_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_job_events_1 ON public.training_job_events USING btree (training_job_id, received_at);


--
-- Name: ix_training_jobs_1; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_jobs_1 ON public.training_jobs USING btree (owner_id, status, created_at DESC);


--
-- Name: ix_training_jobs_2; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_jobs_2 ON public.training_jobs USING btree (skill_id, status, created_at DESC);


--
-- Name: ix_training_jobs_3; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_jobs_3 ON public.training_jobs USING btree (assigned_storage_node_id, status);


--
-- Name: ix_training_jobs_4; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_jobs_4 ON public.training_jobs USING btree (assigned_worker_id, status);


--
-- Name: ix_training_jobs_base_skill_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_jobs_base_skill_version ON public.training_jobs USING btree (base_skill_version_id) WHERE (base_skill_version_id IS NOT NULL);


--
-- Name: ix_training_jobs_claim_candidate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_jobs_claim_candidate ON public.training_jobs USING btree (created_at) WHERE ((deleted_at IS NULL) AND ((status)::text = ANY ((ARRAY['CREATED'::character varying, 'QUEUED'::character varying])::text[])));


--
-- Name: ix_training_jobs_expired_lease; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_jobs_expired_lease ON public.training_jobs USING btree (lease_expires_at) WHERE ((lease_expires_at IS NOT NULL) AND ((status)::text = ANY (ARRAY[('COLLECTING'::character varying)::text, ('PREPARING'::character varying)::text, ('RUNNING'::character varying)::text, ('MODEL_UPLOADING'::character varying)::text, ('MODEL_VALIDATING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text])));


--
-- Name: ix_training_jobs_owner_active_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_training_jobs_owner_active_created ON public.training_jobs USING btree (owner_id, created_at DESC) WHERE (deleted_at IS NULL);


--
-- Name: ix_users_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_users_status ON public.users USING btree (status);


--
-- Name: uq_device_enrollments_active_device; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_device_enrollments_active_device ON public.device_enrollments USING btree (device_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_episode_active_purge_job; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_episode_active_purge_job ON public.data_purge_jobs USING btree (episode_id) WHERE ((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('RUNNING'::character varying)::text]));


--
-- Name: uq_retention_policy_default; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_retention_policy_default ON public.retention_policies USING btree (is_default) WHERE (is_default = true);


--
-- Name: uq_robot_active_deployment; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_robot_active_deployment ON public.deployments USING btree (robot_id) WHERE ((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('DOWNLOADING'::character varying)::text, ('VERIFYING'::character varying)::text, ('APPLYING'::character varying)::text]));


--
-- Name: uq_robot_active_execution; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_robot_active_execution ON public.robot_executions USING btree (robot_id) WHERE ((status)::text = ANY (ARRAY[('REQUESTED'::character varying)::text, ('STARTING'::character varying)::text, ('RUNNING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text, ('STATUS_UNKNOWN'::character varying)::text]));


--
-- Name: uq_robot_active_skill; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_robot_active_skill ON public.robot_skill_deployments USING btree (robot_id, skill_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: uq_robot_execution_report_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_robot_execution_report_id ON public.robot_executions USING btree (robot_id, last_report_id) WHERE (last_report_id IS NOT NULL);


--
-- Name: uq_skills_owner_display_name_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_skills_owner_display_name_lower ON public.skills USING btree (owner_id, lower((display_name)::text)) WHERE (owner_id IS NOT NULL);


--
-- Name: uq_task_steps_sibling_order; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_task_steps_sibling_order ON public.task_steps USING btree (task_version_id, COALESCE(parent_step_id, '00000000-0000-0000-0000-000000000000'::uuid), step_order);


--
-- Name: uq_training_job_one_mapping_episode; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_training_job_one_mapping_episode ON public.training_job_episodes USING btree (training_job_id) WHERE ((input_role)::text = 'MAPPING'::text);


--
-- Name: uq_training_jobs_active_worker_gpu; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_training_jobs_active_worker_gpu ON public.training_jobs USING btree (assigned_worker_id, gpu_index) WHERE ((assigned_worker_id IS NOT NULL) AND (gpu_index IS NOT NULL) AND ((status)::text = ANY (ARRAY[('COLLECTING'::character varying)::text, ('PREPARING'::character varying)::text, ('RUNNING'::character varying)::text, ('MODEL_UPLOADING'::character varying)::text, ('MODEL_VALIDATING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text])));


--
-- Name: INDEX uq_training_jobs_active_worker_gpu; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON INDEX public.uq_training_jobs_active_worker_gpu IS 'worker의 GPU 슬롯별 활성 학습 작업 하나, gpuIndex 0~29로 최대 30개 병렬 실행';


--
-- Name: uq_users_email_lower; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_users_email_lower ON public.users USING btree (lower((email)::text));


--
-- Name: deployment_items trg_deployment_item_deployable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_deployment_item_deployable BEFORE INSERT OR UPDATE OF skill_version_id ON public.deployment_items FOR EACH ROW EXECUTE FUNCTION public.enforce_deployable_skill_version();


--
-- Name: data_purge_jobs fk_data_purge_jobs_35; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_purge_jobs
    ADD CONSTRAINT fk_data_purge_jobs_35 FOREIGN KEY (episode_id) REFERENCES public.episodes(id) ON DELETE RESTRICT;


--
-- Name: data_purge_jobs fk_data_purge_jobs_36; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_purge_jobs
    ADD CONSTRAINT fk_data_purge_jobs_36 FOREIGN KEY (storage_node_id) REFERENCES public.storage_nodes(id) ON DELETE RESTRICT;


--
-- Name: data_purge_reports fk_data_purge_reports_37; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_purge_reports
    ADD CONSTRAINT fk_data_purge_reports_37 FOREIGN KEY (data_purge_job_id) REFERENCES public.data_purge_jobs(id) ON DELETE RESTRICT;


--
-- Name: deployment_items fk_deployment_items_48; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployment_items
    ADD CONSTRAINT fk_deployment_items_48 FOREIGN KEY (skill_version_id, gate_passed) REFERENCES public.skill_versions(id, gate_passed) ON DELETE RESTRICT;


--
-- Name: deployment_items fk_deployment_items_49; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployment_items
    ADD CONSTRAINT fk_deployment_items_49 FOREIGN KEY (deployment_id) REFERENCES public.deployments(id) ON DELETE RESTRICT;


--
-- Name: deployment_items fk_deployment_items_50; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployment_items
    ADD CONSTRAINT fk_deployment_items_50 FOREIGN KEY (skill_version_id) REFERENCES public.skill_versions(id) ON DELETE RESTRICT;


--
-- Name: deployment_reports fk_deployment_reports_52; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployment_reports
    ADD CONSTRAINT fk_deployment_reports_52 FOREIGN KEY (deployment_id) REFERENCES public.deployments(id) ON DELETE RESTRICT;


--
-- Name: deployments fk_deployments_46; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployments
    ADD CONSTRAINT fk_deployments_46 FOREIGN KEY (robot_id) REFERENCES public.robots(id) ON DELETE RESTRICT;


--
-- Name: deployments fk_deployments_47; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deployments
    ADD CONSTRAINT fk_deployments_47 FOREIGN KEY (task_version_id) REFERENCES public.task_versions(id) ON DELETE RESTRICT;


--
-- Name: device_credentials fk_device_credentials_4; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_credentials
    ADD CONSTRAINT fk_device_credentials_4 FOREIGN KEY (device_id) REFERENCES public.devices(id) ON DELETE RESTRICT;


--
-- Name: device_enrollments fk_device_enrollments_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_enrollments
    ADD CONSTRAINT fk_device_enrollments_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: device_enrollments fk_device_enrollments_device; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_enrollments
    ADD CONSTRAINT fk_device_enrollments_device FOREIGN KEY (device_id) REFERENCES public.devices(id) ON DELETE RESTRICT;


--
-- Name: devices fk_devices_3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT fk_devices_3 FOREIGN KEY (owner_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: episode_archives fk_episode_archives_10; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_archives
    ADD CONSTRAINT fk_episode_archives_10 FOREIGN KEY (episode_id, storage_node_id) REFERENCES public.episodes(id, storage_node_id) ON DELETE RESTRICT;


--
-- Name: episode_archives fk_episode_archives_11; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_archives
    ADD CONSTRAINT fk_episode_archives_11 FOREIGN KEY (episode_id) REFERENCES public.episodes(id) ON DELETE RESTRICT;


--
-- Name: episode_archives fk_episode_archives_12; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_archives
    ADD CONSTRAINT fk_episode_archives_12 FOREIGN KEY (storage_node_id) REFERENCES public.storage_nodes(id) ON DELETE RESTRICT;


--
-- Name: episode_upload_sessions fk_episode_upload_device; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_upload_sessions
    ADD CONSTRAINT fk_episode_upload_device FOREIGN KEY (umi_device_id) REFERENCES public.devices(id) ON DELETE RESTRICT;


--
-- Name: episode_upload_sessions fk_episode_upload_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_upload_sessions
    ADD CONSTRAINT fk_episode_upload_owner FOREIGN KEY (owner_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: episode_upload_sessions fk_episode_upload_training_job; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episode_upload_sessions
    ADD CONSTRAINT fk_episode_upload_training_job FOREIGN KEY (training_job_id) REFERENCES public.training_jobs(id) ON DELETE RESTRICT;


--
-- Name: episodes fk_episodes_5; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episodes
    ADD CONSTRAINT fk_episodes_5 FOREIGN KEY (owner_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: episodes fk_episodes_6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episodes
    ADD CONSTRAINT fk_episodes_6 FOREIGN KEY (umi_device_id) REFERENCES public.devices(id) ON DELETE RESTRICT;


--
-- Name: episodes fk_episodes_7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episodes
    ADD CONSTRAINT fk_episodes_7 FOREIGN KEY (storage_node_id) REFERENCES public.storage_nodes(id) ON DELETE RESTRICT;


--
-- Name: episodes fk_episodes_8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episodes
    ADD CONSTRAINT fk_episodes_8 FOREIGN KEY (retention_policy_id) REFERENCES public.retention_policies(id) ON DELETE RESTRICT;


--
-- Name: episodes fk_episodes_9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.episodes
    ADD CONSTRAINT fk_episodes_9 FOREIGN KEY (skill_id) REFERENCES public.skills(id) ON DELETE RESTRICT;


--
-- Name: refresh_sessions fk_refresh_sessions_1; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_sessions
    ADD CONSTRAINT fk_refresh_sessions_1 FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: refresh_sessions fk_refresh_sessions_2; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_sessions
    ADD CONSTRAINT fk_refresh_sessions_2 FOREIGN KEY (replaced_by_session_id) REFERENCES public.refresh_sessions(id) ON DELETE RESTRICT;


--
-- Name: robot_executions fk_robot_executions_deployment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_executions
    ADD CONSTRAINT fk_robot_executions_deployment FOREIGN KEY (deployment_id) REFERENCES public.deployments(id) ON DELETE RESTRICT;


--
-- Name: robot_executions fk_robot_executions_deployment_context; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_executions
    ADD CONSTRAINT fk_robot_executions_deployment_context FOREIGN KEY (deployment_id, robot_id, task_version_id) REFERENCES public.deployments(id, robot_id, task_version_id) ON DELETE RESTRICT;


--
-- Name: robot_executions fk_robot_executions_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_executions
    ADD CONSTRAINT fk_robot_executions_owner FOREIGN KEY (owner_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: robot_executions fk_robot_executions_robot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_executions
    ADD CONSTRAINT fk_robot_executions_robot FOREIGN KEY (robot_id) REFERENCES public.robots(id) ON DELETE RESTRICT;


--
-- Name: robot_executions fk_robot_executions_task_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_executions
    ADD CONSTRAINT fk_robot_executions_task_version FOREIGN KEY (task_version_id) REFERENCES public.task_versions(id) ON DELETE RESTRICT;


--
-- Name: robot_skill_deployments fk_robot_skill_deployments_53; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_skill_deployments
    ADD CONSTRAINT fk_robot_skill_deployments_53 FOREIGN KEY (skill_version_id, skill_id) REFERENCES public.skill_versions(id, skill_id) ON DELETE RESTRICT;


--
-- Name: robot_skill_deployments fk_robot_skill_deployments_54; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_skill_deployments
    ADD CONSTRAINT fk_robot_skill_deployments_54 FOREIGN KEY (robot_id) REFERENCES public.robots(id) ON DELETE RESTRICT;


--
-- Name: robot_skill_deployments fk_robot_skill_deployments_55; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_skill_deployments
    ADD CONSTRAINT fk_robot_skill_deployments_55 FOREIGN KEY (skill_id) REFERENCES public.skills(id) ON DELETE RESTRICT;


--
-- Name: robot_skill_deployments fk_robot_skill_deployments_56; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_skill_deployments
    ADD CONSTRAINT fk_robot_skill_deployments_56 FOREIGN KEY (skill_version_id) REFERENCES public.skill_versions(id) ON DELETE RESTRICT;


--
-- Name: robot_skill_deployments fk_robot_skill_deployments_57; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robot_skill_deployments
    ADD CONSTRAINT fk_robot_skill_deployments_57 FOREIGN KEY (deployment_id) REFERENCES public.deployments(id) ON DELETE RESTRICT;


--
-- Name: robots fk_robots_42; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robots
    ADD CONSTRAINT fk_robots_42 FOREIGN KEY (owner_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: robots fk_robots_43; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robots
    ADD CONSTRAINT fk_robots_43 FOREIGN KEY (jetson_device_id) REFERENCES public.devices(id) ON DELETE RESTRICT;


--
-- Name: robots fk_robots_44; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robots
    ADD CONSTRAINT fk_robots_44 FOREIGN KEY (robot_profile_id) REFERENCES public.robot_profiles(id) ON DELETE RESTRICT;


--
-- Name: robots fk_robots_45; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.robots
    ADD CONSTRAINT fk_robots_45 FOREIGN KEY (current_deployment_id) REFERENCES public.deployments(id) ON DELETE RESTRICT;


--
-- Name: skill_versions fk_skill_versions_25; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT fk_skill_versions_25 FOREIGN KEY (skill_id) REFERENCES public.skills(id) ON DELETE RESTRICT;


--
-- Name: skill_versions fk_skill_versions_26; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT fk_skill_versions_26 FOREIGN KEY (training_job_id) REFERENCES public.training_jobs(id) ON DELETE RESTRICT;


--
-- Name: skill_versions fk_skill_versions_28; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT fk_skill_versions_28 FOREIGN KEY (robot_profile_id) REFERENCES public.robot_profiles(id) ON DELETE RESTRICT;


--
-- Name: skill_versions fk_skill_versions_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skill_versions
    ADD CONSTRAINT fk_skill_versions_parent FOREIGN KEY (parent_skill_version_id) REFERENCES public.skill_versions(id) ON DELETE RESTRICT;


--
-- Name: skills fk_skills_owner; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.skills
    ADD CONSTRAINT fk_skills_owner FOREIGN KEY (owner_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: task_steps fk_task_steps_40; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_steps
    ADD CONSTRAINT fk_task_steps_40 FOREIGN KEY (task_version_id) REFERENCES public.task_versions(id) ON DELETE RESTRICT;


--
-- Name: task_steps fk_task_steps_41; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_steps
    ADD CONSTRAINT fk_task_steps_41 FOREIGN KEY (skill_version_id) REFERENCES public.skill_versions(id) ON DELETE RESTRICT;


--
-- Name: task_steps fk_task_steps_parent; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_steps
    ADD CONSTRAINT fk_task_steps_parent FOREIGN KEY (parent_step_id) REFERENCES public.task_steps(id) ON DELETE RESTRICT;


--
-- Name: task_versions fk_task_versions_39; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.task_versions
    ADD CONSTRAINT fk_task_versions_39 FOREIGN KEY (task_id) REFERENCES public.tasks(id) ON DELETE RESTRICT;


--
-- Name: tasks fk_tasks_38; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT fk_tasks_38 FOREIGN KEY (owner_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: training_job_episodes fk_training_job_episodes_20; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_job_episodes
    ADD CONSTRAINT fk_training_job_episodes_20 FOREIGN KEY (training_job_id) REFERENCES public.training_jobs(id) ON DELETE RESTRICT;


--
-- Name: training_job_episodes fk_training_job_episodes_21; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_job_episodes
    ADD CONSTRAINT fk_training_job_episodes_21 FOREIGN KEY (episode_id) REFERENCES public.episodes(id) ON DELETE RESTRICT;


--
-- Name: training_job_events fk_training_job_events_22; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_job_events
    ADD CONSTRAINT fk_training_job_events_22 FOREIGN KEY (training_job_id) REFERENCES public.training_jobs(id) ON DELETE RESTRICT;


--
-- Name: training_jobs fk_training_jobs_14; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_jobs
    ADD CONSTRAINT fk_training_jobs_14 FOREIGN KEY (owner_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: training_jobs fk_training_jobs_15; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_jobs
    ADD CONSTRAINT fk_training_jobs_15 FOREIGN KEY (skill_id) REFERENCES public.skills(id) ON DELETE RESTRICT;


--
-- Name: training_jobs fk_training_jobs_16; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_jobs
    ADD CONSTRAINT fk_training_jobs_16 FOREIGN KEY (umi_device_id) REFERENCES public.devices(id) ON DELETE RESTRICT;


--
-- Name: training_jobs fk_training_jobs_18; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_jobs
    ADD CONSTRAINT fk_training_jobs_18 FOREIGN KEY (assigned_storage_node_id) REFERENCES public.storage_nodes(id) ON DELETE RESTRICT;


--
-- Name: training_jobs fk_training_jobs_19; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_jobs
    ADD CONSTRAINT fk_training_jobs_19 FOREIGN KEY (result_skill_version_id) REFERENCES public.skill_versions(id) ON DELETE RESTRICT;


--
-- Name: training_jobs fk_training_jobs_base_skill_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.training_jobs
    ADD CONSTRAINT fk_training_jobs_base_skill_version FOREIGN KEY (base_skill_version_id) REFERENCES public.skill_versions(id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--


