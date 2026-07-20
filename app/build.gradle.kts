import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Release signing credentials, in priority order:
//  1. keystore.properties at the project root (local dev; gitignored, never committed)
//  2. KEYSTORE_FILE/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD env vars (CI — see release.yml)
// Neither present is fine: releaseSigningConfig stays null and the release build type falls back
// to debug signing below, so the build still succeeds for contributors without the keystore.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val releaseStoreFile = keystoreProperties.getProperty("storeFile") ?: System.getenv("KEYSTORE_FILE")
val releaseStorePassword = keystoreProperties.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
val releaseKeyAlias = keystoreProperties.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
val releaseKeyPassword = keystoreProperties.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
val hasReleaseSigning = !releaseStoreFile.isNullOrBlank() && !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()

// Version stamp. CI injects the release tag (leading "v" already stripped) via -PappVersionName
// and the run number via -PappVersionCode (see release.yml), so a released APK self-reports the
// exact tag it was published under and the in-app update check reads tag == installed as
// up-to-date. Local/dev builds get a sentinel that never matches a real release.
val injectedVersionName = (project.findProperty("appVersionName") as String?)?.takeIf { it.isNotBlank() }
    ?: System.getenv("APP_VERSION_NAME")?.takeIf { it.isNotBlank() }
val injectedVersionCode = (project.findProperty("appVersionCode") as String?)?.toIntOrNull()
    ?: System.getenv("APP_VERSION_CODE")?.toIntOrNull()

android {
    namespace = "com.foundation.scpreader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.foundation.scpreader"
        minSdk = 24
        targetSdk = 34
        versionCode = injectedVersionCode ?: 1
        versionName = injectedVersionName ?: "0.0.0-dev"
        // Launcher label, referenced by the manifest as @string/app_name. The debug buildType
        // overrides this so the side-by-side debug app is distinguishable on the launcher.
        resValue("string", "app_name", "SCP Reader")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Real signing when credentials are available; otherwise debug-signed so `assembleRelease`
            // still works for contributors/CI runs without the keystore (see SIGNING.md).
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            // Ship debug as a SEPARATE app (com.foundation.scpreader.debug) so it installs
            // side-by-side with a release install — no downgrade or signature conflicts, and you
            // can run both at once. The distinct label makes them tell-apart-able on the launcher.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "SCP Reader Debug")
            // Same key as release when available, so a sideloaded debug build can be overwritten by
            // (or overwrite) a prior debug build without an "signatures don't match" reinstall error.
            // Falls back to AGP's default auto-generated debug keystore when no keystore is set up,
            // so contributors/CI without the keystore still get a normal, installable debug build.
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
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

    // WorkManager (background poll for new friend recommendations → local notification)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

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
