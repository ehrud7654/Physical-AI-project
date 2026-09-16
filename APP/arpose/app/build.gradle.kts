plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ssafy.arpose"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.a103.arpose"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        // OpenCV 네이티브는 arm64 만 담는다 (S22=arm64). 전 ABI 넣으면 APK 가 수백 MB 로 튄다.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // 실서버 업로드 테스트(임시). 목 서버로 돌아가려면 "http://127.0.0.1:8000" 로 복구.
            buildConfigField("String", "UPLOAD_BASE_URL", "\"http://j15a103.p.ssafy.io\"")
        }
        release {
            isMinifyEnabled = false
            // 실제 서버. 정해지면 여기만 바꾼다 (S15P21A103-10).
            buildConfigField("String", "UPLOAD_BASE_URL", "\"https://TODO.example.com\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // ARCore SDK
    implementation("com.google.ar:core:1.45.0")
    // 업로드 큐 (앱이 죽어도 재시도, Wi-Fi·충전 조건)
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // QR 스캔(온보딩): CameraX 미리보기 + ML Kit 바코드(온디바이스, 네트워크 불필요)
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    // 촬영 직후 마커 검출 즉시 QC (온디바이스, 샘플 프레임) — S15P21A103-160
    implementation("org.opencv:opencv:4.11.0")
}
