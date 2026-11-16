plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "ru.echo.radar"
    compileSdk = 34

    // Постоянный ключ подписи. Без него Android ставит APK как «повреждённый файл».
    // На CI ключ восстанавливается из секретов в echo_release.jks.
    signingConfigs {
        create("echo") {
            storeFile = file("../echo_release.jks")
            storePassword = System.getenv("ECHO_KEYSTORE_PASSWORD") ?: "echoRelease2026"
            keyAlias = System.getenv("ECHO_KEY_ALIAS") ?: "echo"
            keyPassword = System.getenv("ECHO_KEY_PASSWORD") ?: "echoRelease2026"
        }
    }

    defaultConfig {
        applicationId = "ru.echo.radar"
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "0.7.0-alpha"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("echo")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("echo")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.11"
    }
    // v1 (JAR) нужен старым прошивкам и файловым менеджерам, которые проверяют
    // только JAR-подпись; без него Android пишет «файл повреждён».
    signingConfigs {
        getByName("echo") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Сеть
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // DataStore для хранения настроек (адрес сервера)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Coil для загрузки обложек треков
    implementation("io.coil-kt:coil-compose:2.6.0")
}
