plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jarvis.asistan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jarvis.asistan"
        minSdk = 29
        targetSdk = 35
        versionCode = 5
        versionName = "2.3"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        // Kişisel kullanım için sabit anahtar: güncellemeler eski sürümün üzerine sorunsuz kurulur.
        create("jarvis") {
            storeFile = file("jarvis.keystore")
            storePassword = "jarvis123"
            keyAlias = "jarvis"
            keyPassword = "jarvis123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("jarvis")
        }
        debug {
            signingConfig = signingConfigs.getByName("jarvis")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }

    packaging {
        // APK boyutunu küçültmek için yerel kütüphaneleri ve dex dosyalarını sıkıştır.
        jniLibs { useLegacyPackaging = true }
        dex { useLegacyPackaging = true }
        resources {
            excludes += listOf(
                "META-INF/*.kotlin_module", "META-INF/DEPENDENCIES", "META-INF/LICENSE*",
                "META-INF/NOTICE*", "META-INF/versions/**", "META-INF/INDEX.LIST",
                "META-INF/*.SF", "META-INF/native-image/**", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/io.netty.versions.properties"
            )
        }
    }
    androidResources { noCompress += "onnx" }
    lint { checkReleaseBuilds = false }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("com.anthropic:anthropic-java:3.8.0")
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.32.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
}
