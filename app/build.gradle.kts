import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

// Konfigurasi dibaca dari build.env di root proyek
val buildEnv = Properties().apply {
    val f = rootProject.file("build.env")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "id.dstech.keuangan"
    compileSdk = buildEnv.getProperty("ANDROID_PLATFORM", "34").trim().toInt()

    defaultConfig {
        applicationId = buildEnv.getProperty("APPLICATION_ID", "id.dstech.keuangan").trim()
        minSdk = buildEnv.getProperty("MIN_SDK", "26").trim().toInt()
        targetSdk = buildEnv.getProperty("TARGET_SDK", "34").trim().toInt()
        versionCode = buildEnv.getProperty("VERSION_CODE", "1").trim().toInt()
        versionName = buildEnv.getProperty("VERSION_NAME", "1.0").trim()
        resValue("string", "app_name", buildEnv.getProperty("APP_NAME", "Keuangan").trim())
        resValue("color", "ic_launcher_background", buildEnv.getProperty("ICON_BACKGROUND", "#000000").trim())
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
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
