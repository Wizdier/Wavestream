# Add project specific ProGuard rules here.

# Keep WaveStream plugin/provider classes (loaded reflectively)
-keep class com.wizdier.wavestream.data.api.** { *; }
-keep class * implements com.wizdier.wavestream.data.api.Provider { *; }
-keep class * implements com.wizdier.wavestream.data.api.MainAPI { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Jsoup
-dontwarn org.jsoup.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Koin
-keep class org.koin.** { *; }

# Media3
-dontwarn androidx.media3.**

# Coil
-dontwarn coil.**
