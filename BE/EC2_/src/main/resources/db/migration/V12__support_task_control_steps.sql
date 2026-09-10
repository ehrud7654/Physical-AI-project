ALTER TABLE task_steps
    DROP CONSTRAINT task_steps_task_version_id_step_order_key,
    ALTER COLUMN skill_version_id DROP NOT NULL,
    ADD COLUMN parent_step_id uuid,
    ADD COLUMN step_type varchar(20) DEFAULT 'SKILL' NOT NULL,
    ADD COLUMN repeat_count integer;

ALTER TABLE task_steps
    ADD CONSTRAINT fk_task_steps_parent
        FOREIGN KEY (parent_step_id) REFERENCES task_steps(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_task_steps_type
        CHECK (step_type IN ('SKILL', 'MOTOR', 'REPEAT')),
    ADD CONSTRAINT ck_task_steps_payload
        CHECK (
            (step_type = 'SKILL' AND skill_version_id IS NOT NULL AND repeat_count IS NULL)
            OR (step_type = 'MOTOR' AND skill_version_id IS NULL AND repeat_count IS NULL)
            OR (step_type = 'REPEAT' AND skill_version_id IS NULL AND repeat_count >= 1)
        ),
    ADD CONSTRAINT ck_task_steps_not_self_parent
        CHECK (parent_step_id IS NULL OR parent_step_id <> id);

CREATE UNIQUE INDEX uq_task_steps_sibling_order
    ON task_steps (task_version_id, COALESCE(parent_step_id, '00000000-0000-0000-0000-000000000000'::uuid), step_order);

CREATE INDEX ix_task_steps_parent ON task_steps(parent_step_id);

COMMENT ON COLUMN task_steps.parent_step_id IS 'REPEAT 단계에 포함된 경우 부모 task_steps.id';
COMMENT ON COLUMN task_steps.step_type IS 'SKILL, MOTOR, REPEAT';
COMMENT ON COLUMN task_steps.repeat_count IS 'REPEAT 단계 반복 횟수';
