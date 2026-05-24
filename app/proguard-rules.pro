# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Room
-keep class * extends androidx.room.RoomDatabase
-keep class * implements androidx.room.RoomDatabase$Callback
-dontwarn androidx.room.paging.**

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }

# OkHttp
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
}

# AndroidX Lifecycle / ViewModel
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Keep Entities / Models to prevent database serialisation issues
-keep class com.example.ProductEntity { *; }
-keep class com.example.ExpenseEntity { *; }
-keep class com.example.SettingEntity { *; }
-keep class com.example.ui.theme.SaleEntity { *; }
-keep class com.example.WholesaleTier { *; }
-keep class com.example.EscPosBuilder { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.PosViewModel { *; }

# Kotlin Serialization
-keepattributes *Annotation*,Signature
-dontwarn kotlinx.serialization.**
-keep class kotlinx.serialization.** { *; }

