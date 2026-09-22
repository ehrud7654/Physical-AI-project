-- CaptureMetadata is mapped to a fixed value object containing only
-- device, camera, coordinateSystem, and units. The Test dataset role remains
-- available in quality_summary.values.datasetRole and archive manifest details.
UPDATE episodes
SET capture_metadata = capture_metadata - 'datasetRole',
    updated_at = CURRENT_TIMESTAMP
WHERE skill_id = '54000000-0000-4000-8000-000000000003'
  AND capture_metadata ? 'datasetRole';
