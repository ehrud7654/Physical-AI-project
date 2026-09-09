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

}  // namespace so101_hardware

#endif  // SO101_HARDWARE__CONVERSION_HPP_
