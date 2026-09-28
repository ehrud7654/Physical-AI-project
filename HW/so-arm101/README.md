# SO-ARM101 Base Robot Arm

This directory is reserved for the upstream open-source SO-ARM101 design.
The base frame and arm geometry used by this project have not been modified,
and this project does not claim authorship of that geometry.

Project-specific gripper and end-effector files belong in `../gripper/`.

## Upstream project

- Project URL: https://github.com/TheRobotStudio/SO-ARM100
- License: Apache-2.0; [license text](LICENSE)
- Imported commit: `7629d2ad9853d10fb903093a33ef6114099d97e5`
- Source assets: [Simulation/SO101/assets at that commit](https://github.com/TheRobotStudio/SO-ARM100/tree/7629d2ad9853d10fb903093a33ef6114099d97e5/Simulation/SO101/assets)

## Source attribution

Before adding an upstream file, record its source URL, license, and upstream
version, tag, or commit in this README. Preserve the original filename when
practical so that the file remains traceable to its source.

Only unchanged upstream SO-ARM101 STL files should be placed in `stl/`. A
modified or project-specific part must not be stored here.

## Files

- `stl/` — unmodified STL files obtained from the upstream SO-ARM101 project.
- `ver1-stl/` — nine frame parts re-exported from `Ver1.blend`, with its
  world transforms baked into millimetre coordinates. These are not byte-for-byte
  upstream STL copies; they retain the source filenames and upstream attribution.
  See [export provenance and validation](../assembly/VER1_STL.md).
