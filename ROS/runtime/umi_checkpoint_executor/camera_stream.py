"""S22 UMI1 JPEG receiver over adb forward. No Android/robot dependencies."""
import argparse
import json
import socket
import struct
import threading
import time
from pathlib import Path

import cv2
import numpy as np

HEADER = struct.Struct('!4sQQIII')


def recv_exact(sock, count):
    data = bytearray()
    while len(data) < count:
        part = sock.recv(count - len(data))
        if not part:
            raise ConnectionError('S22 disconnected')
        data.extend(part)
    return data


def receive(sock):
    sock.sendall(b'\x01')
    magic, seq, timestamp, width, height, size = HEADER.unpack(recv_exact(sock, HEADER.size))
    if magic != b'UMI1' or (width, height) != (1920, 1080) or not 0 < size <= 8_000_000:
        raise ValueError('Invalid UMI1 frame header')
    jpeg = recv_exact(sock, size)
    bgr = cv2.imdecode(np.frombuffer(jpeg, np.uint8), cv2.IMREAD_COLOR)
    if bgr is None or bgr.shape != (height, width, 3):
        raise ValueError('Invalid JPEG image or dimensions')
    return bgr, {'sequence': seq, 'sensor_timestamp_ns': timestamp,
                 'received_monotonic_ns': time.monotonic_ns(), 'jpeg_bytes': size}


class UsbCamera:
    """One latest frame; stale/disconnected input raises instead of repeating frames."""
    def __init__(self, port=8765):
        self.sock = socket.create_connection(('127.0.0.1', port), timeout=5)
        self.sock.settimeout(5)
        self.condition = threading.Condition()
        self.latest = None
        self.error = None
        self.stopped = False
        self.consumed = -1
        self.thread = threading.Thread(target=self._receive, daemon=True)
        self.thread.start()

    def _receive(self):
        previous = -1
        try:
            while not self.stopped:
                frame, info = receive(self.sock)
                if info['sequence'] <= previous:
                    raise ValueError('Non-increasing frame sequence')
                previous = info['sequence']
                with self.condition:
                    self.latest = (frame, info)
                    self.condition.notify_all()
        except (OSError, ValueError) as exc:
            with self.condition:
                self.error = exc
                self.condition.notify_all()

    def read_frame(self):
        with self.condition:
            ready = self.condition.wait_for(lambda: self.error is not None or self.stopped or
                (self.latest is not None and self.latest[1]['sequence'] > self.consumed), timeout=5)
            if self.error is not None:
                raise ConnectionError(f'S22 stream stopped: {self.error}') from self.error
            if not ready or self.stopped:
                raise TimeoutError('No new S22 frame within 5 seconds')
            frame, info = self.latest
            if time.monotonic_ns() - info['received_monotonic_ns'] > 1_000_000_000:
                raise TimeoutError('S22 frame is stale')
            self.consumed = info['sequence']
            return frame, dict(info)

    def read(self):
        frame, _ = self.read_frame()
        return True, frame

    def release(self):
        with self.condition:
            self.stopped = True
            self.condition.notify_all()
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.sock.close()
        self.thread.join(timeout=6)


def observation_interval_ok(interval_ms, target_ms, tolerance_ms):
    return abs(interval_ms - target_ms) <= tolerance_ms


def capture_history(camera, read_state, preprocess_frame, target_ms, tolerance_ms):
    """Capture two timestamp-spaced frames with the robot state sampled at each frame."""
    samples = []
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        bgr, info = camera.read_frame()
        timestamp = int(info["sensor_timestamp_ns"])
        if not samples:
            samples = [{"rgb": preprocess_frame(bgr), "frame": dict(info), **read_state()}]
            continue
        interval_ms = (timestamp - samples[0]["frame"]["sensor_timestamp_ns"]) / 1e6
        if interval_ms < target_ms - tolerance_ms:
            continue
        sample = {"rgb": preprocess_frame(bgr), "frame": dict(info), **read_state()}
        if observation_interval_ok(interval_ms, target_ms, tolerance_ms):
            return [samples[0], sample]
        samples = [sample]
    raise RuntimeError(
        f"could not capture a {target_ms:.1f}+/-{tolerance_ms:.1f} ms observation pair within 5 s"
    )


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--port', type=int, default=8765)
    p.add_argument('--frames', type=int, default=60)
    p.add_argument('--out', type=Path, required=True)
    args = p.parse_args()
    if not 1 <= args.frames <= 10000:
        p.error('frames must be 1..10000')
    args.out.mkdir(parents=True, exist_ok=False)
    camera = UsbCamera(args.port)
    try:
        with (args.out / 'frames.jsonl').open('w') as log:
            start = time.monotonic()
            for _ in range(args.frames):
                frame, info = camera.read_frame()
                log.write(json.dumps(info) + '\n')
            elapsed = time.monotonic() - start
        ok, jpeg = cv2.imencode('.jpg', frame)
        if not ok:
            raise RuntimeError('Preview encoding failed')
        jpeg.tofile(args.out / 'preview.jpg')
        report = {'frames': args.frames, 'receive_fps': args.frames / elapsed,
                  'size': [1920, 1080], 'motor_commands_sent': 0,
                  'note': 'Sensor timestamps are not synchronized to Jetson. FPS is not end-to-end latency.'}
        (args.out / 'report.json').write_text(json.dumps(report, indent=2))
        print(json.dumps(report, indent=2))
    finally:
        camera.release()
