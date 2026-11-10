plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.jarvisai.assistant"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jarvisai.assistant"
        minSdk = 26
        targetSdk = 35
        ndk { abiFilters += listOf("arm64-v8a") }
        versionCode = 10
        versionName = "1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(files("libs/sherpa-onnx.aar"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
}