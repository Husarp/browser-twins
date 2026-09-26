# Keep the clone-engine libraries whole: they use reflection internally, so R8 must not rename or
# remove their classes in the release (minified) build.
-keep class com.reandroid.** { *; }
-dontwarn com.reandroid.**
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
