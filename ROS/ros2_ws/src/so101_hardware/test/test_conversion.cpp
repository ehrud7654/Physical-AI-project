// rad<->tick 왕복 self-check. ROS 안 띄우고 순수 컴파일로 도는 최소 검증.
#include <cassert>
#include <cmath>
#include <cstdio>

#include "so101_hardware/conversion.hpp"

using so101_hardware::GripperCalib;
using so101_hardware::JointCalib;
using so101_hardware::rad_to_tick;
using so101_hardware::tick_to_rad;
using so101_hardware::tick_to_width;
using so101_hardware::width_to_tick;

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

  // 5) 그리퍼 width↔tick (미터). closed_tick=1720(width0), open_tick=70(width max)
  GripperCalib g;  // 기본: closed 1720, open 70, 0..4095, max_width 0.09
  g.min_tick = 70; g.max_tick = 1720;
  assert(width_to_tick(0.0, g) == 1720);          // 닫힘
  assert(width_to_tick(0.09, g) == 70);           // 완전 개방
  // 왕복
  for (double w = 0.0; w <= 0.09; w += 0.005) {
    int32_t t = width_to_tick(w, g);
    double back = tick_to_width(t, g);
    assert(std::abs(back - w) < 0.001);           // 1mm 이내
    assert(t >= 70 && t <= 1720);                 // 리밋 내
  }
  // 범위 밖 clamp
  assert(width_to_tick(1.0, g) == 70);            // 과개방 → open clamp
  assert(width_to_tick(-1.0, g) == 1720);         // 음수 → closed clamp

  std::printf("test_conversion: OK\n");
  return 0;
}
