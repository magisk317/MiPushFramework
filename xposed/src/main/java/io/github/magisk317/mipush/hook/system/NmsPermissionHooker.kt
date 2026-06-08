package io.github.magisk317.mipush.hook.system

import android.app.Notification
import android.app.NotificationChannelGroup
import android.content.Context
import android.os.Binder
import android.os.Build
import android.os.Process
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.HookCallback
import io.github.magisk317.mipush.xposed.HookContext
import io.github.magisk317.mipush.xposed.MethodHookParam
import io.github.magisk317.mipush.xposed.currentApplication
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.findMethodExact
import io.github.magisk317.mipush.xposed.hook
import io.github.magisk317.mipush.xposed.hookMethod

object NmsPermissionHooker {
    private const val TAG = "NmsPermissionHooker"

    private var xmsfUid = -1
    private fun getXmsfUid(): Int {
        if (xmsfUid == -1) {
            runCatching {
                xmsfUid = getContext().packageManager.getPackageUid(XMSF_PACKAGE_NAME, 0)
            }
        }
        return xmsfUid
    }

    internal fun isXmsfCallingIdentity(
        callingUid: Int,
        primaryXmsfUid: Int,
        callingPackages: Collection<String>,
    ): Boolean {
        if (primaryXmsfUid > 0 && callingUid == primaryXmsfUid) {
            return true
        }
        return XMSF_PACKAGE_NAME in callingPackages
    }

    private fun getCallingPackages(callingUid: Int): List<String> {
        return runCatching {
            getContext().packageManager.getPackagesForUid(callingUid)?.toList().orEmpty()
        }.getOrDefault(emptyList())
    }

    private fun fromXmsf() = try {
        val callingUid = Binder.getCallingUid()
        isXmsfCallingIdentity(
            callingUid = callingUid,
            primaryXmsfUid = getXmsfUid(),
            callingPackages = getCallingPackages(callingUid),
        )
    } catch (e: Throwable) {
        false
    }

    private fun getPackageUid(packageName: String) = getContext().packageManager.getPackageUid(packageName, 0)

    private fun getContext(): Context = currentApplication()
        ?: throw IllegalStateException("currentApplication unavailable")

    private fun hookPermission(targetPackageNameParamIndex: Int, hookExtra: (MethodHookParam.() -> Unit)? = null): HookCallback = {
        replace {
            var token: Long? = null
            if (fromXmsf()) {
                token = Binder.clearCallingIdentity()
                hookExtra?.invoke(this)
            }
            try {
                invokeOriginal()
            } catch (e: java.lang.reflect.InvocationTargetException) {
                throw e.targetException ?: e.cause ?: e
            } finally {
                if (token != null) {
                    Binder.restoreCallingIdentity(token)
                }
            }
        }
    }

    private fun hookCanNotifyAsPackage(): HookCallback = {
        doBefore {
            if (fromXmsf()) {
                XLog.d(TAG, "force canNotifyAsPackage(callingPkg=${args[0]}, targetPkg=${args[1]}, userId=${args[2]}) = true")
                result = true
            }
        }
    }

