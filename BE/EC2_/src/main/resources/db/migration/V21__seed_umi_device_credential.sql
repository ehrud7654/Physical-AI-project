-- 로컬 데모 UMI 장치의 개발용 credential seed.
-- 이미 QR enrollment 등으로 credential이 발급된 경우 기존 secret을 덮어쓰지 않는다.
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
    '51000000-0000-4000-8000-000000000001',
    '$2b$10$hk7nZpCKkvK4L9ottyvN/OYf1hCEa8tB91XorGdvjoUBnapCazdWa',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (device_id) DO NOTHING;
