package com.a103.collect

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.OutputConfiguration
import android.media.MediaCodec
import android.os.Handler
import android.os.HandlerThread
import android.util.Range
import android.util.Size
import android.view.Surface
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.atan

/**
 * 후면 초광각(최대 FOV·고정초점) 렌즈를 골라 프리뷰+인코더에 30fps 로 연속 공급한다.
 * 세션은 한 번 열고 계속 유지(녹화/정지 시 재구성 없음 → 화면 멈춤 없음).
 * AF/OIS/EIS/왜곡보정 OFF, 인코더 출력 timestampBase=SENSOR(PTS=센서 시각).
 * frames.csv 는 rec 가 설정된 동안(녹화 중)에만 기록한다.
 */
class CameraCapture(context: Context) {

    interface Listener { fun ready(); fun failed(reason: String) }

    val metadata = JSONObject()

    /** 녹화 중일 때만 프레임 메타를 여기에 기록. 대기 중엔 null. */
    @Volatile var rec: Recording? = null

    private val manager = context.getSystemService(CameraManager::class.java)
    private val id: String
    private val characteristics: CameraCharacteristics
    private val thread = HandlerThread("collect-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val stopped = CountDownLatch(1)

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    @Volatile private var closed = false

    init {
        val candidates = JSONArray()
        var best: String? = null
        var bestFov = 0.0
        for (cid in manager.cameraIdList) {
            val c = manager.getCameraCharacteristics(cid)
            if (c.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) continue
            val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val phys = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            if (focal == null || focal.isEmpty() || phys == null) continue
            val hFov = Math.toDegrees(2.0 * atan(phys.width / (focal[0] * 2.0)))
            candidates.put(JSONObject().put("id", cid).put("focal_mm", focal[0]).put("horizontal_fov_estimate", hFov))
            if (hFov > bestFov) { bestFov = hFov; best = cid }
        }
        if (best == null || bestFov < 90.0) throw Exception("초광각 카메라 접근 불가. 메인 렌즈로 대체하지 않습니다.")
        id = best
        characteristics = manager.getCameraCharacteristics(id)

        val minFocus = characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
        if (minFocus == null || minFocus != 0f) throw Exception("선택된 렌즈가 고정초점 초광각인지 확인 필요: $id")

        val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(MediaCodec::class.java)
        if (sizes == null || !sizes.contains(Size(1920, 1080))) throw Exception("초광각 1080p 인코딩 미지원")

        metadata
            .put("id", id)
            .put("candidates", candidates)
            .put("minimum_focus_distance", minFocus)
            .put("sensor_orientation", characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION))
            .put("timestamp_source", characteristics.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE))
            .put("focal_lengths_mm", characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.contentToString())
            .put("horizontal_fov_estimate", bestFov)
    }

    /** 프리뷰 + 인코더 surface 를 한 세션에 물려 연속 공급 시작. */
    fun open(preview: Surface, encoder: Surface, listener: Listener) {
        handler.post {
            try {
                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        if (closed) { device.close(); return }
                        camera = device
                        try { configure(device, preview, encoder, listener) }
                        catch (e: Exception) { listener.failed(e.toString()) }
                    }
                    override fun onDisconnected(device: CameraDevice) { device.close(); if (!closed) listener.failed("카메라 연결 종료") }
                    override fun onError(device: CameraDevice, err: Int) { device.close(); if (!closed) listener.failed("카메라 오류 $err") }
                }, handler)
            } catch (e: Exception) { listener.failed(e.toString()) }
        }
    }

    private fun configure(device: CameraDevice, preview: Surface, encoder: Surface, listener: Listener) {
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        req.addTarget(preview); req.addTarget(encoder)
        req.set(CaptureRequest.CONTROL_AF_MODE, 0)
        req.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 0)
        characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.takeIf { it.contains(0) }?.let { req.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 0) }
        characteristics.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
            ?.takeIf { it.contains(0) }?.let { req.set(CaptureRequest.DISTORTION_CORRECTION_MODE, 0) }
        val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        if (fpsRanges == null || !fpsRanges.contains(Range(30, 30))) throw Exception("고정 30fps 미지원")
        req.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))

        val previewCfg = OutputConfiguration(preview)
        val encoderCfg = OutputConfiguration(encoder).apply { timestampBase = OutputConfiguration.TIMESTAMP_BASE_SENSOR }
        device.createCaptureSessionByOutputConfigurations(listOf(previewCfg, encoderCfg),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cs: CameraCaptureSession) {
                    if (closed) { cs.close(); return }
                    session = cs
                    try { cs.setRepeatingRequest(req.build(), frameCb, handler); listener.ready() }
                    catch (e: Exception) { listener.failed(e.toString()) }
                }
                override fun onReady(cs: CameraCaptureSession) {
                    if (closed) { cs.close(); camera?.close(); stopped.countDown() }
                }
                override fun onConfigureFailed(cs: CameraCaptureSession) { listener.failed("카메라 세션 구성 실패") }
            }, handler)
    }

    private val frameCb = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, req: CaptureRequest, res: TotalCaptureResult) {
            val r = rec ?: return   // 녹화 중에만 기록
            val crop = res.get(CaptureResult.SCALER_CROP_REGION)
            val cropStr = if (crop == null) ",,," else "${crop.left},${crop.top},${crop.right},${crop.bottom}"
            r.row("frames.csv", listOf(
                res.frameNumber,
                v(res, CaptureResult.SENSOR_TIMESTAMP),
                v(res, CaptureResult.SENSOR_EXPOSURE_TIME),
                v(res, CaptureResult.SENSOR_FRAME_DURATION),
                v(res, CaptureResult.SENSOR_SENSITIVITY),
                v(res, CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW),
                v(res, CaptureResult.LENS_FOCUS_DISTANCE),
                v(res, CaptureResult.CONTROL_AF_MODE),
                v(res, CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE),
                v(res, CaptureResult.LENS_OPTICAL_STABILIZATION_MODE),
            ).joinToString(",") + ",$cropStr")
        }
        override fun onCaptureFailed(s: CameraCaptureSession, req: CaptureRequest, f: CaptureFailure) {
            rec?.error = "카메라 캡처 실패: ${f.frameNumber}"
        }
    }

    private fun <T> v(res: TotalCaptureResult, key: CaptureResult.Key<T>): String = res.get(key)?.toString() ?: ""

    fun close() {
        handler.post {
            closed = true
            try {
                val cs = session
                if (cs == null) { camera?.close(); stopped.countDown() } else cs.stopRepeating()
            } catch (e: Exception) { camera?.close(); stopped.countDown() }
        }
        if (!stopped.await(3, TimeUnit.SECONDS)) handler.post { session?.close(); camera?.close() }
        thread.quitSafely()
        thread.join(2000)
    }
}
