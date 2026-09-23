ALTER TABLE training_job_episodes ADD COLUMN input_role varchar(20);

UPDATE training_job_episodes tje
SET input_role = CASE
    WHEN COALESCE(
        NULLIF(e.quality_summary -> 'values' ->> 'datasetRole', ''),
        NULLIF(ea.manifest_summary -> 'details' ->> 'datasetRole', '')
    ) = 'MAPPING' THEN 'MAPPING'
    ELSE 'DEMONSTRATION'
END
FROM episodes e
JOIN episode_archives ea ON ea.episode_id = e.id
WHERE e.id = tje.episode_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM training_job_episodes
        WHERE input_role IS NULL OR input_role NOT IN ('DEMONSTRATION', 'MAPPING')
    ) THEN
        RAISE EXCEPTION 'training_job_episodes contains rows without a supported datasetRole';
    END IF;
END $$;

ALTER TABLE training_job_episodes
    ALTER COLUMN input_role SET NOT NULL,
    ADD CONSTRAINT ck_training_job_episode_input_role
        CHECK (input_role IN ('DEMONSTRATION', 'MAPPING'));

CREATE UNIQUE INDEX uq_training_job_one_mapping_episode
    ON training_job_episodes (training_job_id)
    WHERE input_role = 'MAPPING';
