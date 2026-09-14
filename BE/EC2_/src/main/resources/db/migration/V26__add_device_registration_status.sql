ALTER TABLE devices
    ADD COLUMN registration_status varchar(20) NOT NULL DEFAULT 'REGISTERED';

ALTER TABLE devices
    ADD CONSTRAINT ck_devices_registration_status
    CHECK (registration_status IN ('PENDING', 'REGISTERED'));

COMMENT ON COLUMN devices.registration_status IS
    'UMI 장치 등록 상태. QR 등록 완료 전 PENDING, Device Secret 발급 완료 후 REGISTERED';
