ALTER TABLE skill_versions
  ADD COLUMN runtime_spec jsonb NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN skill_versions.runtime_spec IS
  'Runtime conversion and execution contract. EC2 stores and forwards this JSON without interpreting its fields.';

UPDATE robot_profiles
SET action_contract = action_contract || jsonb_build_object(
  'actionSpec', jsonb_build_object(
    'schemaVersion', 1,
    'dim', 10,
    'horizon', 8,
    'rateHz', 10,
    'rateNote', '공칭. v10 원본 간격이 균일하지 않다. 실제 시각은 observation_timestamp',
    'layout', jsonb_build_array('dx','dy','dz','r00','r01','r02','r10','r11','r12','gap_m'),
    'rotation', 'ROTATION_MATRIX_ROWS_0_1',
    'compose', 'T_next = T_cur @ A_relative',
    'gapUnit', 'm',
    'gapRange', jsonb_build_array(0, 0.09)
  ),
  'runtimeSpec', jsonb_build_object(
    'consumerMustConvert', true,
    'convertedDim', 7,
    'convertedLayout', jsonb_build_array('x','y','z','rx','ry','rz','gap_m'),
    'convertedRotation', 'AXIS_ANGLE_ROTVEC',
    'convertedFrame', 'ABSOLUTE',
    'execSlice', jsonb_build_object('startInclusive', 1, 'endExclusive', 5),
    'execSliceAppliesTo', 'converted',
    'actionPointRateHz', 10,
    'reobserveRateHz', 2.5
  )
)
WHERE action_contract ->> 'actionSpace' = 'EEF_RELATIVE_ROT6D';
