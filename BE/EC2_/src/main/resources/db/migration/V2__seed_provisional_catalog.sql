-- 개발 초기화를 위한 임시 seed (최종 운영 설정이 아님).
-- V1의 확정 Skill 5종 및 VLM threshold 0.7000은 변경하지 않는다.
-- NOT NULL 값은 아래 임시값을 사용하고 선택 컬럼은 NULL로 둔다.
-- 실제 장치/모델/학습 작업 같은 업무 데이터는 가짜 행으로 생성하지 않는다.
-- Flyway가 한 번만 적용하며 기존 행을 UPDATE/덮어쓰기하지 않는다.
-- 확정 후 참조된 프로필/정책은 수정하지 말고 새 버전/행으로 전환한다.

-- V1 생성 오류 정정: algorithm/hyperparameters는 해시 입력 필드 이름이며
-- config_sha256의 허용값 enum이 아니다. 잘못 추가된 CHECK만 제거한다.
-- 소문자 64자리 SHA-256 정규식 CHECK(training_profiles_config_sha256_check)는 유지한다.
ALTER TABLE training_profiles
    DROP CONSTRAINT training_profiles_config_sha256_check1;

-- 임시 고정 학습값: ACT, epochs=100, batchSize=16, seed=42,
-- learningRate=0.0001, optimizer=AdamW (DB 문서의 예시값).
-- 해시는 아래 UTF-8 JSON 바이트를 대상으로 계산한다.
-- 키 사전순 정렬 / 공백 없음 / learningRate의 소수 표기 사용.
-- API/FastAPI canonical JSON 규칙 최종 합의 전에는 운영 학습에 사용하지 않는다.
WITH provisional_config AS (
    SELECT '{"algorithm":"ACT","hyperparameters":{"batchSize":16,"epochs":100,"learningRate":0.0001,"optimizer":"AdamW","seed":42}}'::text AS canonical_json
)
INSERT INTO training_profiles (
    id, profile_key, version, status, algorithm, hyperparameters,
    config_sha256, description, activated_at, retired_at
)
SELECT
    '30000000-0000-4000-8000-000000000001'::uuid,
    'umi-act-default', 1, 'ACTIVE', 'ACT',
    canonical_json::jsonb -> 'hyperparameters',
    encode(sha256(convert_to(canonical_json, 'UTF8')), 'hex'),
    NULL, NULL, NULL
FROM provisional_config;

-- 임시 보관값: 기본 7일 / 학습 후 유예 3일 / 최대 30일 / 삭제 재시도 3회.
-- 개발용 기본 정책. 사용자 연장 및 조기 삭제는 임시로 허용하지 않는다.
-- 정책을 만드는 것만으로 파일을 삭제하지 않는다. 실제 삭제 worker는 별도 구현이다.
INSERT INTO retention_policies (
    id, name, default_retention_days, post_training_grace_days,
    max_retention_days, max_delete_attempts,
    allow_manual_extension, allow_manual_purge, is_default, active
) VALUES (
    '40000000-0000-4000-8000-000000000001', 'dev-provisional-retention',
    7, 3, 30, 3, false, false, true, true
);

-- sort_two에만 임시 목적지 2개를 등록한다.
-- 목적지 enum/실제 좌표는 미합의 상태이므로 enabled=false로 동작 허용을 차단한다.
-- 다른 스킬은 현재 needs_vlm=false이므로 불필요한 목적지 행을 만들지 않는다.
INSERT INTO skill_destinations (skill_id, destination_key, display_name, enabled)
VALUES
    ('10000000-0000-4000-8000-000000000002', 'TEMP_LEFT', '임시 왼쪽 목적지', false),
    ('10000000-0000-4000-8000-000000000002', 'TEMP_RIGHT', '임시 오른쪽 목적지', false);
