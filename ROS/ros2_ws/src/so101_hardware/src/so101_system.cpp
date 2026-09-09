#include "so101_hardware/so101_system.hpp"

#include <limits>
#include <string>
#include <vector>

#include "hardware_interface/types/hardware_interface_type_values.hpp"
#include "pluginlib/class_list_macros.hpp"
#include "rclcpp/rclcpp.hpp"

#include "so101_hardware/fake_bus.hpp"

namespace so101_hardware
{

namespace
{
auto LOGGER = rclcpp::get_logger("SO101System");

// URDF param 헬퍼: 있으면 파싱, 없으면 기본값.
template <typename T>
T get_param(const std::unordered_map<std::string, std::string> & p,
  const std::string & key, T fallback);

template <>
double get_param(const std::unordered_map<std::string, std::string> & p,
  const std::string & key, double fb)
{ auto it = p.find(key); return it == p.end() ? fb : std::stod(it->second); }

template <>
int get_param(const std::unordered_map<std::string, std::string> & p,
  const std::string & key, int fb)
{ auto it = p.find(key); return it == p.end() ? fb : std::stoi(it->second); }
}  // namespace

hardware_interface::CallbackReturn SO101System::on_init(
  const hardware_interface::HardwareInfo & info)
{
  if (SystemInterface::on_init(info) != hardware_interface::CallbackReturn::SUCCESS) {
    return hardware_interface::CallbackReturn::ERROR;
  }

  const size_t n = info_.joints.size();
  pos_state_.assign(n, 0.0);
  pos_command_.assign(n, 0.0);
  servo_ids_.resize(n);
  calib_.resize(n);

  for (size_t i = 0; i < n; ++i) {
    const auto & jp = info_.joints[i].parameters;
    servo_ids_[i] = static_cast<uint8_t>(get_param<int>(jp, "servo_id", static_cast<int>(i + 1)));
    calib_[i].zero_tick = get_param<int>(jp, "zero_tick", 2048);
    calib_[i].ticks_per_rad = get_param<double>(jp, "ticks_per_rad", 4096.0 / (2.0 * M_PI));
    calib_[i].direction = static_cast<int8_t>(get_param<int>(jp, "direction", 1));
    calib_[i].min_tick = get_param<int>(jp, "min_tick", 0);
    calib_[i].max_tick = get_param<int>(jp, "max_tick", 4095);

    // 관절당 position command/state 인터페이스 1개씩(라디안) 을 기대.
    if (info_.joints[i].command_interfaces.size() != 1 ||
      info_.joints[i].command_interfaces[0].name != hardware_interface::HW_IF_POSITION)
    {
      RCLCPP_FATAL(LOGGER, "joint '%s' 는 position command 1개만 지원",
        info_.joints[i].name.c_str());
      return hardware_interface::CallbackReturn::ERROR;
    }
  }

  // bus 선택. 지금은 fake 만 존재(st3215 는 151 완료 후 여기서 분기).
  const auto & hp = info_.hardware_parameters;
  std::string bus = hp.count("bus") ? hp.at("bus") : "fake";
  if (bus == "fake") {
    bus_ = std::make_unique<FakeBus>();
  } else {
    // ponytail: st3215 미구현. 151 완료 시 else if (bus=="st3215") 로 분기 추가.
    RCLCPP_FATAL(LOGGER, "bus '%s' 미구현 (fake 만 가능, st3215 는 151 대기)", bus.c_str());
    return hardware_interface::CallbackReturn::ERROR;
  }

  RCLCPP_INFO(LOGGER, "SO101System 초기화: %zu 관절, bus=%s", n, bus.c_str());
  return hardware_interface::CallbackReturn::SUCCESS;
}

std::vector<hardware_interface::StateInterface> SO101System::export_state_interfaces()
{
  std::vector<hardware_interface::StateInterface> ifaces;
  for (size_t i = 0; i < info_.joints.size(); ++i) {
    ifaces.emplace_back(
      info_.joints[i].name, hardware_interface::HW_IF_POSITION, &pos_state_[i]);
  }
  return ifaces;
}

std::vector<hardware_interface::CommandInterface> SO101System::export_command_interfaces()
{
  std::vector<hardware_interface::CommandInterface> ifaces;
  for (size_t i = 0; i < info_.joints.size(); ++i) {
    ifaces.emplace_back(
      info_.joints[i].name, hardware_interface::HW_IF_POSITION, &pos_command_[i]);
  }
  return ifaces;
}

hardware_interface::CallbackReturn SO101System::on_activate(
  const rclcpp_lifecycle::State &)
{
  bus_->set_torque(servo_ids_, true);
  // 현재 위치를 명령 초기값으로 → 활성화 순간 튀지 않음.
  auto ticks = bus_->read_positions(servo_ids_);
  for (size_t i = 0; i < ticks.size(); ++i) {
    pos_state_[i] = tick_to_rad(ticks[i], calib_[i]);
    pos_command_[i] = pos_state_[i];
  }
  RCLCPP_INFO(LOGGER, "SO101System 활성화(torque on)");
  return hardware_interface::CallbackReturn::SUCCESS;
}

hardware_interface::CallbackReturn SO101System::on_deactivate(
  const rclcpp_lifecycle::State &)
{
  bus_->set_torque(servo_ids_, false);
  RCLCPP_INFO(LOGGER, "SO101System 비활성화(torque off)");
  return hardware_interface::CallbackReturn::SUCCESS;
}

hardware_interface::return_type SO101System::read(
  const rclcpp::Time &, const rclcpp::Duration &)
{
  auto ticks = bus_->read_positions(servo_ids_);
  for (size_t i = 0; i < ticks.size(); ++i) {
    pos_state_[i] = tick_to_rad(ticks[i], calib_[i]);
  }
  return hardware_interface::return_type::OK;
}

hardware_interface::return_type SO101System::write(
  const rclcpp::Time &, const rclcpp::Duration &)
{
  std::vector<int32_t> ticks(pos_command_.size());
  for (size_t i = 0; i < pos_command_.size(); ++i) {
    ticks[i] = rad_to_tick(pos_command_[i], calib_[i]);  // clamp 은 conversion 내부
  }
  bus_->write_positions(servo_ids_, ticks);
  return hardware_interface::return_type::OK;
}

}  // namespace so101_hardware

PLUGINLIB_EXPORT_CLASS(so101_hardware::SO101System, hardware_interface::SystemInterface)
