package io.github.magisk317.mipush.hook.system

import android.app.AndroidAppHelper
import android.app.NotificationManager
import android.content.Context
import android.os.Binder
import de.robv.android.xposed.XposedHelpers
import io.github.magisk317.mipush.common.IS_SYSTEM_HOOK_READY
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_FAKE_CONDITION_PROVIDER_PATH
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.callMethod
import io.github.magisk317.mipush.xposed.get
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.hookMethod

class HookSystemService {
    companion object {
        private const val TAG = "HookSystemService"

        private var _isSystemHookReady: Boolean? = null
        val isSystemHookReady: Boolean
            get() {
                if (_isSystemHookReady == true) return true
                return try {
                    val app = AndroidAppHelper.currentApplication() ?: return false
                    val nm = app.getSystemService(NotificationManager::class.java) ?: return false
                    val ready = nm.callMethod("isSystemConditionProviderEnabled", IS_SYSTEM_HOOK_READY) as? Boolean ?: false
                    if (ready) _isSystemHookReady = true
                    ready
                } catch (t: Throwable) {
                    false
                }
            }

    }

    fun hook(classLoader: ClassLoader) {
        val classNotificationManagerService = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService", classLoader)
        XLog.i(TAG, "installing system notification hooks")

        classNotificationManagerService.hookMethod("onStart") {
            doAfter {
                XLog.d(TAG, "onStart invoked")
                val context = thisObject.callMethod("getContext") as Context
                val service = thisObject.get<Any?>("mService")
                if (service == null) {
                    XLog.w(TAG, "skip system notification hook install because mService is null")
                    return@doAfter
                }
                val stubClass = service.javaClass
                hookPermission(stubClass)
                hookSystemReadyFlag(stubClass)
                XLog.i(TAG, "system notification hooks installed")
            }
        }

        //private boolean isPackageSuspendedForUser(String pkg, int uid)
        classNotificationManagerService.hookMethod("isPackageSuspendedForUser", String::class.java, Int::class.java) {
            doBefore {
                if (Binder.getCallingUid() == 1000) {
                    //suspend app can not show notification, fake its state
                    result = false
                }
            }
        }


        val classShortcutService = XposedHelpers.findClass("com.android.server.pm.ShortcutService", classLoader)
        ShortcutPermissionHooker.hook(classShortcutService)
        hookGlobalVisibility(classLoader)
    }

    private fun hookGlobalVisibility(classLoader: ClassLoader) {
        runCatching {
            val packageStateClass = XposedHelpers.findClass("com.android.server.pm.pkg.PackageState", classLoader)
            val getPackageNameMethod = packageStateClass.getMethod("getPackageName")
            val appsFilterClass = XposedHelpers.findClass("com.android.server.pm.AppsFilterBase", classLoader)
            appsFilterClass.hookAllMethods("shouldFilterApplication") {
                doAfter {
                    if (result == false) return@doAfter
                    val targetPackageState = args.getOrNull(3) ?: return@doAfter
                    val targetPackageName = runCatching {
                        getPackageNameMethod.invoke(targetPackageState) as? String
                    }.getOrNull()
                    if (targetPackageName == XMSF_PACKAGE_NAME) {
                        result = false
                    }
                }
            }
            XLog.d(TAG, "installed xmsf global visibility hook")
        }.onFailure {
            XLog.e(TAG, "install xmsf global visibility hook failed", it)
        }
    }

    private fun hookSystemReadyFlag(stubClass: Class<Any>) {
        XLog.d(TAG, "install system ready flag hook on ${stubClass.name}")
        stubClass.hookMethod("isSystemConditionProviderEnabled", String::class.java) {
            doBefore {
                if (args[0] == IS_SYSTEM_HOOK_READY || args[0] == XMSF_FAKE_CONDITION_PROVIDER_PATH) {
                    XLog.d(TAG, "force system condition provider enabled: ${args[0]}")
                    result = true
                }
            }
        }
    }

    private fun hookPermission(stubClass: Class<Any>) {
        XLog.d(TAG, "install permission hook on ${stubClass.name}")
        NmsPermissionHooker.hook(stubClass)
    }
}
