-- 2026-09-15 UMI-001이 S3에 업로드한 Episode archive 메타데이터 seed.
-- "청기들기" 데이터는 현재 카탈로그의 grab_blue_flag(청기잡기)에 연결한다.
-- 요청에 실제 ZIP 크기와 SHA-256이 포함되지 않아 size/hash는 migration fixture 값이다.
-- GPU 학습 입력으로 사용하기 전 반드시 실제 S3 객체의 크기와 SHA-256으로 교체해야 한다.

INSERT INTO episodes (
    id, owner_id, umi_device_id, storage_node_id, retention_policy_id, skill_id,
    session_id, source, success, label_status, label_source, labeled_at,
    contract_version, meta_schema_version, summary_schema_version, status,
    started_at, ended_at, start_monotonic_ns, timestamp_timebase,
    first_timestamp_ns, last_timestamp_ns, duration_ns,
    frame_count, pose_sample_count, imu_sample_count, gripper_sample_count,
    missing_frame_count, tracking_gap_count, tracking_lost_duration_ns,
    quality_status, capture_metadata, quality_summary,
    validated_at, retention_expires_at, created_at, updated_at, row_version
) VALUES
(
    '61000000-0000-4000-8000-000000000001',
    '50000000-0000-4000-8000-000000000001',
    '51000000-0000-4000-8000-000000000001',
    '18000000-0000-4000-8000-000000000001',
    '40000000-0000-4000-8000-000000000001',
    '54000000-0000-4000-8000-000000000001',
    'b4a1d35f-9b90-4c49-8f49-bd4ca35059b1',
    'REAL', true, 'LABELED', 'UMI', '2026-09-15T03:17:55Z',
    'episode-archive-v1', 'meta-v1', 'summary-v1', 'READY',
    '2026-09-15T03:17:53Z', '2026-09-15T03:17:55Z',
    1000000000, 'MONOTONIC_NS', 1000000000, 3000000000, 2000000000,
    60, 60, 400, 0, 0, 0, 0, 'VALID',
    '{"device":{"deviceId":"51000000-0000-4000-8000-000000000001","handleNumber":"UMI-001"},"camera":{"source":"S3_SEED"},"coordinateSystem":{"pose":"ARCORE_WORLD"},"units":{"position":"m","rotation":"quaternion","time":"ns"}}'::jsonb,
    '{"values":{"seedFixture":true,"missingFrameCount":0,"trackingGapCount":0}}'::jsonb,
    '2026-09-15T03:17:55Z', '2026-09-22T03:17:55Z',
    '2026-09-15T03:17:55Z', '2026-09-15T03:17:55Z', 0
),
(
    '61000000-0000-4000-8000-000000000002',
    '50000000-0000-4000-8000-000000000001',
    '51000000-0000-4000-8000-000000000001',
    '18000000-0000-4000-8000-000000000001',
    '40000000-0000-4000-8000-000000000001',
    '54000000-0000-4000-8000-000000000001',
    'dff0f188-a016-46e3-a39b-8050711ce130',
    'REAL', true, 'LABELED', 'UMI', '2026-09-15T03:18:00Z',
    'episode-archive-v1', 'meta-v1', 'summary-v1', 'READY',
    '2026-09-15T03:17:58Z', '2026-09-15T03:18:00Z',
    4000000000, 'MONOTONIC_NS', 4000000000, 6000000000, 2000000000,
    60, 60, 400, 0, 0, 0, 0, 'VALID',
    '{"device":{"deviceId":"51000000-0000-4000-8000-000000000001","handleNumber":"UMI-001"},"camera":{"source":"S3_SEED"},"coordinateSystem":{"pose":"ARCORE_WORLD"},"units":{"position":"m","rotation":"quaternion","time":"ns"}}'::jsonb,
    '{"values":{"seedFixture":true,"missingFrameCount":0,"trackingGapCount":0}}'::jsonb,
    '2026-09-15T03:18:00Z', '2026-09-22T03:18:00Z',
    '2026-09-15T03:18:00Z', '2026-09-15T03:18:00Z', 0
),
(
    '61000000-0000-4000-8000-000000000003',
    '50000000-0000-4000-8000-000000000001',
    '51000000-0000-4000-8000-000000000001',
    '18000000-0000-4000-8000-000000000001',
    '40000000-0000-4000-8000-000000000001',
    '54000000-0000-4000-8000-000000000002',
    '13845047-dc3f-4ce5-985f-e9d9baece5bb',
    'REAL', true, 'LABELED', 'UMI', '2026-09-15T03:18:05Z',
    'episode-archive-v1', 'meta-v1', 'summary-v1', 'READY',
    '2026-09-15T03:18:03Z', '2026-09-15T03:18:05Z',
    7000000000, 'MONOTONIC_NS', 7000000000, 9000000000, 2000000000,
    60, 60, 400, 0, 0, 0, 0, 'VALID',
    '{"device":{"deviceId":"51000000-0000-4000-8000-000000000001","handleNumber":"UMI-001"},"camera":{"source":"S3_SEED"},"coordinateSystem":{"pose":"ARCORE_WORLD"},"units":{"position":"m","rotation":"quaternion","time":"ns"}}'::jsonb,
    '{"values":{"seedFixture":true,"missingFrameCount":0,"trackingGapCount":0}}'::jsonb,
    '2026-09-15T03:18:05Z', '2026-09-22T03:18:05Z',
    '2026-09-15T03:18:05Z', '2026-09-15T03:18:05Z', 0
),
(
    '61000000-0000-4000-8000-000000000004',
    '50000000-0000-4000-8000-000000000001',
    '51000000-0000-4000-8000-000000000001',
    '18000000-0000-4000-8000-000000000001',
    '40000000-0000-4000-8000-000000000001',
    '54000000-0000-4000-8000-000000000002',
    '0b4a7981-552e-40e5-8db3-fdcbdd0b5ca3',
    'REAL', true, 'LABELED', 'UMI', '2026-09-15T03:18:09Z',
    'episode-archive-v1', 'meta-v1', 'summary-v1', 'READY',
    '2026-09-15T03:18:07Z', '2026-09-15T03:18:09Z',
    10000000000, 'MONOTONIC_NS', 10000000000, 12000000000, 2000000000,
    60, 60, 400, 0, 0, 0, 0, 'VALID',
    '{"device":{"deviceId":"51000000-0000-4000-8000-000000000001","handleNumber":"UMI-001"},"camera":{"source":"S3_SEED"},"coordinateSystem":{"pose":"ARCORE_WORLD"},"units":{"position":"m","rotation":"quaternion","time":"ns"}}'::jsonb,
    '{"values":{"seedFixture":true,"missingFrameCount":0,"trackingGapCount":0}}'::jsonb,
    '2026-09-15T03:18:09Z', '2026-09-22T03:18:09Z',
    '2026-09-15T03:18:09Z', '2026-09-15T03:18:09Z', 0
);

