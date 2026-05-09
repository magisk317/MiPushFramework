package io.github.magisk317.mipush.hook.keepalive

import android.app.AndroidAppHelper
import android.net.Uri
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
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
            val app = AndroidAppHelper.currentApplication() ?: return
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
            val oomAdjusterClass = XposedHelpers.findClass("com.android.server.am.OomAdjuster", classLoader)
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

            XposedBridge.hookAllMethods(oomAdjusterClass, targetMethodName, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (!flags.oomAdj) return
                    adjustOomAdjForTarget(param)
                }
            })
            XLog.w(TAG, "successfully hooked OomAdjuster")
        } catch (t: Throwable) {
            XLog.e(TAG, "failed to hook OomAdjuster", t)
        }
    }

    private fun adjustOomAdjForTarget(param: XC_MethodHook.MethodHookParam) {
        for (arg in param.args) {
            if (arg == null) continue
            val processName = try {
                XposedHelpers.getObjectField(arg, "processName") as? String
            } catch (e: Exception) { null } ?: continue

            if (processName != XMSF_PACKAGE_NAME) continue

            val adjFields = listOf("curAdj", "mCurAdj", "setAdj")
            for (field in adjFields) {
                try {
                    val currentAdj = XposedHelpers.getIntField(arg, field)
                    if (currentAdj > FOREGROUND_APP_ADJ) {
                        XposedHelpers.setIntField(arg, field, FOREGROUND_APP_ADJ)
                        XLog.d(TAG, "set adj=$FOREGROUND_APP_ADJ for $processName (field=$field, was=$currentAdj)")
                    }
                    return
                } catch (e: Exception) { }
            }
            break
        }
    }

    private fun hookKillProcess(classLoader: ClassLoader) {
        try {
            val amsClass = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", classLoader)
            val methodName = "killProcessLocked"
            XposedBridge.hookAllMethods(amsClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!flags.antiKill) return
                    if (shouldSkipKill(param)) {
                        param.result = defaultResultFor(param.method)
                        XLog.w(TAG, "intercepted kill for $XMSF_PACKAGE_NAME in $methodName")
                    }
                }
            })
            XLog.w(TAG, "successfully hooked AMS kill method: $methodName")
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

    private fun shouldSkipKill(param: XC_MethodHook.MethodHookParam): Boolean {
        for (arg in param.args) {
            if (arg == null) continue
            if (arg is String && arg == XMSF_PACKAGE_NAME) {
                return true
            }
            try {
                val processName = XposedHelpers.getObjectField(arg, "processName") as? String
                if (processName == XMSF_PACKAGE_NAME) return true
            } catch (e: Exception) { }

            try {
                val info = XposedHelpers.getObjectField(arg, "info")
                if (info != null) {
                    val pkgName = XposedHelpers.getObjectField(info, "packageName") as? String
                    if (pkgName == XMSF_PACKAGE_NAME) return true
                }
            } catch (e: Exception) { }
        }
        return false
    }

    private fun hookAppStandbyController(classLoader: ClassLoader) {
        try {
            val standbyClass = XposedHelpers.findClass("com.android.server.usage.AppStandbyController", classLoader)
            val methods = listOf("setActiveBucket", "setAppStandbyBucket")
            for (methodName in methods) {
                try {
                    XposedBridge.hookAllMethods(standbyClass, methodName, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!flags.standbyBypass) return
                            overrideStandbyBucket(param)
                        }
                    })
                } catch (e: Exception) {}
            }
            XLog.w(TAG, "successfully hooked AppStandbyController")
        } catch (t: Throwable) {
            XLog.e(TAG, "failed to hook AppStandbyController", t)
        }
    }

    private fun overrideStandbyBucket(param: XC_MethodHook.MethodHookParam) {
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
            val idleClass = XposedHelpers.findClass("com.android.server.DeviceIdleController", classLoader)
            val methodName = "setAppIdleAsync"
            XposedBridge.hookAllMethods(idleClass, methodName, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!flags.dozeBypass) return
                    keepTargetActive(param)
                }
            })
            XLog.w(TAG, "successfully hooked DeviceIdleController")
        } catch (t: Throwable) {
            XLog.e(TAG, "failed to hook DeviceIdleController", t)
        }
    }

    private fun keepTargetActive(param: XC_MethodHook.MethodHookParam) {
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

    private fun hasTargetPackageArg(param: XC_MethodHook.MethodHookParam): Boolean {
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
