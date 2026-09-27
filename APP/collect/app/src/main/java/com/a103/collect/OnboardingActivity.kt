package com.a103.collect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
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
import java.util.concurrent.Executors

/**
 * 초기화면. QR(umi://enroll?enrollmentId=..&registrationToken=..)을 스캔해 장치를 등록하고 촬영 화면으로.
 * 이미 등록된(Device Secret 보유) 기기는 바로 진입. 등록은 필수라 QR 없이 진행은 없다(ARpose 와 동일).
 * 카메라 권한 거부 시 '카메라 권한 허용' 버튼 → 앱 설정, 허용하고 돌아오면 자동 재개.
 */
class OnboardingActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var guide: TextView
    private lateinit var grantBtn: Button
    private var cameraStarted = false
    private val analysisExec = Executors.newSingleThreadExecutor()
    private val scanner = BarcodeScanning.getClient()
    private var provider: ProcessCameraProvider? = null
    @Volatile private var handled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.deviceSecret(this).isNotBlank()) { enterApp(); finish(); return }
        setContentView(R.layout.activity_onboarding)
        previewView = findViewById(R.id.previewView)
        status = findViewById(R.id.scanStatus)
        guide = findViewById(R.id.scanGuide)
        grantBtn = findViewById(R.id.grantBtn)
        grantBtn.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }

        if (hasCam()) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), CAM_REQ)
    }

    /** 설정에서 권한을 허용하고 돌아오면 자동으로 카메라 재개. */
    override fun onResume() {
        super.onResume()
        if (::previewView.isInitialized && !cameraStarted && hasCam()) startCamera()
    }

    private fun hasCam() = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(rc: Int, p: Array<String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (hasCam()) startCamera()
        else { status.text = "카메라 권한이 필요해요"; guide.visibility = View.GONE; grantBtn.visibility = View.VISIBLE }
    }

    private fun startCamera() {
        if (cameraStarted) return
        cameraStarted = true
        status.text = ""; grantBtn.visibility = View.GONE; guide.visibility = View.VISIBLE
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                provider = future.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    .also { it.setAnalyzer(analysisExec, ::analyze) }
                provider?.unbindAll()
                provider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 시작 실패", e); status.text = "카메라 오류 — 앱을 다시 실행해 주세요"
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

    private fun onQr(raw: String) {
        val u = Uri.parse(raw)
        val enrollmentId = u.getQueryParameter("enrollmentId") ?: ""
        val regToken = u.getQueryParameter("registrationToken") ?: ""
        if (enrollmentId.isBlank() || regToken.isBlank()) {
            handled = false
            runOnUiThread { status.text = "등록 QR 아님 — 다시 비춰주세요" }
            return
        }
        runOnUiThread { status.text = "QR 인식 완료 · 장치 등록 중…"; guide.visibility = View.GONE }
        Thread {
            val ok = DeviceAuth.enroll(this, enrollmentId, regToken)
            runOnUiThread {
                if (ok) { status.text = "등록 완료"; goMain() }
                else {
                    status.text = "등록 실패 — 네트워크 확인 후 다시 비춰주세요"
                    previewView.postDelayed({ handled = false }, 2000)
                }
            }
        }.start()
    }

    private fun goMain() {
        try { provider?.unbindAll() } catch (_: Exception) {}
        enterApp(); finish()
    }

    /** 튜토리얼 미시청이면 먼저(끝나면 Main), 봤으면 바로 Main. */
    private fun enterApp() {
        val next = if (!Prefs.tutorialSeen(this))
            Intent(this, TutorialActivity::class.java).putExtra("go_main", true)
        else Intent(this, MainActivity::class.java)
        startActivity(next)
    }

    override fun onDestroy() { super.onDestroy(); analysisExec.shutdown() }

    companion object { private const val TAG = "QR"; private const val CAM_REQ = 2001 }
}
