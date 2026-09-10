// STS3215 UART 드라이버 구현 — 팀원(151) 코드. servo_control.py 포팅.
#include "so101_hardware/st3215_bus.hpp"

#include <fcntl.h>
#include <poll.h>
#include <termios.h>
#include <unistd.h>

#include <cerrno>
#include <chrono>
#include <cstring>
#include <numeric>
#include <sstream>
#include <thread>

namespace so101_hardware
{
namespace
{
speed_t baud_constant(int baudrate)
{
  if (baudrate == 1000000) {
    return B1000000;
  }
  throw std::invalid_argument("ST3215 baudrate must be 1000000");
}

std::string system_error(const std::string & what)
{
  return what + ": " + std::strerror(errno);
}
}  // namespace

ST3215Bus::ST3215Bus(const std::string & port, int baudrate, int timeout_ms)
: timeout_ms_(timeout_ms)
{
  if (timeout_ms <= 0) {
    throw std::invalid_argument("timeout_ms must be positive");
  }
  const auto speed = baud_constant(baudrate);
  fd_ = ::open(port.c_str(), O_RDWR | O_NOCTTY | O_CLOEXEC);
  if (fd_ < 0) {
    throw ServoError(system_error("cannot open " + port));
  }

  termios tty{};
  if (tcgetattr(fd_, &tty) != 0) {
    const auto message = system_error("tcgetattr failed");
    close();
    throw ServoError(message);
  }
  cfmakeraw(&tty);
  tty.c_cflag |= CLOCAL | CREAD;
  tty.c_cflag &= ~CSTOPB;
  tty.c_cflag &= ~CRTSCTS;
  tty.c_cc[VMIN] = 0;
  tty.c_cc[VTIME] = 0;
  cfsetispeed(&tty, speed);
  cfsetospeed(&tty, speed);
  if (tcsetattr(fd_, TCSANOW, &tty) != 0) {
    const auto message = system_error("tcsetattr failed");
    close();
    throw ServoError(message);
  }
  tcflush(fd_, TCIOFLUSH);
}

ST3215Bus::~ST3215Bus()
{
  close();
}

void ST3215Bus::close()
{
  if (fd_ >= 0) {
    ::close(fd_);
    fd_ = -1;
  }
}

void ST3215Bus::check_id(std::uint8_t servo_id)
{
  if (servo_id > 253) {
    throw std::invalid_argument("servo_id must be 0..253");
  }
}

std::vector<std::uint8_t> ST3215Bus::packet(
  std::uint8_t servo_id, std::uint8_t instruction,
  const std::vector<std::uint8_t> & parameters)
{
  if (parameters.size() > 253) {
    throw std::invalid_argument("packet parameters are too large");
  }
  std::vector<std::uint8_t> result{
    0xFF, 0xFF, servo_id,
    static_cast<std::uint8_t>(parameters.size() + 2), instruction};
  result.insert(result.end(), parameters.begin(), parameters.end());
  const auto sum = std::accumulate(result.begin() + 2, result.end(), 0u);
  result.push_back(static_cast<std::uint8_t>(~sum));
  return result;
}

std::vector<std::uint8_t> ST3215Bus::motion_data(
  std::uint16_t position, std::uint16_t speed, std::uint8_t acceleration)
{
  if (position > 4095) {
    throw std::out_of_range("position must be 0..4095");
  }
  if (speed > 32767) {
    throw std::out_of_range("speed must be 0..32767");
  }
  if (acceleration > 150) {
    throw std::out_of_range("acceleration must be 0..150");
  }
  return {
    acceleration,
    static_cast<std::uint8_t>(position & 0xFF),
    static_cast<std::uint8_t>(position >> 8),
    0, 0,
    static_cast<std::uint8_t>(speed & 0xFF),
    static_cast<std::uint8_t>(speed >> 8)};
}

void ST3215Bus::write_all(const std::vector<std::uint8_t> & data)
{
  if (fd_ < 0) {
    throw ServoError("UART is closed");
  }
  std::size_t written = 0;
  while (written < data.size()) {
    const auto count = ::write(fd_, data.data() + written, data.size() - written);
    if (count < 0 && errno == EINTR) {
      continue;
    }
    if (count <= 0) {
      throw ServoError(system_error("UART write failed"));
    }
    written += static_cast<std::size_t>(count);
  }
  if (tcdrain(fd_) != 0) {
    throw ServoError(system_error("UART drain failed"));
  }
}

std::vector<std::uint8_t> ST3215Bus::read_exact(std::size_t size)
{
  std::vector<std::uint8_t> data(size);
  std::size_t received = 0;
  const auto deadline =
    std::chrono::steady_clock::now() + std::chrono::milliseconds(timeout_ms_);

  while (received < size) {
    const auto remaining = std::chrono::duration_cast<std::chrono::milliseconds>(
      deadline - std::chrono::steady_clock::now()).count();
    if (remaining <= 0) {
      break;
    }
    pollfd descriptor{fd_, POLLIN, 0};
    const int ready = ::poll(&descriptor, 1, static_cast<int>(remaining));
    if (ready < 0 && errno == EINTR) {
      continue;
    }
    if (ready < 0) {
      throw ServoError(system_error("UART poll failed"));
    }
    if (ready == 0) {
      break;
    }
    const auto count = ::read(fd_, data.data() + received, size - received);
    if (count < 0 && errno == EINTR) {
      continue;
    }
    if (count <= 0) {
      break;
    }
    received += static_cast<std::size_t>(count);
  }
  if (received != size) {
    std::ostringstream message;
    message << "UART response timeout: expected " << size << ", got " << received << " bytes";
    throw ServoError(message.str());
  }
  return data;
}

std::vector<std::uint8_t> ST3215Bus::read_status(std::uint8_t expected_id)
{
  if (read_exact(2) != std::vector<std::uint8_t>({0xFF, 0xFF})) {
    throw ServoError("invalid response header");
  }
  const auto id_and_length = read_exact(2);
  const auto servo_id = id_and_length[0];
  const auto length = id_and_length[1];
  if (length < 2) {
    throw ServoError("invalid response length");
  }
  const auto payload = read_exact(length);
  if (servo_id != expected_id) {
    throw ServoError("unexpected servo ID: " + std::to_string(servo_id));
  }
  unsigned int checksum = servo_id + length;
  checksum = std::accumulate(payload.begin(), payload.end(), checksum);
  if ((checksum & 0xFFu) != 0xFFu) {
    throw ServoError("invalid response checksum");
  }
  if (payload.front() != 0) {
    std::ostringstream message;
    message << "servo " << static_cast<int>(servo_id) << " error: 0x" << std::hex <<
      static_cast<int>(payload.front());
    throw ServoError(message.str());
  }
  return {payload.begin() + 1, payload.end() - 1};
}

std::vector<std::uint8_t> ST3215Bus::request(
  std::uint8_t servo_id, std::uint8_t instruction,
  const std::vector<std::uint8_t> & parameters)
{
  const auto command = packet(servo_id, instruction, parameters);
  std::string last_error;
  for (int attempt = 0; attempt < 3; ++attempt) {
    tcflush(fd_, TCIFLUSH);
    write_all(command);
    try {
      return read_status(servo_id);
    } catch (const ServoError & error) {
      last_error = error.what();
      std::this_thread::sleep_for(std::chrono::milliseconds(10));
    }
  }
  throw ServoError(
          "servo " + std::to_string(servo_id) +
          " failed after 3 attempts: " + last_error);
}

bool ST3215Bus::ping(std::uint8_t servo_id)
{
  check_id(servo_id);
  request(servo_id, PING);
  return true;
}

std::uint16_t ST3215Bus::read_position(std::uint8_t servo_id)
{
  check_id(servo_id);
  const auto data = request(servo_id, READ, {PRESENT_POSITION, 2});
  if (data.size() != 2) {
    throw ServoError("invalid position response from servo " + std::to_string(servo_id));
  }
  return static_cast<std::uint16_t>(data[0] | (data[1] << 8));
}

std::map<std::uint8_t, std::uint16_t> ST3215Bus::read_positions(
  const std::vector<std::uint8_t> & servo_ids)
{
  std::map<std::uint8_t, std::uint16_t> positions;
  for (const auto servo_id : servo_ids) {
    positions.emplace(servo_id, read_position(servo_id));
  }
  return positions;
}

void ST3215Bus::write_position(
  std::uint8_t servo_id, std::uint16_t position,
  std::uint16_t speed, std::uint8_t acceleration)
{
  check_id(servo_id);
  auto parameters = std::vector<std::uint8_t>{ACCELERATION};
  const auto data = motion_data(position, speed, acceleration);
  parameters.insert(parameters.end(), data.begin(), data.end());
  request(servo_id, WRITE, parameters);
}

void ST3215Bus::write_positions(
  const std::map<std::uint8_t, std::uint16_t> & positions,
  std::uint16_t speed, std::uint8_t acceleration)
{
  if (positions.empty()) {
    return;
  }
  std::vector<std::uint8_t> parameters{ACCELERATION, 7};
  for (const auto & [servo_id, position] : positions) {
    check_id(servo_id);
    parameters.push_back(servo_id);
    const auto data = motion_data(position, speed, acceleration);
    parameters.insert(parameters.end(), data.begin(), data.end());
  }
  write_all(packet(BROADCAST_ID, SYNC_WRITE, parameters));
}

void ST3215Bus::set_torque(std::uint8_t servo_id, bool enabled)
{
  check_id(servo_id);
  request(servo_id, WRITE, {TORQUE_ENABLE, static_cast<std::uint8_t>(enabled)});
}

void ST3215Bus::set_torque_all(
  const std::vector<std::uint8_t> & servo_ids, bool enabled)
{
  for (const auto servo_id : servo_ids) {
    set_torque(servo_id, enabled);
  }
}

}  // namespace so101_hardware
