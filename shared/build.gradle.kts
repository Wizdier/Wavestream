import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.library)
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

val javaTarget = JvmTarget.fromTarget("17")

kotlin {
    androidTarget()
    jvm("desktop") {
        compilerOptions { freeCompilerArgs.add("-Xjvm-default=all") }
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xannotation-default-target=param-property")
    }
    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlinx.datetime.format.FormatStringsInDatetimeFormats")
                optIn("org.jetbrains.compose.ExperimentalComposeApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                optIn("androidx.compose.animation.ExperimentalAnimationApi")
            }
        }
        val commonMain by getting {
            dependencies {
                api(project(":library"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)
                implementation(compose.animation)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)

                implementation(libs.androidx.lifecycle.runtime.compose)
                implementation(libs.androidx.lifecycle.viewmodel.compose)
                implementation(libs.androidx.navigation.compose)

                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor)
                implementation(libs.coil.svg)
                implementation(libs.ktor.client.core)
            }
        }
        val androidMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.android)
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.core.ktx)
                implementation(libs.androidx.appcompat)
                implementation(libs.androidx.media3.exoplayer)
                implementation(libs.androidx.media3.exoplayer.hls)
                implementation(libs.androidx.media3.exoplayer.dash)
                implementation(libs.androidx.media3.datasource.okhttp)
                implementation(libs.androidx.media3.ui)
                implementation(libs.androidx.media3.session)
                implementation(libs.material)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.swing)
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.wavestream.shared"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

tasks.withType<KotlinJvmCompile> { compilerOptions { jvmTarget.set(javaTarget) } }

// Desktop distribution config. Use `./gradlew :shared:run` to launch the
// desktop UI and `./gradlew :shared:packageDistributionForCurrentOS` to
// build a native installer (dmg/msi/deb).
compose.desktop {
    application {
        mainClass = "com.wavestream.MainKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
            )
            packageName = "Wavestream"
            packageVersion = "1.0.0"
            description = "Wavestream — Compose Multiplatform CloudStream fork"
            vendor = "Wavestream"
        }
    }
}
