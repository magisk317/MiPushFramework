package io.github.magisk317.mipush.hook.xmsf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.content.Context
import android.service.notification.StatusBarNotification
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.XposedHelpers.ClassNotFoundError
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.xmsf.nm.SystemNotificationManager
import io.github.magisk317.mipush.hook.system.HookSystemService
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.getOrNull
import io.github.magisk317.mipush.xposed.hookMethod
import io.github.magisk317.mipush.xposed.set
import java.lang.reflect.InvocationTargetException

object HookPushNC {
    private const val TAG = "HookPushNC"
    private const val ExpectedHookApiVersion = 1

    private const val TargetClass = "io.github.magisk317.mipush.notification.NotificationManagerEx"
    private const val IdentityBridgeClass = "com.xiaomi.push.service.NotificationIdentityBridge"
    private const val IdentityStrategyClass = "com.xiaomi.push.service.NotificationIdentityBridge\$Strategy"

    private val hookCheck = { HookSystemService.isSystemHookReady }

    fun canHook(classLoader: ClassLoader): Boolean {
        return try {
            classLoader.findClass(TargetClass)
            true
        } catch (e: ClassNotFoundError) {
            false
        }
    }

    fun hook(classLoader: ClassLoader) {
        XLog.d(TAG, "hookPushNC() called with: classLoader = $classLoader")

        val classNotificationManager = classLoader.findClass(TargetClass)
        val managerHookApiVersion = runCatching {
            classNotificationManager.getOrNull<Int>("HOOK_API_VERSION") ?: 0
        }.getOrDefault(0)
        if (managerHookApiVersion != ExpectedHookApiVersion) {
            XLog.e(
                TAG,
                "NotificationManagerEx hook api mismatch: expected=$ExpectedHookApiVersion actual=$managerHookApiVersion",
                null
            )
            return
        }

        try {
            classNotificationManager["isHooked"] = true
            XLog.i(TAG, "marked NotificationManagerEx.isHooked = true")
        } catch (_: Throwable) {

        }

        //notify(
        //        packageName: String,
        //        tag: String?, id: Int, notification: Notification
        //    )
        classNotificationManager.hookMethod(
            "notify",
            String::class.java,
            String::class.java,
            Int::class.java,
            Notification::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    SystemNotificationManager.notify(
                        args[0] as String,
                        args[1] as String?,
                        args[2] as Int,
                        args[3] as Notification
                    )
                }
            }
        }

        //cancel(
        //        packageName: String,
        //        tag: String?, id: Int
        //    )
        classNotificationManager.hookMethod(
            "cancel",
            String::class.java,
            String::class.java,
            Int::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    SystemNotificationManager.cancel(
                        args[0] as String,
                        args[1] as String?,
                        args[2] as Int
                    )
                }
            }
        }

        //createNotificationChannels(
        //        packageName: String,
        //        channels: List<NotificationChannel?>
        //    )
        classNotificationManager.hookMethod(
            "createNotificationChannels",
            String::class.java,
            List::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    @Suppress("UNCHECKED_CAST")
                    SystemNotificationManager.createNotificationChannels(
                        args[0] as String,
                        args[1] as List<NotificationChannel>
                    )
                }
            }
        }

        //getNotificationChannel(
        //        packageName: String,
        //        channelId: String?
        //    ): NotificationChannel?
        classNotificationManager.hookMethod(
            "getNotificationChannel",
            String::class.java,
            String::class.java
        ) {
            replace() {
                tryInvoke {
                    return@replace SystemNotificationManager.getNotificationChannel(
                        args[0] as String,
                        args[1] as String
                    )
                }
            }
        }

        //getNotificationChannels(
        //        packageName: String
        //    ): List<NotificationChannel?>?
        classNotificationManager.hookMethod("getNotificationChannels", String::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace SystemNotificationManager.getNotificationChannels(args[0] as String)
                }
            }
        }

        //deleteNotificationChannel(
        //        packageName: String,
        //        channelId: String?
        //    )
        classNotificationManager.hookMethod(
            "deleteNotificationChannel",
            String::class.java,
            String::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    SystemNotificationManager.deleteNotificationChannel(
                        args[0] as String,
                        args[1] as String
                    )
                }
            }
        }

        //createNotificationChannelGroups(
        //        packageName: String,
        //        groups: List<NotificationChannelGroup?>
        //    )
        classNotificationManager.hookMethod(
            "createNotificationChannelGroups",
            String::class.java,
            List::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    @Suppress("UNCHECKED_CAST")
                    SystemNotificationManager.createNotificationChannelGroups(
                        args[0] as String,
                        args[1] as List<NotificationChannelGroup>
                    )
                }
            }
        }

        //getNotificationChannelGroup(
        //        packageName: String,
        //        groupId: String?
        //    ): NotificationChannelGroup?
        runCatching {
            classNotificationManager.hookMethod(
                "getNotificationChannelGroup",
                String::class.java,
                String::class.java
            ) {
                replace(hookCheck) {
                    tryInvoke {
                        return@replace SystemNotificationManager.getNotificationChannelGroup(
                            args[0] as String,
                            args[1] as String
                        )
                    }
                }
            }
        }.onFailure {
            XLog.e(TAG, "skip getNotificationChannelGroup hook", it)
        }

        //getNotificationChannelGroups(
        //        packageName: String
        //    ): List<NotificationChannelGroup?>?
        classNotificationManager.hookMethod("getNotificationChannelGroups", String::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace SystemNotificationManager.getNotificationChannelGroups(args[0] as String)
                }
            }
        }

        //deleteNotificationChannelGroup(
        //        packageName: String,
        //        groupId: String?
        //    )
        classNotificationManager.hookMethod(
            "deleteNotificationChannelGroup",
            String::class.java,
            String::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    SystemNotificationManager.deleteNotificationChannelGroup(
                        args[0] as String,
                        args[1] as String
                    )
                }
            }
        }

        //areNotificationsEnabled(
        //        packageName: String
        //    ): Boolean
        classNotificationManager.hookMethod("areNotificationsEnabled", String::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace SystemNotificationManager.areNotificationsEnabled(args[0] as String)
                }
            }
        }

        //getActiveNotifications(
        //        packageName: String
        //    ): Array<StatusBarNotification?>?
        classNotificationManager.hookMethod("getActiveNotifications", String::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace SystemNotificationManager.getActiveNotifications(args[0] as String)
                }
            }
        }

        classNotificationManager.hookMethod("supportsTargetChannelProvisioning", String::class.java) {
            replace(hookCheck) {
                true
            }
        }

        classNotificationManager.hookMethod(
            "findPreferredTargetChannel",
            String::class.java,
            String::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace SystemNotificationManager.findPreferredTargetChannel(
                        args[0] as String,
                        args[1] as String?
                    )
                }
            }
        }

        hookIdentityBridge(classLoader)
        XLog.i(TAG, "host notification takeover hooks installed")
    }

    private fun hookIdentityBridge(classLoader: ClassLoader) {
        val identityBridgeClass = runCatching { classLoader.findClass(IdentityBridgeClass) }
            .getOrElse {
                XLog.d(TAG, "identity bridge class not found, skip")
                return
            }
        val bridgeHookApiVersion = runCatching {
            identityBridgeClass.getOrNull<Int>("HOOK_API_VERSION") ?: 0
        }.getOrDefault(0)
        if (bridgeHookApiVersion != ExpectedHookApiVersion) {
            XLog.e(
                TAG,
                "NotificationIdentityBridge hook api mismatch: expected=$ExpectedHookApiVersion actual=$bridgeHookApiVersion",
                null
            )
            return
        }
        try {
            identityBridgeClass["isHooked"] = true
            XLog.i(TAG, "marked NotificationIdentityBridge.isHooked = true")
        } catch (_: Throwable) {
        }
        val identityStrategyClass = runCatching { classLoader.findClass(IdentityStrategyClass) }
            .getOrElse {
                XLog.d(TAG, "identity strategy enum not found, skip")
                return
            }

        val frameworkStrategy = runCatching {
            @Suppress("UNCHECKED_CAST")
            java.lang.Enum.valueOf(identityStrategyClass as Class<out Enum<*>>, "FRAMEWORK")
        }.getOrElse {
            XLog.e(TAG, "resolve FRAMEWORK strategy failed", it)
            return
        }
        XLog.i(TAG, "installing identity bridge hooks")

        identityBridgeClass.hookMethod("isFrameworkIdentitySupported", Context::class.java) {
            replace(hookCheck) { true }
        }

        identityBridgeClass.hookMethod("canNotifyAsPackage", Context::class.java, String::class.java) {
            replace(hookCheck) { true }
        }

        identityBridgeClass.hookMethod("canCreateTargetChannels", Context::class.java, String::class.java) {
            replace(hookCheck) { true }
        }

        identityBridgeClass.hookMethod("resolveStrategy", Context::class.java, String::class.java) {
            replace(hookCheck) { frameworkStrategy }
        }

        identityBridgeClass.hookMethod("getTargetNotificationChannels", Context::class.java, String::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace SystemNotificationManager.getNotificationChannels(args[1] as String)
                }
            }
        }

        identityBridgeClass.hookMethod("getTargetNotificationChannel", Context::class.java, String::class.java, String::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace SystemNotificationManager.getNotificationChannel(
                        args[1] as String,
                        args[2] as String
                    )
                }
            }
        }

        identityBridgeClass.hookMethod(
            "getPreferredTargetNotificationChannel",
            Context::class.java,
            String::class.java,
            String::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    return@replace SystemNotificationManager.findPreferredTargetChannel(
                        args[1] as String,
                        args[2] as String?
                    )
                }
            }
        }

        identityBridgeClass.hookMethod("createTargetNotificationChannelGroups", Context::class.java, String::class.java, List::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    @Suppress("UNCHECKED_CAST")
                    SystemNotificationManager.createNotificationChannelGroups(
                        args[1] as String,
                        args[2] as List<NotificationChannelGroup>
                    )
                    return@replace true
                }
            }
        }

        identityBridgeClass.hookMethod("createTargetNotificationChannels", Context::class.java, String::class.java, List::class.java) {
            replace(hookCheck) {
                tryInvoke {
                    @Suppress("UNCHECKED_CAST")
                    SystemNotificationManager.createNotificationChannels(
                        args[1] as String,
                        args[2] as List<NotificationChannel>
                    )
                    return@replace true
                }
            }
        }

        identityBridgeClass.hookMethod(
            "notifyAsTargetPackage",
            Context::class.java,
            String::class.java,
            String::class.java,
            Int::class.java,
            Notification::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    SystemNotificationManager.notify(
                        args[1] as String,
                        args[2] as String?,
                        args[3] as Int,
                        args[4] as Notification
                    )
                    return@replace true
                }
            }
        }

        identityBridgeClass.hookMethod(
            "cancelAsTargetPackage",
            Context::class.java,
            String::class.java,
            String::class.java,
            Int::class.java
        ) {
            replace(hookCheck) {
                tryInvoke {
                    SystemNotificationManager.cancel(
                        args[1] as String,
                        args[2] as String?,
                        args[3] as Int
                    )
                    return@replace true
                }
            }
        }
        XLog.i(TAG, "identity bridge hooks installed")
    }

    private inline fun <R> tryInvoke(invoke: () -> R): R {
        try {
            return invoke()
        } catch (e: XposedHelpers.InvocationTargetError) {
            XLog.e(TAG, "tryInvoke: ", e)
            XLog.e(TAG, "tryInvoke targetException: ", e.cause)
            throw e.cause ?: e
        } catch (e: InvocationTargetException) {
            XLog.e(TAG, "tryInvoke: ", e)
            XLog.e(TAG, "tryInvoke targetException: ", e.targetException)
            throw e.targetException ?: e
        } catch (e: Throwable) {
            XLog.e(TAG, "tryInvoke: ", e)
            XLog.e(TAG, "tryInvoke cause: ", e.cause)
            throw e.cause ?: e
        }
    }
}
