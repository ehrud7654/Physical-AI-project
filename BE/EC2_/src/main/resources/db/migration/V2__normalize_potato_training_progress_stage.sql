-- Normalize the completed potato skill seed without changing active training jobs.
UPDATE public.training_jobs
SET progress_stage = 'SUCCEEDED',
    updated_at = CURRENT_TIMESTAMP,
    row_version = row_version + 1
WHERE id = '55000000-0000-4000-8000-000000000003'
  AND skill_id = '54000000-0000-4000-8000-000000000003'
  AND result_skill_version_id = '56000000-0000-4000-8000-000000000003'
  AND status = 'SUCCEEDED'
  AND progress_stage = 'MODEL_VALIDATED';
