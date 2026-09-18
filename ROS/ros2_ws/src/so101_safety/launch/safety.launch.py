"""
안전 계층 실행 — safety 노드 + estop 브리지 (S15P21A103-49).

  ros2 launch so101_safety safety.launch.py config:=/path/to/so101.yaml

safety:       /joint_states 범위·워치독·수동 → /estop 발행
estop_bridge: /estop → SO101System deactivate(토크 off)

bringup(ros2_control)과 별도로 띄우거나, bringup launch에서 include 한다.
"""
import os

from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import LaunchConfiguration
from launch_ros.actions import Node


def generate_launch_description():
    default_cfg = os.path.expanduser("~/S15P21A103/AI/configs/so101.yaml")
    config = LaunchConfiguration("config")
    hw = LaunchConfiguration("hardware_component")

    return LaunchDescription([
        DeclareLaunchArgument("config", default_value=default_cfg,
                              description="so101.yaml (관절 리밋 출처)"),
        DeclareLaunchArgument("hardware_component", default_value="SO101System",
                              description="deactivate 대상 하드웨어(URDF <ros2_control name=>)"),
        Node(
            package="so101_safety", executable="safety", output="screen",
            parameters=[{"config": config}],
        ),
        Node(
            package="so101_safety", executable="estop_bridge", output="screen",
            parameters=[{"hardware_component": hw}],
        ),
    ])
