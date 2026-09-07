-- DeviceType을 Java/API enum 명명 규칙과 동일한 대문자 snake case로 통일한다.
-- V1은 이미 적용된 환경의 Flyway checksum을 보존하기 위해 수정하지 않는다.

ALTER TABLE devices
    DROP CONSTRAINT devices_device_type_check;

UPDATE devices
SET device_type = 'UMI_CAMERA'
WHERE device_type = 'UMI_Camera';

ALTER TABLE devices
    ADD CONSTRAINT ck_devices_device_type
        CHECK (device_type IN ('UMI_CAMERA', 'JETSON'));

COMMENT ON COLUMN devices.device_type IS '`UMI_CAMERA`, `JETSON`';
