plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

android {
    namespace = "com.wavestream.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wavestream.app"
        minSdk = 26  // must match :library (Rhino 1.9.1 requires API 26)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // OSGi manifests from okhttp-dnsoverhttps and jspecify collide.
            excludes += "/META-INF/versions/9/OSGI-INF/MANIFEST.MF"
            // Signing files from transitive JARs (jackson, bouncy, etc.)
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            // Kotlin module metadata files can collide between KMP artifacts.
            excludes += "/META-INF/*.kotlin_module"
            // Index files from various libs (often duplicated).
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/io.netty.versions.properties"
            // License files commonly shipped by multiple deps.
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "/META-INF/maven/**"
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":library"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.material)

    implementation(libs.kotlinx.coroutines.android)

    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
}
