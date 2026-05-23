package io.github.magisk317.mipush.hook.system

import android.app.NotificationManager
import android.content.Context
import android.os.Binder
import android.os.Process
import io.github.magisk317.mipush.xposed.XposedHelpers
import io.github.magisk317.mipush.common.IS_SYSTEM_HOOK_READY
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.XMSF_FAKE_CONDITION_PROVIDER_PATH
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.fakedevice.compat.ModuleCompatRegistry
import io.github.magisk317.mipush.xposed.callMethod
import io.github.magisk317.mipush.xposed.currentApplication
import io.github.magisk317.mipush.xposed.get
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.hookMethod
import java.lang.reflect.Method
import java.util.Collections

class HookSystemService {
    companion object {
        private const val TAG = "HookSystemService"
        private const val MAX_VISIBILITY_LOGS_PER_KEY = 8

        private var _isSystemHookReady: Boolean? = null
        private val explicitCompatPackages: Set<String> =
            ModuleCompatRegistry.allProfiles().mapTo(LinkedHashSet()) { it.packageName }
        private val visibilityLogCounts: MutableMap<String, Int> = Collections.synchronizedMap(HashMap())

        val isSystemHookReady: Boolean
            get() {
                if (_isSystemHookReady == true) return true
                return try {
                    val app = currentApplication() ?: return false
                    val nm = app.getSystemService(NotificationManager::class.java) ?: return false
                    val ready = nm.callMethod("isSystemConditionProviderEnabled", IS_SYSTEM_HOOK_READY) as? Boolean ?: false
                    if (ready) _isSystemHookReady = true
                    ready
                } catch (t: Throwable) {
                    false
                }
            }

        internal data class VisibilityDecision(
            val allow: Boolean,
            val reason: String,
            val caller: String? = null,
        )

        internal fun shouldAllowMiPushVisibility(
            callingPackages: Collection<String>,
            targetPackageName: String?,
        ): VisibilityDecision {
            val caller = callingPackages.firstOrNull { it in explicitCompatPackages }
                ?: return VisibilityDecision(allow = false, reason = "caller_not_profile")
            if (targetPackageName == XMSF_PACKAGE_NAME) {
                return VisibilityDecision(allow = true, reason = "xmsf_for_profile", caller = caller)
            }
            if (targetPackageName == caller) {
                return VisibilityDecision(allow = true, reason = "self_for_profile", caller = caller)
            }
            return VisibilityDecision(allow = false, reason = "target_not_mipush_visible", caller = caller)
        }

        private fun hookGlobalVisibility(classLoader: ClassLoader) {
            runCatching {
                val packageStateClass = XposedHelpers.findClass("com.android.server.pm.pkg.PackageState", classLoader)
                val getPackageNameMethod = packageStateClass.getMethod("getPackageName")
                val appsFilterClass = XposedHelpers.findClass("com.android.server.pm.AppsFilterBase", classLoader)
                appsFilterClass.hookAllMethods("shouldFilterApplication") {
                    doAfter {
                        if (result == false) return@doAfter
                        val targetPackageState = findPackageStateArg(args, getPackageNameMethod) ?: return@doAfter
                        val targetPackageName = packageNameFromState(targetPackageState, getPackageNameMethod) ?: return@doAfter
                        val callingUid = findCallingUid(args)
                        val callingPackages = resolveCallingPackages(args, callingUid)
                        val decision = shouldAllowMiPushVisibility(callingPackages, targetPackageName)
                        logVisibilityDecision(callingUid, callingPackages, targetPackageName, decision)
                        if (decision.allow) {
                            result = false
                        }
                    }
                }
                XLog.d(TAG, "installed scoped xmsf visibility hook")
            }.onFailure {
                XLog.e(TAG, "install xmsf global visibility hook failed", it)
            }
        }

        private fun findPackageStateArg(args: Array<Any?>, getPackageNameMethod: Method): Any? {
            return args.lastOrNull { arg ->
                arg != null && packageNameFromState(arg, getPackageNameMethod) != null
            }
        }

        private fun packageNameFromState(packageState: Any, getPackageNameMethod: Method): String? {
            return runCatching { getPackageNameMethod.invoke(packageState) as? String }.getOrNull()
        }

        private fun findCallingUid(args: Array<Any?>): Int {
            return args.firstOrNull { it is Int && it != Process.SYSTEM_UID } as? Int
                ?: Binder.getCallingUid()
        }

        private fun resolveCallingPackages(args: Array<Any?>, callingUid: Int): List<String> {
            val fromCallingSetting = args
                .asSequence()
                .drop(2)
                .firstNotNullOfOrNull { callingPackagesFromSetting(it) }
                .orEmpty()
            if (fromCallingSetting.isNotEmpty()) return fromCallingSetting

            val fromComputer = args.firstNotNullOfOrNull { computer ->
                runCatching {
                    val packages = XposedHelpers.callMethod(computer, "getPackagesForUid", callingUid) as? Array<*>
                    packages?.filterIsInstance<String>()
                }.getOrNull()
            }.orEmpty()
            if (fromComputer.isNotEmpty()) return fromComputer

            val app = currentApplication()
            val pm = app?.packageManager ?: return emptyList()
            return runCatching { pm.getPackagesForUid(callingUid)?.toList().orEmpty() }.getOrDefault(emptyList())
        }

        internal fun callingPackagesFromSetting(callingSetting: Any?): List<String>? {
            if (callingSetting == null) return null
            val directPackageName = packageNameFromSetting(callingSetting)
            if (!directPackageName.isNullOrBlank()) return listOf(directPackageName)

            val packageStates = runCatching {
                callingSetting.javaClass.methods
                    .firstOrNull { it.name == "getPackageStates" && it.parameterCount == 0 }
                    ?.invoke(callingSetting) as? Iterable<*>
            }.getOrNull()
            return packageStates
                ?.mapNotNull { state -> state?.let { packageNameFromSetting(it) } }
                ?.filter { it.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
        }

        private fun packageNameFromSetting(setting: Any): String? {
            return runCatching {
                setting.javaClass.methods
                    .firstOrNull { it.name == "getPackageName" && it.parameterCount == 0 }
                    ?.invoke(setting) as? String
            }.getOrNull()
        }

        private fun logVisibilityDecision(
            callingUid: Int,
            callingPackages: Collection<String>,
            targetPackageName: String,
            decision: VisibilityDecision,
        ) {
            val callers = callingPackages.joinToString(limit = 4)
            val key = "$callingUid:$callers:$targetPackageName:${decision.reason}:${decision.allow}"
            val count = visibilityLogCounts.getOrDefault(key, 0)
            if (count >= MAX_VISIBILITY_LOGS_PER_KEY) return
            visibilityLogCounts[key] = count + 1
            XLog.i(
                TAG,
                "visibility decision uid=$callingUid callers=[$callers] target=$targetPackageName " +
                    "allow=${decision.allow} reason=${decision.reason}"
            )
        }
    }

    fun hook(classLoader: ClassLoader) {
        val classNotificationManagerService = XposedHelpers.findClass("com.android.server.notification.NotificationManagerService", classLoader)
        XLog.i(TAG, "installing system notification hooks")

        classNotificationManagerService.hookMethod("onStart") {
            doAfter {
                XLog.d(TAG, "onStart invoked")
                val owner = thisObject ?: return@doAfter
                val context = owner.callMethod("getContext") as Context
                val service = owner.get<Any?>("mService")
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
