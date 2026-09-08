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
import org.json.JSONObject
import java.util.UUID
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

    private fun onQr(raw: String) {
        try {
            val j = JSONObject(raw)
            val server = j.optString("server_url", "")
            val user = j.optString("user_id", "")
            var session = j.optString("session_id", "")
            val skill = j.optString("skill_id", "")
            if (session.isBlank()) session = "sess_" + UUID.randomUUID().toString().take(8)
            Prefs.setSession(this, server, user, session, skill)
            runOnUiThread {
                status.text = "연동 완료: ${user.ifBlank { "(user 없음)" }} · $session"
                goMain()
            }
        } catch (e: Exception) {
            handled = false   // JSON 아니면 계속 스캔
            runOnUiThread { status.text = "QR 형식(JSON) 아님 — 다시 비춰주세요" }
        }
    }

    private fun goMain() {
        try { provider?.unbindAll() } catch (_: Exception) {}   // ARCore 전에 카메라 해제
        startActivity(Intent(this, MainActivity::class.java))
        finish()
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
