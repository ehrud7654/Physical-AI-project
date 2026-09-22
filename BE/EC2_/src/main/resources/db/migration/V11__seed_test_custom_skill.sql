-- Seed a custom skill using the same values assigned by POST /api/v1/skills.
-- The identifiers are fixed so the catalog entry remains stable across environments.

INSERT INTO skills (
    id,
    skill_key,
    tier,
    status,
    display_name,
    description,
    persona,
    created_at,
    updated_at,
    owner_id
) VALUES (
    '54000000-0000-4000-8000-000000000003',
    'custom_00000000000040008000000000000003',
    'CUSTOM',
    'PLANNED',
    'Test',
    '플랫폼 동작 확인용 Test 스킬입니다',
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    '50000000-0000-4000-8000-000000000001'
);
