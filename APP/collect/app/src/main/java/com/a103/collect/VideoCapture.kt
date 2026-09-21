package com.a103.collect

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import java.io.File
import java.io.IOException

/**
 * H.264/AVC 1080p 인코더 + MP4 muxer. 인코더 입력은 RotatedVideoSurface(회전 baked) 가 그린다.
 * 인코더 PTS = 카메라 센서 타임스탬프(µs). 각 샘플 메타는 encoded.csv 로 남긴다.
 */
class VideoCapture(recording: Recording) {

    val surface: Surface

    private val codec: MediaCodec
    private val muxer: MediaMuxer
    private val rotation: RotatedVideoSurface
    private val drain: Thread
    private var track = -1
    private var muxing = false
    private var count = 0L
    @Volatile private var stopping = false
    @Volatile private var error: String? = null

    init {
        val fmt = MediaFormat.createVideoFormat("video/avc", 1920, 1080).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, 16_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        codec = MediaCodec.createEncoderByType("video/avc")
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val encoderInput = codec.createInputSurface()
        muxer = MediaMuxer(File(recording.dir, "video.mp4").path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        codec.start()
        rotation = RotatedVideoSurface(encoderInput, recording)
        surface = rotation.surface   // 카메라가 이 Surface 로 프레임을 낸다
        drain = Thread({ drainLoop(recording) }, "collect-encoder").apply { start() }
    }

    private fun drainLoop(recording: Recording) {
        val info = MediaCodec.BufferInfo()
        var eosDeadline = Long.MAX_VALUE
        try {
            while (true) {
                if (stopping && eosDeadline == Long.MAX_VALUE) eosDeadline = System.nanoTime() + 5_000_000_000L
                if (System.nanoTime() > eosDeadline) throw IOException("인코더 EOS 타임아웃")
                val out = codec.dequeueOutputBuffer(info, 10_000L)
                when {
                    out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = muxer.addTrack(codec.outputFormat); muxer.start(); muxing = true
                    }
                    out < 0 -> { /* try again */ }
                    else -> {
                        if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (!muxing) throw IOException("포맷 전 인코더 출력")
                            val buf = codec.getOutputBuffer(out)!!
                            muxer.writeSampleData(track, buf, info)
                            recording.row("encoded.csv", "${count++},${info.presentationTimeUs},${info.size},${info.flags}")
                        }
                        codec.releaseOutputBuffer(out, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) return
                    }
                }
            }
        } catch (e: Exception) {
            error = e.toString(); recording.error = error
        }
    }

    fun stop() {
        rotation.stop()
        stopping = true
        codec.signalEndOfInputStream()
        drain.join(6500)
        if (drain.isAlive) throw IOException("인코더가 멈추지 않음")
        try {
            if (muxing) muxer.stop()
        } finally {
            muxer.release(); codec.stop(); codec.release()
        }
        if (error != null || count == 0L) throw IOException(error ?: "영상 샘플 없음")
    }
}
