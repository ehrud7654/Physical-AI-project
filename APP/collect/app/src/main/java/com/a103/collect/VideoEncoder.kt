package com.a103.collect

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 지속형 H.264 인코더. 프리뷰 진입 시 한 번 생성돼 카메라 세션에 계속 물려 있고(=세션 재구성 없음 → freeze 없음),
 * 녹화는 파일(muxer)만 열고 닫는다. 대기 중엔 RotatedVideoSurface.active=false 로 인코더 공급이 끊겨 유휴.
 * 인코더 PTS = 카메라 센서 타임스탬프(µs). 샘플 메타는 encoded.csv.
 */
class VideoEncoder {

    val cameraSurface: Surface

    private val codec = MediaCodec.createEncoderByType("video/avc")
    private val rotation: RotatedVideoSurface
    private val drain: Thread

    @Volatile private var format: MediaFormat? = null
    @Volatile private var muxer: MediaMuxer? = null
    @Volatile private var track = -1
    @Volatile private var muxing = false
    @Volatile private var rec: Recording? = null
    @Volatile private var startDir: File? = null
    @Volatile private var stopLatch: CountDownLatch? = null
    @Volatile private var count = 0L
    @Volatile private var quit = false
    @Volatile var error: String? = null; private set

    init {
        val fmt = MediaFormat.createVideoFormat("video/avc", 1920, 1080).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 16_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val input = codec.createInputSurface()
        rotation = RotatedVideoSurface(input) { e -> error = e; rec?.error = e }
        cameraSurface = rotation.surface
        codec.start()
        drain = Thread(::drainLoop, "collect-encoder").apply { start() }
    }

    /** 녹화 파일 시작 — muxer 생성 예약 + 인코더 공급 개시. */
    fun startFile(recording: Recording) {
        rec = recording; count = 0; startDir = recording.dir
        rotation.active = true
    }

    /** 녹화 파일 종료 — 공급 중단 후 muxer 마무리(드레인 스레드에서). 카메라/인코더는 유지. */
    fun stopFile() {
        rotation.active = false
        val latch = CountDownLatch(1); stopLatch = latch
        latch.await(3, TimeUnit.SECONDS)
        rec = null
    }

    fun release() {
        quit = true
        try { rotation.stop() } catch (_: Exception) {}
        try { codec.signalEndOfInputStream() } catch (_: Exception) {}
        try { drain.join(3000) } catch (_: Exception) {}
        try { codec.stop() } catch (_: Exception) {}
        codec.release()
    }

    private fun drainLoop() {
        val info = MediaCodec.BufferInfo()
        while (!quit) {
            try {
                val out = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> format = codec.outputFormat
                    out < 0 -> {}
                    else -> {
                        // muxer 생성 예약 처리(포맷 준비되면).
                        val dir = startDir
                        if (dir != null && muxer == null && format != null) {
                            try {
                                val mx = MediaMuxer(File(dir, "video.mp4").path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                                track = mx.addTrack(format!!); mx.start()
                                muxer = mx; muxing = true; startDir = null
                            } catch (e: Exception) { error = e.toString(); rec?.error = error }
                        }
                        if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            val mx = muxer; val r = rec
                            if (mx != null && muxing && r != null && rotation.active) {
                                try {
                                    val buf = codec.getOutputBuffer(out)!!
                                    mx.writeSampleData(track, buf, info)
                                    r.row("encoded.csv", "${count++},${info.presentationTimeUs},${info.size},${info.flags}")
                                } catch (e: Exception) { error = e.toString(); r.error = error }
                            }
                        }
                        codec.releaseOutputBuffer(out, false)
                    }
                }
            } catch (e: Exception) { error = e.toString() }

            // 파일 마무리(정지 요청).
            stopLatch?.let { sl ->
                muxer?.let { mx -> try { if (muxing) mx.stop() } catch (_: Exception) {}; mx.release() }
                muxer = null; muxing = false; track = -1; startDir = null
                stopLatch = null; sl.countDown()
            }
        }
    }
}
