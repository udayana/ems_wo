# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep ZXing classes (QR code scanning)
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**
-keep class com.journeyapps.zxing.** { *; }
-dontwarn com.journeyapps.zxing.**

# Keep Retrofit classes
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# Keep Retrofit parameter names - CRITICAL for API calls
# This ensures @Query, @Field, @Part parameter names are preserved
-keepclassmembers,allowobfuscation class * {
    @retrofit2.http.* <methods>;
}

# Keep parameter names in Retrofit interface methods
-keepclassmembers interface * {
    @retrofit2.http.* <methods>;
}

# Specifically keep ApiService interface and all its methods
-keep interface com.sofindo.ems.api.ApiService { *; }
-keepclassmembers interface com.sofindo.ems.api.ApiService {
    <methods>;
}

# Keep all Retrofit annotations to preserve parameter names
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# Keep OkHttp classes
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Keep Moshi classes
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# Keep Glide classes
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}

# Keep your app's model classes
-keep class com.sofindo.ems.models.** { *; }
-keep class com.sofindo.ems.api.** { *; }

# Keep AndroidX classes
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep Kotlin classes
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-dontwarn kotlin.**

# Keep CameraX classes
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# Keep Lifecycle classes
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# Keep Material Design classes
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# Keep AppCompat classes
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**

# Keep ConstraintLayout classes
-keep class androidx.constraintlayout.** { *; }
-dontwarn androidx.constraintlayout.**

# Keep SwipeRefreshLayout classes
-keep class androidx.swiperefreshlayout.** { *; }
-dontwarn androidx.swiperefreshlayout.**

# Keep MultiDex classes
-keep class androidx.multidex.** { *; }
-dontwarn androidx.multidex.**
