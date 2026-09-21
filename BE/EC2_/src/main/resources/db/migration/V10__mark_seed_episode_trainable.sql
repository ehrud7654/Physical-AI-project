-- Keep V9 immutable for databases where it has already been applied.
-- The normalized seed archive has since been verified as a selectable
-- demonstration input, so promote it in a follow-up migration.

UPDATE episodes
SET success = true,
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'a14071bf-3677-46bf-aa2b-e32bea650982'
  AND deleted_at IS NULL;
