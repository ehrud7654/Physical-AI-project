-- 로컬 데모 Jetson 장치의 개발용 credential seed.
-- 평문 secret은 저장하지 않으며, 이미 credential이 존재하면 덮어쓰지 않는다.
INSERT INTO device_credentials (
    device_id,
    device_secret_hash,
    status,
    enrolled_at,
    secret_rotated_at,
    created_at,
    updated_at
)
VALUES (
    '51000000-0000-4000-8000-000000000002',
    '$2a$10$.y2GoOBYYemXP84Lpx713OnxK6yy.5BF4ajYzaTIz7xLnVNzHrs86',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (device_id) DO NOTHING;
