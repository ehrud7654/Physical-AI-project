#include "so101_hardware/so101_system.hpp"

#include <algorithm>
#include <cmath>
#include <stdexcept>
#include <string>
#include <unordered_map>
#include <vector>

#include "hardware_interface/types/hardware_interface_type_values.hpp"
#include "pluginlib/class_list_macros.hpp"
#include "rclcpp/rclcpp.hpp"

#include "so101_hardware/fake_bus.hpp"
#include "so101_hardware/st3215_servo_bus.hpp"

namespace so101_hardware
{

namespace
{
auto LOGGER = rclcpp::get_logger("SO101System");
using ParamMap = std::unordered_map<std::string, std::string>;

std::string opt_param(const ParamMap & p, const std::string & k, const std::string & fb)
{
  auto it = p.find(k);
  return it == p.end() ? fb : it->second;
}

std::string req_param(const ParamMap & p, const std::string & joint, const std::string & k)
{
  auto it = p.find(k);
  if (it == p.end()) {
    throw std::invalid_argument(joint + ": missing param " + k);
  }
  return it->second;
}
}  // namespace

hardware_interface::CallbackReturn SO101System::on_init(
  const hardware_interface::HardwareInfo & info)
{
  if (SystemInterface::on_init(info) != hardware_interface::CallbackReturn::SUCCESS) {
    return hardware_interface::CallbackReturn::ERROR;
  }

  try {
    const auto & hp = info_.hardware_parameters;
    bus_type_ = opt_param(hp, "bus", "fake");
    port_ = opt_param(hp, "port", "/dev/ttyTHS1");
    baudrate_ = std::stoi(opt_param(hp, "baudrate", "1000000"));
    const int spd = std::stoi(opt_param(hp, "speed", "300"));
    const int acc = std::stoi(opt_param(hp, "acceleration", "10"));
    if (spd < 0 || spd > 32767 || acc < 0 || acc > 150) {
      throw std::out_of_range("speed/acceleration outside ST3215 limits");
    }
    speed_ = static_cast<std::uint16_t>(spd);
    acceleration_ = static_cast<std::uint8_t>(acc);

    joints_.clear();
    servo_ids_.clear();
    for (const auto & j : info_.joints) {
      // position command/state 1개씩 기대 (팔=rad, 그리퍼=m 이지만 인터페이스 이름은 동일).
      if (j.command_interfaces.size() != 1 ||
        j.command_interfaces[0].name != hardware_interface::HW_IF_POSITION)
      {
        throw std::invalid_argument(j.name + ": position command interface 1개 필요");
      }
      const int sid = std::stoi(req_param(j.parameters, j.name, "servo_id"));
      if (sid < 0 || sid > 253) {
        throw std::invalid_argument(j.name + ": servo_id 0..253");
      }

      JointRuntime rt;
      rt.name = j.name;
      rt.servo_id = static_cast<std::uint8_t>(sid);
      rt.is_gripper = (j.name == "gripper");

      if (rt.is_gripper) {
        rt.grip.closed_tick = std::stoi(req_param(j.parameters, j.name, "closed_tick"));
        rt.grip.open_tick = std::stoi(req_param(j.parameters, j.name, "open_tick"));
        rt.grip.min_tick = std::stoi(opt_param(j.parameters, "min_tick", "0"));
        rt.grip.max_tick = std::stoi(opt_param(j.parameters, "max_tick", "4095"));
        rt.grip.max_width_m = std::stod(req_param(j.parameters, j.name, "max_width_m"));
        const auto & g = rt.grip;
        if (g.closed_tick == g.open_tick || g.min_tick < 0 || g.max_tick > 4095 ||
          g.min_tick > std::min(g.closed_tick, g.open_tick) ||
          g.max_tick < std::max(g.closed_tick, g.open_tick) || g.max_width_m <= 0.0)
        {
          throw std::invalid_argument(j.name + ": invalid gripper calibration");
        }
      } else {
        rt.arm.zero_tick = std::stoi(req_param(j.parameters, j.name, "zero_tick"));
        rt.arm.ticks_per_rad = std::stod(
          opt_param(
            j.parameters, "ticks_per_rad", std::to_string(4096.0 / (2.0 * M_PI))));
        rt.arm.direction = static_cast<int8_t>(std::stoi(
            req_param(j.parameters, j.name, "direction")));
        rt.arm.min_tick = std::stoi(opt_param(j.parameters, "min_tick", "0"));
        rt.arm.max_tick = std::stoi(opt_param(j.parameters, "max_tick", "4095"));
        const auto & a = rt.arm;
        if (a.direction * a.direction != 1 || a.min_tick < 0 || a.max_tick > 4095 ||
          a.min_tick > a.zero_tick || a.zero_tick > a.max_tick)
        {
          throw std::invalid_argument(j.name + ": invalid arm calibration");
        }
      }
      joints_.push_back(rt);
      servo_ids_.push_back(rt.servo_id);
    }

    auto sorted = servo_ids_;
    std::sort(sorted.begin(), sorted.end());
    if (std::adjacent_find(sorted.begin(), sorted.end()) != sorted.end()) {
      throw std::invalid_argument("servo_id 중복");
    }
  } catch (const std::exception & e) {
    RCLCPP_FATAL(LOGGER, "on_init 설정 오류: %s", e.what());
    return hardware_interface::CallbackReturn::ERROR;
  }

  pos_state_.assign(joints_.size(), 0.0);
  pos_command_.assign(joints_.size(), 0.0);
  RCLCPP_INFO(LOGGER, "on_init: 관절 %zu개, bus=%s", joints_.size(), bus_type_.c_str());
  return hardware_interface::CallbackReturn::SUCCESS;
}

std::vector<hardware_interface::StateInterface> SO101System::export_state_interfaces()
{
  std::vector<hardware_interface::StateInterface> ifaces;
  for (size_t i = 0; i < joints_.size(); ++i) {
    ifaces.emplace_back(joints_[i].name, hardware_interface::HW_IF_POSITION, &pos_state_[i]);
  }
  return ifaces;
}

std::vector<hardware_interface::CommandInterface> SO101System::export_command_interfaces()
{
  std::vector<hardware_interface::CommandInterface> ifaces;
  for (size_t i = 0; i < joints_.size(); ++i) {
    ifaces.emplace_back(joints_[i].name, hardware_interface::HW_IF_POSITION, &pos_command_[i]);
  }
  return ifaces;
}

hardware_interface::CallbackReturn SO101System::on_configure(const rclcpp_lifecycle::State &)
{
  try {
    if (bus_type_ == "fake") {
      bus_ = std::make_unique<FakeBus>();
    } else if (bus_type_ == "st3215") {
      bus_ = std::make_unique<St3215ServoBus>(port_, baudrate_, speed_, acceleration_);
    } else {
      RCLCPP_FATAL(LOGGER, "알 수 없는 bus: %s (fake|st3215)", bus_type_.c_str());
      return hardware_interface::CallbackReturn::ERROR;
    }
    if (!bus_->ping(servo_ids_)) {
      RCLCPP_FATAL(LOGGER, "서보 ping 실패 (bus=%s)", bus_type_.c_str());
      bus_.reset();
      return hardware_interface::CallbackReturn::ERROR;
    }
  } catch (const std::exception & e) {
    RCLCPP_FATAL(LOGGER, "on_configure 실패: %s", e.what());
    bus_.reset();
    return hardware_interface::CallbackReturn::ERROR;
  }
  RCLCPP_INFO(LOGGER, "on_configure: bus 연결·ping OK");
  return hardware_interface::CallbackReturn::SUCCESS;
}

hardware_interface::CallbackReturn SO101System::on_activate(const rclcpp_lifecycle::State &)
{
  if (read(rclcpp::Time{}, rclcpp::Duration{0, 0}) != hardware_interface::return_type::OK) {
    return hardware_interface::CallbackReturn::ERROR;
  }
  pos_command_ = pos_state_;   // 활성화 순간 튐 방지
  try {
    bus_->set_torque(servo_ids_, true);
  } catch (const std::exception & e) {
    RCLCPP_FATAL(LOGGER, "torque on 실패: %s", e.what());
    return hardware_interface::CallbackReturn::ERROR;
  }
  RCLCPP_INFO(LOGGER, "on_activate: torque on");
  return hardware_interface::CallbackReturn::SUCCESS;
}

hardware_interface::CallbackReturn SO101System::on_deactivate(const rclcpp_lifecycle::State &)
{
  if (bus_) {
    try {
      bus_->set_torque(servo_ids_, false);
    } catch (const std::exception & e) {
      RCLCPP_ERROR(LOGGER, "torque off 실패: %s", e.what());
    }
  }
  RCLCPP_INFO(LOGGER, "on_deactivate: torque off");
  return hardware_interface::CallbackReturn::SUCCESS;
}

hardware_interface::return_type SO101System::read(
  const rclcpp::Time &, const rclcpp::Duration &)
{
  if (!bus_) {
    return hardware_interface::return_type::ERROR;
  }
  try {
    const auto ticks = bus_->read_positions(servo_ids_);
    for (size_t i = 0; i < joints_.size(); ++i) {
      pos_state_[i] = joints_[i].is_gripper ?
        tick_to_width(ticks[i], joints_[i].grip) :
        tick_to_rad(ticks[i], joints_[i].arm);
    }
  } catch (const std::exception & e) {
    RCLCPP_ERROR(LOGGER, "read 실패: %s", e.what());
    return hardware_interface::return_type::ERROR;
  }
  return hardware_interface::return_type::OK;
}

hardware_interface::return_type SO101System::write(
  const rclcpp::Time &, const rclcpp::Duration &)
{
  if (!bus_) {
    return hardware_interface::return_type::ERROR;
  }
  try {
    std::vector<int32_t> ticks(joints_.size());
    for (size_t i = 0; i < joints_.size(); ++i) {
      ticks[i] = joints_[i].is_gripper ?
        width_to_tick(pos_command_[i], joints_[i].grip) :   // clamp 은 conversion 내부
        rad_to_tick(pos_command_[i], joints_[i].arm);
    }
    bus_->write_positions(servo_ids_, ticks);
  } catch (const std::exception & e) {
    RCLCPP_ERROR(LOGGER, "write 실패: %s", e.what());
    return hardware_interface::return_type::ERROR;
  }
  return hardware_interface::return_type::OK;
}

}  // namespace so101_hardware

PLUGINLIB_EXPORT_CLASS(so101_hardware::SO101System, hardware_interface::SystemInterface)
