# DeepCopy AI — R8 comercial: conservar red/JSON/modelos y crypto.

-keepclassmembers class com.blackeye.deepcopy.BuildConfig {
    public static <fields>;
}

-keep class com.blackeye.deepcopy.TranscriptionResult { *; }
-keep class com.blackeye.deepcopy.LanguageMode { *; }
-keep class com.blackeye.deepcopy.BillingManager$HistoryEntry { *; }
-keep class com.blackeye.deepcopy.BillingManager$CostEstimate { *; }
-keep class com.blackeye.deepcopy.DeepgramBillingClient$BalanceSnapshot { *; }
-keep class com.blackeye.deepcopy.DeepgramBillingClient$BillingScopeStatus { *; }
-keep class com.blackeye.deepcopy.AudioExtractor$ExtractedAudio { *; }

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
