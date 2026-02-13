# OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.openclaw.android.**$$serializer { *; }
-keepclassmembers class com.openclaw.android.** {
    *** Companion;
}
-keepclasseswithmembers class com.openclaw.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
