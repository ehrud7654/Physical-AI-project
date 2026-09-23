-- Keep the seeded baked-potato skill description aligned with the product copy.
UPDATE skills
SET description = '구운감자 포장용 동작',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '54000000-0000-4000-8000-000000000003'
  AND skill_key = 'custom_00000000000040008000000000000003';
