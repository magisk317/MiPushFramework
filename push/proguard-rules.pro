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
-keepclassmembers class com.magisk317.push.hook.ModernHookHandler { <init>(); }
-keepclassmembers class com.magisk317.MiPushEventListener { <init>(); }
-keepclassmembers class com.magisk317.service.RegistrationRecorder { <init>(); }
-keepclassmembers class com.xiaomi.xmsf.push.utils.ConfigValueConverter { <init>(); }
-keepclassmembers class com.xiaomi.xmsf.push.utils.IconConfigurations { <init>(); }
-keepclassmembers class com.xiaomi.xmsf.utils.ConfigCenter { <init>(); }
-keepclassmembers class com.xiaomi.xmsf.push.utils.Configurations { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.common.cache.ApplicationNameCache { <init>(); }
-keepclassmembers class io.github.magisk317.mipush.common.cache.IconCache { <init>(); }

# Avoid R8 horizontal class merging/obfuscation side effects in wizard permission operators.
-keep class top.trumeet.mipushframework.wizard.permission.** { *; }
