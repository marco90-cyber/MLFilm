plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    androidResources {
        noCompress += "tflite"
    }
    namespace = "com.marco.mlfilm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.marco.mlfilm"
        minSdk = 26
        targetSdk = 35
        val build = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionCode = build
        versionName = "1.0.$build"
    }

    // Stessa logica di firma delle altre MLapps: chiave dai Secrets di GitHub, altrimenti firma di debug.
    val ksFile = System.getenv("KEYSTORE_FILE")
    val hasKey = !ksFile.isNullOrBlank() && file(ksFile).exists()
    signingConfigs {
        if (hasKey) {
            create("release") {
                storeFile = file(ksFile!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")?.trim()
                keyAlias = (System.getenv("KEY_ALIAS") ?: "mlfilm").trim().lowercase()
                keyPassword = System.getenv("KEY_PASSWORD")?.trim()
            }
        }
    }
    val signing = if (hasKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signing
        }
        debug {
            signingConfig = signing
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
}
