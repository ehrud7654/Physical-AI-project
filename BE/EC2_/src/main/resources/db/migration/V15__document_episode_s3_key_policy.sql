COMMENT ON COLUMN episodes.session_id IS
    'UMI가 생성한 소문자 UUID v4. ZIP 파일명의 확장자를 제외한 값과 일치';

COMMENT ON COLUMN episode_archives.storage_key IS
    '서버 생성 S3 object key: episodes/{owner UUID 소문자}/{yyyyMMddTHHmmssZ}/{session UUID v4 소문자}.zip';

COMMENT ON COLUMN episode_archives.original_filename IS
    '감사용 원본 파일명 {session UUID v4 소문자}.zip';

