plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ssafy.arpose"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ssafy.arpose"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}
