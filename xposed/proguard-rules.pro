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

# Keep libxposed API interfaces to prevent obfuscation mismatch
-keep class io.github.libxposed.api.** { *; }
-keep interface io.github.libxposed.api.** { *; }

# Keep XposedRuntime and prevent any optimization
-keep,allowobfuscation class io.github.magisk317.mipush.xposed.XposedRuntime {
    *;
}
-keep,allowobfuscation class io.github.magisk317.mipush.xposed.XposedRuntime$* {
    *;
}

# Prevent R8 from converting Hooker implementations to lambdas
-keep,allowobfuscation class * implements io.github.libxposed.api.XposedInterface$Hooker {
    <methods>;
}

# Disable lambda desugaring for XposedRuntime
-keep class io.github.magisk317.mipush.xposed.XposedRuntime$$ExternalSyntheticLambda* {
    *;
}

# Keep all inner classes of XposedRuntime
-keepclassmembers class io.github.magisk317.mipush.xposed.XposedRuntime {
    <init>(...);
    <fields>;
    <methods>;
}

# Disable optimization for XposedRuntime
-optimizations !class/merging/*,!code/simplification/*,!code/allocation/*
-keep,allowshrinking,allowobfuscation class io.github.magisk317.mipush.xposed.XposedRuntime