from setuptools import find_packages, setup

package_name = "so101_driver"

setup(
    name=package_name,
    version="0.0.1",
    packages=find_packages(exclude=["test"]),
    data_files=[
        ("share/ament_index/resource_index/packages", ["resource/" + package_name]),
        ("share/" + package_name, ["package.xml"]),
    ],
    install_requires=["setuptools"],
    zip_safe=True,
    maintainer="Hw",
    maintainer_email="shwzone@gmail.com",
    description="SO-101 하드웨어 드라이버 (ROS 래퍼 + HardwareInterface)",
    license="TODO",
    tests_require=["pytest"],
    entry_points={
        "console_scripts": [
            "driver = so101_driver.driver_node:main",
        ],
    },
)
