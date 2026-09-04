# 정책 노드 → IK 노드. 다음 스텝 목표 EE pose.
#
# 프레임: 로봇 베이스 좌표계, 절대(absolute) 목표.
# 액션 표현: 정책은 델타(상대) + 6D 회전으로 학습하지만, 실행 시 현재 pose에
#            누적해 절대 목표로 발행한다. (델타는 학습/정책 내부, 와이어=절대 목표)
# 회전: geometry_msgs/Pose는 쿼터니언. 6D 표현↔쿼터니언 변환은 정책 노드가 처리.
#
# 잠정(provisional): 액션 공간 확정(S15P21A103-108, 계약 episode.py) 전. 바뀌면 여기 수정.

std_msgs/Header header
geometry_msgs/Pose pose      # 위치(m) + 회전(quaternion)
float64 gripper              # 0=닫힘 .. 1=열림
