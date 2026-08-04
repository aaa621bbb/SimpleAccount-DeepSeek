# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in proguard-android-optimize.txt.

-keepattributes *Annotation*, InnerClasses, Signature, Exception

# Keep Room entities
-keep class com.simpleaccount.app.data.entity.** { *; }

# Keep Hilt / Dagger
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }

# Keep POI
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.commons.** { *; }
-dontwarn org.apache.commons.**
