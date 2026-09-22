-- The official UMI policy applies [startInclusive, endExclusive) to the raw
-- (horizon, action-dimension) tensor before runtime representation conversion.
UPDATE robot_profiles
SET action_contract = jsonb_set(
      action_contract,
      '{runtimeSpec,execSliceAppliesTo}',
      '"raw"'::jsonb,
      false
    ),
    updated_at = now()
WHERE id = '52000000-0000-4000-8000-000000000001';
