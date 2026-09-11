ALTER TABLE skills
    ADD COLUMN owner_id uuid;

ALTER TABLE skills
    ADD CONSTRAINT fk_skills_owner
        FOREIGN KEY (owner_id)
        REFERENCES users (id)
        ON DELETE RESTRICT;

ALTER TABLE skills
    DROP CONSTRAINT skills_tier_check;

ALTER TABLE skills
    ADD CONSTRAINT ck_skills_tier
        CHECK (tier IN ('TUTORIAL', 'DEMO', 'CUSTOM'));

ALTER TABLE skills
    ADD CONSTRAINT ck_skills_owner_scope
        CHECK (
            (owner_id IS NULL AND tier IN ('TUTORIAL', 'DEMO'))
            OR (owner_id IS NOT NULL AND tier = 'CUSTOM')
        );

CREATE UNIQUE INDEX uq_skills_owner_display_name_lower
    ON skills (owner_id, lower(display_name))
    WHERE owner_id IS NOT NULL;

CREATE INDEX ix_skills_owner_created
    ON skills (owner_id, created_at DESC)
    WHERE owner_id IS NOT NULL;

COMMENT ON COLUMN skills.owner_id IS
    '커스텀 스킬 소유 사용자. NULL이면 시스템 공통 스킬';
