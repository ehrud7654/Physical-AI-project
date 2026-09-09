// FakeBus — 실물·st3215_bus(151)·AI 확정 없이 launch 를 돌리기 위한 스텁.
// 쓴 tick 을 그대로 기억해서 read 로 되돌려준다(1차 지연 없는 완벽 추종 모델).
#ifndef SO101_HARDWARE__FAKE_BUS_HPP_
#define SO101_HARDWARE__FAKE_BUS_HPP_

#include <cstdint>
#include <unordered_map>
#include <vector>

#include "so101_hardware/servo_bus.hpp"

namespace so101_hardware
{

class FakeBus : public IServoBus
{
public:
  bool ping(const std::vector<uint8_t> &) override { return true; }

  std::vector<int32_t> read_positions(const std::vector<uint8_t> & ids) override
  {
    std::vector<int32_t> out;
    out.reserve(ids.size());
    for (auto id : ids) {
      out.push_back(state_.count(id) ? state_[id] : 2048);  // 초기 = 센터
    }
    return out;
  }

  void write_positions(
    const std::vector<uint8_t> & ids, const std::vector<int32_t> & ticks) override
  {
    for (size_t i = 0; i < ids.size(); ++i) {
      state_[ids[i]] = ticks[i];
    }
  }

  void set_torque(const std::vector<uint8_t> &, bool) override {}

private:
  std::unordered_map<uint8_t, int32_t> state_;
};

}  // namespace so101_hardware

#endif  // SO101_HARDWARE__FAKE_BUS_HPP_
