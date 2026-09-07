package io.github.magisk317.mipush.hook.freeze

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import io.github.magisk317.mipush.common.ANDROID_PACKAGE_NAME
import io.github.magisk317.mipush.common.EXTRA_FREEZE_PACKAGE
import io.github.magisk317.mipush.common.EXTRA_FREEZE_USER_ID
import io.github.magisk317.mipush.common.ACTION_FREEZE_LAUNCH_ACTIVATED
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_READ_PERMISSION
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.BaseHook
import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.xposed.currentApplication
import io.github.magisk317.xposed.findHookClass
import io.github.magisk317.xposed.getHookIntField
import io.github.magisk317.xposed.getHookObjectField
import io.github.magisk317.xposed.hookAllMethods
import java.util.concurrent.ConcurrentHashMap

/**
 * System_server hook that refreezes packages when the user removes them from Recents.
 *
 * XMSF's [FrozenAppCoordinator] broadcasts [ACTION_FREEZE_LAUNCH_ACTIVATED] after unfreezing
 * a package under the TASK_REMOVED policy. This hook maintains the in-memory tracking map
 * (package -> userId) and, on Task removal, calls PM to DISABLED_USER + set stopped state,
 * mirroring the original MiPush-Enhance module's behavior. [KEEPALIVE_PREF_READ_PERMISSION]
 * guards the receiver so only XMSF can trigger tracking.
 */
class FreezeTaskRemovedHook : BaseHook() {

    companion object {
        private const val TAG = "FreezeTaskRemovedHook"
        private const val MANAGER_PACKAGE_NAME = "io.github.magisk317.mipush"

        /** Packages XMSF asked us to refreeze on task removal; keyed by packageName, value = userId. */
        private val trackedThaws = ConcurrentHashMap<String, Int>()
    }

    override fun onLoadPackage(param: LoadParam) {
        if (param.packageName != ANDROID_PACKAGE_NAME || param.processName != ANDROID_PACKAGE_NAME) return
        XLog.i(TAG, "loading in system_server")
        registerTrackingReceiver()
        hookTaskRemoval(param.classLoader)
    }

