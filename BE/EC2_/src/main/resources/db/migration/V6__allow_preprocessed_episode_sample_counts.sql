ALTER TABLE episodes
    DROP CONSTRAINT IF EXISTS episodes_pose_sample_count_check;

ALTER TABLE episodes
    ADD CONSTRAINT episodes_pose_sample_count_check
        CHECK (pose_sample_count >= 0);

COMMENT ON COLUMN episodes.pose_sample_count IS
    'Raw UMI archive has no pose.csv; zero until SLAM preprocessing produces poses';

COMMENT ON COLUMN episodes.imu_sample_count IS
    'accelerometer.csv and gyroscope.csv data row count sum';

COMMENT ON COLUMN episodes.gripper_sample_count IS
    'Raw UMI archive has no gripper.csv; zero until marker preprocessing produces the channel';
