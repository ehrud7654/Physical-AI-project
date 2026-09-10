cmake_minimum_required(VERSION 3.16)
project(so101_hardware LANGUAGES CXX)

if(NOT CMAKE_CXX_STANDARD)
  set(CMAKE_CXX_STANDARD 17)
endif()

find_package(ament_cmake REQUIRED)
find_package(hardware_interface REQUIRED)
find_package(pluginlib REQUIRED)
find_package(rclcpp REQUIRED)
find_package(rclcpp_lifecycle REQUIRED)

add_library(so101_hardware SHARED
  src/so101_system.cpp
  src/st3215_bus.cpp)
target_include_directories(so101_hardware PUBLIC
  $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
  $<INSTALL_INTERFACE:include>)
ament_target_dependencies(so101_hardware
  hardware_interface pluginlib rclcpp rclcpp_lifecycle)

# ros2_control 이 이 라이브러리를 SystemInterface 플러그인으로 찾게 함.
pluginlib_export_plugin_description_file(hardware_interface so101_hardware.xml)

install(TARGETS so101_hardware
  ARCHIVE DESTINATION lib
  LIBRARY DESTINATION lib
  RUNTIME DESTINATION bin)
install(DIRECTORY include/ DESTINATION include)

if(BUILD_TESTING)
  find_package(ament_lint_auto REQUIRED)
  # copyright 만 끈다 (내부 프로젝트, 저작권 문구 불필요).
  # cpplint/uncrustify(코드 스타일)는 유지.
  set(ament_cmake_copyright_FOUND TRUE)
  ament_lint_auto_find_test_dependencies()

  # 순수 변환 self-check (ROS 불필요).
  add_executable(test_conversion test/test_conversion.cpp)
  target_include_directories(test_conversion PRIVATE include)
  add_test(NAME test_conversion COMMAND test_conversion)
endif()

ament_export_include_directories(include)
ament_export_libraries(so101_hardware)
ament_package()
