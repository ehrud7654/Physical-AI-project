# DB 스키마 — Flyway V22 기준

| 항목 | 내용 |
|---|---|
| 애플리케이션 테이블 | public 스키마 29개 |
| 추출·복원 확인 | 테이블 29개, 제약조건 250개, 인덱스 134개, 트리거 1개, 함수 1개 |
| 기준 DBMS | PostgreSQL 17 |
| 구조 전용 SQL | [DB스키마_V22.sql](./DB스키마_V22.sql) |
| 초기·더미데이터 | [더미데이터_삽입방법.md](./더미데이터_삽입방법.md) |
| 마이그레이션 원본 | [db/migration](../../BE/EC2_/src/main/resources/db/migration) |

SQL에는 테이블·컬럼·기본값·PK·FK·UNIQUE·CHECK·인덱스·트리거·함수·주석만 포함하며 데이터 INSERT/COPY, 사용자/장치 인증 값, Flyway 이력은 포함하지 않습니다. 테이블별 아래 설명 중 컬럼 주석은 DB에 저장된 원문이며, 실제 제약은 별도 제약조건 표를 기준으로 읽습니다.


## 설치 및 실제 적용 버전 확인

일반 서비스 설치는 기존 Flyway 마이그레이션을 사용합니다. 구조 SQL을 먼저 넣고 기존 Flyway를 실행하면 V1과 충돌하므로 두 설치 방식을 섞지 않습니다. 구조 SQL은 구조 검토 또는 Flyway를 실행하지 않는 별도 빈 DB에만 사용합니다. 파일은 PostgreSQL 17.11 `pg_dump` 결과이며 `psql` 메타 명령이 포함되어 있으므로 호환되는 PostgreSQL 17.11 이상 클라이언트를 사용합니다. 기본 public 스키마가 이미 있는 빈 DB를 허용하도록 `CREATE SCHEMA IF NOT EXISTS public`만 정규화했습니다.

EC2의 `BE/EC2_` 경로에서 실행 중인 DB의 적용 이력을 확인합니다. 로컬 전용 Compose를 사용하면 동일 명령에 해당 `--env-file`과 `-f` 옵션을 추가합니다.

```bash
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' <<'SQL'
SELECT installed_rank, version, description, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
SQL
```

이 문서의 상태와 일치하려면 V1~V22가 성공한 상태여야 합니다. V22는 데이터 변경이므로 마지막 DDL 변경은 V19입니다. 실패하거나 누락된 이력을 문서만 보고 성공 처리하지 않습니다.

## 테이블 목록

