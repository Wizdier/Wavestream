# Wavestream ProGuard rules

# Keep CloudStream-style plugin contract classes
-keep class com.wavestream.api.** { *; }
-keep class com.wavestream.plugins.BasePlugin { *; }
-keep class com.wavestream.plugins.BasePlugin$Manifest { *; }
-keep @com.wavestream.plugins.WavestreamPlugin class *

# Keep Rhino classes (for JS plugin runtime)
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Ktor
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Coil
-keep class coil3.** { *; }
-dontwarn coil3.**
