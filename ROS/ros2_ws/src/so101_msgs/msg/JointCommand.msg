# IK 노드 → 드라이버. 목표 관절각.
#
# 단위: 라디안 (틱 변환은 드라이버 하드웨어 구현체가 so101.yaml 규칙으로).
# 순서: so101.yaml joint_order 와 일치
#       [shoulder_pan, shoulder_lift, elbow_flex, wrist_flex, wrist_roll, gripper]
#       팔 5관절 + 그리퍼 1. IK는 팔 5관절만 풀고, 그리퍼는 EECommand.gripper 통과.
#
# 잠정(provisional): 드라이버는 현재 sensor_msgs/JointState 사용. 이 msg로 이관은 선택.

std_msgs/Header header
float64[] positions          # 라디안, joint_order 순서
