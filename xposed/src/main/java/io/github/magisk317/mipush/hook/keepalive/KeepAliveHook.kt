package io.github.magisk317.mipush.hook.keepalive

import android.net.Uri
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_ANTI_KILL
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_AUTHORITY
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_COLUMN_ENABLED
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_COLUMN_KEY
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_DOZE_BYPASS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_OOM_ADJ
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_PATH_FLAGS
import io.github.magisk317.mipush.common.KEEPALIVE_PREF_STANDBY_BYPASS
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.MethodHookParam
import io.github.magisk317.mipush.xposed.currentApplication
import io.github.magisk317.mipush.xposed.findHookClass
import io.github.magisk317.mipush.xposed.getHookIntField
import io.github.magisk317.mipush.xposed.getHookObjectField
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.setHookIntField
import java.lang.reflect.Method

class KeepAliveHook {
    companion object {
        private const val TAG = "KeepAliveHook"
        private const val FOREGROUND_APP_ADJ = 0
        private const val STANDBY_BUCKET_ACTIVE = 10
        private const val PREF_REFRESH_INTERVAL_MS = 60_000L
        private val PREF_URI = Uri.parse("content://$KEEPALIVE_PREF_AUTHORITY/$KEEPALIVE_PREF_PATH_FLAGS")
        private val STANDBY_RESTRICTED_BUCKETS = setOf(20, 30, 40, 45, 50)
        private val PREF_KEYS = arrayOf(
            KEEPALIVE_PREF_OOM_ADJ,
            KEEPALIVE_PREF_ANTI_KILL,
            KEEPALIVE_PREF_STANDBY_BYPASS,
            KEEPALIVE_PREF_DOZE_BYPASS,
        )

        @Volatile
        private var flags = KeepAliveFlags()

        @Volatile
        private var refreshLoopStarted = false
    }

    fun hook(classLoader: ClassLoader) {
        XLog.i(TAG, "loading in system_server")
        refreshFlags()
        startPreferenceRefreshLoop()
        hookOomAdjuster(classLoader)
        hookKillProcess(classLoader)
        hookAppStandbyController(classLoader)
        hookDeviceIdleController(classLoader)
    }

