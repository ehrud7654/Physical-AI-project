-- Make the seeded "구운감자 옮기기" skill executable.
--
-- V20 created a validated READY model version and a SUCCEEDED training job, but
-- left the parent skill in PLANNED.  The user-facing skill overview exposes
-- skills.status as deploymentStatus, so all three records must describe the
-- same completed state.

UPDATE skill_versions
SET status = 'READY',
    validated_at = COALESCE(validated_at, CURRENT_TIMESTAMP),
    archived_at = NULL
WHERE id = '56000000-0000-4000-8000-000000000003'
  AND skill_id = '54000000-0000-4000-8000-000000000003';

UPDATE training_jobs
SET status = 'SUCCEEDED',
    progress_stage = 'MODEL_VALIDATED',
    progress_percent = 100.00,
    result_skill_version_id = '56000000-0000-4000-8000-000000000003',
    model_completed_at = COALESCE(model_completed_at, CURRENT_TIMESTAMP),
    finished_at = COALESCE(finished_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
WHERE id = '55000000-0000-4000-8000-000000000003'
  AND skill_id = '54000000-0000-4000-8000-000000000003'
  AND EXISTS (
      SELECT 1
      FROM skill_versions sv
      WHERE sv.id = '56000000-0000-4000-8000-000000000003'
        AND sv.skill_id = training_jobs.skill_id
        AND sv.status = 'READY'
        AND sv.archived_at IS NULL
  );

UPDATE skills
SET status = 'DEPLOYED',
    updated_at = CURRENT_TIMESTAMP
WHERE id = '54000000-0000-4000-8000-000000000003'
  AND EXISTS (
      SELECT 1
      FROM skill_versions sv
      JOIN training_jobs tj ON tj.id = sv.training_job_id
      WHERE sv.skill_id = skills.id
        AND sv.id = '56000000-0000-4000-8000-000000000003'
        AND sv.status = 'READY'
        AND sv.archived_at IS NULL
        AND tj.status = 'SUCCEEDED'
        AND tj.result_skill_version_id = sv.id
  );
