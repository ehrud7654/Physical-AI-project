# 단일 V1 DB 덤프 복원

## 신규 설치 방법 선택

**일반 설치:** 비어 있는 DB에서 EC2 backend를 실행하면 Flyway가 새 V1을 실행합니다. 덤프를 먼저 넣을 필요가 없습니다.

**덤프 복원 설치:** backend를 시작하지 않은 빈 DB에 아래 덤프를 복원한 다음 backend를 시작합니다. 덤프에 새 V1 이력이 있으므로 같은 소스의 Flyway는 V1을 다시 실행하지 않고 checksum을 검증합니다. PostgreSQL 17.11 이상 호환 `psql` 클라이언트를 사용합니다.

저장소의 `BE/EC2_`에서 실행:

```bash
# .env와 빈 DB를 먼저 준비하고, 복원 중 backend는 실행하지 않습니다.
docker compose up -d postgres
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 --single-transaction' < ../../exec/03_DB_dump.sql

docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1' <<'SQL'
SELECT version, script, success FROM flyway_schema_history ORDER BY installed_rank;
SELECT count(*) AS application_table_count
FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
  AND table_name <> 'flyway_schema_history';
SQL

# 다른 필수 환경설정(AWS/Redis 등)도 준비한 뒤 서비스 시작
docker compose up -d --build backend
```

확인 값: `version=1`, `script=V1__db_schema.sql`, `success=true`, 업무 테이블 수 29. S3 객체 및 Redis 실시간 세션·장치 상태는 이 덤프에 포함되지 않습니다.