INSERT INTO episode_archives (
    id, episode_id, storage_node_id, storage_key, original_filename,
    content_type, archive_format_version, compression_method,
    size_bytes, uncompressed_size_bytes, entry_count, sha256,
    manifest_summary, status, stored_at, validated_at, created_at, updated_at
) VALUES
(
    '62000000-0000-4000-8000-000000000001',
    '61000000-0000-4000-8000-000000000001',
    '18000000-0000-4000-8000-000000000001',
    'episodes/50000000-0000-4000-8000-000000000001/20260915T031755Z/b4a1d35f-9b90-4c49-8f49-bd4ca35059b1.zip',
    'b4a1d35f-9b90-4c49-8f49-bd4ca35059b1.zip',
    'application/zip', 'umi-episode-zip-v1', 'STORE',
    25690112, 10480000, 66, repeat('1', 64),
    '{"fileSha256":{},"frameEntryCount":60,"details":{"seedFixture":true,"actualObjectMetadataRequired":true}}'::jsonb,
    'AVAILABLE', '2026-09-15T03:17:55Z', '2026-09-15T03:17:55Z',
    '2026-09-15T03:17:55Z', '2026-09-15T03:17:55Z'
),
(
    '62000000-0000-4000-8000-000000000002',
    '61000000-0000-4000-8000-000000000002',
    '18000000-0000-4000-8000-000000000001',
    'episodes/50000000-0000-4000-8000-000000000001/20260915T031800Z/dff0f188-a016-46e3-a39b-8050711ce130.zip',
    'dff0f188-a016-46e3-a39b-8050711ce130.zip',
    'application/zip', 'umi-episode-zip-v1', 'STORE',
    24956109, 10480000, 66, repeat('2', 64),
    '{"fileSha256":{},"frameEntryCount":60,"details":{"seedFixture":true,"actualObjectMetadataRequired":true}}'::jsonb,
    'AVAILABLE', '2026-09-15T03:18:00Z', '2026-09-15T03:18:00Z',
    '2026-09-15T03:18:00Z', '2026-09-15T03:18:00Z'
),
(
    '62000000-0000-4000-8000-000000000003',
    '61000000-0000-4000-8000-000000000003',
    '18000000-0000-4000-8000-000000000001',
    'episodes/50000000-0000-4000-8000-000000000001/20260915T031805Z/13845047-dc3f-4ce5-985f-e9d9baece5bb.zip',
    '13845047-dc3f-4ce5-985f-e9d9baece5bb.zip',
    'application/zip', 'umi-episode-zip-v1', 'STORE',
    21076378, 10480000, 66, repeat('3', 64),
    '{"fileSha256":{},"frameEntryCount":60,"details":{"seedFixture":true,"actualObjectMetadataRequired":true}}'::jsonb,
    'AVAILABLE', '2026-09-15T03:18:05Z', '2026-09-15T03:18:05Z',
    '2026-09-15T03:18:05Z', '2026-09-15T03:18:05Z'
),
(
    '62000000-0000-4000-8000-000000000004',
    '61000000-0000-4000-8000-000000000004',
    '18000000-0000-4000-8000-000000000001',
    'episodes/50000000-0000-4000-8000-000000000001/20260915T031809Z/0b4a7981-552e-40e5-8db3-fdcbdd0b5ca3.zip',
    '0b4a7981-552e-40e5-8db3-fdcbdd0b5ca3.zip',
    'application/zip', 'umi-episode-zip-v1', 'STORE',
    27996979, 10480000, 66, repeat('4', 64),
    '{"fileSha256":{},"frameEntryCount":60,"details":{"seedFixture":true,"actualObjectMetadataRequired":true}}'::jsonb,
    'AVAILABLE', '2026-09-15T03:18:09Z', '2026-09-15T03:18:09Z',
    '2026-09-15T03:18:09Z', '2026-09-15T03:18:09Z'
);
