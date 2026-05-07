package io.github.magisk317.mipush.hook.fakedevice

import android.app.Application
import android.app.AndroidAppHelper
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.hook.fakedevice.compat.ModuleCredentialResolver
import io.github.magisk317.mipush.hook.fakedevice.compat.ModuleProcessPolicy
import io.github.magisk317.mipush.xposed.callStaticMethod
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.hookMethod
import java.util.Collections

object ForceMiPushRegister {
    private const val TAG = "ForceMiPushRegister"
    private const val RETRY_DELAY_MS = 3000L
    private const val MAX_RETRY_COUNT = 8
    private const val CLOUD_PUSH_RETRY_COOLDOWN_MS = 5_000L
    private val REGID_RETRY_DELAYS_MS = longArrayOf(2000L, 6000L, 15_000L, 30_000L)

    private val triedPackages: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val tracedPackages: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val retryCounts: MutableMap<String, Int> = Collections.synchronizedMap(HashMap())
    private val regIdRetryCounts: MutableMap<String, Int> = Collections.synchronizedMap(HashMap())
    private val cloudPushRetryElapsedMs: MutableMap<String, Long> = Collections.synchronizedMap(HashMap())

    fun hook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        val processName = lpparam.processName
        if (!ModuleProcessPolicy.shouldHandleProcess(packageName, processName)) return

