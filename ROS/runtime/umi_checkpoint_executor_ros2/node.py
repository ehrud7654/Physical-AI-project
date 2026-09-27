#!/usr/bin/env python3
"""ROS 2 start/stop services for the shared UMI checkpoint executor."""
from __future__ import annotations

import os
import signal
import subprocess
from datetime import datetime
from pathlib import Path

import rclpy
from rclpy.node import Node
from std_srvs.srv import Trigger


class UmiCheckpointExecutorNode(Node):
    def __init__(self):
        super().__init__("umi_checkpoint_executor")
        self.repo_root = Path(__file__).resolve().parents[2]
        self.process = None
        self.declare_parameter("model", "models/s22_pick_v3_atlas_v1_e120")
        self.declare_parameter("out_prefix", "umi_checkpoint_ros2")
        self.declare_parameter("move", False)
        self.declare_parameter("enable_gripper", True)
        self.declare_parameter("cycles", 0)
        self.declare_parameter("max_resamples", 10)
        self.declare_parameter("speed", 100)
        self.declare_parameter("acceleration", 30)
        self.declare_parameter("extra_args", [])
        self.create_service(Trigger, "~/start", self.start_executor)
        self.create_service(Trigger, "~/stop", self.stop_executor)
        self.create_timer(0.5, self.poll_executor)
        self.get_logger().info("ready: call ~/start; call ~/stop or Ctrl+C to stop")

    def command(self):
        stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        model = Path(self.get_parameter("model").value)
        if not model.is_absolute():
            model = self.repo_root / model
        out = self.repo_root / "runs" / f"{self.get_parameter('out_prefix').value}_{stamp}"
        command = [
            str(self.repo_root / ".venv/bin/python"),
            "-m",
            "runtime.umi_checkpoint_executor",
            "--model", str(model),
            "--out", str(out),
            "--cycles", str(self.get_parameter("cycles").value),
            "--max-resamples", str(self.get_parameter("max_resamples").value),
            "--speed", str(self.get_parameter("speed").value),
            "--acceleration", str(self.get_parameter("acceleration").value),
        ]
        if self.get_parameter("move").value:
            command.append("--move")
        if self.get_parameter("enable_gripper").value:
            command.append("--enable-gripper")
        command.extend(self.get_parameter("extra_args").value)
        return command

    def start_executor(self, _request, response):
        if self.process is not None and self.process.poll() is None:
            response.success = False
            response.message = f"already running pid={self.process.pid}"
            return response
        environment = os.environ.copy()
        library_path = str(self.repo_root / ".venv/lib/python3.10/site-packages/cusparselt/lib")
        environment["LD_LIBRARY_PATH"] = library_path + ":/usr/local/cuda/lib64:" + environment.get("LD_LIBRARY_PATH", "")
        command = self.command()
        self.process = subprocess.Popen(command, cwd=self.repo_root, env=environment)
        response.success = True
        response.message = f"started pid={self.process.pid}"
        self.get_logger().info(response.message)
        return response

    def stop_executor(self, _request, response):
        if self.process is None or self.process.poll() is not None:
            response.success = False
            response.message = "executor is not running"
            return response
        self.process.send_signal(signal.SIGINT)
        response.success = True
        response.message = f"SIGINT sent to pid={self.process.pid}"
        self.get_logger().info(response.message)
        return response

    def poll_executor(self):
        if self.process is None:
            return
        code = self.process.poll()
        if code is not None:
            self.get_logger().info(f"executor exited code={code}")
            self.process = None

    def destroy_node(self):
        if self.process is not None and self.process.poll() is None:
            self.process.send_signal(signal.SIGINT)
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.process.terminate()
        super().destroy_node()


def main():
    rclpy.init()
    node = UmiCheckpointExecutorNode()
    try:
        rclpy.spin(node)
    except KeyboardInterrupt:
        pass
    finally:
        node.destroy_node()
        rclpy.shutdown()


if __name__ == "__main__":
    main()
