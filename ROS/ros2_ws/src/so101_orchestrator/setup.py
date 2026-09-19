from setuptools import find_packages, setup

package_name = "so101_orchestrator"

setup(
    name=package_name,
    version="0.0.1",
    packages=find_packages(exclude=["test"]),
    data_files=[
        ("share/ament_index/resource_index/packages", ["resource/" + package_name]),
        ("share/" + package_name, ["package.xml"]),
    ],
    install_requires=["setuptools", "httpx"],
    zip_safe=True,
    maintainer="Hw",
    maintainer_email="shwzone@gmail.com",
    description="배포 오케스트레이터 (SSE 명령 수신 + 정책 lifecycle 제어)",
    license="TODO",
    tests_require=["pytest"],
    entry_points={
        "console_scripts": [
            "orchestrator = so101_orchestrator.orchestrator_node:main",
        ],
    },
)
