### greenDAO 3
-keepclassmembers class * extends org.greenrobot.greendao.AbstractDao {
public static java.lang.String TABLENAME;
}
-keep class **$Properties

# If you do not use SQLCipher:
-dontwarn org.greenrobot.greendao.database.**
# If you do not use RxJava:
-dontwarn rx.**

# Logback for Android
#
# Tested on the following *.gradle dependencies
#
#    compile 'org.slf4j:slf4j-api:1.7.7'
#    compile 'com.github.tony19:logback-android-core:1.1.1-3'
#    compile 'com.github.tony19:logback-android-classic:1.1.1-3'
#

-keep class ch.qos.** { *; }
-keep class org.slf4j.** { *; }
-keepattributes *Annotation*
-dontwarn ch.qos.logback.core.net.*

# MiPush
-keepclasseswithmembernames class com.xiaomi.**{*;}
-keep public class * extends com.xiaomi.mipush.sdk.PushMessageReceiver
-dontwarn android.app.Notification

# Singleton.instance() creates these via reflection with no-arg constructors.
-keepclassmembers class io.github.magisk317.mipush.push.hook.ModernHookHandler { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.MiPushEventListener { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.service.RegistrationRecorder { <init>(); }
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
