# KlipNote — R8 comercial: conservar red/JSON/modelos y crypto.

-keepclassmembers class com.blackeye.klipnote.BuildConfig {
    public static <fields>;
}

-keep class com.blackeye.klipnote.** { *; }
-keepclassmembers class com.blackeye.klipnote.** { *; }
-keep class com.blackeye.klipnote.InworldClient { *; }
-keep class com.blackeye.klipnote.InworldClient$InworldUsage { *; }
-keep class com.blackeye.klipnote.RoutingSelection { *; }
-keep class com.blackeye.klipnote.RoutingSelection$* { *; }
-keep class com.blackeye.klipnote.ManualRouteException { *; }

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

# youtubedl-android / Seal natives (mirror Seal proguard)
-dontobfuscate
-keep class com.yausername.** { *; }
-keepclassmembers class com.yausername.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.compress.archivers.zip.** { *; }
-dontwarn org.apache.commons.compress.**
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn org.mozilla.javascript.**
-keep class org.apache.commons.io.** { *; }
