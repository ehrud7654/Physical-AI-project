package com.a103.collect

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * UMI USB Camera2(org.umi.stream) 호환 프레임 서버. PC(ROS camera_stream.py)가 adb forward 로 접속.
 * TCP 127.0.0.1:8765, 클라이언트가 1바이트(0x01)를 보낼 때마다 최신 프레임 1개를 보낸다(빅엔디안):
 *   "UMI1" · sequence(u64) · sensor_timestamp_ns(u64) · width(u32) · height(u32) · jpeg_size(u32) · jpeg
 * 같은 프레임은 두 번 보내지 않는다(새 프레임이 올 때까지 대기) — 수신측이 seq 증가를 검사함.
 */
class FrameServer(
    private val port: Int = 8765,
    private val onClient: (Boolean) -> Unit,
    private val onError: (String) -> Unit = {},
) {

    private val lock = Object()
    private var jpeg: ByteArray? = null
    private var seq = 0L
    private var ts = 0L
    @Volatile private var running = true
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    /** 누적 전송 프레임 수(fps 표시용). */
    @Volatile var sent = 0L; private set

    private val thread = Thread(::serve, "umi-frame-server").apply { start() }

    fun publish(data: ByteArray, sensorTs: Long) {
        synchronized(lock) { jpeg = data; ts = sensorTs; seq++; lock.notifyAll() }
    }

    private fun serve() {
        // 포트를 다른 앱(예: 원본 UMI USB Camera2)이 쓰고 있으면 bind 가 실패 — 조용히 넘기면 '로봇 연결 대기'에 멈추므로 알린다.
        val ss = try {
            ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port)) }
        } catch (_: IOException) {
            if (running) onError("포트 $port 사용 중 — 다른 스트리밍 앱을 종료하세요")
            return
        }
        server = ss
        ss.use {
            while (running) {
                val s = try { ss.accept() } catch (_: IOException) { break }
                client = s
                onClient(true)
                try { s.use { serveClient(it) } } catch (_: IOException) {}
                client = null
                synchronized(lock) { jpeg = null }   // 재접속한 클라이언트에 끊기 전 프레임을 주지 않도록
                onClient(false)
            }
        }
    }

    private fun serveClient(s: Socket) {
        s.tcpNoDelay = true
        val input = s.getInputStream()
        val out = DataOutputStream(BufferedOutputStream(s.getOutputStream(), 1 shl 16))
        var last = -1L
        while (running) {
            s.soTimeout = 10_000
            if (input.read() != 1) return          // 0x01 외(끊김 포함)는 종료
            val (d, n, t) = awaitFrame(s, input, last) ?: return
            out.writeInt(MAGIC)
            out.writeLong(n); out.writeLong(t)
            out.writeInt(WIDTH); out.writeInt(HEIGHT)
            out.writeInt(d.size); out.write(d)
            out.flush()
            last = n; sent++
        }
    }

    /**
     * 새 프레임을 기다린다. 송출 전엔 프레임이 안 와서 소켓을 읽지 않게 되는데, 그러면 로봇이 끊어도 모른다
     * → 기다리는 동안 0.5초마다 상대 종료를 확인한다. 끊겼거나 멈추면 null.
     */
    private fun awaitFrame(s: Socket, input: InputStream, last: Long): Triple<ByteArray, Long, Long>? {
        while (running) {
            val (data, n, t) = synchronized(lock) {
                if (jpeg == null || seq == last) lock.wait(500)
                Triple(jpeg, seq, ts)
            }
            if (data != null && n != last) return Triple(data, n, t)
            if (peerGone(s, input)) return null
        }
        return null
    }

    /** 응답 대기 중엔 클라이언트가 보낼 게 없다 → EOF·데이터 모두 끊김/비정상으로 본다. 1ms 안에 아무것도 없으면 살아 있음. */
    private fun peerGone(s: Socket, input: InputStream): Boolean {
        s.soTimeout = 1
        return try { input.read(); true } catch (_: SocketTimeoutException) { false }
    }

    fun close() {
        running = false
        synchronized(lock) { lock.notifyAll() }
        try { client?.close() } catch (_: IOException) {}
        try { server?.close() } catch (_: IOException) {}
        thread.join(1500)
    }

    companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
        private const val MAGIC = 0x554D4931   // "UMI1"
    }
}
