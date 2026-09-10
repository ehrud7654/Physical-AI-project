ALTER TABLE devices
    ADD COLUMN location varchar(200);

UPDATE devices
SET location = '801호'
WHERE location IS NULL;

COMMENT ON COLUMN devices.location IS '장치 설치 위치. 미지정 가능';
