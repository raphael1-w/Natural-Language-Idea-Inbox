plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.myapplication"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 33
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
    buildFeatures {
        viewBinding = true
        mlModelBinding = true
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.material.v150)
    implementation(libs.room.common)
    implementation(libs.room.runtime)
    implementation(libs.tensorflow.lite.metadata)
    testImplementation(libs.junit)
    implementation(libs.tensorflow.lite.v2161)
    implementation(libs.tensorflow.lite.gpu.v2161)
    implementation(libs.tensorflow.lite.support.v044)
    implementation(libs.play.services.tasks.v1810)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    val room_version = "2.6.1"
    annotationProcessor(libs.room.compiler)
    testImplementation(libs.room.testing)

    // LiteRT dependencies for Google Play services
    implementation(libs.play.services.tflite.java)
    // Optional: include LiteRT Support Library
    implementation(libs.play.services.tflite.support)
}