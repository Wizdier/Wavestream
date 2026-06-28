import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File as JFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.wavestream.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wavestream.app"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Build config fields for API keys (set via local.properties or env vars)
        buildConfigField("long", "BUILD_DATE", "${System.currentTimeMillis()}L")
    }

    signingConfigs {
        create("prerelease") {
            val keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            val storePassword = System.getenv("SIGNING_STORE_PASSWORD")
            val keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            if (keyAlias != null && storePassword != null && keyPassword != null) {
                val tmpFilePath = System.getProperty("user.home") + "/work/_temp/keystore/"
                val prereleaseStoreFile: JFile? = JFile(tmpFilePath).listFiles()?.first()
                storeFile = prereleaseStoreFile?.let { file(it) }
                this.storePassword = storePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use debug signing for CI builds (no keystore needed)
            // For production releases, set up signing via GitHub Secrets
        }
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
        }
    }

    flavorDimensions += "state"
    productFlavors {
        create("stable") {
            dimension = "state"
        }
        create("prerelease") {
            dimension = "state"
            applicationIdSuffix = ".prerelease"
            versionNameSuffix = "-PRE"
            if (signingConfigs.names.contains("prerelease")) {
                signingConfig = signingConfigs.getByName("prerelease")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))

    // Android-specific dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material)

    // Splash screen
    implementation("androidx.core:core-splashscreen:1.0.1")
}
