"""SO-101 ros2_control bringup.

controller_manager + robot_state_publisher 띄우고
joint_state_broadcaster + forward_position_controller 스폰.

  ros2 launch so101_bringup bringup.launch.py          # FakeBus(기본)
  ros2 launch so101_bringup bringup.launch.py bus:=st3215   # 151 완료 후 실물
"""
from launch import LaunchDescription
from launch.actions import DeclareLaunchArgument
from launch.substitutions import Command, FindExecutable, LaunchConfiguration, PathJoinSubstitution
from launch_ros.actions import Node
from launch_ros.parameter_descriptions import ParameterValue
from launch_ros.substitutions import FindPackageShare


def generate_launch_description():
    bus = LaunchConfiguration("bus")
    pkg = FindPackageShare("so101_bringup")

    robot_description = {
        "robot_description": ParameterValue(Command([
            FindExecutable(name="xacro"), " ",
            PathJoinSubstitution([pkg, "urdf", "so101.urdf.xacro"]),
            " bus:=", bus,
        ]), value_type=str)
    }
    controllers = PathJoinSubstitution([pkg, "config", "controllers.yaml"])

    control_node = Node(
        package="controller_manager",
        executable="ros2_control_node",
        parameters=[robot_description, controllers],
        output="screen",
    )
    rsp_node = Node(
        package="robot_state_publisher",
        executable="robot_state_publisher",
        parameters=[robot_description],
        output="screen",
    )
    # 스폰: 브로드캐스터 먼저, 그다음 명령 컨트롤러.
    jsb = Node(
        package="controller_manager", executable="spawner",
        arguments=["joint_state_broadcaster", "-c", "/controller_manager"],
    )
    fwd = Node(
        package="controller_manager", executable="spawner",
        arguments=["forward_position_controller", "-c", "/controller_manager"],
    )

    return LaunchDescription([
        DeclareLaunchArgument("bus", default_value="fake",
                              description="fake | st3215(151 완료 후)"),
        control_node, rsp_node, jsb, fwd,
    ])
