plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.a103.collect"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.a103.collect"
        // Camera2 OutputConfiguration.setTimestampBase(SENSOR) 가 API 33+ 필요.
        minSdk = 33
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            buildConfigField("String", "UPLOAD_BASE_URL", "\"http://j15a103.p.ssafy.io\"")
        }
        release {
            isMinifyEnabled = false
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
    implementation("androidx.activity:activity-ktx:1.9.2")
    // QR 스캔(온보딩): CameraX 미리보기 + ML Kit 바코드(온디바이스)
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
