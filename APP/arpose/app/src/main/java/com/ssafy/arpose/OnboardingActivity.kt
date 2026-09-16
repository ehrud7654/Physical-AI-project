package com.ssafy.arpose

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import android.net.Uri
import java.util.concurrent.Executors

/**
 * 초기화면. QR 을 스캔해 세션 메타(server_url·user_id·session_id·skill_id)를 Prefs 에 저장하고
 * 촬영 화면으로 넘어간다. QR payload 는 JSON. QR 없이 진행도 가능(빈 값).
 *
 * 카메라는 여기서만 쓰고 넘어갈 때 unbind → 촬영 화면 ARCore 가 카메라를 잡는다(순차, 충돌 없음).
 */
class OnboardingActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private val analysisExec = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient()
    private var provider: ProcessCameraProvider? = null
    @Volatile private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 이미 등록됨(장치 secret 보유) → 재스캔 없이 바로 진입 (S15P21A103-168, 최초 1회).
        if (Prefs.deviceSecret(this).isNotBlank()) {
            enterApp(); finish(); return
        }
        setContentView(R.layout.activity_onboarding)
        previewView = findViewById(R.id.previewView)
        status = findViewById(R.id.scanStatus)
        findViewById<Button>(R.id.skipBtn).setOnClickListener { goMain() }   // QR 없이 진행

        if (hasCam()) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), CAM_REQ)
    }

    private fun hasCam() =
        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (hasCam()) startCamera() else status.text = "카메라 권한 필요 — QR 없이 진행 가능"
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                provider = future.get()
                val preview = Preview.Builder().build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExec, ::analyze) }
                provider?.unbindAll()
                provider?.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "카메라 시작 실패", e)
                status.text = "카메라 오류 — QR 없이 진행 가능"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @ExperimentalGetImage
    private fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null || handled) { proxy.close(); return }
        val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        scanner.process(img)
            .addOnSuccessListener { codes ->
                val raw = codes.firstOrNull { it.rawValue != null }?.rawValue
                if (raw != null && !handled) { handled = true; onQr(raw) }
            }
            .addOnCompleteListener { proxy.close() }
    }

    /** QR = umi://enroll?enrollmentId=..&registrationToken=.. (계약 §6.0). enroll 교환 후 촬영 화면. */
    private fun onQr(raw: String) {
        val u = Uri.parse(raw)
        val enrollmentId = u.getQueryParameter("enrollmentId") ?: ""
        val regToken = u.getQueryParameter("registrationToken") ?: ""
        if (enrollmentId.isBlank() || regToken.isBlank()) {
            handled = false   // 등록 QR 아님 → 계속 스캔
            runOnUiThread { status.text = "등록 QR 아님 — 다시 비춰주세요" }
            return
        }
        runOnUiThread { status.text = "장치 등록 중…" }
        Thread {
            val ok = DeviceAuth.enroll(this, enrollmentId, regToken)
            runOnUiThread {
                if (ok) { status.text = "등록 완료"; goMain() }
                else {
                    status.text = "등록 실패 — 네트워크 확인 후 다시 비춰주세요"
                    // 실패 시 즉시 재스캔하면 같은 QR 로 초당 수십회 폭주 → 2초 쿨다운 후 재개.
                    previewView.postDelayed({ handled = false }, 2000)
                }
            }
        }.start()
    }

    private fun goMain() {
        try { provider?.unbindAll() } catch (_: Exception) {}   // ARCore 전에 카메라 해제
        enterApp()
        finish()
    }

    /** 튜토리얼 미시청이면 튜토리얼 먼저(끝나면 Main으로), 봤으면 바로 Main. */
    private fun enterApp() {
        val next = if (!Prefs.tutorialSeen(this))
            Intent(this, TutorialActivity::class.java).putExtra("go_main", true)
        else Intent(this, MainActivity::class.java)
        startActivity(next)
    }

    override fun onDestroy() {
        super.onDestroy()
        analysisExec.shutdown()
    }

    companion object {
        private const val TAG = "QR"
        private const val CAM_REQ = 2001
    }
}
