// IServoBus — SO101System(132, ROS) 과 st3215_bus(151, HW) 사이의 계약.
//
// 틱 단위. servo_control.py 의 ST3215Bus 공개 메서드를 그대로 미러한다.
// 151 담당은 이 인터페이스를 구현하면 됨(UART/프로토콜은 그쪽). ROS 는 모름.
#ifndef SO101_HARDWARE__SERVO_BUS_HPP_
#define SO101_HARDWARE__SERVO_BUS_HPP_

#include <cstdint>
#include <vector>

namespace so101_hardware
{

// 모든 벡터는 관절 순서(so101.yaml index 0..5)로 정렬. ids 는 그 순서의 서보 ID.
class IServoBus
{
public:
  virtual ~IServoBus() = default;

  // 서보 응답 확인. 전부 살아있으면 true.
  virtual bool ping(const std::vector<uint8_t> & ids) = 0;

  // 현재 위치 읽기 → tick. servo_control.py read_positions 대응.
  virtual std::vector<int32_t> read_positions(const std::vector<uint8_t> & ids) = 0;

  // 목표 위치 쓰기(tick). Sync Write 로 동시. servo_control.py write_positions 대응.
  // 30Hz 스트림이라 per-move speed/accel 안 넘김 — st3215 내부 기본값(최대) 사용.
  virtual void write_positions(
    const std::vector<uint8_t> & ids, const std::vector<int32_t> & ticks) = 0;

  // 토크 on/off. estop·활성화용. servo_control.py set_torque_all 대응.
  virtual void set_torque(const std::vector<uint8_t> & ids, bool on) = 0;
};

}  // namespace so101_hardware

#endif  // SO101_HARDWARE__SERVO_BUS_HPP_
