// St3215ServoBus — 어댑터. 팀원 ST3215Bus 를 우리 IServoBus 계약으로 감싼다.
//   상속(is-a): IServoBus 를 구현 → SO101System 이 IServoBus 로 들고 씀
//   합성(has-a): 안에 ST3215Bus 를 품고 호출을 번역 (map<id,tick> ↔ vector, 등)
#ifndef SO101_HARDWARE__ST3215_SERVO_BUS_HPP_
#define SO101_HARDWARE__ST3215_SERVO_BUS_HPP_

#include <algorithm>
#include <cstdint>
#include <map>
#include <string>
#include <vector>

#include "so101_hardware/servo_bus.hpp"
#include "so101_hardware/st3215_bus.hpp"

namespace so101_hardware
{

class St3215ServoBus : public IServoBus
{
public:
  St3215ServoBus(
    const std::string & port, int baudrate,
    std::uint16_t speed = 300, std::uint8_t accel = 10)
  : bus_(port, baudrate), speed_(speed), accel_(accel) {}

  bool ping(const std::vector<uint8_t> & ids) override
  {
    try {
      for (auto id : ids) {
        bus_.ping(id);      // ST3215Bus::ping 은 실패 시 ServoError throw
      }
      return true;
    } catch (const ServoError &) {
      return false;
    }
  }

  std::vector<int32_t> read_positions(const std::vector<uint8_t> & ids) override
  {
    const auto m = bus_.read_positions(ids);       // map<id,tick>
    std::vector<int32_t> out;
    out.reserve(ids.size());
    for (auto id : ids) {
      out.push_back(static_cast<int32_t>(m.at(id)));   // ids 순서로 정렬
    }
    return out;
  }

  void write_positions(
    const std::vector<uint8_t> & ids, const std::vector<int32_t> & ticks) override
  {
    std::map<std::uint8_t, std::uint16_t> targets;
    for (size_t i = 0; i < ids.size(); ++i) {
      // 상위(SO101System)에서 이미 0..4095 clamp 됨. 방어적으로 한 번 더.
      const int32_t t = std::clamp(ticks[i], 0, 4095);
      targets.emplace(ids[i], static_cast<std::uint16_t>(t));
    }
    bus_.write_positions(targets, speed_, accel_);   // Sync Write
  }

  void set_torque(const std::vector<uint8_t> & ids, bool on) override
  {
    bus_.set_torque_all(ids, on);
  }

private:
  ST3215Bus bus_;
  std::uint16_t speed_;
  std::uint8_t accel_;
};

}  // namespace so101_hardware

#endif  // SO101_HARDWARE__ST3215_SERVO_BUS_HPP_
