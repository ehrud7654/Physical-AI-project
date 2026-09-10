// rad <-> tick 변환 — 단일 출처. SO101System 과 self-check 가 공용으로 쓴다.
//
// 계층: 정책(EE pose)→IK(AI)→관절각[rad]→ros2_control→[여기서 tick]→st3215_bus→서보
// tick 은 STS3215 전용(0~4095). ROS 경계는 라디안. 변환은 이 한 곳에서만.
#ifndef SO101_HARDWARE__CONVERSION_HPP_
#define SO101_HARDWARE__CONVERSION_HPP_

#include <algorithm>
#include <cmath>
#include <cstdint>

namespace so101_hardware
{

// 관절 1개의 캘리브. URDF <joint><param> 에서 읽는다(로봇마다 프로필 다름 → 온보딩).
// ⚠️ zero_tick·direction 은 실물 펌웨어 캘리브 확인 전까지 미검증. FakeBus 는 안 씀.
struct JointCalib
{
  int32_t zero_tick = 2048;        // rad=0 일 때의 tick (센터)
  double ticks_per_rad = 4096.0 / (2.0 * M_PI);  // STS3215 직결, tick_per_turn=4096
  int8_t direction = 1;            // +1 / -1 (서보 장착 방향)
  int32_t min_tick = 0;            // per-joint 소프트 리밋(캘리브). 하드리밋(0~4095)은 서보펌웨어
  int32_t max_tick = 4095;
};

inline int32_t rad_to_tick(double rad, const JointCalib & c)
{
  int32_t t = c.zero_tick +
    static_cast<int32_t>(std::lround(rad * c.ticks_per_rad * c.direction));
  return std::clamp(t, c.min_tick, c.max_tick);
}

inline double tick_to_rad(int32_t tick, const JointCalib & c)
{
  return static_cast<double>(tick - c.zero_tick) / (c.ticks_per_rad * c.direction);
}

// 그리퍼 캘리브 — 팔과 달리 명령/상태 단위가 **미터(손가락 간격 width)** 다 (AI 계약).
// closed_tick(닫힘)~open_tick(열림) tick 이 width 0~max_width_m 에 대응.
struct GripperCalib
{
  int32_t closed_tick = 1720;   // width=0 (닫힘)
  int32_t open_tick = 70;        // width=max_width_m (열림). 닫힘보다 작을 수 있음
  int32_t min_tick = 0;
  int32_t max_tick = 4095;
  double max_width_m = 0.09;      // 완전 개방 간격
};

// ponytail: width↔tick 선형 근사. 실제 gap 은 so101.yaml gap_curve 로 비선형이나,
// 파지 정밀도 필요해지면 gap_curve 보간으로 교체. 지금은 선형(팀원 방식)으로 충분.
inline int32_t width_to_tick(double width_m, const GripperCalib & c)
{
  double raw = c.closed_tick +
    (c.open_tick - c.closed_tick) * width_m / c.max_width_m;
  return std::clamp(static_cast<int32_t>(std::lround(raw)), c.min_tick, c.max_tick);
}

inline double tick_to_width(int32_t tick, const GripperCalib & c)
{
  return static_cast<double>(tick - c.closed_tick) * c.max_width_m /
         (c.open_tick - c.closed_tick);
}

}  // namespace so101_hardware

#endif  // SO101_HARDWARE__CONVERSION_HPP_
