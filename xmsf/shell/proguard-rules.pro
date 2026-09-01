# MiPush
-keepclasseswithmembernames class com.xiaomi.**{*;}
-keep public class * extends com.xiaomi.mipush.sdk.PushMessageReceiver
-dontwarn android.app.Notification

# Singleton.instance() creates these via reflection with no-arg constructors.
-keepclassmembers class io.github.magisk317.mipush.push.hook.ModernHookHandler { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.MiPushEventListener { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.service.RegisterRecorder { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.utils.ConfigValueConverter { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.utils.IconConfigurations { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.app.ConfigCenter { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.utils.Configurations { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.common.cache.ApplicationNameCache { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.common.cache.IconCache { <init>(); }

# Avoid R8 horizontal class merging/obfuscation side effects in wizard permission operators.
-keep class io.github.magisk317.mipush.feature.wizard.permission.** { *; }

# Xposed hooks resolve these classes and methods by string name from the host
# process, so minified builds must keep their binary API stable.
-keep class io.github.magisk317.mipush.notification.NotificationManagerEx { *; }
-keep class com.xiaomi.push.service.NotificationIdentityBridge { *; }
-keep class com.xiaomi.push.service.NotificationIdentityBridge$* { *; }

# Keep libxposed API to prevent obfuscation mismatch between interface and implementation
-keep class io.github.libxposed.api.** { *; }
-keep interface io.github.libxposed.api.** { *; }

# Keep XposedRuntime inner classes to prevent lambda optimization issues
-keep class io.github.magisk317.mipush.xposed.XposedRuntime$* { *; }
