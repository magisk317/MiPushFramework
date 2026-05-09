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

class KeepAliveHook {
    companion object {
        private const val TAG = "KeepAliveHook"
        private const val FOREGROUND_APP_ADJ = 0
        private const val STANDBY_BUCKET_ACTIVE = 10
        private const val PREF_CACHE_TTL_MS = 5_000L
        private val PREF_URI = Uri.parse("content://$KEEPALIVE_PREF_AUTHORITY/$KEEPALIVE_PREF_PATH_FLAGS")
    }

    private var cachedPrefs: Map<String, Boolean> = emptyMap()
    private var cachedAt: Long = 0L

    private fun isKeepAliveOomAdjEnabled() = readPrefEnabled(KEEPALIVE_PREF_OOM_ADJ)
    private fun isKeepAliveAntiKillEnabled() = readPrefEnabled(KEEPALIVE_PREF_ANTI_KILL)
    private fun isKeepAliveStandbyBypassEnabled() = readPrefEnabled(KEEPALIVE_PREF_STANDBY_BYPASS)
    private fun isKeepAliveDozeBypassEnabled() = readPrefEnabled(KEEPALIVE_PREF_DOZE_BYPASS)

    private fun readPrefEnabled(key: String): Boolean {
        val now = System.currentTimeMillis()
        val cached = cachedPrefs
        if (now - cachedAt < PREF_CACHE_TTL_MS) {
            return cached[key] == true
        }

        val loaded: Map<String, Boolean> = runCatching {
            val app = AndroidAppHelper.currentApplication() ?: return@runCatching cached
            app.contentResolver.query(PREF_URI, null, null, null, null)?.use { cursor ->
                val keyIndex = cursor.getColumnIndex(KEEPALIVE_PREF_COLUMN_KEY)
                val enabledIndex = cursor.getColumnIndex(KEEPALIVE_PREF_COLUMN_ENABLED)
                if (keyIndex < 0 || enabledIndex < 0) return@use emptyMap<String, Boolean>()
                buildMap<String, Boolean> {
                    while (cursor.moveToNext()) {
                        put(cursor.getString(keyIndex), cursor.getInt(enabledIndex) != 0)
                    }
                }
            }.orEmpty()
        }.onFailure {
            XLog.w(TAG, "failed to read keepalive prefs: ${it.message}")
        }.getOrDefault(emptyMap())

        cachedPrefs = loaded
        cachedAt = now
        return loaded[key] == true
    }

    fun hook(classLoader: ClassLoader) {
        XLog.i(TAG, "loading in system_server")
        hookOomAdjuster(classLoader)
        hookKillProcess(classLoader)
        hookAppStandbyController(classLoader)
        hookDeviceIdleController(classLoader)
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
                    if (!isKeepAliveOomAdjEnabled()) return
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
            val methods = listOf("killProcessLocked", "cleanUpApplicationRecordLocked", "handleAppDiedLocked")
            for (methodName in methods) {
                try {
                    XposedBridge.hookAllMethods(amsClass, methodName, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!isKeepAliveAntiKillEnabled()) return
                            if (shouldSkipKill(param)) {
                                param.result = null
                                XLog.w(TAG, "intercepted kill for $XMSF_PACKAGE_NAME in $methodName")
                            }
                        }
                    })
                    XLog.w(TAG, "successfully hooked AMS kill method: $methodName")
                } catch (e: Exception) { }
            }
        } catch (t: Throwable) {
            XLog.e(TAG, "failed to hook AMS kill", t)
        }
    }

    private fun shouldSkipKill(param: XC_MethodHook.MethodHookParam): Boolean {
        for (arg in param.args) {
            if (arg == null) continue
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
            val methods = listOf("setActiveBucket", "postMessage", "setAppStandbyBucket")
            for (methodName in methods) {
                try {
                    XposedBridge.hookAllMethods(standbyClass, methodName, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!isKeepAliveStandbyBypassEnabled()) return
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
        var targetPkg: String? = null
        for (arg in param.args) {
            if (arg is String && targetPkg == null) {
                targetPkg = arg
            }
        }
        if (targetPkg != XMSF_PACKAGE_NAME) return

        for (i in param.args.indices) {
            if (param.args[i] is Int) {
                val currentBucket = param.args[i] as Int
                if (currentBucket != STANDBY_BUCKET_ACTIVE) {
                    param.args[i] = STANDBY_BUCKET_ACTIVE
                    XLog.d(TAG, "forced standby bucket ACTIVE for $targetPkg (was $currentBucket)")
                }
                break
            }
        }
    }

    private fun hookDeviceIdleController(classLoader: ClassLoader) {
        try {
            val idleClass = XposedHelpers.findClass("com.android.server.DeviceIdleController", classLoader)
            val methods = listOf("becomeActiveIfAppTempIdleLocked", "stepIdleStateLocked", "setAppIdleAsync")
            for (methodName in methods) {
                try {
                    XposedBridge.hookAllMethods(idleClass, methodName, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!isKeepAliveDozeBypassEnabled()) return
                            bypassDozeForTarget(param)
                        }
                    })
                } catch (e: Exception) {}
            }
            XLog.w(TAG, "successfully hooked DeviceIdleController")
        } catch (t: Throwable) {
            XLog.e(TAG, "failed to hook DeviceIdleController", t)
        }
    }

    private fun bypassDozeForTarget(param: XC_MethodHook.MethodHookParam) {
        for (arg in param.args) {
            if (arg is String && arg == XMSF_PACKAGE_NAME) {
                param.result = null
                XLog.d(TAG, "bypassed doze for $XMSF_PACKAGE_NAME")
                return
            }
        }
    }
}