    fun hook(classINotificationManager: Class<*>) {
        XLog.i(TAG, "installing NMS permission hooks on ${classINotificationManager.name}")
        //boolean canNotifyAsPackage(String callingPkg, String targetPkg, int userId);
        findMethodExact(classINotificationManager, "canNotifyAsPackage", String::class.java, String::class.java, Int::class.java)
            .hook(hookCanNotifyAsPackage())

        //boolean areNotificationsEnabledForPackage(String pkg, int uid);
        findMethodExact(classINotificationManager, "areNotificationsEnabledForPackage", String::class.java, Int::class.java)
            .hook(hookPermission(0))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, String conversationId, boolean includeDeleted);
            findMethodExact(classINotificationManager, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, String::class.java, Boolean::class.java)
                .hook(hookPermission(0))
        } else {
            //NotificationChannel getNotificationChannelForPackage(String pkg, int uid, String channelId, boolean includeDeleted);
            findMethodExact(classINotificationManager, "getNotificationChannelForPackage", String::class.java, Int::class.java, String::class.java, Boolean::class.java)
                .hook(hookPermission(0))
        }

        //ParceledListSlice getNotificationChannelsForPackage(String pkg, int uid, boolean includeDeleted);
        findMethodExact(classINotificationManager, "getNotificationChannelsForPackage", String::class.java, Int::class.java, Boolean::class.java)
            .hook(hookPermission(0))

        //void enqueueNotificationWithTag(String pkg, String opPkg, String tag, int id, Notification notification, int userId)
        findMethodExact(classINotificationManager, "enqueueNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Notification::class.java, Int::class.java)
            .hook(hookPermission(0) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    args[1] = ANDROID_PACKAGE_NAME
                }
            })

        //void createNotificationChannelsForPackage(String pkg, int uid, in ParceledListSlice channelsList);
        findMethodExact(classINotificationManager, "createNotificationChannelsForPackage", String::class.java, Int::class.java, findClass("android.content.pm.ParceledListSlice", null))
            .hook(hookPermission(0))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
            findMethodExact(classINotificationManager, "cancelNotificationWithTag", String::class.java, String::class.java, String::class.java, Int::class.java, Int::class.java)
                .hook(hookPermission(0) {
                    args[1] = ANDROID_PACKAGE_NAME
                })
        } else {
            //void cancelNotificationWithTag(String pkg, String opPkg, String tag, int id, int userId);
            findMethodExact(classINotificationManager, "cancelNotificationWithTag", String::class.java, String::class.java, Int::class.java, Int::class.java)
                .hook(hookPermission(0))
        }

        //void deleteNotificationChannel(String pkg, String channelId);
        findMethodExact(classINotificationManager, "deleteNotificationChannel", String::class.java, String::class.java)
            .hook(hookPermission(0))

        //ParceledListSlice getAppActiveNotifications(String callingPkg, int userId);
        findMethodExact(classINotificationManager, "getAppActiveNotifications", String::class.java, Int::class.java)
            .hook(hookPermission(0))

        //ParceledListSlice getNotificationChannelsForPackage(String pkg, int uid, boolean includeDeleted);
        findMethodExact(classINotificationManager, "getNotificationChannelsForPackage", String::class.java, Int::class.java, Boolean::class.java)
            .hook(hookPermission(0))

        val deleteNotificationChannelHook: HookContext.() -> Unit = {
            doBefore {
                val packageName = args[0] as String
                if (Binder.getCallingUid() == Process.SYSTEM_UID) {
                    args[1] = getPackageUid(packageName)
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                findClass("com.android.server.notification.PreferencesHelper", classINotificationManager.classLoader)
                    //public boolean deleteNotificationChannel(String pkg, int uid, String channelId, int callingUid, boolean fromSystemOrSystemUi)
                    .hookMethod(
                        "deleteNotificationChannel", String::class.java, Int::class.java, String::class.java, Int::class.java, Boolean::class.java,
                        callback = deleteNotificationChannelHook
                    )
            } catch (e: NoSuchMethodError) {
                //Samsung One UI 7 delete this method
                XLog.d(TAG, "hook deleteNotificationChannel error, NoSuchMethodError")
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findClass("com.android.server.notification.PreferencesHelper", classINotificationManager.classLoader)
                //public boolean deleteNotificationChannel(String pkg, int uid, String channelId)
                .hookMethod("deleteNotificationChannel", String::class.java, Int::class.java, String::class.java,
                    callback = deleteNotificationChannelHook
                )
        } else {
            findClass("com.android.server.notification.RankingHelper", classINotificationManager.classLoader)
                //public void deleteNotificationChannel(String pkg, int uid, String channelId)
                .hookMethod("deleteNotificationChannel", String::class.java, Int::class.java, String::class.java,
                    callback = deleteNotificationChannelHook
                )
        }

        //void updateNotificationChannelGroupForPackage(String pkg, int uid, in NotificationChannelGroup group);
        findMethodExact(classINotificationManager, "updateNotificationChannelGroupForPackage", String::class.java, Int::class.java, NotificationChannelGroup::class.java)
            .hook(hookPermission(0))

        //NotificationChannelGroup getNotificationChannelGroupForPackage(String groupId, String pkg, int uid);
        findMethodExact(classINotificationManager, "getNotificationChannelGroupForPackage", String::class.java, String::class.java, Int::class.java)
            .hook(hookPermission(1))

        //ParceledListSlice getNotificationChannelGroupsForPackage(String pkg, int uid, boolean includeDeleted);
        findMethodExact(classINotificationManager, "getNotificationChannelGroupsForPackage", String::class.java, Int::class.java, Boolean::class.java)
            .hook(hookPermission(0))

        //void deleteNotificationChannelGroup(String pkg, String channelGroupId);
        findMethodExact(classINotificationManager, "deleteNotificationChannelGroup", String::class.java, String::class.java)
            .hook(hookPermission(0))

        runCatching {
            val classLoader = classINotificationManager.classLoader
            val nmsClass = findClass("com.android.server.notification.NotificationManagerService", classLoader)
            findMethodExact(nmsClass, "checkCallerIsSystem", *emptyArray<Any>())
                .hook {
                    replace {
                        if (fromXmsf()) {
                            XLog.d(TAG, "checkCallerIsSystem bypassed for xmsf")
                            return@replace null
                        }
                        // buzzBeepBlinkForNotification calls checkCallerIsSystem after
                        // binder identity is restored. Clear identity to avoid SecurityException.
                        val token = Binder.clearCallingIdentity()
                        try {
                            invokeOriginal()
                        } finally {
                            Binder.restoreCallingIdentity(token)
                        }
                    }
                }
            XLog.i(TAG, "checkCallerIsSystem hook installed")
        }.onFailure {
            XLog.e(TAG, "failed to hook checkCallerIsSystem", it)
        }

        XLog.i(TAG, "NMS permission hooks installed")
    }
}
