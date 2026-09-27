# Hardware

This project uses the open-source SO-ARM101 design as its base robot arm.
The original SO-ARM101 frame and arm geometry are used without modification.
Only the gripper/end-effector is custom-designed for this project.

Keeping the upstream arm and the custom end-effector in separate directories
makes their origin and ownership explicit.

## Directory structure

- `so-arm101/` — attribution and unmodified STL files from the upstream
  SO-ARM101 project.
- `gripper/` — custom gripper/end-effector CAD files and project images.
- `assembly/` — documentation and images of the complete SO-ARM101 and custom
  gripper assembly.

Do not place custom gripper files in `so-arm101/`, and do not present upstream
SO-ARM101 geometry as original work from this project.