    private fun startPreferenceRefreshLoop() {
        if (refreshLoopStarted) return
        synchronized(KeepAliveHook::class.java) {
            if (refreshLoopStarted) return
            refreshLoopStarted = true
            Thread({
                while (true) {
                    refreshFlags()
                    try {
                        Thread.sleep(PREF_REFRESH_INTERVAL_MS)
                    } catch (_: InterruptedException) {
                        return@Thread
                    }
                }
            }, "MiPushKeepAlivePrefs").apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun refreshFlags() {
        runCatching {
            val app = currentApplication() ?: return
            val values = app.contentResolver.query(PREF_URI, null, null, PREF_KEYS, null)?.use { cursor ->
                val keyIndex = cursor.getColumnIndex(KEEPALIVE_PREF_COLUMN_KEY)
                val enabledIndex = cursor.getColumnIndex(KEEPALIVE_PREF_COLUMN_ENABLED)
                if (keyIndex < 0 || enabledIndex < 0) {
                    emptyMap()
                } else {
                    buildMap<String, Boolean> {
                        while (cursor.moveToNext()) {
                            put(cursor.getString(keyIndex), cursor.getInt(enabledIndex) != 0)
                        }
                    }
                }
            }.orEmpty()

            flags = KeepAliveFlags(
                oomAdj = values[KEEPALIVE_PREF_OOM_ADJ] == true,
                antiKill = values[KEEPALIVE_PREF_ANTI_KILL] == true,
                standbyBypass = values[KEEPALIVE_PREF_STANDBY_BYPASS] == true,
                dozeBypass = values[KEEPALIVE_PREF_DOZE_BYPASS] == true,
            )
        }.onFailure {
            XLog.w(TAG, "failed to refresh keepalive prefs: ${it.message}")
        }
    }

    private fun hookOomAdjuster(classLoader: ClassLoader) {
        try {
            val oomAdjusterClass = findHookClass("com.android.server.am.OomAdjuster", classLoader)
            var targetMethodName: String? = null
            for (method in oomAdjusterClass.declaredMethods) {
                if (method.name == "computeOomAdjLSP" || method.name == "computeOomAdjLocked") {
                    targetMethodName = method.name
                    break
                }
            }

            if (targetMethodName == null) {
                XLog.w(TAG, "no computeOomAdj method found")
                return
            }

            val hooks = oomAdjusterClass.hookAllMethods(targetMethodName) {
                doAfter {
                    if (!flags.oomAdj) return@doAfter
                    adjustOomAdjForTarget(this)
                }
            }
            if (hooks.isEmpty()) {
                XLog.w(TAG, "no OomAdjuster hooks installed for $targetMethodName")
            } else {
                XLog.w(TAG, "successfully hooked OomAdjuster method=$targetMethodName count=${hooks.size}")
            }
        } catch (t: Throwable) {
            XLog.e(TAG, "failed to hook OomAdjuster", t)
        }
    }

    private fun adjustOomAdjForTarget(param: MethodHookParam) {
        for (arg in param.args) {
            if (arg == null) continue
            val processName = try {
                getHookObjectField(arg, "processName") as? String
            } catch (_: Throwable) { null } ?: continue

            if (processName != XMSF_PACKAGE_NAME) continue

            val adjFields = listOf("curAdj", "mCurAdj", "setAdj")
            for (field in adjFields) {
                try {
                    val currentAdj = getHookIntField(arg, field)
                    if (currentAdj > FOREGROUND_APP_ADJ) {
                        setHookIntField(arg, field, FOREGROUND_APP_ADJ)
                        XLog.d(TAG, "set adj=$FOREGROUND_APP_ADJ for $processName (field=$field, was=$currentAdj)")
                    }
                    return
                } catch (_: Throwable) { }
            }
            break
        }
    }

    private fun hookKillProcess(classLoader: ClassLoader) {
        try {
            val amsClass = findHookClass("com.android.server.am.ActivityManagerService", classLoader)
            val methodName = "killProcessLocked"
            val hooks = amsClass.hookAllMethods(methodName) {
                doBefore {
                    if (!flags.antiKill) return@doBefore
                    if (shouldSkipKill(this)) {
                        result = defaultResultFor(method)
                        XLog.w(TAG, "intercepted kill for $XMSF_PACKAGE_NAME in $methodName")
                    }
                }
            }
            if (hooks.isEmpty()) {
                XLog.w(TAG, "no AMS kill hooks installed for $methodName")
            } else {
                XLog.w(TAG, "successfully hooked AMS kill method: $methodName count=${hooks.size}")
            }
        } catch (t: Throwable) {
            XLog.e(TAG, "failed to hook AMS kill", t)
        }
    }

    private fun defaultResultFor(method: Any?): Any? {
        val returnType = (method as? Method)?.returnType ?: return null
        return when (returnType) {
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> 0.toChar()
            else -> null
        }
    }

    private fun shouldSkipKill(param: MethodHookParam): Boolean {
        for (arg in param.args) {
            if (arg == null) continue
            if (arg is String && arg == XMSF_PACKAGE_NAME) {
                return true
            }
            try {
                val processName = getHookObjectField(arg, "processName") as? String
                if (processName == XMSF_PACKAGE_NAME) return true
            } catch (_: Throwable) { }

            try {
                val info = getHookObjectField(arg, "info")
                if (info != null) {
                    val pkgName = getHookObjectField(info, "packageName") as? String
                    if (pkgName == XMSF_PACKAGE_NAME) return true
                }
            } catch (_: Throwable) { }
        }
        return false
    }

    private fun hookAppStandbyController(classLoader: ClassLoader) {
        try {
            val standbyClass = findHookClass("com.android.server.usage.AppStandbyController", classLoader)
            val methods = listOf("setActiveBucket", "setAppStandbyBucket")
            var installed = 0
            for (methodName in methods) {
                try {
                    installed += standbyClass.hookAllMethods(methodName) {
                        doBefore {
                            if (!flags.standbyBypass) return@doBefore
                            overrideStandbyBucket(this)
                        }
                    }.size
                } catch (_: Throwable) {}
            }
            if (installed == 0) {
                XLog.w(TAG, "no AppStandbyController hooks installed")
            } else {
                XLog.w(TAG, "successfully hooked AppStandbyController count=$installed")
            }
        } catch (t: Throwable) {
            XLog.e(TAG, "failed to hook AppStandbyController", t)
        }
    }

    private fun overrideStandbyBucket(param: MethodHookParam) {
        if (!hasTargetPackageArg(param)) return

        for (i in param.args.indices) {
            val currentBucket = param.args[i] as? Int ?: continue
            if (currentBucket in STANDBY_RESTRICTED_BUCKETS) {
                param.args[i] = STANDBY_BUCKET_ACTIVE
                XLog.d(TAG, "forced standby bucket ACTIVE for $XMSF_PACKAGE_NAME (was $currentBucket)")
                return
            }
        }
    }

    private fun hookDeviceIdleController(classLoader: ClassLoader) {
        try {
            val idleClass = findHookClass("com.android.server.DeviceIdleController", classLoader)
            val methodName = "setAppIdleAsync"
            val hooks = idleClass.hookAllMethods(methodName) {
                doBefore {
                    if (!flags.dozeBypass) return@doBefore
                    keepTargetActive(this)
                }
            }
            if (hooks.isEmpty()) {
                XLog.w(TAG, "no DeviceIdleController hooks installed for $methodName")
            } else {
                XLog.w(TAG, "successfully hooked DeviceIdleController count=${hooks.size}")
            }
        } catch (t: Throwable) {
            XLog.e(TAG, "failed to hook DeviceIdleController", t)
        }
    }

    private fun keepTargetActive(param: MethodHookParam) {
        if (!hasTargetPackageArg(param)) return

        for (i in param.args.indices) {
            val idle = param.args[i] as? Boolean ?: continue
            if (idle) {
                param.args[i] = false
                XLog.d(TAG, "kept $XMSF_PACKAGE_NAME active in DeviceIdleController")
            }
            return
        }
    }

    private fun hasTargetPackageArg(param: MethodHookParam): Boolean {
        for (arg in param.args) {
            if (arg is String && arg == XMSF_PACKAGE_NAME) return true
        }
        return false
    }

    private data class KeepAliveFlags(
        val oomAdj: Boolean = false,
        val antiKill: Boolean = false,
        val standbyBypass: Boolean = false,
        val dozeBypass: Boolean = false,
    )
}
