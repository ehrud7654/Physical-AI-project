-- Align the deployable robot model contract with the official UMI diffusion pipeline.
-- Trainer-owned metadata is stored in SkillVersion and forwarded unchanged to Jetson.

UPDATE robot_profiles
SET camera_contract = jsonb_build_object(
      'cameraNames', jsonb_build_array('camera0_rgb')
    ),
    state_contract = jsonb_build_object(
      'camera0_rgb', jsonb_build_object('shape', jsonb_build_array(2, 3, 224, 224)),
      'robot0_eef_pos', jsonb_build_object('shape', jsonb_build_array(2, 3)),
      'robot0_eef_rot_axis_angle', jsonb_build_object('shape', jsonb_build_array(2, 6)),
      'robot0_eef_rot_axis_angle_wrt_start', jsonb_build_object('shape', jsonb_build_array(2, 6)),
      'robot0_gripper_width', jsonb_build_object('shape', jsonb_build_array(2, 1))
    ),
    action_contract = jsonb_build_object(
      'actionSpace', 'EEF_RELATIVE_ROT6D',
      'actionSpec', jsonb_build_object(
        'schemaVersion', 1,
        'dim', 10,
        'horizon', 16,
        'nActionSteps', 8,
        'rateHz', 10,
        'layout', jsonb_build_array(
          'dx', 'dy', 'dz', 'r00', 'r01', 'r02', 'r10', 'r11', 'r12', 'gap_m'
        ),
        'rotation', 'ROTATION_MATRIX_ROWS_0_1',
        'compose', 'T_next = T_cur @ A_relative',
        'gapUnit', 'm',
        'gapRange', jsonb_build_array(0, 0.09)
      ),
      'runtimeSpec', jsonb_build_object(
        'consumerMustConvert', true,
        'convertedDim', 7,
        'convertedLayout', jsonb_build_array('x', 'y', 'z', 'rx', 'ry', 'rz', 'gap_m'),
        'convertedRotation', 'AXIS_ANGLE_ROTVEC',
        'convertedFrame', 'ABSOLUTE',
        'execSlice', jsonb_build_object('startInclusive', 1, 'endExclusive', 5),
        'execSliceAppliesTo', 'converted',
        'actionPointRateHz', 10,
        'reobserveRateHz', 2.5
      ),
      'outputSchema', jsonb_build_object('actionShape', jsonb_build_array(16, 10))
    ),
    updated_at = now()
WHERE id = '52000000-0000-4000-8000-000000000001';

COMMENT ON COLUMN robot_profiles.state_contract IS
  'Trainer-defined observation schema validated against CKPT inputSchema.';

COMMENT ON COLUMN robot_profiles.action_contract IS
  'Trainer-defined actionSpec, runtimeSpec, and outputSchema validated against CKPT metadata.';
