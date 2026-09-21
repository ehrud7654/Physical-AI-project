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
import android.hardware.camera2.params.StreamConfigurationMap
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
 * 후면 초광각(최대 FOV·고정초점) 렌즈를 골라 30fps 로 프리뷰+인코더에 준다.
 * AF/OIS/EIS/왜곡보정 전부 OFF(원본 센서 픽셀 보존), 노출·화이트밸런스는 연속(프레임별 값 기록).
 * 인코더 출력의 timestampBase 를 SENSOR 로 두어 PTS 가 센서 시각이 되게 한다.
 * 프레임별 메타는 frames.csv 로 남긴다.
 */
class CameraCapture(context: Context) {

    interface Listener { fun ready(); fun failed(reason: String) }

    val metadata = JSONObject()

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

    fun open(preview: Surface, encoder: Surface, recording: Recording, listener: Listener) {
        handler.post {
            try {
                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        if (closed) { device.close(); return }
                        camera = device
                        try { configure(device, preview, encoder, recording, listener) }
                        catch (e: Exception) { listener.failed(e.toString()) }
                    }
                    override fun onDisconnected(device: CameraDevice) {
                        device.close(); if (!closed) listener.failed("카메라 연결 종료")
                    }
                    override fun onError(device: CameraDevice, err: Int) {
                        device.close(); if (!closed) listener.failed("카메라 오류 $err")
                    }
                }, handler)
            } catch (e: Exception) { listener.failed(e.toString()) }
        }
    }

    /** AF/EIS/OIS/왜곡보정 OFF + 고정 30fps. 프리뷰·녹화 공통. */
    private fun applyDefaults(req: CaptureRequest.Builder) {
        req.set(CaptureRequest.CONTROL_AF_MODE, 0)
        req.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, 0)
        characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.takeIf { it.contains(0) }?.let { req.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, 0) }
        characteristics.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES)
            ?.takeIf { it.contains(0) }?.let { req.set(CaptureRequest.DISTORTION_CORRECTION_MODE, 0) }
        val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        if (fpsRanges == null || !fpsRanges.contains(Range(30, 30))) throw Exception("고정 30fps 미지원")
        req.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
    }

    /** 녹화 없이 초광각 라이브 프리뷰만. 촬영 대기 화면용. */
    fun openPreview(preview: Surface, listener: Listener) {
        handler.post {
            try {
                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(device: CameraDevice) {
                        if (closed) { device.close(); return }
                        camera = device
                        try {
                            val req = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                            req.addTarget(preview); applyDefaults(req)
                            device.createCaptureSessionByOutputConfigurations(listOf(OutputConfiguration(preview)),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(cs: CameraCaptureSession) {
                                        if (closed) { cs.close(); return }
                                        session = cs
                                        try { cs.setRepeatingRequest(req.build(), null, handler); listener.ready() }
                                        catch (e: Exception) { listener.failed(e.toString()) }
                                    }
                                    override fun onConfigureFailed(cs: CameraCaptureSession) { listener.failed("프리뷰 세션 구성 실패") }
                                }, handler)
                        } catch (e: Exception) { listener.failed(e.toString()) }
                    }
                    override fun onDisconnected(device: CameraDevice) { device.close(); if (!closed) listener.failed("카메라 연결 종료") }
                    override fun onError(device: CameraDevice, err: Int) { device.close(); if (!closed) listener.failed("카메라 오류 $err") }
                }, handler)
            } catch (e: Exception) { listener.failed(e.toString()) }
        }
    }

    private fun configure(device: CameraDevice, preview: Surface, encoder: Surface, recording: Recording, listener: Listener) {
        val req = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        req.addTarget(preview); req.addTarget(encoder)
        applyDefaults(req)

        recording.manifest
            .put("auto_exposure", "continuous; per-frame exposure recorded")
            .put("auto_white_balance", "continuous")
            .put("encoder_timestamp_base", "SENSOR")
        recording.save()

        val previewCfg = OutputConfiguration(preview)
        val encoderCfg = OutputConfiguration(encoder).apply { timestampBase = OutputConfiguration.TIMESTAMP_BASE_SENSOR }

        device.createCaptureSessionByOutputConfigurations(listOf(previewCfg, encoderCfg),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(cs: CameraCaptureSession) {
                    if (closed) { cs.close(); return }
                    session = cs
                    try {
                        cs.setRepeatingRequest(req.build(), frameCb(recording), handler)
                        listener.ready()
                    } catch (e: Exception) { listener.failed(e.toString()) }
                }
                override fun onReady(cs: CameraCaptureSession) {
                    if (closed) { cs.close(); camera?.close(); stopped.countDown() }
                }
                override fun onConfigureFailed(cs: CameraCaptureSession) { listener.failed("카메라 세션 구성 실패") }
            }, handler)
    }

    private fun frameCb(recording: Recording) = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, req: CaptureRequest, res: TotalCaptureResult) {
            val crop = res.get(CaptureResult.SCALER_CROP_REGION)
            val cropStr = if (crop == null) ",,," else "${crop.left},${crop.top},${crop.right},${crop.bottom}"
            recording.row("frames.csv", listOf(
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
            recording.error = "카메라 캡처 실패: ${f.frameNumber}"
        }
    }

    private fun <T> v(res: TotalCaptureResult, key: CaptureResult.Key<T>): String = res.get(key)?.toString() ?: ""

    fun stop() {
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
