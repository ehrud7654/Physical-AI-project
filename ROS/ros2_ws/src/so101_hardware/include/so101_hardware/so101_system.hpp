// SO101System — ros2_control SystemInterface 플러그인.
// 컨트롤러(팔=rad, 그리퍼=미터)와 서보(tick) 사이 얇은 래퍼. IK 없음(정책 노드가 함).
//   write: command → tick → IServoBus     read: IServoBus → tick → state
// 서보 통신은 IServoBus 구현체(FakeBus=테스트 / St3215ServoBus=실물)로 갈아끼움.
#ifndef SO101_HARDWARE__SO101_SYSTEM_HPP_
#define SO101_HARDWARE__SO101_SYSTEM_HPP_

#include <cstdint>
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
  hardware_interface::CallbackReturn on_configure(
    const rclcpp_lifecycle::State & previous_state) override;
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
  // 관절 1개의 런타임 정보. 그리퍼는 단위가 미터라 별도 캘리브.
  struct JointRuntime
  {
    std::string name;
    std::uint8_t servo_id;
    bool is_gripper;
    JointCalib arm;       // is_gripper==false 일 때 유효 (rad↔tick)
    GripperCalib grip;    // is_gripper==true  일 때 유효 (width[m]↔tick)
  };

  std::vector<JointRuntime> joints_;    // info_.joints 순서
  std::vector<std::uint8_t> servo_ids_;  // joints_ 순서의 서보 ID
  std::vector<double> pos_state_;       // 상태: 팔=rad, 그리퍼=m
  std::vector<double> pos_command_;     // 명령: 팔=rad, 그리퍼=m

  std::string bus_type_{"fake"};        // fake | st3215
  std::string port_{"/dev/ttyTHS1"};
  int baudrate_{1000000};
  std::uint16_t speed_{300};
  std::uint8_t acceleration_{10};
  std::unique_ptr<IServoBus> bus_;

  // 일시적 UART 글리치 견딤 — 한 번 실패는 이전 상태 유지, 연속 실패만 ERROR.
  int read_fail_count_{0};
  int write_fail_count_{0};
};

}  // namespace so101_hardware

#endif  // SO101_HARDWARE__SO101_SYSTEM_HPP_
