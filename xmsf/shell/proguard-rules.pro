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
# HookPushNC resolves exactly these four names through the com.xiaomi.xmsf
# classloader; any rename here breaks injection (isHooked stays false and mock
# replay receipts degrade to the xmsf identity, producing duplicate posts).
-keep class io.github.magisk317.mipush.notification.NotificationManagerEx { *; }
-keep class io.github.magisk317.mipush.notification.NotificationHookBridge { *; }
-keep class io.github.magisk317.mipush.notification.NotificationHookBackend { *; }
-keep class io.github.magisk317.mipush.notification.BackendLog { *; }
-keep class com.xiaomi.push.service.NotificationIdentityBridge { *; }
-keep class com.xiaomi.push.service.NotificationIdentityBridge$* { *; }

# Keep libxposed API to prevent obfuscation mismatch between interface and implementation
-keep class io.github.libxposed.api.** { *; }
-keep interface io.github.libxposed.api.** { *; }

# Keep XposedRuntime inner classes to prevent lambda optimization issues
-keep class io.github.magisk317.mipush.xposed.XposedRuntime$* { *; }

# libsu reaches its su shell through classes the app never references directly at the bytecode
# level (Shell.Builder.create(), Shell$Result, internal.JobTask, internal.ShellInputSource).
# Without this rule R8 shrinks them away — vc105Release mapping.txt recorded all four as
# R8$$REMOVED$$CLASS — so PrivilegeElevator.tryToElevate() threw NoClassDefFoundError in every
# release build: the root grant prompt never appeared even though `id -u` probing still worked.
# NOTE: keep in sync between the application and library rule files.
-keep class com.topjohnwu.superuser.** { *; }

# Release log tags are javaClass.simpleName (common/.../utils/LoggerExtensions.kt). Minifying
# them makes the daily runtime jsonl unreadable and flavor-specific, so the classes annotated
# with StableLogTag keep their names — and nothing else does.
# NOTE: keep in sync between the application and library rule files.
-keep interface io.github.magisk317.mipush.common.logging.StableLogTag
-keepnames @io.github.magisk317.mipush.common.logging.StableLogTag class *