        Application::class.java.hookMethod("onCreate") {
            doAfter {
                val app = thisObject as? Application ?: return@doAfter
                hookFromRuntime(
                    packageName = packageName,
                    processName = processName,
                    classLoader = lpparam.classLoader,
                    application = app
                )
            }
        }
    }

    fun hookFromRuntime(
        packageName: String,
        processName: String,
        classLoader: ClassLoader,
        application: Application
    ) {
        if (!ModuleProcessPolicy.shouldHandleProcess(packageName, processName)) return
        traceRegisterCalls(packageName, classLoader)
        tryRegister(application, packageName, processName, classLoader)
    }

    fun nudgeAfterCloudPushHandshake(
        packageName: String,
        processName: String,
        classLoader: ClassLoader
    ) {
        if (!ModuleProcessPolicy.shouldHandleProcess(packageName, processName)) return
        val processKey = "$packageName@$processName"
        val now = SystemClock.elapsedRealtime()
        val lastRetryAt = cloudPushRetryElapsedMs[processKey] ?: 0L
        if (now - lastRetryAt < CLOUD_PUSH_RETRY_COOLDOWN_MS) {
            XLog.d(
                TAG,
                "skip cloudpush register retry for $packageName in process=$processName " +
                    "cooldownRemaining=${CLOUD_PUSH_RETRY_COOLDOWN_MS - (now - lastRetryAt)}ms"
            )
            return
        }
        val app = runCatching { AndroidAppHelper.currentApplication() }.getOrNull() ?: run {
            XLog.w(TAG, "skip cloudpush register retry: currentApplication unavailable for $packageName in process=$processName")
            return
        }
        cloudPushRetryElapsedMs[processKey] = now
        triedPackages.remove(processKey)
        retryCounts.remove(processKey)
        regIdRetryCounts.remove(processKey)
        XLog.i(TAG, "cloudpush handshake detected, retry register for $packageName in process=$processName")
        traceRegisterCalls(packageName, classLoader)
        tryRegister(app, packageName, processName, classLoader)
    }

    private fun tryRegister(
        app: Application,
        packageName: String,
        processName: String,
        classLoader: ClassLoader
    ) {
        val processKey = "$packageName@$processName"
        if (triedPackages.contains(processKey)) {
            return
        }

        val classMiPushClient = try {
            classLoader.findClass("com.xiaomi.mipush.sdk.MiPushClient")
        } catch (_: Throwable) {
            XLog.d(TAG, "MiPushClient not found for $packageName in process=$processName")
            scheduleRetry(app, packageName, processName)
            return
        }

        val credential = ModuleCredentialResolver.resolve(app, packageName)
        if (credential == null) {
            XLog.d(TAG, "meta-data appId/appKey not found for $packageName, skip force register")
            triedPackages.add(processKey)
            return
        }

        try {
            val appContext = app.applicationContext
            classMiPushClient.callStaticMethod(
                "registerPush",
                appContext,
                credential.appId,
                credential.appKey
            )
            val regId = runCatching {
                classMiPushClient.callStaticMethod("getRegId", appContext) as? String
            }.getOrNull().orEmpty()
            XLog.i(
                TAG,
                "forced registerPush for $packageName in process=$processName, appId=${credential.appId.take(12)}..., regId=${regId.take(24)}"
            )
            if (regId.isNotBlank()) {
                triedPackages.add(processKey)
            } else {
                if (!regIdRetryCounts.containsKey(processKey)) {
                    scheduleRegIdCheck(app, packageName, processName, classMiPushClient, appContext)
                } else {
                    XLog.d(TAG, "regId still empty for $packageName in process=$processName, check already scheduled")
                }
            }
        } catch (e: Throwable) {
            XLog.e(TAG, "force registerPush failed for $packageName in process=$processName", e)
            triedPackages.add(processKey)
        }
    }

    private fun traceRegisterCalls(packageName: String, classLoader: ClassLoader) {
        if (!tracedPackages.add(packageName)) {
            return
        }

        try {
            val classMiPushClient = classLoader.findClass("com.xiaomi.mipush.sdk.MiPushClient")
            classMiPushClient.hookAllMethods("registerPush") {
                doBefore {
                    XLog.t(TAG, "MiPushClient.registerPush before: pkg=$packageName, args=${safeArgs(args)}")
                }
                doAfter {
                    if (throwable != null) {
                        XLog.e(TAG, "MiPushClient.registerPush after throwable for $packageName", throwable)
                    } else {
                        XLog.t(TAG, "MiPushClient.registerPush after: pkg=$packageName")
                    }
                }
            }
            classMiPushClient.hookAllMethods("initialize") {
                doBefore {
                    XLog.t(TAG, "MiPushClient.initialize before: pkg=$packageName, args=${safeArgs(args)}")
                }
                doAfter {
                    if (throwable != null) {
                        XLog.e(TAG, "MiPushClient.initialize after throwable for $packageName", throwable)
                    } else {
                        XLog.t(TAG, "MiPushClient.initialize after: pkg=$packageName")
                    }
                }
            }
            classMiPushClient.hookAllMethods("getRegId") {
                doAfter {
                    val regId = (result as? String).orEmpty()
                    XLog.t(TAG, "MiPushClient.getRegId after: pkg=$packageName, regId=${regId.take(24)}")
                }
            }
        } catch (_: Throwable) {
        }

        traceAliBridge(packageName, classLoader)
        traceAccsBridge(packageName, classLoader)
    }

    private fun traceAliBridge(packageName: String, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.alibaba.sdk.android.push.channel.XiaomiPushUtils",
            "com.alipay.pushsdk.thirdparty.xiaomi.XiaoMIPushWorker"
        )
        val methodNames = listOf(
            "isMiui",
            "isSupport",
            "register",
            "registerPush",
            "turnOnPush",
            "enablePush"
        )
        classNames.forEach { className ->
            val clazz = runCatching { classLoader.findClass(className) }.getOrNull() ?: return@forEach
            methodNames.forEach { methodName ->
                val exists = runCatching { clazz.declaredMethods.any { it.name == methodName } }.getOrDefault(false)
                if (!exists) return@forEach
                runCatching {
                    clazz.hookAllMethods(methodName) {
                        doBefore {
                            XLog.t(TAG, "$className.$methodName before: pkg=$packageName, args=${safeArgs(args)}")
                        }
                        doAfter {
                            if (throwable != null) {
                                XLog.e(TAG, "$className.$methodName throwable for $packageName", throwable)
                            } else {
                                XLog.t(TAG, "$className.$methodName after: pkg=$packageName, result=${safeValue(result)}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun traceAccsBridge(packageName: String, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.taobao.accs.EventReceiver",
            "com.taobao.accs.ChannelService",
            "com.taobao.accs.data.MsgDistributeService",
            "org.android.agoo.accs.AgooService",
            "com.aliyun.ams.emas.push.MsgService",
            "com.aliyun.ams.emas.push.AgooInnerService",
            "com.alibaba.sdk.android.push.MiPushBroadcastReceiver"
        )
        val methodNames = listOf(
            "onCreate",
            "onStartCommand",
            "onHandleIntent",
            "onReceive",
            "register",
            "registerPush",
            "turnOnPush",
            "init"
        )
        classNames.forEach { className ->
            val clazz = runCatching { classLoader.findClass(className) }.getOrNull() ?: return@forEach
            methodNames.forEach { methodName ->
                val exists = runCatching { clazz.declaredMethods.any { it.name == methodName } }.getOrDefault(false)
                if (!exists) return@forEach
                runCatching {
                    clazz.hookAllMethods(methodName) {
                        doBefore {
                            XLog.t(TAG, "$className.$methodName before: pkg=$packageName, args=${safeArgs(args)}")
                        }
                        doAfter {
                            if (throwable != null) {
                                XLog.e(TAG, "$className.$methodName throwable for $packageName", throwable)
                            } else {
                                XLog.t(TAG, "$className.$methodName after: pkg=$packageName, result=${safeValue(result)}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun safeArgs(args: Array<Any?>): String {
        return args.joinToString(prefix = "[", postfix = "]") { safeValue(it) }
    }

    private fun safeValue(value: Any?): String {
        if (value == null) return "null"
        val text = value.toString().replace("\n", " ").replace("\r", " ")
        return if (text.length > 200) text.take(200) + "..." else text
    }

    private fun scheduleRetry(app: Application, packageName: String, processName: String) {
        val processKey = "$packageName@$processName"
        val next = (retryCounts[processKey] ?: 0) + 1
        if (next > MAX_RETRY_COUNT) {
            triedPackages.add(processKey)
            XLog.w(TAG, "retry exhausted for $packageName in process=$processName")
            return
        }
        retryCounts[processKey] = next
        Handler(Looper.getMainLooper()).postDelayed({
            if (!triedPackages.contains(processKey)) {
                hookFromRuntime(
                    packageName = packageName,
                    processName = processName,
                    classLoader = app.classLoader ?: return@postDelayed,
                    application = app
                )
            }
        }, RETRY_DELAY_MS)
        XLog.d(TAG, "scheduled retry#$next for $packageName in process=$processName")
    }

    private fun scheduleRegIdCheck(
        app: Application,
        packageName: String,
        processName: String,
        classMiPushClient: Class<*>,
        appContext: Context
    ) {
        val processKey = "$packageName@$processName"
        val nextIndex = (regIdRetryCounts[processKey] ?: 0)
        if (nextIndex >= REGID_RETRY_DELAYS_MS.size) {
            triedPackages.add(processKey)
            XLog.w(TAG, "regId still empty after ${REGID_RETRY_DELAYS_MS.size} checks for $packageName in process=$processName")
            return
        }
        val delay = REGID_RETRY_DELAYS_MS[nextIndex]
        regIdRetryCounts[processKey] = nextIndex + 1
        Handler(Looper.getMainLooper()).postDelayed({
            if (triedPackages.contains(processKey)) {
                return@postDelayed
            }
            val regId = runCatching {
                classMiPushClient.callStaticMethod("getRegId", appContext) as? String
            }.getOrNull().orEmpty()
            if (regId.isNotBlank()) {
                XLog.i(
                    TAG,
                    "regId available for $packageName in process=$processName, regId=${regId.take(24)}"
                )
                triedPackages.add(processKey)
            } else {
                scheduleRegIdCheck(app, packageName, processName, classMiPushClient, appContext)
            }
        }, delay)
        XLog.d(TAG, "scheduled regId check#${nextIndex + 1} for $packageName in process=$processName after ${delay}ms")
    }

}
