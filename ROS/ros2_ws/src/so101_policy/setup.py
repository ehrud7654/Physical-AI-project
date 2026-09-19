from setuptools import find_packages, setup

package_name = 'so101_policy'

setup(
    name=package_name,
    version='0.0.0',
    packages=find_packages(exclude=['test']),
    data_files=[
        ('share/ament_index/resource_index/packages',
            ['resource/' + package_name]),
        ('share/' + package_name, ['package.xml']),
    ],
    install_requires=['setuptools'],
    zip_safe=True,
    maintainer='Hw',
    maintainer_email='shwzone@gmail.com',
    description='SO-101 정책 노드 (lifecycle, ckpt→IK→관절각 발행)',
    license='Apache-2.0',
    extras_require={
        'test': [
            'pytest',
        ],
    },
    entry_points={
        'console_scripts': [
            'policy_node = so101_policy.policy_node:main',
        ],
    },
)
