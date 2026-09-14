ALTER TABLE episodes
    DROP CONSTRAINT IF EXISTS episodes_gripper_sample_count_check;

ALTER TABLE episodes
    ADD CONSTRAINT ck_episodes_gripper_sample_count
    CHECK (gripper_sample_count >= 0);

ALTER TABLE episode_archives
    DROP CONSTRAINT IF EXISTS episode_archives_entry_count_check;

ALTER TABLE episode_archives
    ADD CONSTRAINT ck_episode_archives_entry_count
    CHECK (entry_count >= 6);

COMMENT ON COLUMN episodes.gripper_sample_count IS
    'gripper.csv data row count; zero when gripper data is deferred to PC post-processing';
