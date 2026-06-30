plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}
java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }
android {
    namespace = "com.wavestream.app"
    compileSdk = 35
    defaultConfig { applicationId = "com.wavestream.app"; minSdk = 21; targetSdk = 35; versionCode = 1; versionName = "1.0.0" }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
}
dependencies {
    implementation(project(":shared")); implementation(project(":library"))
    implementation(libs.androidx.core.ktx); implementation(libs.androidx.appcompat); implementation(libs.androidx.activity.compose); implementation(libs.material)
    implementation(compose.runtime); implementation(compose.foundation); implementation(compose.material3); implementation(compose.materialIconsExtended); implementation(compose.ui); implementation(compose.uiTooling)
}
