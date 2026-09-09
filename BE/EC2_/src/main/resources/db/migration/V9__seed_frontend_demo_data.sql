-- FE 연동 개발용 데모 데이터.
-- 실제 CKPT 객체는 생성하지 않으며, checkpoint_bucket/key는 메타데이터 fixture다.
-- 로그인 계정: admin01@ssafy.local / 12345678

-- 사용자
INSERT INTO users (id, email, password_hash, name, status, created_at, updated_at)
VALUES (
    '50000000-0000-4000-8000-000000000001',
    'admin01@ssafy.local',
    '$2a$10$ylKVWybCimE9yO2EWUnV4OesA4liqYxdzWmWeCyb4Mo65MpxSrQCu',
    'admin01',
    'ACTIVE',
    '2026-09-09T00:00:00Z',
    '2026-09-09T00:00:00Z'
);

-- 사용자의 UMI와 Robot용 Jetson 장치
INSERT INTO devices (
    id, owner_id, device_type, handle_number, name, connection_status,
    firmware_version, runtime_version, last_seen_at, created_at, updated_at
) VALUES
(
    '51000000-0000-4000-8000-000000000001',
    '50000000-0000-4000-8000-000000000001',
    'UMI_CAMERA', 'UMI-001', 'shanks umi', 'ONLINE',
    '1.0.0', '1.0.0', '2026-09-09T00:30:00Z',
    '2026-09-09T00:00:00Z', '2026-09-09T00:30:00Z'
),
(
    '51000000-0000-4000-8000-000000000002',
    '50000000-0000-4000-8000-000000000001',
    'JETSON', 'JETSON-001', 'Shanks', 'ONLINE',
    '1.0.0', 'ros2-humble', '2026-09-09T00:30:00Z',
    '2026-09-09T00:00:00Z', '2026-09-09T00:30:00Z'
);

-- 두 Skill은 시스템 공통 카탈로그이며, 아래 TrainingJob을 통해 사용자 소유 학습 결과와 연결된다.
INSERT INTO skills (
    id, skill_key, tier, status, display_name, description, persona, created_at, updated_at
) VALUES
(
    '54000000-0000-4000-8000-000000000001',
    'grab_blue_flag', 'TUTORIAL', 'DEPLOYED', '청기잡기',
    '청기를 잡는 학습 동작', NULL,
    '2026-09-09T00:00:00Z', '2026-09-09T00:10:00Z'
),
(
    '54000000-0000-4000-8000-000000000002',
    'raise_arm', 'TUTORIAL', 'DEPLOYED', '팔 올리기',
    '로봇팔을 위로 올리는 학습 동작', NULL,
    '2026-09-09T00:10:00Z', '2026-09-09T00:20:00Z'
);

-- SkillVersion과 Robot이 공유하는 모델 입출력 계약
INSERT INTO robot_profiles (
    id, name, contract_version, architecture, runtime, runtime_version,
    camera_contract, state_contract, action_contract, created_at, updated_at
) VALUES (
    '52000000-0000-4000-8000-000000000001',
    'demo-robot-arm', 'umi-policy-v1', 'jetson-orin-nano', 'ros2', 'humble',
    '{"cameraNames":["front"]}'::jsonb,
    '{"jointCount":6,"includesGripper":true}'::jsonb,
    '{"type":"JOINT_ABSOLUTE","jointCount":6,"includesGripper":true}'::jsonb,
    '2026-09-09T00:00:00Z', '2026-09-09T00:00:00Z'
);

-- 완료된 학습 작업. training_config 값은 현재 ACTIVE profile의 불변 snapshot을 사용한다.
INSERT INTO training_jobs (
    id, owner_id, skill_id, umi_device_id, training_profile_id,
    input_mode, status, attempt,
    training_config_snapshot, training_config_sha256, dataset_query,
    expected_episode_count, received_episode_count, received_bytes, episode_count,
    input_snapshot_sha256, progress_stage, progress_percent, current_epoch, total_epochs,
    created_at, started_at, model_completed_at, finished_at, updated_at, row_version
)
SELECT
    seed.id,
    '50000000-0000-4000-8000-000000000001'::uuid,
    seed.skill_id,
    '51000000-0000-4000-8000-000000000001'::uuid,
    profile.id,
    'EXISTING_EPISODES', 'SUCCEEDED', 1,
    jsonb_build_object(
        'profileKey', profile.profile_key,
        'profileVersion', profile.version,
        'algorithm', profile.algorithm,
        'hyperparameters', profile.hyperparameters
    ),
    profile.config_sha256,
    '{"seedFixture":true,"source":"DEVELOPMENT_SEED"}'::jsonb,
    0, 0, 0, 0,
    seed.input_hash,
    'COMPLETED', 100.00, 100, 100,
    seed.created_at, seed.started_at, seed.completed_at, seed.completed_at,
    seed.completed_at, 0
FROM training_profiles profile
CROSS JOIN (
    VALUES
        (
            '55000000-0000-4000-8000-000000000001'::uuid,
            '54000000-0000-4000-8000-000000000001'::uuid,
            repeat('1', 64),
            '2026-09-09T00:01:00Z'::timestamptz,
            '2026-09-09T00:02:00Z'::timestamptz,
            '2026-09-09T00:10:00Z'::timestamptz
        ),
        (
            '55000000-0000-4000-8000-000000000002'::uuid,
            '54000000-0000-4000-8000-000000000002'::uuid,
            repeat('2', 64),
            '2026-09-09T00:11:00Z'::timestamptz,
            '2026-09-09T00:12:00Z'::timestamptz,
            '2026-09-09T00:20:00Z'::timestamptz
        )
) AS seed(id, skill_id, input_hash, created_at, started_at, completed_at)
WHERE profile.status = 'ACTIVE';

