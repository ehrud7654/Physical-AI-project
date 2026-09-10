-- config_sha256 is a digest, not one of the JSON document's field names.
-- The original CHECK made every valid SHA-256 value impossible to insert.
DO $$
DECLARE invalid_constraint text;
BEGIN
    SELECT conname INTO invalid_constraint
    FROM pg_constraint
    WHERE conrelid = 'training_profiles'::regclass
      AND contype = 'c'
      AND pg_get_constraintdef(oid) LIKE '%config_sha256 IN (%algorithm%hyperparameters%';

    IF invalid_constraint IS NOT NULL THEN
        EXECUTE format('ALTER TABLE training_profiles DROP CONSTRAINT %I', invalid_constraint);
    END IF;
END $$;
