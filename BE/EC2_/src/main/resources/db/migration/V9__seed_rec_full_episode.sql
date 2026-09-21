-- Register the real UMI raw archive uploaded on 2026-09-21.
-- Source object before normalization:
-- s3://pjt2-shanks-ssafy-720223570179-ap-northeast-2-an/rec_full.zip
--
-- The database points at the normalized immutable key described below. Copy the
-- object to that key before applying this migration.

INSERT INTO episodes (
    id,
    owner_id,
    umi_device_id,
    storage_node_id,
    retention_policy_id,
    skill_id,
    session_id,
    source,
    success,
    label_status,
    label_source,
    labeled_at,
    contract_version,
    meta_schema_version,
    summary_schema_version,
    status,
    started_at,
    ended_at,
    start_monotonic_ns,
    timestamp_timebase,
    first_timestamp_ns,
    last_timestamp_ns,
    duration_ns,
    frame_count,
    pose_sample_count,
    imu_sample_count,
    gripper_sample_count,
    missing_frame_count,
    tracking_gap_count,
    tracking_lost_duration_ns,
    quality_status,
    capture_metadata,
    quality_summary,
    validated_at,
    last_used_at,
    retention_expires_at,
    deleted_at,
    error_code,
    error_message,
    created_at,
    updated_at,
    row_version
) VALUES (
    'a14071bf-3677-46bf-aa2b-e32bea650982',
    '50000000-0000-4000-8000-000000000001',
    '51000000-0000-4000-8000-000000000001',
    '18000000-0000-4000-8000-000000000001',
    '40000000-0000-4000-8000-000000000001',
    '54000000-0000-4000-8000-000000000001',
    '5cb1d732-404a-42c1-840c-53d449fd9ab5',
    'REAL',
    false,
    'LABELED',
    'UMI',
    '2026-09-21T09:00:05.684578070Z',
    'umi-raw-v2',
    '2',
    'manifest-derived-v1',
    'READY',
    '2026-09-21T09:00:00Z',
    '2026-09-21T09:00:05.684578070Z',
    1014759411269965,
    'MONOTONIC_NS',
    1014760010155241,
    1014764843195501,
    4833040260,
    145,
    0,
    2395,
    0,
    0,
    0,
    0,
    'WARNING',
    $json$
    {
      "device": {
        "deviceId": "51000000-0000-4000-8000-000000000001",
        "handleNumber": "UMI-001",
        "model": "SM-S901N",
        "android": "16",
        "appVersion": "0.3"
      },
      "camera": {
        "id": "2",
        "width": 1920,
        "height": 1080,
        "minimumFocusDistance": 0,
        "sensorOrientation": 90,
        "timestampSource": 1,
        "focalLengthsMm": [2.2],
        "horizontalFovEstimate": 104.12870803319929,
        "imageRotationDegrees": 180,
        "rotationBakedIntoPixels": true,
        "decoderRotationDegrees": 0,
        "imageOrientationContract": "upright_v1",
        "encoderTimestampBase": "SENSOR"
      },
      "coordinateSystem": {
        "imuAxes": "Android device axes; acceleration includes gravity",
        "imuRotationApplied": false
      },
      "units": {
        "time": "ns",
        "accelerometer": "m/s^2",
        "gyroscope": "rad/s",
        "markerSide": "mm"
      }
    }
    $json$::jsonb,
    $json$
    {
      "values": {
        "manifestStatus": "recorded",
        "task": "grasp_lift_hold",
        "outcome": "failure",
        "trainingReady": false,
        "warningReason": "Manifest marks this recording as a failed, non-training-ready demonstration",
        "framesRows": 145,
        "encodedRows": 145,
        "accelerometerRows": 1205,
        "gyroscopeRows": 1190,
        "ptsMatchesSensorTimestamp": true,
        "markerBlackSquareSizeVerified": false,
        "calibrationId": null
      }
    }
    $json$::jsonb,
    '2026-09-21T09:05:15Z',
    NULL,
    '2026-09-28T09:00:00Z',
    NULL,
    NULL,
    NULL,
    '2026-09-21T09:05:15Z',
    '2026-09-21T09:05:15Z',
    0
);

INSERT INTO episode_archives (
    id,
    episode_id,
    storage_node_id,
    storage_key,
    original_filename,
    content_type,
    archive_format_version,
    compression_method,
    size_bytes,
    uncompressed_size_bytes,
    entry_count,
    sha256,
    manifest_summary,
    status,
    stored_at,
    validated_at,
    deleted_at,
    error_code,
    error_message,
    created_at,
    updated_at
) VALUES (
    '663e79f0-260b-44ea-ad51-8a095eac4d40',
    'a14071bf-3677-46bf-aa2b-e32bea650982',
    '18000000-0000-4000-8000-000000000001',
    'episodes/50000000-0000-4000-8000-000000000001/20260921T090000Z/5cb1d732-404a-42c1-840c-53d449fd9ab5.zip',
    'rec_full.zip',
    'application/zip',
    'umi-raw-v2',
    'STORE',
    9628714,
    9627874,
    6,
    'cf763cd242c964b92f95681e9deac8d8e7cdadf1ab7795850cf7daba3907ed42',
    $json$
    {
      "fileSha256": {
        "manifest.json": "cb5ec7d5f24d00bd7240f8d87b4b6d497fc6aa331d1f5f82e7cd5bb95cf81b61",
        "frames.csv": "6b0b54ee5877b2eebd34fa2a2f0e303d034e5ed2ffa555793173c7872309853e",
        "encoded.csv": "38e821ba9a8c22d39aefffd715f45c6306ea7a7678b33d3dfefac6cc964de0ce",
        "accelerometer.csv": "de4818327baf4d1cddac3e8c9a5ee2741bc39773bf518a87840af9219e6f280c",
        "gyroscope.csv": "6a1fa448b5f30338336bd8937286c79ace5be61cc875ca413193e4cec0778ed8",
        "video.mp4": "91c13159b29046efb0efcc0b09fbb6b8a1544fb8c66d10aad75f6879c2f76a22"
      },
      "frameEntryCount": 145,
      "details": {
        "bucket": "pjt2-shanks-ssafy-720223570179-ap-northeast-2-an",
        "sourceObjectKey": "rec_full.zip",
        "checksumType": "FULL_OBJECT",
        "s3ChecksumSha256Base64": "z3Y80kLJZLkvlWgenerI2OfNrfGrd5WFDPfaujkH7UI=",
        "compressionMethod": "STORE",
        "schemaVersion": 2
      }
    }
    $json$::jsonb,
    'AVAILABLE',
    '2026-09-21T09:05:15Z',
    '2026-09-21T09:05:15Z',
    NULL,
    NULL,
    NULL,
    '2026-09-21T09:05:15Z',
    '2026-09-21T09:05:15Z'
);
