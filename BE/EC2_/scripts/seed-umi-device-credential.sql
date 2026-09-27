-- 로컬 개발 DB 전용. device_credentials에 secret 해시를 등록한다 (이미 있으면 갱신).
INSERT INTO device_credentials (device_id, device_secret_hash, status, enrolled_at, secret_rotated_at, created_at, updated_at)
VALUES ('51000000-0000-4000-8000-000000000001', '$2b$10$hk7nZpCKkvK4L9ottyvN/OYf1hCEa8tB91XorGdvjoUBnapCazdWa', 'ACTIVE', now(), now(), now(), now())
ON CONFLICT (device_id) DO UPDATE
SET device_secret_hash = EXCLUDED.device_secret_hash, status = 'ACTIVE',
    enrolled_at = now(), secret_rotated_at = now(), updated_at = now();
