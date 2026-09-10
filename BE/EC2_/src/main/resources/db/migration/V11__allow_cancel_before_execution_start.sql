-- A REQUESTED execution may be canceled before Jetson acknowledges its start.
ALTER TABLE robot_executions
    DROP CONSTRAINT ck_robot_executions_started_at;

ALTER TABLE robot_executions
    ADD CONSTRAINT ck_robot_executions_started_at CHECK (
        status IN ('REQUESTED', 'CANCELED') OR started_at IS NOT NULL
    );