    private fun registerTrackingReceiver() {
        runCatching {
            val app = currentApplication() ?: return
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val pkg = intent?.getStringExtra(EXTRA_FREEZE_PACKAGE) ?: return
                    val userId = intent.getIntExtra(EXTRA_FREEZE_USER_ID, 0)
                    if (isForbidden(pkg)) return
                    trackedThaws[pkg] = userId
                    XLog.d(TAG, "tracking $pkg userId=$userId for task-removed refreeze")
                }
            }
            val filter = IntentFilter(ACTION_FREEZE_LAUNCH_ACTIVATED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(
                    receiver, filter,
                    KEEPALIVE_PREF_READ_PERMISSION, null,
                    Context.RECEIVER_EXPORTED,
                )
            } else {
                @Suppress("DEPRECATION")
                app.registerReceiver(receiver, filter, KEEPALIVE_PREF_READ_PERMISSION, null)
            }
            XLog.i(TAG, "tracking receiver registered")
        }.onFailure {
            XLog.w(TAG, "failed to register tracking receiver: ${it.message}")
        }
    }

    private fun hookTaskRemoval(classLoader: ClassLoader) {
        // Task-level hooks: thisObject is the Task, extract package directly.
        val taskClass = runCatching {
            findHookClass("com.android.server.wm.Task", classLoader)
        }.getOrNull()
        if (taskClass != null) {
            for (method in arrayOf("removedFromRecents", "removeIfPossible", "removeImmediately")) {
                runCatching {
                    taskClass.hookAllMethods(method) {
                        doBefore { onTaskRemoved(thisObject, "task.$method") }
                    }
                    XLog.i(TAG, "Task.$method hook installed")
                }.onFailure {
                    XLog.d(TAG, "Task.$method hook skipped: ${it.message}")
                }
            }
        } else {
            XLog.w(TAG, "com.android.server.wm.Task not found; task-level hooks skipped")
        }

        // Service-level hooks: args[0] = taskId int, resolve Task from mRootWindowContainer.
        val atmsClass = runCatching {
            findHookClass("com.android.server.wm.ActivityTaskManagerService", classLoader)
        }.getOrNull()
        if (atmsClass != null) {
            for (method in arrayOf("removeTask", "removeTaskWithFlags")) {
                runCatching {
                    atmsClass.hookAllMethods(method) {
                        doBefore { onServiceTaskRemoved(thisObject, args, classLoader, "atms.$method") }
                    }
                    XLog.i(TAG, "ATMS.$method hook installed")
                }.onFailure {
                    XLog.d(TAG, "ATMS.$method hook skipped: ${it.message}")
                }
            }
        } else {
            XLog.w(TAG, "ActivityTaskManagerService not found; service-level hooks skipped")
        }
    }

    /** Called when a Task object is being removed (Task.thisObject). */
    private fun onTaskRemoved(taskObj: Any?, reason: String) {
        if (taskObj == null) return
        val packages = collectPackagesFromTask(taskObj)
        val userId = readUserIdFromTask(taskObj)
        refreezeMatching(packages, userId, reason)
    }

    /** Called when ATMS.removeTask(taskId) is being invoked. */
    private fun onServiceTaskRemoved(
        service: Any?,
        args: Array<Any?>,
        classLoader: ClassLoader,
        reason: String,
    ) {
        if (service == null) return
        val taskId = readTaskIdFromArgs(args)
        if (taskId < 0) return
        val task = findTaskById(service, taskId) ?: return
        val packages = collectPackagesFromTask(task)
        val userId = readUserIdFromTask(task)
        refreezeMatching(packages, userId, reason)
    }

    private fun refreezeMatching(packages: Set<String>, userId: Int, reason: String) {
        for (pkg in packages) {
            val trackedUser = trackedThaws.remove(pkg) ?: continue
            val userId = if (userId >= 0) userId else trackedUser
            if (isForbidden(pkg)) continue
            refreezePackage(pkg, userId, reason)
        }
    }

    private fun refreezePackage(packageName: String, userId: Int, reason: String) {
        runCatching {
            val app = currentApplication() ?: return
            val pm = app.packageManager
            val state = runCatching { pm.getApplicationEnabledSetting(packageName) }
                .getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
            ) {
                XLog.d(TAG, "skip refreeze, already disabled pkg=$packageName reason=$reason")
                return
            }

            // In system_server context, we can call PM directly without root.
            val ipm = runCatching {
                Class.forName("android.app.ActivityThread")
                    .getMethod("getPackageManager")
                    .invoke(null)
            }.getOrNull()

            if (ipm != null) {
                runCatching {
                    ipm.javaClass.getMethod(
                        "setApplicationEnabledSetting",
                        String::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        String::class.java,
                    ).invoke(ipm, packageName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, 0, userId, "com.xiaomi.xmsf")
                }
            } else {
                // Fallback: direct PM API (system uid, works for user 0)
                pm.setApplicationEnabledSetting(
                    packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    0,
                )
            }

            // Set stopped state so alarms and other wake paths skip the frozen package.
            runCatching {
                if (ipm != null) {
                    ipm.javaClass.getMethod(
                        "setPackageStoppedState",
                        String::class.java,
                        Boolean::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                    ).invoke(ipm, packageName, true, userId)
                }
            }

            XLog.i(TAG, "task-removed refroze pkg=$packageName userId=$userId reason=$reason")
        }.onFailure {
            XLog.w(TAG, "task-removed refreeze failed pkg=$packageName reason=$reason: $it")
        }
    }

    private fun isForbidden(packageName: String): Boolean =
        packageName == "com.xiaomi.xmsf" ||
            packageName == MANAGER_PACKAGE_NAME ||
            packageName == ANDROID_PACKAGE_NAME

    private fun collectPackagesFromTask(task: Any): Set<String> {
        val packages = mutableSetOf<String>()
        collectComponentPackage(task, "realActivity", packages)
        collectComponentPackage(task, "origActivity", packages)
        collectIntentPackage(task, "intent", packages)
        collectIntentPackage(task, "affinityIntent", packages)
        collectMethodPackage(task, "getBaseIntent", packages)
        addPackage(getField(task, "affinity"), packages)
        addPackage(getField(task, "rootAffinity"), packages)
        return packages
    }

    private fun readUserIdFromTask(task: Any): Int {
        val userId = runCatching { getHookIntField(task, "mUserId") }.getOrDefault(-1)
        if (userId >= 0) return userId
        return runCatching { getHookIntField(task, "userId") }.getOrDefault(-1)
    }

    private fun readTaskIdFromArgs(args: Array<Any?>?): Int {
        args ?: return -1
        for (arg in args) {
            val int = (arg as? Int) ?: continue
            if (int >= 0) return int
        }
        return -1
    }

    private fun findTaskById(service: Any, taskId: Int): Any? {
        val rwc = runCatching { getHookObjectField(service, "mRootWindowContainer") }.getOrNull()
            ?: return null
        // Try 1-arg then 2-arg form of anyTaskForId across API levels.
        return runCatching {
            rwc.javaClass.getMethod("anyTaskForId", Int::class.javaPrimitiveType).invoke(rwc, taskId)
        }.getOrNull() ?: runCatching {
            rwc.javaClass.getMethod("anyTaskForId", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                .invoke(rwc, taskId, 0)
        }.getOrNull()
    }

    private fun collectComponentPackage(obj: Any, field: String, out: MutableSet<String>) {
        val component = runCatching { getHookObjectField(obj, field) }.getOrNull() ?: return
        val pkg = runCatching {
            component.javaClass.getMethod("getPackageName").invoke(component) as? String
        }.getOrNull()
        addPackage(pkg, out)
    }

    private fun collectIntentPackage(obj: Any, field: String, out: MutableSet<String>) {
        val intent = runCatching { getHookObjectField(obj, field) }.getOrNull() ?: return
        val pkg = runCatching {
            (intent as android.content.Intent).`package`
                ?: intent.component?.packageName
        }.getOrNull()
        addPackage(pkg, out)
    }

    private fun collectMethodPackage(obj: Any, method: String, out: MutableSet<String>) {
        val intent = runCatching {
            obj.javaClass.getMethod(method).invoke(obj) as? android.content.Intent
        }.getOrNull()
        val pkg = intent?.`package` ?: intent?.component?.packageName
        addPackage(pkg, out)
    }

    private fun getField(obj: Any, field: String): String? =
        runCatching { getHookObjectField(obj, field) as? String }.getOrNull()

    private fun addPackage(value: String?, out: MutableSet<String>) {
        if (!value.isNullOrBlank() && !value.contains(" ") && !value.startsWith("-")) {
            out.add(value)
        }
    }
}
