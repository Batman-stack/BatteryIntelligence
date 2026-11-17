# Kotlinx Serialization (Type-safe Navigation Compose routes)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.batteryintelligence.ui.navigation.**$$serializer { *; }
-keepclassmembers class com.batteryintelligence.ui.navigation.** {
    *** Companion;
}

# Hidden AOSP PowerProfile reflection used safely in AndroidBatteryDataSource
-dontwarn com.android.internal.os.PowerProfile