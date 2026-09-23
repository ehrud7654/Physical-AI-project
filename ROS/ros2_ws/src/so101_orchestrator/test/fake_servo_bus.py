"""가짜 ST3215 서보 버스 — 팔 없이 런타임/오케스트레이터를 끝까지 돌리기 위한 것 (S15P21A103-46).

pty(가상 시리얼)를 만들고 LINK 경로에 심볼릭 링크를 건 뒤, servo_bus.py 가 쓰는 프로토콜
(PING/READ/WRITE/SYNC_WRITE)에 응답한다. 런타임은 --motor-port LINK 로만 바꾸면 된다.

  python3 fake_servo_bus.py [/tmp/so101_fake_servo]

- 서보 ID 1~6. 진단은 정상값(온도 30C·전압 12.4V·토크 ON·status 0).
- SYNC_WRITE/WRITE 로 목표 위치가 오면 즉시 도달한 것으로 반영(present = goal).
- 실물 없는 테스트 전용. /dev/ttyTHS1 은 건드리지 않는다.
"""
import os
import sys
import tty

LINK = sys.argv[1] if len(sys.argv) > 1 else "/tmp/so101_fake_servo"
IDS = range(1, 7)
# 레지스터 주소 (servo_bus.py 와 동일)
TORQUE, ACC, GOAL, GOAL_SPEED, TORQUE_LIMIT = 40, 41, 42, 46, 48
POS, SPEED, LOAD, VOLT, TEMP, STATUS, MOVING, CURRENT = 56, 58, 60, 62, 63, 65, 66, 69
START_TICKS = {1: 1990, 2: 2048, 3: 2048, 4: 2048, 5: 2126, 6: 1000}   # 캘리브 영점 부근


def word(reg, addr, value):
    reg[addr], reg[addr + 1] = value & 0xFF, (value >> 8) & 0xFF


def make_servo(tick):
    reg = bytearray(128)
    reg[TORQUE] = 1
    word(reg, GOAL, tick)
    word(reg, TORQUE_LIMIT, 1000)
    word(reg, POS, tick)
    reg[VOLT], reg[TEMP], reg[STATUS], reg[MOVING] = 124, 30, 0, 0
    return reg


servos = {i: make_servo(START_TICKS[i]) for i in IDS}


def reply(sid, params=b""):
    body = bytes((sid, len(params) + 2, 0)) + params          # id, length, error=0, params
    return b"\xff\xff" + body + bytes(((~sum(body)) & 0xFF,))


def set_goal(sid, pos):
    word(servos[sid], GOAL, pos)
    word(servos[sid], POS, pos)                                # 즉시 도달


def handle(sid, instr, params):
    if instr == 0x83:                                          # SYNC_WRITE(broadcast): 응답 없음
        addr, n = params[0], params[1]
        data = params[2:]
        for k in range(0, len(data), n + 1):
            s, chunk = data[k], data[k + 1:k + 1 + n]
            if s in servos:
                servos[s][addr:addr + n] = chunk
                if addr <= GOAL < addr + n:
                    set_goal(s, servos[s][GOAL] | servos[s][GOAL + 1] << 8)
        return None
    if sid not in servos:
        return None                                            # 없는 ID → 무응답(타임아웃)
    if instr == 0x01:                                          # PING
        return reply(sid)
    if instr == 0x02:                                          # READ addr, n
        addr, n = params[0], params[1]
        return reply(sid, bytes(servos[sid][addr:addr + n]))
    if instr == 0x03:                                          # WRITE addr, data...
        addr, data = params[0], params[1:]
        servos[sid][addr:addr + len(data)] = data
        if addr <= GOAL < addr + len(data):
            set_goal(sid, servos[sid][GOAL] | servos[sid][GOAL + 1] << 8)
        return reply(sid)
    return None


def main():
    master, slave = os.openpty()
    tty.setraw(slave)                  # 에코·줄 단위 처리 끔 (클라이언트 열기 전에도 raw)
    if os.path.lexists(LINK):
        os.remove(LINK)
    os.symlink(os.ttyname(slave), LINK)
    print(f"fake servo bus: {LINK} -> {os.ttyname(slave)} (ids {list(IDS)})", flush=True)
    buf = bytearray()
    try:
        while True:
            buf += os.read(master, 256)                        # slave fd 를 쥐고 있어 EIO 없음
            while True:
                start = buf.find(b"\xff\xff")
                if start < 0:
                    buf.clear()
                    break
                del buf[:start]
                if len(buf) < 4 or len(buf) < 4 + buf[3]:
                    break                                      # 패킷 덜 옴
                sid, length = buf[2], buf[3]
                instr, params = buf[4], bytes(buf[5:4 + length - 1])
                del buf[:4 + length]
                out = handle(sid, instr, params)
                if out:
                    os.write(master, out)
    finally:
        if os.path.islink(LINK):
            os.remove(LINK)


if __name__ == "__main__":
    main()
