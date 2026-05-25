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

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-keep class io.github.magisk317.mipush.hook.** { *; }
-keep interface io.github.magisk317.mipush.hook.** { *; }
-keep class io.github.magisk317.mipush.xposed.** { *; }

# Keep parcelables used across module boundaries when minification is enabled
-keep class io.github.magisk317.mipush.common.model.** { *; }

# libxposed API must not be obfuscated to prevent interface signature mismatch
# The API is only present at runtime on rooted devices, but we need to keep
# the interface signatures stable so our Hooker implementations can match
-dontwarn io.github.libxposed.api.**
-keep class io.github.libxposed.api.** { *; }
-keep interface io.github.libxposed.api.** { *; }
