# KlipNote — R8 comercial: conservar red/JSON/modelos y crypto.

-keepclassmembers class com.blackeye.klipnote.BuildConfig {
    public static <fields>;
}

-keep class com.blackeye.klipnote.** { *; }
-keepclassmembers class com.blackeye.klipnote.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
