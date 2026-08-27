package com.ssafy.arpose

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Locale

/**
 * 자이로·가속도를 200Hz 로 받아 imu.csv 에 기록한다.
 *
 * pose/영상(30Hz)과는 완전히 별개인 스트림이다. ARCore 는 카메라 프레임에 묶여 있어
 * 30Hz 가 상한이므로, 고주파 신호는 Android 센서 API 로 직접 받는다.
 *
 * imu.csv : timestamp_ns,sensor,x,y,z
 *   자이로와 가속도는 샘플이 도착하는 시점이 서로 다르다. 한 행에 묶으면 없는 값을
 *   만들어내야 하므로 행마다 어느 센서인지 적는다.
 *
 * 주의: Android 에서 샘플링 주기는 "요청"이지 보장이 아니다. 실제로 몇 Hz 가 나왔는지는
 * 녹화가 끝난 뒤 summary.txt 로 확인해야 한다.
 */
class ImuLogger(ctx: Context) : SensorEventListener {

    companion object {
        private const val TAG = "IMU"
        const val TARGET_HZ = 200
        private const val PERIOD_US = 1_000_000 / TARGET_HZ   // 5000us
    }

    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro: Sensor? = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var thread: HandlerThread? = null
    private var registered = false

    @Volatile private var writing = false
    private var csv: BufferedWriter? = null
    private val lock = Any()

    // 실측용 카운터 (센서별로 따로 센다)
    private var accelCount = 0
    private var gyroCount = 0
    private var accelFirstNs = 0L
    private var accelLastNs = 0L
    private var gyroFirstNs = 0L
    private var gyroLastNs = 0L

    /**
     * 센서 타임스탬프가 ARCore frame.timestamp 와 같은 기준(elapsedRealtimeNanos)인지
     * 확인하기 위한 값. 첫 샘플을 받은 순간의 elapsedRealtimeNanos 와 그 샘플의
     * timestamp 차이다. 두 기준이 같다면 수 ms 안쪽이어야 한다.
     */
    private var clockSkewMs = Double.NaN

    val available: Boolean get() = accel != null && gyro != null

    fun describe(): String {
        val a = accel?.let { "${it.name} (max ${"%.1f".format(1e6f / it.minDelay)}Hz)" } ?: "없음"
        val g = gyro?.let { "${it.name} (max ${"%.1f".format(1e6f / it.minDelay)}Hz)" } ?: "없음"
        return "imu_accel=$a\nimu_gyro=$g\nimu_requested_hz=$TARGET_HZ\n"
    }

    fun register() {
        if (registered || !available) {
            if (!available) Log.w(TAG, "가속도/자이로 없음 - IMU 기록 불가")
            return
        }
        val t = HandlerThread("imu").apply { start() }
        thread = t
        val h = Handler(t.looper)
        sm.registerListener(this, accel, PERIOD_US, h)
        sm.registerListener(this, gyro, PERIOD_US, h)
        registered = true
        Log.i(TAG, "센서 등록 (${TARGET_HZ}Hz 요청)")
    }

    fun unregister() {
        if (!registered) return
        sm.unregisterListener(this)
        thread?.quitSafely()
        thread = null
        registered = false
    }

    fun start(dir: File) {
        if (!available) return
        synchronized(lock) {
            accelCount = 0; gyroCount = 0
            accelFirstNs = 0L; gyroFirstNs = 0L
            clockSkewMs = Double.NaN
            try {
                csv = BufferedWriter(FileWriter(File(dir, "imu.csv")), 1 shl 16).apply {
                    write("timestamp_ns,sensor,x,y,z\n")
                }
                writing = true
            } catch (e: Exception) {
                Log.e(TAG, "imu.csv 열기 실패", e)
                csv = null; writing = false
            }
        }
    }

    /** 녹화 종료. summary.txt 에 붙일 텍스트를 돌려준다. */
    fun stop(): String {
        if (!available) return "imu=없음(센서 미지원)\n"
        writing = false
        synchronized(lock) {
            try { csv?.flush(); csv?.close() } catch (_: Exception) {}
            csv = null
        }
        val aHz = rate(accelCount, accelFirstNs, accelLastNs)
        val gHz = rate(gyroCount, gyroFirstNs, gyroLastNs)
        return buildString {
            append("imu_accel_samples=$accelCount\n")
            append("imu_accel_hz=${String.format(Locale.US, "%.1f", aHz)}\n")
            append("imu_gyro_samples=$gyroCount\n")
            append("imu_gyro_hz=${String.format(Locale.US, "%.1f", gHz)}\n")
            append("imu_clock_skew_ms=${String.format(Locale.US, "%.2f", clockSkewMs)}\n")
        }
    }

    private fun rate(n: Int, firstNs: Long, lastNs: Long): Double {
        val dur = (lastNs - firstNs) / 1e9
        return if (n > 1 && dur > 0) (n - 1) / dur else 0.0
    }

    override fun onSensorChanged(e: SensorEvent) {
        if (!writing) return
        val kind = when (e.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "accel"
            Sensor.TYPE_GYROSCOPE -> "gyro"
            else -> return
        }
        if (clockSkewMs.isNaN()) {
            clockSkewMs = (SystemClock.elapsedRealtimeNanos() - e.timestamp) / 1e6
        }
        if (kind == "accel") {
            if (accelFirstNs == 0L) accelFirstNs = e.timestamp
            accelLastNs = e.timestamp; accelCount++
        } else {
            if (gyroFirstNs == 0L) gyroFirstNs = e.timestamp
            gyroLastNs = e.timestamp; gyroCount++
        }
        val line = String.format(
            Locale.US, "%d,%s,%.6f,%.6f,%.6f\n",
            e.timestamp, kind, e.values[0], e.values[1], e.values[2]
        )
        synchronized(lock) {
            try { csv?.write(line) } catch (_: Exception) {}
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
