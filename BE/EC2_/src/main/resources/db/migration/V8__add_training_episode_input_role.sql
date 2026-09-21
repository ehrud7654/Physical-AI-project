ALTER TABLE training_job_episodes
    ADD COLUMN input_role varchar(20);

UPDATE training_job_episodes
SET input_role = 'DEMONSTRATION'
WHERE input_role IS NULL;

ALTER TABLE training_job_episodes
    ALTER COLUMN input_role SET NOT NULL;

ALTER TABLE training_job_episodes
    ADD CONSTRAINT ck_training_job_episode_input_role
    CHECK (input_role IN ('DEMONSTRATION', 'MAPPING'));

CREATE UNIQUE INDEX uq_training_job_one_mapping_episode
    ON training_job_episodes (training_job_id)
    WHERE input_role = 'MAPPING';
