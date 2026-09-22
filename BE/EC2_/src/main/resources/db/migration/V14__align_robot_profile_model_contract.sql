UPDATE robot_profiles
SET camera_contract = jsonb_build_object(
      'cameraNames', jsonb_build_array('camera0_rgb')
    ),
    state_contract = jsonb_build_object(
      'stateShape', jsonb_build_array(10),
      'layout', jsonb_build_array(
        'robot0_eef_pos_x',
        'robot0_eef_pos_y',
        'robot0_eef_pos_z',
        'robot0_eef_rot_axis_angle_x',
        'robot0_eef_rot_axis_angle_y',
        'robot0_eef_rot_axis_angle_z',
        'robot0_eef_rot_axis_angle_wrt_start_x',
        'robot0_eef_rot_axis_angle_wrt_start_y',
        'robot0_eef_rot_axis_angle_wrt_start_z',
        'robot0_gripper_width'
      )
    ),
    updated_at = now()
WHERE id = '52000000-0000-4000-8000-000000000001';
