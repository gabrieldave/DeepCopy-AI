# DeepCopy AI — keep OkHttp / BuildConfig intact for release builds if minify is enabled later.
-keepclassmembers class com.blackeye.deepcopy.BuildConfig {
    public static <fields>;
}

-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
