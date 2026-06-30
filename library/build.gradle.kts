import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.library)
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
val javaTarget = JvmTarget.fromTarget("17")

kotlin {
    androidTarget()
    jvm("desktop") { compilerOptions { freeCompilerArgs.add("-Xjvm-default=all") } }
    compilerOptions { freeCompilerArgs.addAll("-Xexpect-actual-classes", "-Xannotation-default-target=param-property") }
    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("kotlin.time.ExperimentalTime")
                optIn("kotlinx.datetime.format.FormatStringsInDatetimeFormats")
            }
        }
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.jsoup)
                implementation(libs.ksoup)
                implementation(libs.ktor.http)
                implementation(libs.rhino)
                implementation(libs.jackson.module.kotlin)
                implementation(libs.fuzzywuzzy)
                implementation(libs.androidx.annotation)
                api(libs.nicehttp)
                implementation(libs.cryptography.core)
                implementation(libs.cryptography.provider.jdk)
                implementation(libs.gson)
            }
        }
        val androidMain by getting { dependencies { implementation(libs.kotlinx.coroutines.android) } }
        val desktopMain by getting { dependencies { implementation(libs.kotlinx.coroutines.swing) } }
    }
}

android {
    namespace = "com.lagradost.cloudstream3.library"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
tasks.withType<KotlinJvmCompile> { compilerOptions { jvmTarget.set(javaTarget) } }
