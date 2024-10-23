plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp.android)
}

android {
    namespace = "com.devjethava.facialattendance"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.devjethava.facialattendance"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    androidResources {
        noCompress += "tflite"
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }

    sourceSets.getByName("main").assets.srcDir("src/main/assets")

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ML Kit Face Detection
    implementation("com.google.mlkit:face-detection:16.1.7")

    // CameraX
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // Room for SQLite
    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    implementation(libs.itextg)

    implementation("org.tensorflow:tensorflow-lite:2.9.0")

    implementation("com.google.mediapipe:tasks-vision:0.10.16")
    implementation("com.google.mediapipe:tasks-core:0.10.16")

    implementation("com.google.code.gson:gson:2.11.0")

}