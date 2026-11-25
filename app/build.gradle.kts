import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing config read from an external properties file (never committed).
val keystorePropsFile = file(System.getenv("CALORIES_KEYSTORE_PROPS") ?: "/home/box/android-keys/keystore.properties")
val keystoreProps = Properties().apply { if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile)) }

android {
    namespace = "com.wolnerman.calories"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wolnerman.calories"
        minSdk = 24
        targetSdk = 34
        versionCode = 29
        versionName = "2.18"
        // Téléphones ARM uniquement (le moteur TFLite pèse ~3-6 Mo par architecture)
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = true } // .so compressées : APK ~4 Mo plus léger à télécharger
    }
    // Modèle hors-ligne lu par mmap : doit rester non compressé dans l'APK
    androidResources { noCompress += "tflite" }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303") // org.json réel pour les tests JVM (Android n'a que des bouchons)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Santé Connect : 1.1.0-alpha07 = dernière version compatible compileSdk 34 / AGP 8.5 (les suivantes exigent SDK 35-36)
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")
    implementation("com.google.ai.edge.litert:litert:1.4.2") // reconnaissance hors-ligne (Interpreter TFLite)
    implementation("com.github.elinkthings:AILinkSecretAndroid:2.0.0") { isTransitive = false }
}
