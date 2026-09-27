package com.a103.collect

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

/**
 * 가속도계+자이로를 ~200Hz(5000µs)로 기록. event.timestamp 는 카메라 SENSOR_TIMESTAMP 와
 * 동일한 elapsedRealtimeNanos 시간축 → 오프라인 SLAM 정렬의 근간. 가속도는 중력 포함.
 */
class ImuCapture(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(SensorManager::class.java)
    private val accel: Sensor? = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro: Sensor? = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val thread = HandlerThread("collect-imu")
    private lateinit var recording: Recording

    fun start(recording: Recording) {
        if (accel == null || gyro == null) throw Exception("가속도/자이로 센서 없음")
        this.recording = recording
        recording.manifest
            .put("imu_requested_hz", 200)
            .put("accelerometer_name", accel.name)
            .put("gyroscope_name", gyro.name)
        thread.start()
        val handler = Handler(thread.looper)
        val ok = manager.registerListener(this, accel, 5000, handler) &&
            manager.registerListener(this, gyro, 5000, handler)
        if (!ok) { stop(); throw Exception("IMU 등록 실패") }
    }

    override fun onSensorChanged(e: SensorEvent) {
        val name = if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) "accelerometer.csv" else "gyroscope.csv"
        recording.row(name, "${e.timestamp},${e.values[0]},${e.values[1]},${e.values[2]},${e.accuracy}")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun stop() {
        manager.unregisterListener(this)
        thread.quitSafely()
        thread.join(2000)
    }
}
