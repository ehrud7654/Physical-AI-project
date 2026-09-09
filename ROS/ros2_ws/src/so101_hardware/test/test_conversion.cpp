// rad<->tick 왕복 self-check. ROS 안 띄우고 순수 컴파일로 도는 최소 검증.
#include <cassert>
#include <cmath>
#include <cstdio>

#include "so101_hardware/conversion.hpp"

using so101_hardware::JointCalib;
using so101_hardware::rad_to_tick;
using so101_hardware::tick_to_rad;

int main()
{
  JointCalib c;  // 기본: zero=2048, 4096/2pi, dir=+1, 0..4095

  // 1) 센터 왕복
  assert(rad_to_tick(0.0, c) == 2048);
  assert(std::abs(tick_to_rad(2048, c) - 0.0) < 1e-9);

  // 2) 왕복 오차 = 반올림 1 tick 이내
  for (double r = -1.5; r <= 1.5; r += 0.1) {
    int32_t t = rad_to_tick(r, c);
    double back = tick_to_rad(t, c);
    assert(std::abs(back - r) < (1.0 / c.ticks_per_rad) + 1e-9);
  }

  // 3) 방향 반전
  JointCalib inv = c; inv.direction = -1;
  assert(rad_to_tick(0.5, inv) < 2048);   // +rad 인데 dir=-1 → tick 감소
  assert(rad_to_tick(0.5, c) > 2048);

  // 4) 하드리밋 clamp (min/max_tick)
  assert(rad_to_tick(100.0, c) == 4095);
  assert(rad_to_tick(-100.0, c) == 0);

  std::printf("test_conversion: OK\n");
  return 0;
}