| 테이블 | 용도 | 컬럼 수 |
|---|---|---:|
| [data_purge_jobs](#table-data_purge_jobs) | 데이터 삭제 작업·lease | 16 |
| [data_purge_reports](#table-data_purge_reports) | 삭제 처리 결과 | 9 |
| [deployment_items](#table-deployment_items) | 배포 대상 스킬 버전 | 11 |
| [deployment_reports](#table-deployment_reports) | 배포 진행 보고 | 9 |
| [deployments](#table-deployments) | 로봇 배포 요청 | 12 |
| [device_credentials](#table-device_credentials) | 장치 인증 secret 해시 | 8 |
| [device_enrollments](#table-device_enrollments) | 장치 등록 토큰 및 등록 상태 | 13 |
| [devices](#table-devices) | UMI·Jetson 장치 및 소유자 | 13 |
| [episode_archives](#table-episode_archives) | Episode 파일 및 검증 정보 | 21 |
| [episode_upload_sessions](#table-episode_upload_sessions) | S3 업로드 예약·완료·검증 세션 | 24 |
| [episodes](#table-episodes) | 수집 데이터 메타데이터·품질·보관 상태 | 42 |
| [idempotency_keys](#table-idempotency_keys) | 멱등 요청 및 응답 기록 | 13 |
| [outbox_events](#table-outbox_events) | 트랜잭션 기반 발행 대기 이벤트 | 17 |
| [refresh_sessions](#table-refresh_sessions) | Refresh 세션 영속 테이블(현재 로그인 경로는 Redis 사용) | 8 |
| [retention_policies](#table-retention_policies) | 데이터 보관 정책 | 12 |
| [robot_executions](#table-robot_executions) | 로봇 실행 이력 | 20 |
| [robot_profiles](#table-robot_profiles) | 로봇 입출력·런타임 호환 계약 | 11 |
| [robot_skill_deployments](#table-robot_skill_deployments) | 로봇별 스킬 배포 참조 | 9 |
| [robots](#table-robots) | 로봇과 Jetson 연결 | 11 |
| [skill_versions](#table-skill_versions) | 모델 버전·체크포인트·게이트 결과 | 34 |
| [skills](#table-skills) | 기본·사용자 정의 스킬 | 10 |
| [storage_nodes](#table-storage_nodes) | 저장 노드 등록 및 영속 메타데이터 | 12 |
| [task_steps](#table-task_steps) | 작업 단계와 중첩 구조 | 9 |
| [task_versions](#table-task_versions) | 작업 버전 | 4 |
| [tasks](#table-tasks) | 작업 정의 | 9 |
| [training_job_episodes](#table-training_job_episodes) | 학습 입력 Episode 순서·역할 | 11 |
| [training_job_events](#table-training_job_events) | GPU 학습 상태 이벤트 | 9 |
| [training_jobs](#table-training_jobs) | 학습 작업·진행·할당·lease | 35 |
| [users](#table-users) | 사용자 계정 | 7 |

<a id="table-data_purge_jobs"></a>

## data_purge_jobs

데이터 삭제 작업·lease.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| episode_id | uuid | 아니오 | — | FK → &#96;episodes.id&#96; |
| storage_node_id | uuid | 아니오 | — | FK → &#96;storage_nodes.id&#96; |
| status | character varying(20) | 아니오 | — | &#96;PENDING&#96;, &#96;RUNNING&#96;, &#96;SUCCEEDED&#96;, &#96;FAILED&#96; |
| attempt | integer | 아니오 | 0 | 기본 0, claim 시 증가 |
| assigned_worker_id | character varying(100) | 예 | — | 삭제 worker |
| lease_token_hash | character varying(64) | 예 | — | lease token hash |
| lease_expires_at | timestamp with time zone | 예 | — | lease 만료 |
| scheduled_at | timestamp with time zone | 아니오 | — | 삭제 예정 시각 |
| started_at | timestamp with time zone | 예 | — | 실행 시작 |
| finished_at | timestamp with time zone | 예 | — | 성공·실패 완료 |
| error_code | character varying(100) | 예 | — | 삭제 오류 |
| error_message | text | 예 | — | 오류 설명 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| row_version | bigint | 아니오 | 0 | 상태 전이 낙관적 잠금 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| data_purge_jobs_attempt_check | CHECK | CHECK ((attempt >= 0)) |
| data_purge_jobs_lease_token_hash_check | CHECK | CHECK (((lease_token_hash)::text ~ '^[0-9a-f]{64}$'::text)) |
| data_purge_jobs_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('RUNNING'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text]))) |
| fk_data_purge_jobs_35 | FK | FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE RESTRICT |
| fk_data_purge_jobs_36 | FK | FOREIGN KEY (storage_node_id) REFERENCES storage_nodes(id) ON DELETE RESTRICT |
| data_purge_jobs_pkey | PK | PRIMARY KEY (id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX data_purge_jobs_pkey ON public.data_purge_jobs USING btree (id);
CREATE INDEX ix_data_purge_jobs_1 ON public.data_purge_jobs USING btree (storage_node_id, status, scheduled_at);
CREATE INDEX ix_data_purge_jobs_2 ON public.data_purge_jobs USING btree (episode_id, created_at DESC);
CREATE UNIQUE INDEX uq_episode_active_purge_job ON public.data_purge_jobs USING btree (episode_id) WHERE ((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('RUNNING'::character varying)::text]));
```

<a id="table-data_purge_reports"></a>

## data_purge_reports

삭제 처리 결과.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| data_purge_job_id | uuid | 아니오 | — | FK → &#96;data_purge_jobs.id&#96; |
| report_id | character varying(150) | 아니오 | — | worker 멱등성 ID |
| attempt | integer | 아니오 | — | 삭제 attempt |
| status | character varying(20) | 아니오 | — | 보고 상태 |
| archive_result | jsonb | 아니오 | — | Episode archive 삭제 결과 snapshot |
| error_code | character varying(100) | 예 | — | 보고 오류 |
| error_message | text | 예 | — | 오류 설명 |
| reported_at | timestamp with time zone | 아니오 | — | 서버 수신 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| fk_data_purge_reports_37 | FK | FOREIGN KEY (data_purge_job_id) REFERENCES data_purge_jobs(id) ON DELETE RESTRICT |
| data_purge_reports_pkey | PK | PRIMARY KEY (id) |
| data_purge_reports_data_purge_job_id_report_id_key | UNIQUE | UNIQUE (data_purge_job_id, report_id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX data_purge_reports_data_purge_job_id_report_id_key ON public.data_purge_reports USING btree (data_purge_job_id, report_id);
CREATE UNIQUE INDEX data_purge_reports_pkey ON public.data_purge_reports USING btree (id);
CREATE INDEX ix_data_purge_reports_1 ON public.data_purge_reports USING btree (data_purge_job_id, reported_at);
```

<a id="table-deployment_items"></a>

## deployment_items

배포 대상 스킬 버전.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| deployment_id | uuid | 아니오 | — | FK → &#96;deployments.id&#96; |
| skill_version_id | uuid | 아니오 | — | FK → &#96;skill_versions.id&#96; |
| gate_passed | boolean | 아니오 | true | 기본 true이며 항상 true, 배포 게이트 복합 FK 일부 |
| status | character varying(30) | 아니오 | — | &#96;PENDING&#96;, &#96;DOWNLOADING&#96;, &#96;VERIFIED&#96;, &#96;FAILED&#96; |
| downloaded_at | timestamp with time zone | 예 | — | 다운로드 완료 |
| verified_at | timestamp with time zone | 예 | — | 해시·로드 검증 완료 |
| error_code | character varying(100) | 예 | — | 항목 오류 |
| error_message | text | 예 | — | 오류 설명 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| deployment_items_gate_passed_check | CHECK | CHECK ((gate_passed = true)) |
| deployment_items_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('DOWNLOADING'::character varying)::text, ('VERIFIED'::character varying)::text, ('FAILED'::character varying)::text]))) |
| fk_deployment_items_48 | FK | FOREIGN KEY (skill_version_id, gate_passed) REFERENCES skill_versions(id, gate_passed) ON DELETE RESTRICT |
| fk_deployment_items_49 | FK | FOREIGN KEY (deployment_id) REFERENCES deployments(id) ON DELETE RESTRICT |
| fk_deployment_items_50 | FK | FOREIGN KEY (skill_version_id) REFERENCES skill_versions(id) ON DELETE RESTRICT |
| deployment_items_pkey | PK | PRIMARY KEY (id) |
| deployment_items_deployment_id_skill_version_id_key | UNIQUE | UNIQUE (deployment_id, skill_version_id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX deployment_items_deployment_id_skill_version_id_key ON public.deployment_items USING btree (deployment_id, skill_version_id);
CREATE UNIQUE INDEX deployment_items_pkey ON public.deployment_items USING btree (id);
CREATE INDEX ix_deployment_items_1 ON public.deployment_items USING btree (skill_version_id);
```

### 트리거

```sql
CREATE TRIGGER trg_deployment_item_deployable BEFORE INSERT OR UPDATE OF skill_version_id ON public.deployment_items FOR EACH ROW EXECUTE FUNCTION enforce_deployable_skill_version();
```

<a id="table-deployment_reports"></a>

## deployment_reports

배포 진행 보고.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| deployment_id | uuid | 아니오 | — | FK → &#96;deployments.id&#96; |
| report_id | character varying(150) | 아니오 | — | Jetson 멱등성 ID |
| sequence | bigint | 아니오 | — | 증가 순번 |
| status | character varying(30) | 아니오 | — | 보고 Deployment 상태 |
| progress_percent | numeric(5,2) | 아니오 | — | 보고 진행률 |
| items | jsonb | 아니오 | — | 항목별 보고 snapshot |
| error | jsonb | 예 | — | 보고 오류 |
| reported_at | timestamp with time zone | 아니오 | — | 서버 수신 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| fk_deployment_reports_52 | FK | FOREIGN KEY (deployment_id) REFERENCES deployments(id) ON DELETE RESTRICT |
| deployment_reports_pkey | PK | PRIMARY KEY (id) |
| deployment_reports_deployment_id_report_id_key | UNIQUE | UNIQUE (deployment_id, report_id) |
| deployment_reports_deployment_id_sequence_key | UNIQUE | UNIQUE (deployment_id, sequence) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX deployment_reports_deployment_id_report_id_key ON public.deployment_reports USING btree (deployment_id, report_id);
CREATE UNIQUE INDEX deployment_reports_deployment_id_sequence_key ON public.deployment_reports USING btree (deployment_id, sequence);
CREATE UNIQUE INDEX deployment_reports_pkey ON public.deployment_reports USING btree (id);
CREATE INDEX ix_deployment_reports_1 ON public.deployment_reports USING btree (deployment_id, reported_at);
```

<a id="table-deployments"></a>

## deployments

로봇 배포 요청.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| robot_id | uuid | 아니오 | — | FK → &#96;robots.id&#96; |
| task_version_id | uuid | 아니오 | — | FK → &#96;task_versions.id&#96; |
| status | character varying(30) | 아니오 | — | 배포 상태 |
| progress_percent | numeric(5,2) | 아니오 | — | 0~100 |
| last_report_sequence | bigint | 아니오 | 0 | 기본 0 |
| error_code | character varying(100) | 예 | — | 배포 오류 |
| error_message | text | 예 | — | 오류 설명 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| applied_at | timestamp with time zone | 예 | — | SUCCEEDED 확정 시각 |
| row_version | bigint | 아니오 | 0 | 상태 전이 낙관적 잠금 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| deployments_progress_percent_check | CHECK | CHECK (((progress_percent >= (0)::numeric) AND (progress_percent <= (100)::numeric))) |
| deployments_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('DOWNLOADING'::character varying)::text, ('VERIFYING'::character varying)::text, ('APPLYING'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text, ('CANCELED'::character varying)::text]))) |
| fk_deployments_46 | FK | FOREIGN KEY (robot_id) REFERENCES robots(id) ON DELETE RESTRICT |
| fk_deployments_47 | FK | FOREIGN KEY (task_version_id) REFERENCES task_versions(id) ON DELETE RESTRICT |
| deployments_pkey | PK | PRIMARY KEY (id) |
| uq_deployments_identity_robot_task | UNIQUE | UNIQUE (id, robot_id, task_version_id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX deployments_pkey ON public.deployments USING btree (id);
CREATE INDEX ix_deployments_1 ON public.deployments USING btree (robot_id, created_at DESC);
CREATE INDEX ix_deployments_2 ON public.deployments USING btree (robot_id, status);
CREATE UNIQUE INDEX uq_deployments_identity_robot_task ON public.deployments USING btree (id, robot_id, task_version_id);
CREATE UNIQUE INDEX uq_robot_active_deployment ON public.deployments USING btree (robot_id) WHERE ((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('DOWNLOADING'::character varying)::text, ('VERIFYING'::character varying)::text, ('APPLYING'::character varying)::text]));
```

<a id="table-device_credentials"></a>

## device_credentials

장치 인증 secret 해시.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| device_id | uuid | 아니오 | — | PK, FK → &#96;devices.id&#96; |
| device_secret_hash | character varying(255) | 아니오 | — | 장치 secret hash |
| secret_rotated_at | timestamp with time zone | 예 | — | secret 회전 시각 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| status | character varying(20) | 아니오 | 'ACTIVE'::character varying | 장치 인증 정보 상태 |
| enrolled_at | timestamp with time zone | 예 | — | QR 등록 완료 시각 |
| last_authenticated_at | timestamp with time zone | 예 | — | 마지막 Access Token 발급 인증 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_device_credentials_status | CHECK | CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('REVOKED'::character varying)::text]))) |
| fk_device_credentials_4 | FK | FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE RESTRICT |
| device_credentials_pkey | PK | PRIMARY KEY (device_id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX device_credentials_pkey ON public.device_credentials USING btree (device_id);
```

<a id="table-device_enrollments"></a>

## device_enrollments

장치 등록 토큰 및 등록 상태.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | — |
| device_id | uuid | 아니오 | — | — |
| registration_token_hash | character varying(64) | 아니오 | — | QR에 포함된 원문 등록 토큰의 SHA-256 |
| status | character varying(20) | 아니오 | — | — |
| expires_at | timestamp with time zone | 아니오 | — | — |
| used_at | timestamp with time zone | 예 | — | Device Secret 발급 완료 시각 |
| revoked_at | timestamp with time zone | 예 | — | — |
| attempt_count | integer | 아니오 | 0 | 등록 완료 검증 실패 횟수 |
| max_attempts | integer | 아니오 | 5 | 허용되는 최대 검증 실패 횟수 |
| created_by | uuid | 아니오 | — | QR 등록을 시작한 사용자 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | — |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | — |
| row_version | bigint | 아니오 | 0 | — |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_device_enrollments_attempts | CHECK | CHECK (((attempt_count >= 0) AND (max_attempts >= 1) AND (attempt_count <= max_attempts))) |
| ck_device_enrollments_expiration | CHECK | CHECK ((expires_at > created_at)) |
| ck_device_enrollments_state | CHECK | CHECK (((((status)::text = 'ACTIVE'::text) AND (used_at IS NULL) AND (revoked_at IS NULL)) OR (((status)::text = 'USED'::text) AND (used_at IS NOT NULL) AND (revoked_at IS NULL)) OR (((status)::text = 'EXPIRED'::text) AND (used_at IS NULL) AND (revoked_at IS NULL)) OR (((status)::text = 'REVOKED'::text) AND (used_at IS NULL) AND (revoked_at IS NOT NULL)))) |
| ck_device_enrollments_status | CHECK | CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('USED'::character varying)::text, ('EXPIRED'::character varying)::text, ('REVOKED'::character varying)::text]))) |
| ck_device_enrollments_token_hash | CHECK | CHECK (((registration_token_hash)::text ~ '^[0-9a-f]{64}$'::text)) |
| fk_device_enrollments_created_by | FK | FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT |
| fk_device_enrollments_device | FK | FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE RESTRICT |
| device_enrollments_pkey | PK | PRIMARY KEY (id) |
| uq_device_enrollments_token_hash | UNIQUE | UNIQUE (registration_token_hash) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX device_enrollments_pkey ON public.device_enrollments USING btree (id);
CREATE INDEX ix_device_enrollments_created_by_created_at ON public.device_enrollments USING btree (created_by, created_at DESC);
CREATE INDEX ix_device_enrollments_status_expires_at ON public.device_enrollments USING btree (status, expires_at);
CREATE UNIQUE INDEX uq_device_enrollments_active_device ON public.device_enrollments USING btree (device_id) WHERE ((status)::text = 'ACTIVE'::text);
CREATE UNIQUE INDEX uq_device_enrollments_token_hash ON public.device_enrollments USING btree (registration_token_hash);
```

<a id="table-devices"></a>

## devices

UMI·Jetson 장치 및 소유자.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| owner_id | uuid | 아니오 | — | FK → &#96;users.id&#96; |
| device_type | character varying(20) | 아니오 | — | &#96;UMI_CAMERA&#96;, &#96;JETSON&#96; |
| handle_number | character varying(100) | 아니오 | — | 외부 장치 식별자 |
| name | character varying(100) | 아니오 | — | 사용자 표시 이름 |
| connection_status | character varying(20) | 아니오 | — | &#96;ONLINE&#96;, &#96;OFFLINE&#96; |
| firmware_version | character varying(100) | 예 | — | 마지막 heartbeat 값 |
| runtime_version | character varying(100) | 예 | — | 마지막 heartbeat 값 |
| last_seen_at | timestamp with time zone | 예 | — | 서버 수신 기준 시각 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| location | character varying(200) | 예 | — | 장치 설치 위치. 미지정 가능 |
| registration_status | character varying(20) | 아니오 | 'REGISTERED'::character varying | UMI 장치 등록 상태. QR 등록 완료 전 PENDING, Device Secret 발급 완료 후 REGISTERED |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_devices_device_type | CHECK | CHECK (((device_type)::text = ANY (ARRAY[('UMI_CAMERA'::character varying)::text, ('JETSON'::character varying)::text]))) |
| ck_devices_registration_status | CHECK | CHECK (((registration_status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('REGISTERED'::character varying)::text]))) |
| devices_connection_status_check | CHECK | CHECK (((connection_status)::text = ANY (ARRAY[('ONLINE'::character varying)::text, ('OFFLINE'::character varying)::text]))) |
| fk_devices_3 | FK | FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT |
| devices_pkey | PK | PRIMARY KEY (id) |
| devices_handle_number_key | UNIQUE | UNIQUE (handle_number) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX devices_handle_number_key ON public.devices USING btree (handle_number);
CREATE UNIQUE INDEX devices_pkey ON public.devices USING btree (id);
CREATE INDEX ix_devices_1 ON public.devices USING btree (owner_id, device_type);
CREATE INDEX ix_devices_2 ON public.devices USING btree (connection_status, last_seen_at);
```

<a id="table-episode_archives"></a>

## episode_archives

Episode 파일 및 검증 정보.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| episode_id | uuid | 아니오 | — | FK → &#96;episodes.id&#96;, UNIQUE |
| storage_node_id | uuid | 아니오 | — | FK → &#96;storage_nodes.id&#96; |
| storage_key | character varying(500) | 아니오 | — | 서버 생성 S3 object key: episodes/{owner UUID 소문자}/{yyyyMMddTHHmmssZ}/{session UUID v4 소문자}.zip |
| original_filename | character varying(255) | 아니오 | — | 감사용 원본 파일명 {session UUID v4 소문자}.zip |
| content_type | character varying(150) | 아니오 | — | 고정 &#96;application/zip&#96; |
| archive_format_version | character varying(100) | 아니오 | — | 예: &#96;umi-episode-zip-v1&#96; |
| compression_method | character varying(20) | 아니오 | — | 고정 &#96;STORE&#96; |
| size_bytes | bigint | 아니오 | — | ZIP 파일 크기 |
| uncompressed_size_bytes | bigint | 아니오 | — | ZIP entry 원본 크기 합계 |
| entry_count | integer | 아니오 | — | ZIP entry 수 |
| sha256 | character varying(64) | 아니오 | — | 최종 ZIP 전체 SHA-256 |
| manifest_summary | jsonb | 아니오 | — | 필수 entry 존재 여부와 주요 내부 파일 SHA-256 snapshot |
| status | character varying(30) | 아니오 | — | archive 보관·삭제 상태 |
| stored_at | timestamp with time zone | 예 | — | atomic rename 완료 시각 |
| validated_at | timestamp with time zone | 예 | — | ZIP 구조·내용 검증 완료 시각 |
| deleted_at | timestamp with time zone | 예 | — | 실제 ZIP 삭제 확인 시각 |
| error_code | character varying(100) | 예 | — | 최근 archive 오류 |
| error_message | text | 예 | — | 최근 archive 오류 설명 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_episode_archives_entry_count | CHECK | CHECK ((entry_count >= 6)) |
| episode_archives_compression_method_check | CHECK | CHECK (((compression_method)::text = 'STORE'::text)) |
| episode_archives_content_type_check | CHECK | CHECK (((content_type)::text = 'application/zip'::text)) |
| episode_archives_sha256_check | CHECK | CHECK (((sha256)::text ~ '^[0-9a-f]{64}$'::text)) |
| episode_archives_size_bytes_check | CHECK | CHECK ((size_bytes > 0)) |
| episode_archives_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('UPLOADING'::character varying)::text, ('AVAILABLE'::character varying)::text, ('DELETING'::character varying)::text, ('DELETED'::character varying)::text, ('DELETE_FAILED'::character varying)::text]))) |
| episode_archives_storage_key_check | CHECK | CHECK (((storage_key)::text !~ '^/'::text)) |
| episode_archives_storage_key_check1 | CHECK | CHECK (((storage_key)::text !~ '(^&#124;/)\.\.?(/&#124;$)'::text)) |
| episode_archives_storage_key_check2 | CHECK | CHECK (((storage_key)::text !~ '[\\]'::text)) |
| episode_archives_uncompressed_size_bytes_check | CHECK | CHECK ((uncompressed_size_bytes > 0)) |
| fk_episode_archives_10 | FK | FOREIGN KEY (episode_id, storage_node_id) REFERENCES episodes(id, storage_node_id) ON DELETE RESTRICT |
| fk_episode_archives_11 | FK | FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE RESTRICT |
| fk_episode_archives_12 | FK | FOREIGN KEY (storage_node_id) REFERENCES storage_nodes(id) ON DELETE RESTRICT |
| episode_archives_pkey | PK | PRIMARY KEY (id) |
| episode_archives_episode_id_key | UNIQUE | UNIQUE (episode_id) |
| episode_archives_storage_node_id_storage_key_key | UNIQUE | UNIQUE (storage_node_id, storage_key) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX episode_archives_episode_id_key ON public.episode_archives USING btree (episode_id);
CREATE UNIQUE INDEX episode_archives_pkey ON public.episode_archives USING btree (id);
CREATE UNIQUE INDEX episode_archives_storage_node_id_storage_key_key ON public.episode_archives USING btree (storage_node_id, storage_key);
CREATE INDEX ix_episode_archives_1 ON public.episode_archives USING btree (storage_node_id, status);
CREATE INDEX ix_episode_archives_2 ON public.episode_archives USING btree (sha256);
```

<a id="table-episode_upload_sessions"></a>

## episode_upload_sessions

S3 업로드 예약·완료·검증 세션.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | — |
| episode_id | uuid | 아니오 | — | — |
| training_job_id | uuid | 예 | — | Legacy training job reference. New standalone episode uploads leave this column NULL. |
| owner_id | uuid | 아니오 | — | — |
| umi_device_id | uuid | 아니오 | — | — |
| session_id | uuid | 아니오 | — | — |
| content_type | character varying(150) | 아니오 | — | — |
| size_bytes | bigint | 아니오 | — | — |
| sha256 | character varying(64) | 아니오 | — | — |
| metadata | jsonb | 아니오 | — | — |
| summary | jsonb | 아니오 | — | — |
| bucket_name | character varying(255) | 아니오 | — | — |
| object_key | character varying(500) | 아니오 | — | — |
| status | character varying(20) | 아니오 | — | — |
| expires_at | timestamp with time zone | 아니오 | — | — |
| completed_at | timestamp with time zone | 예 | — | — |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | — |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | — |
| row_version | bigint | 아니오 | 0 | — |
| canceled_at | timestamp with time zone | 예 | — | 미완료 업로드 세션 취소 시각 |
| quality_status | character varying(20) | 예 | — | ZIP 검증으로 확정한 VALID, WARNING, INVALID |
| validation_error_code | character varying(100) | 예 | — | 검증 또는 처리 실패 코드 |
| validation_error_message | text | 예 | — | 검증 또는 처리 실패 설명 |
| validated_at | timestamp with time zone | 예 | — | 검증 결과 확정 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_episode_upload_completion | CHECK | CHECK (((((status)::text = 'UPLOAD_PENDING'::text) AND (completed_at IS NULL) AND (canceled_at IS NULL) AND (validated_at IS NULL)) OR (((status)::text = 'VALIDATING'::text) AND (completed_at IS NOT NULL) AND (canceled_at IS NULL) AND (validated_at IS NULL)) OR (((status)::text = 'READY'::text) AND (completed_at IS NOT NULL) AND (canceled_at IS NULL) AND (validated_at IS NOT NULL) AND ((quality_status)::text = ANY (ARRAY[('VALID'::character varying)::text, ('WARNING'::character varying)::text])) AND (validation_error_code IS NULL) AND (validation_error_message IS NULL)) OR (((status)::text = ANY (ARRAY[('INVALID'::character varying)::text, ('FAILED'::character varying)::text])) AND (completed_at IS NOT NULL) AND (canceled_at IS NULL) AND (validated_at IS NOT NULL) AND (validation_error_code IS NOT NULL) AND (validation_error_message IS NOT NULL)) OR (((status)::text = 'EXPIRED'::text) AND (completed_at IS NULL) AND (canceled_at IS NULL) AND (validated_at IS NULL)) OR (((status)::text = 'CANCELLED'::text) AND (completed_at IS NULL) AND (canceled_at IS NOT NULL) AND (validated_at IS NULL)))) |
| ck_episode_upload_content_type | CHECK | CHECK (((content_type)::text = 'application/zip'::text)) |
| ck_episode_upload_sha256 | CHECK | CHECK (((sha256)::text ~ '^[0-9a-f]{64}$'::text)) |
| ck_episode_upload_size | CHECK | CHECK ((size_bytes > 0)) |
| ck_episode_upload_status | CHECK | CHECK (((status)::text = ANY (ARRAY[('UPLOAD_PENDING'::character varying)::text, ('VALIDATING'::character varying)::text, ('READY'::character varying)::text, ('INVALID'::character varying)::text, ('FAILED'::character varying)::text, ('EXPIRED'::character varying)::text, ('CANCELLED'::character varying)::text]))) |
| fk_episode_upload_device | FK | FOREIGN KEY (umi_device_id) REFERENCES devices(id) ON DELETE RESTRICT |
| fk_episode_upload_owner | FK | FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT |
| fk_episode_upload_training_job | FK | FOREIGN KEY (training_job_id) REFERENCES training_jobs(id) ON DELETE RESTRICT |
| episode_upload_sessions_pkey | PK | PRIMARY KEY (id) |
| episode_upload_sessions_episode_id_key | UNIQUE | UNIQUE (episode_id) |
| uq_episode_upload_identity | UNIQUE | UNIQUE (owner_id, umi_device_id, session_id) |
| uq_episode_upload_object | UNIQUE | UNIQUE (bucket_name, object_key) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX episode_upload_sessions_episode_id_key ON public.episode_upload_sessions USING btree (episode_id);
CREATE UNIQUE INDEX episode_upload_sessions_pkey ON public.episode_upload_sessions USING btree (id);
CREATE INDEX ix_episode_upload_expiration ON public.episode_upload_sessions USING btree (status, expires_at);
CREATE INDEX ix_episode_upload_owner_device_status ON public.episode_upload_sessions USING btree (owner_id, umi_device_id, status, created_at DESC);
CREATE UNIQUE INDEX uq_episode_upload_identity ON public.episode_upload_sessions USING btree (owner_id, umi_device_id, session_id);
CREATE UNIQUE INDEX uq_episode_upload_object ON public.episode_upload_sessions USING btree (bucket_name, object_key);
```

<a id="table-episodes"></a>

## episodes

수집 데이터 메타데이터·품질·보관 상태.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| owner_id | uuid | 아니오 | — | FK → &#96;users.id&#96; |
| umi_device_id | uuid | 아니오 | — | FK → &#96;devices.id&#96;, UMI만 허용 |
| storage_node_id | uuid | 아니오 | — | FK → &#96;storage_nodes.id&#96; |
| retention_policy_id | uuid | 아니오 | — | FK → &#96;retention_policies.id&#96; |
| skill_id | uuid | 아니오 | — | FK → &#96;skills.id&#96;, &#96;meta.json.skill_key&#96;를 서버가 해석한 값 |
| session_id | character varying(150) | 아니오 | — | UMI가 생성한 소문자 UUID v4. ZIP 파일명의 확장자를 제외한 값과 일치 |
| source | character varying(10) | 아니오 | — | &#96;SIM&#96;, &#96;REAL&#96; |
| success | boolean | 예 | — | 수행 성공 여부, 미라벨이면 NULL |
| label_status | character varying(20) | 아니오 | — | &#96;UNLABELED&#96;, &#96;LABELED&#96; |
| label_source | character varying(20) | 예 | — | &#96;UMI&#96;, &#96;USER&#96;, &#96;SERVER&#96; |
| labeled_at | timestamp with time zone | 예 | — | 라벨 확정 시각 |
| contract_version | character varying(100) | 아니오 | — | 전체 Episode archive 계약 버전 |
| meta_schema_version | character varying(50) | 아니오 | — | &#96;meta.json.schema_version&#96; |
| summary_schema_version | character varying(50) | 아니오 | — | &#96;summary.json.schema_version&#96; |
| status | character varying(30) | 아니오 | — | Episode 상태 |
| started_at | timestamp with time zone | 아니오 | — | &#96;meta.json.started_at&#96; |
| ended_at | timestamp with time zone | 아니오 | — | &#96;meta.json.ended_at&#96; |
| start_monotonic_ns | bigint | 아니오 | — | CSV &#96;t_ns&#96;와 UTC를 연결하는 기준 |
| timestamp_timebase | character varying(30) | 아니오 | — | MVP는 &#96;MONOTONIC_NS&#96; |
| first_timestamp_ns | bigint | 아니오 | — | 전체 데이터의 가장 이른 &#96;t_ns&#96; |
| last_timestamp_ns | bigint | 아니오 | — | 전체 데이터의 가장 늦은 &#96;t_ns&#96; |
| duration_ns | bigint | 아니오 | — | &#96;last_timestamp_ns - first_timestamp_ns&#96; |
| frame_count | integer | 아니오 | — | 검증된 이미지 프레임 수 |
| pose_sample_count | integer | 아니오 | — | Raw UMI archive has no pose.csv; zero until SLAM preprocessing produces poses |
| imu_sample_count | integer | 아니오 | — | accelerometer.csv and gyroscope.csv data row count sum |
| gripper_sample_count | integer | 아니오 | — | Raw UMI archive has no gripper.csv; zero until marker preprocessing produces the channel |
| missing_frame_count | integer | 아니오 | — | 누락 프레임 수 |
| tracking_gap_count | integer | 아니오 | — | 추적 중단 구간 수 |
| tracking_lost_duration_ns | bigint | 아니오 | — | 추적 중단 누적 시간 |
| quality_status | character varying(20) | 아니오 | — | &#96;PENDING&#96;, &#96;VALID&#96;, &#96;WARNING&#96;, &#96;INVALID&#96; |
| capture_metadata | jsonb | 아니오 | — | 장치·ARCore·카메라 내부 파라미터·좌표계·단위 snapshot |
| quality_summary | jsonb | 아니오 | — | 추가 결측·품질 집계 snapshot |
| validated_at | timestamp with time zone | 예 | — | 서버 archive 검증 완료 시각 |
| last_used_at | timestamp with time zone | 예 | — | 마지막 학습 사용 시각 |
| retention_expires_at | timestamp with time zone | 아니오 | — | 삭제 가능 기준 시각 |
| deleted_at | timestamp with time zone | 예 | — | 물리 ZIP 삭제 확인 시각 |
| error_code | character varying(100) | 예 | — | 최근 오류 코드 |
| error_message | text | 예 | — | 최근 오류 설명 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| row_version | bigint | 아니오 | 0 | 선택·삭제 경합 방지용 낙관적 잠금 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_episodes_gripper_sample_count | CHECK | CHECK ((gripper_sample_count >= 0)) |
| episodes_check | CHECK | CHECK (((((label_status)::text = 'LABELED'::text) AND (success IS NOT NULL) AND (label_source IS NOT NULL) AND (labeled_at IS NOT NULL)) OR (((label_status)::text = 'UNLABELED'::text) AND (success IS NULL) AND (label_source IS NULL) AND (labeled_at IS NULL)))) |
| episodes_check1 | CHECK | CHECK ((ended_at >= started_at)) |
| episodes_check2 | CHECK | CHECK ((first_timestamp_ns >= start_monotonic_ns)) |
| episodes_check3 | CHECK | CHECK ((last_timestamp_ns >= first_timestamp_ns)) |
| episodes_check4 | CHECK | CHECK ((duration_ns = (last_timestamp_ns - first_timestamp_ns))) |
| episodes_frame_count_check | CHECK | CHECK ((frame_count >= 1)) |
| episodes_imu_sample_count_check | CHECK | CHECK ((imu_sample_count >= 1)) |
| episodes_label_source_check | CHECK | CHECK (((label_source)::text = ANY (ARRAY[('UMI'::character varying)::text, ('USER'::character varying)::text, ('SERVER'::character varying)::text]))) |
| episodes_label_status_check | CHECK | CHECK (((label_status)::text = ANY (ARRAY[('UNLABELED'::character varying)::text, ('LABELED'::character varying)::text]))) |
| episodes_missing_frame_count_check | CHECK | CHECK ((missing_frame_count >= 0)) |
| episodes_pose_sample_count_check | CHECK | CHECK ((pose_sample_count >= 0)) |
| episodes_quality_status_check | CHECK | CHECK (((quality_status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('VALID'::character varying)::text, ('WARNING'::character varying)::text, ('INVALID'::character varying)::text]))) |
| episodes_source_check | CHECK | CHECK (((source)::text = ANY (ARRAY[('SIM'::character varying)::text, ('REAL'::character varying)::text]))) |
| episodes_start_monotonic_ns_check | CHECK | CHECK ((start_monotonic_ns >= 0)) |
| episodes_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('UPLOADING'::character varying)::text, ('VALIDATING'::character varying)::text, ('READY'::character varying)::text, ('INVALID'::character varying)::text, ('DELETING'::character varying)::text, ('DELETED'::character varying)::text, ('DELETE_FAILED'::character varying)::text]))) |
| episodes_timestamp_timebase_check | CHECK | CHECK (((timestamp_timebase)::text = 'MONOTONIC_NS'::text)) |
| episodes_tracking_gap_count_check | CHECK | CHECK ((tracking_gap_count >= 0)) |
| episodes_tracking_lost_duration_ns_check | CHECK | CHECK ((tracking_lost_duration_ns >= 0)) |
| fk_episodes_5 | FK | FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT |
| fk_episodes_6 | FK | FOREIGN KEY (umi_device_id) REFERENCES devices(id) ON DELETE RESTRICT |
| fk_episodes_7 | FK | FOREIGN KEY (storage_node_id) REFERENCES storage_nodes(id) ON DELETE RESTRICT |
| fk_episodes_8 | FK | FOREIGN KEY (retention_policy_id) REFERENCES retention_policies(id) ON DELETE RESTRICT |
| fk_episodes_9 | FK | FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT |
| episodes_pkey | PK | PRIMARY KEY (id) |
| episodes_id_storage_node_id_key | UNIQUE | UNIQUE (id, storage_node_id) |
| episodes_owner_id_umi_device_id_session_id_key | UNIQUE | UNIQUE (owner_id, umi_device_id, session_id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX episodes_id_storage_node_id_key ON public.episodes USING btree (id, storage_node_id);
CREATE UNIQUE INDEX episodes_owner_id_umi_device_id_session_id_key ON public.episodes USING btree (owner_id, umi_device_id, session_id);
CREATE UNIQUE INDEX episodes_pkey ON public.episodes USING btree (id);
CREATE INDEX ix_episodes_1 ON public.episodes USING btree (owner_id, status, started_at DESC);
CREATE INDEX ix_episodes_2 ON public.episodes USING btree (umi_device_id, status, started_at DESC);
CREATE INDEX ix_episodes_3 ON public.episodes USING btree (storage_node_id, status);
CREATE INDEX ix_episodes_4 ON public.episodes USING btree (status, retention_expires_at);
CREATE INDEX ix_episodes_5 ON public.episodes USING btree (skill_id, source, label_status, success, contract_version, status);
CREATE INDEX ix_episodes_6 ON public.episodes USING btree (quality_status, started_at DESC);
```

<a id="table-idempotency_keys"></a>

## idempotency_keys

멱등 요청 및 응답 기록.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| actor_type | character varying(30) | 아니오 | — | &#96;USER&#96;, &#96;UMI&#96;, &#96;GPU&#96;, &#96;JETSON&#96;, &#96;INTERNAL&#96; |
| actor_id | character varying(150) | 아니오 | — | 주체 ID |
| http_method | character varying(10) | 아니오 | — | 요청 method |
| request_path | character varying(500) | 아니오 | — | path template 또는 정규화 path |
| idempotency_key | character varying(200) | 아니오 | — | 클라이언트 전달 key |
| request_hash | character varying(64) | 아니오 | — | 정규화 요청 본문 hash |
| response_status | integer | 예 | — | 최초 응답 HTTP status |
| response_body | jsonb | 예 | — | 재사용할 응답 |
| resource_type | character varying(100) | 예 | — | 생성된 리소스 유형 |
| resource_id | character varying(150) | 예 | — | 생성된 리소스 ID |
| expires_at | timestamp with time zone | 아니오 | — | 중복 보장 만료 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 최초 처리 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| idempotency_keys_actor_type_check | CHECK | CHECK (((actor_type)::text = ANY (ARRAY[('USER'::character varying)::text, ('DEVICE'::character varying)::text, ('UMI'::character varying)::text, ('GPU'::character varying)::text, ('JETSON'::character varying)::text, ('INTERNAL'::character varying)::text]))) |
| idempotency_keys_request_hash_check | CHECK | CHECK (((request_hash)::text ~ '^[0-9a-f]{64}$'::text)) |
| idempotency_keys_pkey | PK | PRIMARY KEY (id) |
| idempotency_keys_actor_type_actor_id_http_method_request_pa_key | UNIQUE | UNIQUE (actor_type, actor_id, http_method, request_path, idempotency_key) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX idempotency_keys_actor_type_actor_id_http_method_request_pa_key ON public.idempotency_keys USING btree (actor_type, actor_id, http_method, request_path, idempotency_key);
CREATE UNIQUE INDEX idempotency_keys_pkey ON public.idempotency_keys USING btree (id);
CREATE INDEX ix_idempotency_keys_1 ON public.idempotency_keys USING btree (expires_at);
```

<a id="table-outbox_events"></a>

## outbox_events

트랜잭션 기반 발행 대기 이벤트.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| event_id | character varying(150) | 아니오 | — | 외부 중복 제거 ID |
| aggregate_type | character varying(100) | 아니오 | — | &#96;EPISODE&#96;, &#96;TRAINING_JOB&#96; 등 |
| aggregate_id | character varying(150) | 아니오 | — | 대상 리소스 ID |
| event_type | character varying(150) | 아니오 | — | 이벤트 종류 |
| payload | jsonb | 아니오 | — | secret·binary·서명 URL 제외 |
| status | character varying(20) | 아니오 | — | &#96;PENDING&#96;, &#96;PUBLISHED&#96;, &#96;FAILED&#96; |
| attempt | integer | 아니오 | — | 발행 시도 횟수 |
| available_at | timestamp with time zone | 아니오 | — | 다음 처리 가능 시각 |
| published_at | timestamp with time zone | 예 | — | 발행 완료 |
| last_error | text | 예 | — | 마지막 오류 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| locked_by | character varying(100) | 예 | — | — |
| locked_at | timestamp with time zone | 예 | — | — |
| lease_expires_at | timestamp with time zone | 예 | — | — |
| aggregate_sequence | bigint | 예 | — | — |
| row_version | bigint | 아니오 | 0 | — |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_outbox_attempt | CHECK | CHECK ((attempt >= 0)) |
| ck_outbox_claim_consistency | CHECK | CHECK (((((status)::text = 'PROCESSING'::text) AND (locked_by IS NOT NULL) AND (locked_at IS NOT NULL) AND (lease_expires_at IS NOT NULL)) OR (((status)::text <> 'PROCESSING'::text) AND (locked_by IS NULL) AND (locked_at IS NULL) AND (lease_expires_at IS NULL)))) |
| ck_outbox_sequence | CHECK | CHECK (((aggregate_sequence IS NULL) OR (aggregate_sequence >= 1))) |
| ck_outbox_status | CHECK | CHECK (((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('PROCESSING'::character varying)::text, ('PUBLISHED'::character varying)::text, ('FAILED'::character varying)::text, ('DEAD'::character varying)::text]))) |
| outbox_events_pkey | PK | PRIMARY KEY (id) |
| outbox_events_event_id_key | UNIQUE | UNIQUE (event_id) |
| uq_outbox_aggregate_sequence | UNIQUE | UNIQUE (aggregate_type, aggregate_id, aggregate_sequence) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_outbox_events_1 ON public.outbox_events USING btree (status, available_at);
CREATE INDEX ix_outbox_events_2 ON public.outbox_events USING btree (aggregate_type, aggregate_id, created_at);
CREATE INDEX ix_outbox_expired_processing ON public.outbox_events USING btree (lease_expires_at) WHERE ((status)::text = 'PROCESSING'::text);
CREATE INDEX ix_outbox_publishable ON public.outbox_events USING btree (available_at, created_at) WHERE ((status)::text = ANY (ARRAY[('PENDING'::character varying)::text, ('FAILED'::character varying)::text]));
CREATE INDEX ix_outbox_published_cleanup ON public.outbox_events USING btree (published_at) WHERE ((status)::text = 'PUBLISHED'::text);
CREATE UNIQUE INDEX outbox_events_event_id_key ON public.outbox_events USING btree (event_id);
CREATE UNIQUE INDEX outbox_events_pkey ON public.outbox_events USING btree (id);
CREATE UNIQUE INDEX uq_outbox_aggregate_sequence ON public.outbox_events USING btree (aggregate_type, aggregate_id, aggregate_sequence);
```

<a id="table-refresh_sessions"></a>

## refresh_sessions

Refresh 세션 영속 테이블(현재 로그인 경로는 Redis 사용).

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| user_id | uuid | 아니오 | — | FK → &#96;users.id&#96; |
| token_hash | character varying(64) | 아니오 | — | Refresh Token 원문 저장 금지 |
| expires_at | timestamp with time zone | 아니오 | — | 만료 시각 |
| revoked_at | timestamp with time zone | 예 | — | 폐기 시각 |
| replaced_by_session_id | uuid | 예 | — | 회전된 새 세션 FK |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| last_used_at | timestamp with time zone | 예 | — | 마지막 갱신 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| refresh_sessions_token_hash_check | CHECK | CHECK (((token_hash)::text ~ '^[0-9a-f]{64}$'::text)) |
| fk_refresh_sessions_1 | FK | FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT |
| fk_refresh_sessions_2 | FK | FOREIGN KEY (replaced_by_session_id) REFERENCES refresh_sessions(id) ON DELETE RESTRICT |
| refresh_sessions_pkey | PK | PRIMARY KEY (id) |
| refresh_sessions_token_hash_key | UNIQUE | UNIQUE (token_hash) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_refresh_sessions_1 ON public.refresh_sessions USING btree (user_id, expires_at);
CREATE UNIQUE INDEX refresh_sessions_pkey ON public.refresh_sessions USING btree (id);
CREATE UNIQUE INDEX refresh_sessions_token_hash_key ON public.refresh_sessions USING btree (token_hash);
```

<a id="table-retention_policies"></a>

## retention_policies

데이터 보관 정책.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| name | character varying(100) | 아니오 | — | 정책 이름 |
| default_retention_days | integer | 아니오 | — | 검증 완료 후 기본 보관 일수 |
| post_training_grace_days | integer | 아니오 | — | 마지막 학습 종료 후 유예 일수 |
| max_retention_days | integer | 아니오 | — | 사용자 연장 최대 일수 |
| max_delete_attempts | integer | 아니오 | — | 자동 삭제 최대 시도 횟수 |
| allow_manual_extension | boolean | 아니오 | — | 사용자 연장 허용 |
| allow_manual_purge | boolean | 아니오 | — | 사용자 조기 삭제 허용 |
| is_default | boolean | 아니오 | — | 기본 정책 여부 |
| active | boolean | 아니오 | — | 적용 가능 여부 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| retention_policies_check | CHECK | CHECK ((max_retention_days >= default_retention_days)) |
| retention_policies_default_retention_days_check | CHECK | CHECK ((default_retention_days >= 0)) |
| retention_policies_max_delete_attempts_check | CHECK | CHECK ((max_delete_attempts >= 1)) |
| retention_policies_post_training_grace_days_check | CHECK | CHECK ((post_training_grace_days >= 0)) |
| retention_policies_pkey | PK | PRIMARY KEY (id) |
| retention_policies_name_key | UNIQUE | UNIQUE (name) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX retention_policies_name_key ON public.retention_policies USING btree (name);
CREATE UNIQUE INDEX retention_policies_pkey ON public.retention_policies USING btree (id);
CREATE UNIQUE INDEX uq_retention_policy_default ON public.retention_policies USING btree (is_default) WHERE (is_default = true);
```

<a id="table-robot_executions"></a>

## robot_executions

로봇 실행 이력.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | — |
| owner_id | uuid | 아니오 | — | — |
| robot_id | uuid | 아니오 | — | — |
| task_version_id | uuid | 아니오 | — | — |
| deployment_id | uuid | 아니오 | — | — |
| status | character varying(30) | 아니오 | — | — |
| current_step | integer | 예 | — | — |
| total_steps | integer | 아니오 | — | — |
| progress_percent | numeric(5,2) | 아니오 | 0 | — |
| last_report_id | character varying(150) | 예 | — | — |
| last_report_sequence | bigint | 아니오 | 0 | — |
| last_report_hash | character varying(64) | 예 | — | 동일 reportId/sequence 재전송의 본문 동일성 확인용 SHA-256 |
| error_code | character varying(100) | 예 | — | — |
| error_message | text | 예 | — | — |
| recovery_action | character varying(40) | 예 | — | 오류 발생 시 FE가 안내할 복구 행동 코드 |
| requested_at | timestamp with time zone | 아니오 | now() | — |
| started_at | timestamp with time zone | 예 | — | — |
| finished_at | timestamp with time zone | 예 | — | — |
| updated_at | timestamp with time zone | 아니오 | now() | — |
| row_version | bigint | 아니오 | 0 | — |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_robot_executions_current_step | CHECK | CHECK (((current_step IS NULL) OR (current_step >= 1))) |
| ck_robot_executions_error | CHECK | CHECK (((((status)::text = 'FAILED'::text) AND (error_code IS NOT NULL) AND (error_message IS NOT NULL)) OR ((status)::text <> 'FAILED'::text))) |
| ck_robot_executions_finished_at | CHECK | CHECK (((((status)::text = ANY (ARRAY[('CANCELED'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text])) AND (finished_at IS NOT NULL)) OR (((status)::text <> ALL (ARRAY[('CANCELED'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text])) AND (finished_at IS NULL)))) |
| ck_robot_executions_progress | CHECK | CHECK (((progress_percent >= (0)::numeric) AND (progress_percent <= (100)::numeric))) |
| ck_robot_executions_report_consistency | CHECK | CHECK ((((last_report_sequence = 0) AND (last_report_id IS NULL) AND (last_report_hash IS NULL)) OR ((last_report_sequence > 0) AND (last_report_id IS NOT NULL) AND ((last_report_hash)::text ~ '^[0-9a-f]{64}$'::text)))) |
| ck_robot_executions_report_sequence | CHECK | CHECK ((last_report_sequence >= 0)) |
| ck_robot_executions_started_at | CHECK | CHECK ((((status)::text = ANY (ARRAY[('REQUESTED'::character varying)::text, ('CANCELED'::character varying)::text])) OR (started_at IS NOT NULL))) |
| ck_robot_executions_status | CHECK | CHECK (((status)::text = ANY (ARRAY[('REQUESTED'::character varying)::text, ('STARTING'::character varying)::text, ('RUNNING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text, ('CANCELED'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text, ('STATUS_UNKNOWN'::character varying)::text]))) |
| ck_robot_executions_step_range | CHECK | CHECK (((current_step IS NULL) OR (current_step <= total_steps))) |
| ck_robot_executions_time_order | CHECK | CHECK ((((started_at IS NULL) OR (started_at >= requested_at)) AND ((finished_at IS NULL) OR (finished_at >= COALESCE(started_at, requested_at))) AND (updated_at >= requested_at))) |
| ck_robot_executions_total_steps | CHECK | CHECK ((total_steps >= 1)) |
| fk_robot_executions_deployment | FK | FOREIGN KEY (deployment_id) REFERENCES deployments(id) ON DELETE RESTRICT |
| fk_robot_executions_deployment_context | FK | FOREIGN KEY (deployment_id, robot_id, task_version_id) REFERENCES deployments(id, robot_id, task_version_id) ON DELETE RESTRICT |
| fk_robot_executions_owner | FK | FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT |
| fk_robot_executions_robot | FK | FOREIGN KEY (robot_id) REFERENCES robots(id) ON DELETE RESTRICT |
| fk_robot_executions_task_version | FK | FOREIGN KEY (task_version_id) REFERENCES task_versions(id) ON DELETE RESTRICT |
| robot_executions_pkey | PK | PRIMARY KEY (id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_robot_executions_deployment ON public.robot_executions USING btree (deployment_id);
CREATE INDEX ix_robot_executions_owner_requested ON public.robot_executions USING btree (owner_id, requested_at DESC, id DESC);
CREATE INDEX ix_robot_executions_robot_requested ON public.robot_executions USING btree (robot_id, requested_at DESC, id DESC);
CREATE INDEX ix_robot_executions_robot_status ON public.robot_executions USING btree (robot_id, status, requested_at DESC);
CREATE INDEX ix_robot_executions_task_version ON public.robot_executions USING btree (task_version_id);
CREATE UNIQUE INDEX robot_executions_pkey ON public.robot_executions USING btree (id);
CREATE UNIQUE INDEX uq_robot_active_execution ON public.robot_executions USING btree (robot_id) WHERE ((status)::text = ANY (ARRAY[('REQUESTED'::character varying)::text, ('STARTING'::character varying)::text, ('RUNNING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text, ('STATUS_UNKNOWN'::character varying)::text]));
CREATE UNIQUE INDEX uq_robot_execution_report_id ON public.robot_executions USING btree (robot_id, last_report_id) WHERE (last_report_id IS NOT NULL);
```

<a id="table-robot_profiles"></a>

## robot_profiles

로봇 입출력·런타임 호환 계약.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| name | character varying(150) | 아니오 | — | 프로파일 이름 |
| contract_version | character varying(100) | 아니오 | — | 입력·출력 계약 버전 |
| architecture | character varying(100) | 아니오 | — | 로봇/모델 구조 |
| runtime | character varying(100) | 아니오 | — | PyTorch, TensorRT 등 |
| runtime_version | character varying(100) | 아니오 | — | 런타임 버전 |
| camera_contract | jsonb | 아니오 | — | 카메라 이름·입력 규격 |
| state_contract | jsonb | 아니오 | — | Trainer-defined observation schema validated against CKPT inputSchema. |
| action_contract | jsonb | 아니오 | — | Trainer-defined actionSpec, runtimeSpec, and outputSchema validated against CKPT metadata. |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| robot_profiles_pkey | PK | PRIMARY KEY (id) |
| robot_profiles_name_contract_version_runtime_runtime_versio_key | UNIQUE | UNIQUE (name, contract_version, runtime, runtime_version) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE UNIQUE INDEX robot_profiles_name_contract_version_runtime_runtime_versio_key ON public.robot_profiles USING btree (name, contract_version, runtime, runtime_version);
CREATE UNIQUE INDEX robot_profiles_pkey ON public.robot_profiles USING btree (id);
```

<a id="table-robot_skill_deployments"></a>

## robot_skill_deployments

로봇별 스킬 배포 참조.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| robot_id | uuid | 아니오 | — | FK → &#96;robots.id&#96; |
| skill_id | uuid | 아니오 | — | FK → &#96;skills.id&#96; |
| skill_version_id | uuid | 아니오 | — | FK → &#96;skill_versions.id&#96; |
| deployment_id | uuid | 아니오 | — | FK → &#96;deployments.id&#96;, 이 상태를 만든 작업 |
| status | character varying(20) | 아니오 | — | &#96;ACTIVE&#96;, &#96;RETIRED&#96; |
| deployed_at | timestamp with time zone | 아니오 | — | 활성화 시각 |
| retired_at | timestamp with time zone | 예 | — | 은퇴 시각 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| robot_skill_deployments_check | CHECK | CHECK (((((status)::text = 'ACTIVE'::text) AND (retired_at IS NULL)) OR (((status)::text = 'RETIRED'::text) AND (retired_at IS NOT NULL)))) |
| robot_skill_deployments_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('RETIRED'::character varying)::text]))) |
| fk_robot_skill_deployments_53 | FK | FOREIGN KEY (skill_version_id, skill_id) REFERENCES skill_versions(id, skill_id) ON DELETE RESTRICT |
| fk_robot_skill_deployments_54 | FK | FOREIGN KEY (robot_id) REFERENCES robots(id) ON DELETE RESTRICT |
| fk_robot_skill_deployments_55 | FK | FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT |
| fk_robot_skill_deployments_56 | FK | FOREIGN KEY (skill_version_id) REFERENCES skill_versions(id) ON DELETE RESTRICT |
| fk_robot_skill_deployments_57 | FK | FOREIGN KEY (deployment_id) REFERENCES deployments(id) ON DELETE RESTRICT |
| robot_skill_deployments_pkey | PK | PRIMARY KEY (id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_robot_skill_deployments_1 ON public.robot_skill_deployments USING btree (robot_id, deployed_at DESC);
CREATE INDEX ix_robot_skill_deployments_2 ON public.robot_skill_deployments USING btree (skill_version_id);
CREATE UNIQUE INDEX robot_skill_deployments_pkey ON public.robot_skill_deployments USING btree (id);
CREATE UNIQUE INDEX uq_robot_active_skill ON public.robot_skill_deployments USING btree (robot_id, skill_id) WHERE ((status)::text = 'ACTIVE'::text);
```

<a id="table-robots"></a>

## robots

로봇과 Jetson 연결.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| owner_id | uuid | 아니오 | — | FK → &#96;users.id&#96; |
| jetson_device_id | uuid | 아니오 | — | FK → &#96;devices.id&#96;, JETSON만 허용 |
| robot_profile_id | uuid | 아니오 | — | FK → &#96;robot_profiles.id&#96; |
| name | character varying(150) | 아니오 | — | 표시 이름 |
| connection_status | character varying(20) | 아니오 | — | &#96;ONLINE&#96;, &#96;OFFLINE&#96; |
| current_deployment_id | uuid | 예 | — | FK → &#96;deployments.id&#96;, migration 후 추가 |
| last_seen_at | timestamp with time zone | 예 | — | 마지막 연결 확인 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| row_version | bigint | 아니오 | 0 | 배포 적용 낙관적 잠금 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| robots_connection_status_check | CHECK | CHECK (((connection_status)::text = ANY (ARRAY[('ONLINE'::character varying)::text, ('OFFLINE'::character varying)::text]))) |
| fk_robots_42 | FK | FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT |
| fk_robots_43 | FK | FOREIGN KEY (jetson_device_id) REFERENCES devices(id) ON DELETE RESTRICT |
| fk_robots_44 | FK | FOREIGN KEY (robot_profile_id) REFERENCES robot_profiles(id) ON DELETE RESTRICT |
| fk_robots_45 | FK | FOREIGN KEY (current_deployment_id) REFERENCES deployments(id) ON DELETE RESTRICT |
| robots_pkey | PK | PRIMARY KEY (id) |
| robots_jetson_device_id_key | UNIQUE | UNIQUE (jetson_device_id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_robots_1 ON public.robots USING btree (owner_id, created_at DESC);
CREATE INDEX ix_robots_2 ON public.robots USING btree (connection_status, last_seen_at);
CREATE UNIQUE INDEX robots_jetson_device_id_key ON public.robots USING btree (jetson_device_id);
CREATE UNIQUE INDEX robots_pkey ON public.robots USING btree (id);
```

<a id="table-skill_versions"></a>

## skill_versions

모델 버전·체크포인트·게이트 결과.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| skill_id | uuid | 아니오 | — | FK → &#96;skills.id&#96; |
| training_job_id | uuid | 아니오 | — | FK → &#96;training_jobs.id&#96; |
| robot_profile_id | uuid | 아니오 | — | FK → &#96;robot_profiles.id&#96; |
| version | integer | 아니오 | — | Skill 범위 증가 번호 |
| status | character varying(30) | 아니오 | — | &#96;UPLOADING&#96;, &#96;VALIDATING&#96;, &#96;READY&#96;, &#96;FAILED&#96;, &#96;ARCHIVED&#96; |
| checkpoint_bucket | character varying(255) | 아니오 | — | 내부 S3 bucket |
| checkpoint_key | character varying(700) | 아니오 | — | 서버 생성 불변 S3 key, &#96;ckpt/{owner_id}/{skill_key}/v{version}/{skill_key}_{학습완료UTC}.ckpt&#96; |
| checkpoint_sha256 | character varying(64) | 아니오 | — | CKPT SHA-256 |
| checkpoint_bytes | bigint | 아니오 | — | CKPT 크기 |
| architecture | character varying(100) | 아니오 | — | 모델 architecture |
| framework | character varying(100) | 아니오 | — | 프레임워크 |
| framework_version | character varying(100) | 아니오 | — | 프레임워크 버전 |
| contract_version | character varying(100) | 아니오 | — | 모델 입출력 계약 |
| action_space | character varying(30) | 아니오 | — | &#96;JOINT_ABSOLUTE&#96;, &#96;JOINT_DELTA&#96; |
| n_params | bigint | 아니오 | — | 모델 파라미터 수 |
| control_rate_hz | numeric(10,3) | 아니오 | — | 제어 주기 |
| camera_names | jsonb | 아니오 | — | 카메라 이름 배열 |
| input_schema | jsonb | 아니오 | — | 입력 계약 |
| output_schema | jsonb | 아니오 | — | 출력 계약 |
| gate_metric | character varying(100) | 예 | — | 현재 &#96;rollout_success&#96; |
| gate_n | integer | 예 | — | 평가 표본 수 |
| gate_runs | integer | 예 | — | 독립 학습·평가 반복 수 |
| gate_value | double precision | 예 | — | 게이트 점추정 값 |
| gate_ci_low | double precision | 예 | — | 신뢰구간 하한 |
| gate_ci_high | double precision | 예 | — | 신뢰구간 상한 |
| gate_measured_at | timestamp with time zone | 예 | — | 게이트 측정 시각 |
| gate_passed | boolean | 아니오 | COALESCE((((gate_metric)::text = 'rollout_success'::text) AND (gate_n >= 100) AND (gate_runs >= 3) AND (gate_value > (0.20)::double precision) AND (gate_ci_low > (0.20)::double precision)), false) | 조건식으로 계산되는 generated column |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 버전 생성 시각 |
| validated_at | timestamp with time zone | 예 | — | READY/FAILED 검증 시각 |
| archived_at | timestamp with time zone | 예 | — | 보관 처리 시각 |
| parent_skill_version_id | uuid | 예 | — | — |
| action_spec | jsonb | 아니오 | '{}'::jsonb | Trainer-defined action tensor contract. EC2 stores and forwards this JSON without numeric validation. |
| runtime_spec | jsonb | 아니오 | '{}'::jsonb | Runtime conversion and execution contract. EC2 stores and forwards this JSON without interpreting its fields. |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_skill_versions_gate_complete | CHECK | CHECK ((((gate_metric IS NULL) AND (gate_n IS NULL) AND (gate_runs IS NULL) AND (gate_value IS NULL) AND (gate_ci_low IS NULL) AND (gate_ci_high IS NULL) AND (gate_measured_at IS NULL)) OR ((gate_metric IS NOT NULL) AND (gate_n IS NOT NULL) AND (gate_runs IS NOT NULL) AND (gate_value IS NOT NULL) AND (gate_ci_low IS NOT NULL) AND (gate_ci_high IS NOT NULL) AND (gate_measured_at IS NOT NULL)))) |
| skill_versions_action_space_check | CHECK | CHECK (((action_space)::text = ANY ((ARRAY['JOINT_ABSOLUTE'::character varying, 'JOINT_DELTA'::character varying, 'EEF_RELATIVE_ROT6D'::character varying])::text[]))) |
| skill_versions_check | CHECK | CHECK (((gate_ci_low IS NULL) OR (gate_ci_high IS NULL) OR (gate_ci_low <= gate_ci_high))) |
| skill_versions_checkpoint_bytes_check | CHECK | CHECK ((checkpoint_bytes >= 0)) |
| skill_versions_checkpoint_key_check | CHECK | CHECK (((checkpoint_key)::text !~ '^/'::text)) |
| skill_versions_checkpoint_key_check1 | CHECK | CHECK ((octet_length((checkpoint_key)::text) <= 1024)) |
| skill_versions_checkpoint_sha256_check | CHECK | CHECK (((checkpoint_sha256)::text ~ '^[0-9a-f]{64}$'::text)) |
| skill_versions_gate_ci_high_check | CHECK | CHECK (((gate_ci_high IS NULL) OR ((gate_ci_high >= (0)::double precision) AND (gate_ci_high <= (1)::double precision)))) |
| skill_versions_gate_ci_low_check | CHECK | CHECK (((gate_ci_low IS NULL) OR ((gate_ci_low >= (0)::double precision) AND (gate_ci_low <= (1)::double precision)))) |
| skill_versions_gate_n_check | CHECK | CHECK (((gate_n IS NULL) OR (gate_n > 0))) |
| skill_versions_gate_runs_check | CHECK | CHECK (((gate_runs IS NULL) OR (gate_runs > 0))) |
| skill_versions_gate_value_check | CHECK | CHECK (((gate_value IS NULL) OR ((gate_value >= (0)::double precision) AND (gate_value <= (1)::double precision)))) |
| skill_versions_n_params_check | CHECK | CHECK ((n_params > 0)) |
| skill_versions_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('UPLOADING'::character varying)::text, ('VALIDATING'::character varying)::text, ('READY'::character varying)::text, ('FAILED'::character varying)::text, ('ARCHIVED'::character varying)::text]))) |
| skill_versions_version_check | CHECK | CHECK ((version >= 1)) |
| fk_skill_versions_25 | FK | FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT |
| fk_skill_versions_26 | FK | FOREIGN KEY (training_job_id) REFERENCES training_jobs(id) ON DELETE RESTRICT |
| fk_skill_versions_28 | FK | FOREIGN KEY (robot_profile_id) REFERENCES robot_profiles(id) ON DELETE RESTRICT |
| fk_skill_versions_parent | FK | FOREIGN KEY (parent_skill_version_id) REFERENCES skill_versions(id) ON DELETE RESTRICT |
| skill_versions_pkey | PK | PRIMARY KEY (id) |
| skill_versions_checkpoint_bucket_checkpoint_key_key | UNIQUE | UNIQUE (checkpoint_bucket, checkpoint_key) |
| skill_versions_id_gate_passed_key | UNIQUE | UNIQUE (id, gate_passed) |
| skill_versions_id_skill_id_key | UNIQUE | UNIQUE (id, skill_id) |
| skill_versions_skill_id_version_key | UNIQUE | UNIQUE (skill_id, version) |
| skill_versions_training_job_id_key | UNIQUE | UNIQUE (training_job_id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_skill_versions_1 ON public.skill_versions USING btree (skill_id, status, version DESC);
CREATE INDEX ix_skill_versions_2 ON public.skill_versions USING btree (robot_profile_id, status);
CREATE INDEX ix_skill_versions_parent ON public.skill_versions USING btree (parent_skill_version_id) WHERE (parent_skill_version_id IS NOT NULL);
CREATE UNIQUE INDEX skill_versions_checkpoint_bucket_checkpoint_key_key ON public.skill_versions USING btree (checkpoint_bucket, checkpoint_key);
CREATE UNIQUE INDEX skill_versions_id_gate_passed_key ON public.skill_versions USING btree (id, gate_passed);
CREATE UNIQUE INDEX skill_versions_id_skill_id_key ON public.skill_versions USING btree (id, skill_id);
CREATE UNIQUE INDEX skill_versions_pkey ON public.skill_versions USING btree (id);
CREATE UNIQUE INDEX skill_versions_skill_id_version_key ON public.skill_versions USING btree (skill_id, version);
CREATE UNIQUE INDEX skill_versions_training_job_id_key ON public.skill_versions USING btree (training_job_id);
```

<a id="table-skills"></a>

## skills

기본·사용자 정의 스킬.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| skill_key | character varying(100) | 아니오 | — | FE·BE·AI 공통의 전역 불변 key |
| tier | character varying(20) | 아니오 | — | &#96;TUTORIAL&#96;, &#96;DEMO&#96; |
| status | character varying(30) | 아니오 | — | 스킬 카탈로그 수명주기 상태 |
| display_name | character varying(150) | 아니오 | — | 표시 이름 |
| description | text | 예 | — | 설명 |
| persona | text | 예 | — | DEMO 스킬 대상 페르소나 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| owner_id | uuid | 예 | — | 커스텀 스킬 소유 사용자. NULL이면 시스템 공통 스킬 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_skills_owner_scope | CHECK | CHECK ((((owner_id IS NULL) AND ((tier)::text = ANY (ARRAY[('TUTORIAL'::character varying)::text, ('DEMO'::character varying)::text]))) OR ((owner_id IS NOT NULL) AND ((tier)::text = 'CUSTOM'::text)))) |
| ck_skills_tier | CHECK | CHECK (((tier)::text = ANY (ARRAY[('TUTORIAL'::character varying)::text, ('DEMO'::character varying)::text, ('CUSTOM'::character varying)::text]))) |
| skills_check | CHECK | CHECK ((((tier)::text <> 'DEMO'::text) OR (persona IS NOT NULL))) |
| skills_skill_key_check | CHECK | CHECK (((skill_key)::text ~ '^[a-z0-9][a-z0-9_-]{0,99}$'::text)) |
| skills_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('PLANNED'::character varying)::text, ('COLLECTING'::character varying)::text, ('TRAINING'::character varying)::text, ('GATE_FAILED'::character varying)::text, ('DEPLOYED'::character varying)::text]))) |
| fk_skills_owner | FK | FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT |
| skills_pkey | PK | PRIMARY KEY (id) |
| skills_skill_key_key | UNIQUE | UNIQUE (skill_key) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_skills_1 ON public.skills USING btree (tier, status);
CREATE INDEX ix_skills_owner_created ON public.skills USING btree (owner_id, created_at DESC) WHERE (owner_id IS NOT NULL);
CREATE UNIQUE INDEX skills_pkey ON public.skills USING btree (id);
CREATE UNIQUE INDEX skills_skill_key_key ON public.skills USING btree (skill_key);
CREATE UNIQUE INDEX uq_skills_owner_display_name_lower ON public.skills USING btree (owner_id, lower((display_name)::text)) WHERE (owner_id IS NOT NULL);
```

<a id="table-storage_nodes"></a>

## storage_nodes

저장 노드 등록 및 영속 메타데이터.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| worker_id | character varying(100) | 아니오 | — | service token의 worker 범위 |
| node_key | character varying(100) | 아니오 | — | worker 내부 논리 노드 키 |
| name | character varying(150) | 아니오 | — | 운영 표시 이름 |
| storage_type | character varying(20) | 아니오 | — | 현재 &#96;LOCAL&#96;만 허용 |
| status | character varying(20) | 아니오 | — | &#96;ONLINE&#96;, &#96;OFFLINE&#96;, &#96;DRAINING&#96; |
| capacity_bytes | bigint | 아니오 | — | 0 이상 |
| available_bytes | bigint | 아니오 | — | 0 이상, capacity 이하 |
| last_seen_at | timestamp with time zone | 예 | — | heartbeat 수신 시각 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| row_version | bigint | 아니오 | 0 | 낙관적 잠금, 기본 0 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| storage_nodes_capacity_bytes_check | CHECK | CHECK ((capacity_bytes >= 0)) |
| storage_nodes_check | CHECK | CHECK (((available_bytes >= 0) AND (available_bytes <= capacity_bytes))) |
| storage_nodes_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('ONLINE'::character varying)::text, ('OFFLINE'::character varying)::text, ('DRAINING'::character varying)::text]))) |
| storage_nodes_storage_type_check | CHECK | CHECK (((storage_type)::text = ANY (ARRAY[('LOCAL'::character varying)::text, ('S3'::character varying)::text]))) |
| storage_nodes_pkey | PK | PRIMARY KEY (id) |
| storage_nodes_worker_id_key | UNIQUE | UNIQUE (worker_id) |
| storage_nodes_worker_id_node_key_key | UNIQUE | UNIQUE (worker_id, node_key) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_storage_nodes_1 ON public.storage_nodes USING btree (status, last_seen_at);
CREATE UNIQUE INDEX storage_nodes_pkey ON public.storage_nodes USING btree (id);
CREATE UNIQUE INDEX storage_nodes_worker_id_key ON public.storage_nodes USING btree (worker_id);
CREATE UNIQUE INDEX storage_nodes_worker_id_node_key_key ON public.storage_nodes USING btree (worker_id, node_key);
```

<a id="table-task_steps"></a>

## task_steps

작업 단계와 중첩 구조.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| task_version_id | uuid | 아니오 | — | FK → &#96;task_versions.id&#96; |
| skill_version_id | uuid | 예 | — | FK → &#96;skill_versions.id&#96; |
| step_order | integer | 아니오 | — | 1부터 연속 |
| parameters | jsonb | 아니오 | '{}'::jsonb | 기본 &#96;{}&#96; |
| timeout_seconds | integer | 예 | — | 1 이상 |
| parent_step_id | uuid | 예 | — | REPEAT 단계에 포함된 경우 부모 task_steps.id |
| step_type | character varying(20) | 아니오 | 'SKILL'::character varying | SKILL, MOTOR, REPEAT |
| repeat_count | integer | 예 | — | REPEAT 단계 반복 횟수 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_task_steps_not_self_parent | CHECK | CHECK (((parent_step_id IS NULL) OR (parent_step_id <> id))) |
| ck_task_steps_payload | CHECK | CHECK (((((step_type)::text = 'SKILL'::text) AND (skill_version_id IS NOT NULL) AND (repeat_count IS NULL)) OR (((step_type)::text = 'MOTOR'::text) AND (skill_version_id IS NULL) AND (repeat_count IS NULL)) OR (((step_type)::text = 'REPEAT'::text) AND (skill_version_id IS NULL) AND (repeat_count >= 1)))) |
| ck_task_steps_type | CHECK | CHECK (((step_type)::text = ANY (ARRAY[('SKILL'::character varying)::text, ('MOTOR'::character varying)::text, ('REPEAT'::character varying)::text]))) |
| task_steps_step_order_check | CHECK | CHECK ((step_order >= 1)) |
| task_steps_timeout_seconds_check | CHECK | CHECK (((timeout_seconds IS NULL) OR (timeout_seconds >= 1))) |
| fk_task_steps_40 | FK | FOREIGN KEY (task_version_id) REFERENCES task_versions(id) ON DELETE RESTRICT |
| fk_task_steps_41 | FK | FOREIGN KEY (skill_version_id) REFERENCES skill_versions(id) ON DELETE RESTRICT |
| fk_task_steps_parent | FK | FOREIGN KEY (parent_step_id) REFERENCES task_steps(id) ON DELETE RESTRICT |
| task_steps_pkey | PK | PRIMARY KEY (id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_task_steps_1 ON public.task_steps USING btree (skill_version_id);
CREATE INDEX ix_task_steps_parent ON public.task_steps USING btree (parent_step_id);
CREATE UNIQUE INDEX task_steps_pkey ON public.task_steps USING btree (id);
CREATE UNIQUE INDEX uq_task_steps_sibling_order ON public.task_steps USING btree (task_version_id, COALESCE(parent_step_id, '00000000-0000-0000-0000-000000000000'::uuid), step_order);
```

<a id="table-task_versions"></a>

## task_versions

작업 버전.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| task_id | uuid | 아니오 | — | FK → &#96;tasks.id&#96; |
| version | integer | 아니오 | — | Task 범위 증가 번호 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| task_versions_version_check | CHECK | CHECK ((version >= 1)) |
| fk_task_versions_39 | FK | FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE RESTRICT |
| task_versions_pkey | PK | PRIMARY KEY (id) |
| task_versions_task_id_version_key | UNIQUE | UNIQUE (task_id, version) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_task_versions_1 ON public.task_versions USING btree (task_id, version DESC);
CREATE UNIQUE INDEX task_versions_pkey ON public.task_versions USING btree (id);
CREATE UNIQUE INDEX task_versions_task_id_version_key ON public.task_versions USING btree (task_id, version);
```

<a id="table-tasks"></a>

## tasks

작업 정의.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| owner_id | uuid | 아니오 | — | FK → &#96;users.id&#96; |
| task_key | character varying(100) | 아니오 | — | 소유자 범위 불변 key |
| display_name | character varying(150) | 아니오 | — | 표시 이름 |
| description | text | 예 | — | 설명 |
| latest_version | integer | 예 | — | 목록 표시용 캐시 값 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| deleted_at | timestamp with time zone | 예 | — | 사용자 목록에서 제외된 soft delete 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| fk_tasks_38 | FK | FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT |
| tasks_pkey | PK | PRIMARY KEY (id) |
| tasks_owner_id_task_key_key | UNIQUE | UNIQUE (owner_id, task_key) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_tasks_1 ON public.tasks USING btree (owner_id, created_at DESC);
CREATE INDEX ix_tasks_owner_active_updated ON public.tasks USING btree (owner_id, updated_at DESC) WHERE (deleted_at IS NULL);
CREATE UNIQUE INDEX tasks_owner_id_task_key_key ON public.tasks USING btree (owner_id, task_key);
CREATE UNIQUE INDEX tasks_pkey ON public.tasks USING btree (id);
```

<a id="table-training_job_episodes"></a>

## training_job_episodes

학습 입력 Episode 순서·역할.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| training_job_id | uuid | 아니오 | — | PK 일부, FK → &#96;training_jobs.id&#96; |
| episode_id | uuid | 아니오 | — | PK 일부, FK → &#96;episodes.id&#96; |
| input_order | integer | 아니오 | — | 1부터 연속 |
| archive_sha256_snapshot | character varying(64) | 아니오 | — | 입력 고정 시점의 EpisodeArchive SHA-256 |
| contract_version_snapshot | character varying(100) | 아니오 | — | 입력 데이터 계약 snapshot |
| source_snapshot | character varying(10) | 아니오 | — | &#96;SIM&#96;, &#96;REAL&#96; snapshot |
| label_status_snapshot | character varying(20) | 아니오 | — | 라벨 상태 snapshot |
| success_snapshot | boolean | 예 | — | 성공 라벨 snapshot |
| quality_status_snapshot | character varying(20) | 아니오 | — | 서버 품질 판정 snapshot |
| attached_at | timestamp with time zone | 아니오 | — | 입력 고정 시각 |
| input_role | character varying(20) | 아니오 | — | — |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_training_job_episode_input_role | CHECK | CHECK (((input_role)::text = ANY ((ARRAY['DEMONSTRATION'::character varying, 'MAPPING'::character varying])::text[]))) |
| training_job_episodes_archive_sha256_snapshot_check | CHECK | CHECK (((archive_sha256_snapshot)::text ~ '^[0-9a-f]{64}$'::text)) |
| training_job_episodes_input_order_check | CHECK | CHECK ((input_order >= 1)) |
| training_job_episodes_label_status_snapshot_check | CHECK | CHECK (((label_status_snapshot)::text = ANY (ARRAY[('UNLABELED'::character varying)::text, ('LABELED'::character varying)::text]))) |
| training_job_episodes_quality_status_snapshot_check | CHECK | CHECK (((quality_status_snapshot)::text = ANY (ARRAY[('VALID'::character varying)::text, ('WARNING'::character varying)::text]))) |
| training_job_episodes_source_snapshot_check | CHECK | CHECK (((source_snapshot)::text = ANY (ARRAY[('SIM'::character varying)::text, ('REAL'::character varying)::text]))) |
| fk_training_job_episodes_20 | FK | FOREIGN KEY (training_job_id) REFERENCES training_jobs(id) ON DELETE RESTRICT |
| fk_training_job_episodes_21 | FK | FOREIGN KEY (episode_id) REFERENCES episodes(id) ON DELETE RESTRICT |
| training_job_episodes_pkey | PK | PRIMARY KEY (training_job_id, episode_id) |
| training_job_episodes_training_job_id_input_order_key | UNIQUE | UNIQUE (training_job_id, input_order) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_training_job_episodes_1 ON public.training_job_episodes USING btree (episode_id, training_job_id);
CREATE UNIQUE INDEX training_job_episodes_pkey ON public.training_job_episodes USING btree (training_job_id, episode_id);
CREATE UNIQUE INDEX training_job_episodes_training_job_id_input_order_key ON public.training_job_episodes USING btree (training_job_id, input_order);
CREATE UNIQUE INDEX uq_training_job_one_mapping_episode ON public.training_job_episodes USING btree (training_job_id) WHERE ((input_role)::text = 'MAPPING'::text);
```

<a id="table-training_job_events"></a>

## training_job_events

GPU 학습 상태 이벤트.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| training_job_id | uuid | 아니오 | — | FK → &#96;training_jobs.id&#96; |
| attempt | integer | 아니오 | — | 보고 attempt |
| event_id | character varying(150) | 아니오 | — | worker 이벤트 ID |
| sequence | bigint | 아니오 | — | attempt 안의 순번 |
| event_type | character varying(100) | 아니오 | — | STARTED, PROGRESS, TRAINING_FINISHED, FAILED, CANCELED |
| payload | jsonb | 아니오 | — | 이벤트 내용 |
| occurred_at | timestamp with time zone | 아니오 | — | worker 발생 시각 |
| received_at | timestamp with time zone | 아니오 | — | 서버 수신 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| fk_training_job_events_22 | FK | FOREIGN KEY (training_job_id) REFERENCES training_jobs(id) ON DELETE RESTRICT |
| training_job_events_pkey | PK | PRIMARY KEY (id) |
| training_job_events_training_job_id_attempt_event_id_key | UNIQUE | UNIQUE (training_job_id, attempt, event_id) |
| training_job_events_training_job_id_attempt_sequence_key | UNIQUE | UNIQUE (training_job_id, attempt, sequence) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_training_job_events_1 ON public.training_job_events USING btree (training_job_id, received_at);
CREATE UNIQUE INDEX training_job_events_pkey ON public.training_job_events USING btree (id);
CREATE UNIQUE INDEX training_job_events_training_job_id_attempt_event_id_key ON public.training_job_events USING btree (training_job_id, attempt, event_id);
CREATE UNIQUE INDEX training_job_events_training_job_id_attempt_sequence_key ON public.training_job_events USING btree (training_job_id, attempt, sequence);
```

<a id="table-training_jobs"></a>

## training_jobs

학습 작업·진행·할당·lease.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| owner_id | uuid | 아니오 | — | FK → &#96;users.id&#96; |
| skill_id | uuid | 아니오 | — | FK → &#96;skills.id&#96; |
| umi_device_id | uuid | 아니오 | — | FK → &#96;devices.id&#96; |
| assigned_storage_node_id | uuid | 예 | — | FK → &#96;storage_nodes.id&#96; |
| assigned_worker_id | character varying(100) | 예 | — | 현재 GPU worker |
| input_mode | character varying(30) | 아니오 | — | 고정값 EXISTING_EPISODES. UMI가 S3에 업로드하고 검증 완료된 Episode만 학습 입력으로 사용 |
| status | character varying(30) | 아니오 | — | 학습 상태 |
| attempt | integer | 아니오 | 1 | 기본 1 |
| lease_token_hash | character varying(64) | 예 | — | 현재 lease token hash |
| lease_expires_at | timestamp with time zone | 예 | — | lease 만료 시각 |
| dataset_query | jsonb | 아니오 | — | Episode 선택 조건 snapshot |
| gpu_index | integer | 예 | — | 학습에 할당된 GPU 번호, 0 이상 |
| git_rev | character varying(100) | 예 | — | 학습 코드 revision |
| expected_episode_count | integer | 아니오 | 0 | 기본 0 |
| received_episode_count | integer | 아니오 | 0 | 기본 0 |
| received_bytes | bigint | 아니오 | 0 | 기본 0 |
| episode_count | integer | 아니오 | — | 고정 입력 Episode 수 |
| input_snapshot_sha256 | character varying(64) | 예 | — | 고정 입력 목록 hash |
| progress_stage | character varying(100) | 예 | — | 합의된 stage enum |
| progress_percent | numeric(5,2) | 아니오 | — | 0~100 |
| current_epoch | integer | 예 | — | 현재 epoch |
| total_epochs | integer | 예 | — | 전체 epoch |
| result_skill_version_id | uuid | 예 | — | FK → &#96;skill_versions.id&#96;, migration 후 추가 |
| error_code | character varying(100) | 예 | — | 종료 또는 최근 오류 |
| error_message | text | 예 | — | 오류 설명 |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| started_at | timestamp with time zone | 예 | — | 시작 시각 |
| model_completed_at | timestamp with time zone | 예 | — | FastAPI의 최초 학습 완료 통지 발신 UTC 시각, GPU Spring Boot를 거쳐 전달 |
| finished_at | timestamp with time zone | 예 | — | 종료 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |
| row_version | bigint | 아니오 | 0 | 상태 전이 낙관적 잠금 |
| deleted_at | timestamp with time zone | 예 | — | 사용자 목록에서 제외된 soft delete 시각 |
| training_mode | character varying(30) | 아니오 | 'FROM_SCRATCH'::character varying | — |
| base_skill_version_id | uuid | 예 | — | — |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| ck_training_jobs_base_model | CHECK | CHECK (((((training_mode)::text = 'FROM_SCRATCH'::text) AND (base_skill_version_id IS NULL)) OR (((training_mode)::text = 'FINE_TUNE'::text) AND (base_skill_version_id IS NOT NULL)))) |
| ck_training_jobs_gpu_index_parallel_limit | CHECK | CHECK (((gpu_index IS NULL) OR ((gpu_index >= 0) AND (gpu_index <= 29)))) |
| ck_training_jobs_training_mode | CHECK | CHECK (((training_mode)::text = ANY (ARRAY[('FROM_SCRATCH'::character varying)::text, ('FINE_TUNE'::character varying)::text]))) |
| training_jobs_attempt_check | CHECK | CHECK ((attempt >= 1)) |
| training_jobs_check | CHECK | CHECK (((model_completed_at IS NULL) OR (started_at IS NULL) OR (model_completed_at >= started_at))) |
| training_jobs_check1 | CHECK | CHECK (((finished_at IS NULL) OR (model_completed_at IS NULL) OR (finished_at >= model_completed_at))) |
| training_jobs_episode_count_check | CHECK | CHECK ((episode_count >= 0)) |
| training_jobs_expected_episode_count_check | CHECK | CHECK ((expected_episode_count >= 0)) |
| training_jobs_gpu_index_check | CHECK | CHECK (((gpu_index IS NULL) OR (gpu_index >= 0))) |
| training_jobs_input_mode_check | CHECK | CHECK (((input_mode)::text = 'EXISTING_EPISODES'::text)) |
| training_jobs_input_snapshot_sha256_check | CHECK | CHECK (((input_snapshot_sha256)::text ~ '^[0-9a-f]{64}$'::text)) |
| training_jobs_lease_token_hash_check | CHECK | CHECK (((lease_token_hash)::text ~ '^[0-9a-f]{64}$'::text)) |
| training_jobs_progress_percent_check | CHECK | CHECK (((progress_percent >= (0)::numeric) AND (progress_percent <= (100)::numeric))) |
| training_jobs_received_bytes_check | CHECK | CHECK ((received_bytes >= 0)) |
| training_jobs_received_episode_count_check | CHECK | CHECK ((received_episode_count >= 0)) |
| training_jobs_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('CREATED'::character varying)::text, ('COLLECTING'::character varying)::text, ('INPUT_VALIDATING'::character varying)::text, ('INPUT_READY'::character varying)::text, ('QUEUED'::character varying)::text, ('PREPARING'::character varying)::text, ('RUNNING'::character varying)::text, ('MODEL_UPLOADING'::character varying)::text, ('MODEL_VALIDATING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text, ('CANCELED'::character varying)::text, ('SUCCEEDED'::character varying)::text, ('FAILED'::character varying)::text]))) |
| fk_training_jobs_14 | FK | FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE RESTRICT |
| fk_training_jobs_15 | FK | FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE RESTRICT |
| fk_training_jobs_16 | FK | FOREIGN KEY (umi_device_id) REFERENCES devices(id) ON DELETE RESTRICT |
| fk_training_jobs_18 | FK | FOREIGN KEY (assigned_storage_node_id) REFERENCES storage_nodes(id) ON DELETE RESTRICT |
| fk_training_jobs_19 | FK | FOREIGN KEY (result_skill_version_id) REFERENCES skill_versions(id) ON DELETE RESTRICT |
| fk_training_jobs_base_skill_version | FK | FOREIGN KEY (base_skill_version_id) REFERENCES skill_versions(id) ON DELETE RESTRICT |
| training_jobs_pkey | PK | PRIMARY KEY (id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_training_jobs_1 ON public.training_jobs USING btree (owner_id, status, created_at DESC);
CREATE INDEX ix_training_jobs_2 ON public.training_jobs USING btree (skill_id, status, created_at DESC);
CREATE INDEX ix_training_jobs_3 ON public.training_jobs USING btree (assigned_storage_node_id, status);
CREATE INDEX ix_training_jobs_4 ON public.training_jobs USING btree (assigned_worker_id, status);
CREATE INDEX ix_training_jobs_base_skill_version ON public.training_jobs USING btree (base_skill_version_id) WHERE (base_skill_version_id IS NOT NULL);
CREATE INDEX ix_training_jobs_claim_candidate ON public.training_jobs USING btree (created_at) WHERE ((deleted_at IS NULL) AND ((status)::text = ANY ((ARRAY['CREATED'::character varying, 'QUEUED'::character varying])::text[])));
CREATE INDEX ix_training_jobs_expired_lease ON public.training_jobs USING btree (lease_expires_at) WHERE ((lease_expires_at IS NOT NULL) AND ((status)::text = ANY (ARRAY[('COLLECTING'::character varying)::text, ('PREPARING'::character varying)::text, ('RUNNING'::character varying)::text, ('MODEL_UPLOADING'::character varying)::text, ('MODEL_VALIDATING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text])));
CREATE INDEX ix_training_jobs_owner_active_created ON public.training_jobs USING btree (owner_id, created_at DESC) WHERE (deleted_at IS NULL);
CREATE UNIQUE INDEX training_jobs_pkey ON public.training_jobs USING btree (id);
CREATE UNIQUE INDEX uq_training_jobs_active_worker_gpu ON public.training_jobs USING btree (assigned_worker_id, gpu_index) WHERE ((assigned_worker_id IS NOT NULL) AND (gpu_index IS NOT NULL) AND ((status)::text = ANY (ARRAY[('COLLECTING'::character varying)::text, ('PREPARING'::character varying)::text, ('RUNNING'::character varying)::text, ('MODEL_UPLOADING'::character varying)::text, ('MODEL_VALIDATING'::character varying)::text, ('CANCEL_REQUESTED'::character varying)::text])));
```

<a id="table-users"></a>

## users

사용자 계정.

| 컬럼 | PostgreSQL 타입 | NULL 허용 | 기본값 | DB 주석 |
|---|---|---|---|---|
| id | uuid | 아니오 | — | PK |
| email | character varying(320) | 아니오 | — | 로그인 이메일 |
| password_hash | character varying(255) | 아니오 | — | 평문 저장 금지 |
| name | character varying(100) | 아니오 | — | 표시 이름 |
| status | character varying(20) | 아니오 | — | &#96;ACTIVE&#96;, &#96;LOCKED&#96;, &#96;WITHDRAWN&#96; |
| created_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 생성 시각 |
| updated_at | timestamp with time zone | 아니오 | CURRENT_TIMESTAMP | 수정 시각 |

### 제약조건

| 이름 | 종류 | 정의 |
|---|---|---|
| users_status_check | CHECK | CHECK (((status)::text = ANY (ARRAY[('ACTIVE'::character varying)::text, ('LOCKED'::character varying)::text, ('WITHDRAWN'::character varying)::text]))) |
| users_pkey | PK | PRIMARY KEY (id) |

### 인덱스

PK·UNIQUE 제약으로 자동 생성된 인덱스도 포함합니다.

```sql
CREATE INDEX ix_users_status ON public.users USING btree (status);
CREATE UNIQUE INDEX uq_users_email_lower ON public.users USING btree (lower((email)::text));
CREATE UNIQUE INDEX users_pkey ON public.users USING btree (id);
```

## 사용자 정의 함수

배포에 참조되는 스킬 버전이 READY이고 gate_passed=true인지 확인합니다. 실제 적용 테이블과 이벤트는 위 트리거 정의에 표시했습니다.

```sql
CREATE OR REPLACE FUNCTION public.enforce_deployable_skill_version()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
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
$function$

```
