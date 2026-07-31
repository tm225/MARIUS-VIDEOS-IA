plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.photoparallaxai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.photoparallaxai"
        minSdk = 26 // requis pour MediaCodec Surface input pratique + TFLite GPU delegate
        targetSdk = 34
        versionCode = 1
        versionName = "0.1-prototype"
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

    // Ne pas compresser le modèle .tflite dans l'APK
    androidResources {
        noCompress += "tflite"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.0")

    // TensorFlow Lite : inférence 100% locale, aucun appel réseau
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.16.1") // accélération GPU si dispo

    implementation("androidx.exifinterface:exifinterface:1.3.7")
}
