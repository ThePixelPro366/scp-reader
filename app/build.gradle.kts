plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.foundation.scpreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.foundation.scpreader"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        // NewPipeExtractor uses java.time etc.; desugaring backports them to minSdk 24/25.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Networking + parsing
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("org.jsoup:jsoup:1.17.2")

    // Preferences DataStore (persisted settings)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Encrypted on-device storage for the GitHub PAT (update checker), not plain DataStore.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Room (offline-first source of truth)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Media3 / ExoPlayer (playback) + Transformer (offline SponsorBlock trim/clip+concat)
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-transformer:1.4.1")

    // Image loading (SCP figures)
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Lottie (loading spinner + splash animation)
    implementation("com.airbnb.android:lottie-compose:6.5.2")

    // Core splashscreen (kills the white cold-start flash)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // YouTube stream extraction (keyless). Primary narration source (@scparchives).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")

    // Backports java.time/stream APIs NewPipeExtractor relies on to minSdk 24/25.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    // Unit test (headless stream-resolution checkpoint; not shipped in the APK)
    testImplementation("junit:junit:4.13.2")
}
