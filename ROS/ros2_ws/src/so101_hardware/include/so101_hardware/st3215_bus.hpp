// STS3215 UART 드라이버 — 팀원(151) 코드. servo_control.py 충실 포팅.
// 순수 모터 통신(틱 단위). ROS 몰라도 됨. IServoBus 어댑터(st3215_servo_bus.hpp)로 감쌈.
#pragma once

#include <cstdint>
#include <map>
#include <stdexcept>
#include <string>
#include <vector>

namespace so101_hardware
{

class ServoError : public std::runtime_error
{
public:
  using std::runtime_error::runtime_error;
};

class ST3215Bus
{
public:
  explicit ST3215Bus(
    const std::string & port = "/dev/ttyTHS1", int baudrate = 1000000,
    int timeout_ms = 200);
  ~ST3215Bus();

  ST3215Bus(const ST3215Bus &) = delete;
  ST3215Bus & operator=(const ST3215Bus &) = delete;

  void close();
  bool ping(std::uint8_t servo_id);
  std::uint16_t read_position(std::uint8_t servo_id);
  std::map<std::uint8_t, std::uint16_t> read_positions(
    const std::vector<std::uint8_t> & servo_ids);
  void write_position(
    std::uint8_t servo_id, std::uint16_t position,
    std::uint16_t speed = 300, std::uint8_t acceleration = 10);
  void write_positions(
    const std::map<std::uint8_t, std::uint16_t> & positions,
    std::uint16_t speed = 300, std::uint8_t acceleration = 10);
  void set_torque(std::uint8_t servo_id, bool enabled);
  void set_torque_all(const std::vector<std::uint8_t> & servo_ids, bool enabled);

private:
  static constexpr std::uint8_t PING = 0x01;
  static constexpr std::uint8_t READ = 0x02;
  static constexpr std::uint8_t WRITE = 0x03;
  static constexpr std::uint8_t SYNC_WRITE = 0x83;
  static constexpr std::uint8_t BROADCAST_ID = 0xFE;
  static constexpr std::uint8_t TORQUE_ENABLE = 40;
  static constexpr std::uint8_t ACCELERATION = 41;
  static constexpr std::uint8_t PRESENT_POSITION = 56;

  int fd_{-1};
  int timeout_ms_;

  static void check_id(std::uint8_t servo_id);
  static std::vector<std::uint8_t> packet(
    std::uint8_t servo_id, std::uint8_t instruction,
    const std::vector<std::uint8_t> & parameters = {});
  static std::vector<std::uint8_t> motion_data(
    std::uint16_t position, std::uint16_t speed, std::uint8_t acceleration);
  void write_all(const std::vector<std::uint8_t> & data);
  std::vector<std::uint8_t> read_exact(std::size_t size);
  std::vector<std::uint8_t> read_status(std::uint8_t expected_id);
  std::vector<std::uint8_t> request(
    std::uint8_t servo_id, std::uint8_t instruction,
    const std::vector<std::uint8_t> & parameters = {});
};

}  // namespace so101_hardware
