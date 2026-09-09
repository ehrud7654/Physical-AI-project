// SO101System — ros2_control SystemInterface 플러그인.
// 컨트롤러(라디안)와 서보(tick) 사이의 얇은 래퍼. IK 없음(AI가 이미 함).
//   write: command[rad] → tick → bus
//   read:  bus → tick → state[rad]
#ifndef SO101_HARDWARE__SO101_SYSTEM_HPP_
#define SO101_HARDWARE__SO101_SYSTEM_HPP_

#include <memory>
#include <string>
#include <vector>

#include "hardware_interface/system_interface.hpp"
#include "hardware_interface/types/hardware_interface_return_values.hpp"
#include "rclcpp_lifecycle/state.hpp"

#include "so101_hardware/conversion.hpp"
#include "so101_hardware/servo_bus.hpp"

namespace so101_hardware
{

class SO101System : public hardware_interface::SystemInterface
{
public:
  hardware_interface::CallbackReturn on_init(
    const hardware_interface::HardwareInfo & info) override;

  hardware_interface::CallbackReturn on_activate(
    const rclcpp_lifecycle::State & previous_state) override;
  hardware_interface::CallbackReturn on_deactivate(
    const rclcpp_lifecycle::State & previous_state) override;

  std::vector<hardware_interface::StateInterface> export_state_interfaces() override;
  std::vector<hardware_interface::CommandInterface> export_command_interfaces() override;

  hardware_interface::return_type read(
    const rclcpp::Time & time, const rclcpp::Duration & period) override;
  hardware_interface::return_type write(
    const rclcpp::Time & time, const rclcpp::Duration & period) override;

private:
  std::unique_ptr<IServoBus> bus_;
  std::vector<uint8_t> servo_ids_;      // 관절 순서(0..5)의 서보 ID
  std::vector<JointCalib> calib_;       // 관절별 캘리브(URDF param)
  std::vector<double> pos_state_;       // 상태[rad]
  std::vector<double> pos_command_;     // 명령[rad]
};

}  // namespace so101_hardware

#endif  // SO101_HARDWARE__SO101_SYSTEM_HPP_
