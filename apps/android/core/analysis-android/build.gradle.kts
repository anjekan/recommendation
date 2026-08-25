plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "kr.co.ninetyseconds.recommendation.analysis.android"
    compileSdk { version = release(36) }
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:analysis"))
    implementation(libs.androidx.camera.core)
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.onnxruntime.android)
}
