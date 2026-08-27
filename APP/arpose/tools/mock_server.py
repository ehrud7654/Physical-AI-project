#!/usr/bin/env python3
"""업로드 검증용 목 서버.

앱이 보내는 것을 그대로 받아 디스크에 떨군다. 실제 서버가 생기기 전까지
업로드 경로(zip 패키징 -> 전송 -> 멱등성 -> 로컬 삭제)를 전부 검증할 수 있다.

사용:
    python mock_server.py [--port 8000] [--out ./received]

폰이 이 서버를 보게 하려면 USB 만 꽂고:
    adb reverse tcp:8000 tcp:8000

그러면 폰의 127.0.0.1:8000 이 PC 의 이 서버로 연결된다. Wi-Fi 가 필요 없다.

규약 (실제 서버도 이대로 구현하면 앱은 주소만 바꾸면 된다)
    POST /upload
      X-Device-Id       기기 식별자
      X-Episode         rec_YYYYMMDD_HHmmss
      X-Idempotency-Key {기기ID}_{에피소드}
      Content-Type      application/zip
      본문               zip 바이트

    201 저장함
    409 같은 키를 이미 받음 (앱은 성공과 동일하게 처리하고 로컬을 지운다)
    400 헤더 누락
"""
import argparse
import json
import os
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

OUT = "received"
SEEN = {}          # idempotency key -> 저장된 파일명
SEEN_PATH = None


def load_seen():
    global SEEN
    if SEEN_PATH and os.path.exists(SEEN_PATH):
        with open(SEEN_PATH, encoding="utf-8") as fh:
            SEEN = json.load(fh)


def save_seen():
    with open(SEEN_PATH, "w", encoding="utf-8") as fh:
        json.dump(SEEN, fh, ensure_ascii=False, indent=1)


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass                                   # 기본 액세스 로그는 끈다

    def _send(self, code, body=""):
        data = body.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        if self.path == "/health":
            self._send(200, "ok")
        else:
            self._send(404, "not found")

    def do_POST(self):
        if self.path != "/upload":
            return self._send(404, "not found")

        key = self.headers.get("X-Idempotency-Key")
        episode = self.headers.get("X-Episode")
        device = self.headers.get("X-Device-Id")
        if not key or not episode:
            return self._send(400, "X-Idempotency-Key, X-Episode 필요")

        if key in SEEN:
            print(f"  [409] {episode}  이미 받음 -> {SEEN[key]}")
            return self._send(409, "already uploaded")

        length = int(self.headers.get("Content-Length", 0))
        path = os.path.join(OUT, f"{episode}.zip")
        got = 0
        with open(path, "wb") as fh:
            while got < length:
                chunk = self.rfile.read(min(1 << 16, length - got))
                if not chunk:
                    break
                fh.write(chunk)
                got += len(chunk)

        if got != length:
            os.remove(path)
            print(f"  [500] {episode}  본문 잘림 {got}/{length}")
            return self._send(500, "incomplete body")

        # 내용물을 확인해 무엇이 들어왔는지 바로 보여준다
        try:
            with zipfile.ZipFile(path) as z:
                names = z.namelist()
                bad = z.testzip()
        except zipfile.BadZipFile:
            os.remove(path)
            print(f"  [500] {episode}  깨진 zip")
            return self._send(500, "bad zip")
        if bad:
            os.remove(path)
            print(f"  [500] {episode}  손상된 항목 {bad}")
            return self._send(500, "corrupt entry")

        frames = sum(1 for n in names if n.startswith("frames/"))
        others = sorted(n for n in names if not n.startswith("frames/"))
        SEEN[key] = f"{episode}.zip"
        save_seen()

        print(f"  [201] {episode}  {got/1048576:.1f}MB  "
              f"프레임 {frames}장  {', '.join(others)}")
        print(f"        기기 {device}")
        self._send(201, "stored")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--port", type=int, default=8000)
    ap.add_argument("--out", default="received")
    a = ap.parse_args()
    OUT = a.out
    os.makedirs(OUT, exist_ok=True)
    SEEN_PATH = os.path.join(OUT, "_seen.json")
    load_seen()
    print(f"목 서버 http://0.0.0.0:{a.port}  저장 위치 {os.path.abspath(OUT)}")
    print(f"이미 받은 에피소드 {len(SEEN)}건")
    print("폰에서 보게 하려면:  adb reverse tcp:%d tcp:%d" % (a.port, a.port))
    ThreadingHTTPServer(("0.0.0.0", a.port), Handler).serve_forever()
