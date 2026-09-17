ALTER TABLE training_jobs
    ADD COLUMN training_mode varchar(30) NOT NULL DEFAULT 'FROM_SCRATCH',
    ADD COLUMN base_skill_version_id uuid;

ALTER TABLE skill_versions
    ADD COLUMN parent_skill_version_id uuid;

ALTER TABLE training_jobs
    ADD CONSTRAINT ck_training_jobs_training_mode
        CHECK (training_mode IN ('FROM_SCRATCH', 'FINE_TUNE')),
    ADD CONSTRAINT ck_training_jobs_base_model
        CHECK (
            (training_mode = 'FROM_SCRATCH' AND base_skill_version_id IS NULL)
            OR (training_mode = 'FINE_TUNE' AND base_skill_version_id IS NOT NULL)
        ),
    ADD CONSTRAINT fk_training_jobs_base_skill_version
        FOREIGN KEY (base_skill_version_id) REFERENCES skill_versions(id) ON DELETE RESTRICT;

ALTER TABLE skill_versions
    ADD CONSTRAINT fk_skill_versions_parent
        FOREIGN KEY (parent_skill_version_id) REFERENCES skill_versions(id) ON DELETE RESTRICT;

CREATE INDEX ix_training_jobs_base_skill_version
    ON training_jobs (base_skill_version_id)
    WHERE base_skill_version_id IS NOT NULL;

CREATE INDEX ix_skill_versions_parent
    ON skill_versions (parent_skill_version_id)
    WHERE parent_skill_version_id IS NOT NULL;