-- 학습된 CKPT 메타데이터와 통과한 시뮬레이션 게이트 결과
INSERT INTO skill_versions (
    id, skill_id, training_job_id, training_profile_id, training_config_sha256,
    robot_profile_id, version, status,
    checkpoint_bucket, checkpoint_key, checkpoint_sha256, checkpoint_bytes,
    architecture, framework, framework_version, contract_version, action_space,
    n_params, control_rate_hz, camera_names, input_schema, output_schema,
    gate_metric, gate_n, gate_runs, gate_value, gate_ci_low, gate_ci_high,
    gate_measured_at, created_at, validated_at
)
SELECT
    seed.id, seed.skill_id, seed.training_job_id,
    profile.id, profile.config_sha256,
    '52000000-0000-4000-8000-000000000001'::uuid,
    1, 'READY',
    'ssafy-model-checkpoints', seed.checkpoint_key, seed.checkpoint_hash, seed.checkpoint_bytes,
    'ACT', 'PyTorch', '2.4.0', 'umi-policy-v1', 'JOINT_ABSOLUTE',
    45000000, 20.000, '["front"]'::jsonb,
    '{"camera":"RGB","state":"JOINT_POSITION"}'::jsonb,
    '{"action":"JOINT_ABSOLUTE","includesGripper":true}'::jsonb,
    'rollout_success', 100, 3, seed.gate_value, seed.gate_ci_low, seed.gate_ci_high,
    seed.completed_at, seed.completed_at, seed.completed_at
FROM training_profiles profile
CROSS JOIN (
    VALUES
        (
            '56000000-0000-4000-8000-000000000001'::uuid,
            '54000000-0000-4000-8000-000000000001'::uuid,
            '55000000-0000-4000-8000-000000000001'::uuid,
            'ckpt/50000000-0000-4000-8000-000000000001/grab_blue_flag/v1/grab_blue_flag_20260909T001000Z.ckpt',
            repeat('a', 64), 104857600::bigint,
            0.90::double precision, 0.82::double precision, 0.96::double precision,
            '2026-09-09T00:10:00Z'::timestamptz
        ),
        (
            '56000000-0000-4000-8000-000000000002'::uuid,
            '54000000-0000-4000-8000-000000000002'::uuid,
            '55000000-0000-4000-8000-000000000002'::uuid,
            'ckpt/50000000-0000-4000-8000-000000000001/raise_arm/v1/raise_arm_20260909T002000Z.ckpt',
            repeat('b', 64), 94371840::bigint,
            0.87::double precision, 0.78::double precision, 0.93::double precision,
            '2026-09-09T00:20:00Z'::timestamptz
        )
) AS seed(
    id, skill_id, training_job_id, checkpoint_key, checkpoint_hash, checkpoint_bytes,
    gate_value, gate_ci_low, gate_ci_high, completed_at
)
WHERE profile.status = 'ACTIVE';

-- 순환 FK는 SkillVersion 생성 후 결과 ID를 연결한다.
UPDATE training_jobs
SET result_skill_version_id = CASE id
        WHEN '55000000-0000-4000-8000-000000000001'::uuid
            THEN '56000000-0000-4000-8000-000000000001'::uuid
        WHEN '55000000-0000-4000-8000-000000000002'::uuid
            THEN '56000000-0000-4000-8000-000000000002'::uuid
    END
WHERE id IN (
    '55000000-0000-4000-8000-000000000001'::uuid,
    '55000000-0000-4000-8000-000000000002'::uuid
);

-- 사용자 소유 Robot
INSERT INTO robots (
    id, owner_id, jetson_device_id, robot_profile_id, name,
    connection_status, current_deployment_id, last_seen_at,
    created_at, updated_at, row_version
) VALUES (
    '53000000-0000-4000-8000-000000000001',
    '50000000-0000-4000-8000-000000000001',
    '51000000-0000-4000-8000-000000000002',
    '52000000-0000-4000-8000-000000000001',
    '청기 로봇팔', 'ONLINE', NULL, '2026-09-09T00:30:00Z',
    '2026-09-09T00:00:00Z', '2026-09-09T00:30:00Z', 0
);

-- 사용자 소유 Task와 불변 TaskVersion
INSERT INTO tasks (
    id, owner_id, task_key, display_name, description, latest_version, created_at, updated_at
) VALUES (
    '57000000-0000-4000-8000-000000000001',
    '50000000-0000-4000-8000-000000000001',
    'raise_blue_flag', '청기 들기',
    '청기를 잡고 팔을 올린 뒤 종료하는 작업', 1,
    '2026-09-09T00:25:00Z', '2026-09-09T00:25:00Z'
);

INSERT INTO task_versions (id, task_id, version, created_at)
VALUES (
    '57100000-0000-4000-8000-000000000001',
    '57000000-0000-4000-8000-000000000001',
    1, '2026-09-09T00:25:00Z'
);

-- 마지막 동작이 완료되면 Task 실행이 종료되므로 별도의 종료 step은 저장하지 않는다.
INSERT INTO task_steps (
    id, task_version_id, skill_version_id, step_order, parameters, timeout_seconds
) VALUES
(
    '57200000-0000-4000-8000-000000000001',
    '57100000-0000-4000-8000-000000000001',
    '56000000-0000-4000-8000-000000000001',
    1, '{}'::jsonb, 30
),
(
    '57200000-0000-4000-8000-000000000002',
    '57100000-0000-4000-8000-000000000001',
    '56000000-0000-4000-8000-000000000002',
    2, '{}'::jsonb, 30
);
