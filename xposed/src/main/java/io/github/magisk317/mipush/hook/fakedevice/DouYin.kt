package io.github.magisk317.mipush.hook.fakedevice

import android.os.Build
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.mipush.xposed.findClass
import io.github.magisk317.mipush.xposed.hookAllMethods
import io.github.magisk317.mipush.xposed.hookMethod
import org.json.JSONArray
import org.json.JSONObject


class DouYin : Common() {
    companion object {
        private const val TAG = "DouYin"
        private const val CLOUD_PUSH_SEGMENT = "/cloudpush/"
        private const val UPDATE_SENDER_SEGMENT = "/cloudpush/update_sender/"
        private val CLOUD_PUSH_METHOD_CANDIDATES = listOf("post", "get", "request", "execute")
    }

    override fun fake(lpparam: XC_LoadPackage.LoadPackageParam): Boolean {
        super.fake(lpparam)
        
        // Hook isMIUI detection - critical for DouYin to initialize MiPush SDK
        XLog.d(TAG, "Hooking isMIUI detection for ${lpparam.packageName}...")
        try {
            val classMIUIUtils = lpparam.classLoader.findClass("com.ss.android.socialbase.appdownloader.util.MIUIUtils")
            classMIUIUtils.hookMethod("isMIUI") {
                doAfter {
                    result = true
                    XLog.d(TAG, "✓ Forced isMIUI() to return true")
                }
            }
            classMIUIUtils.hookMethod("isMIUI6Later") {
                doAfter {
                    result = true
                    XLog.d(TAG, "✓ Forced isMIUI6Later() to return true")
                }
            }
            XLog.d(TAG, "✓ Successfully hooked MIUIUtils")
        } catch (e: Throwable) {
            XLog.d(TAG, "MIUIUtils not found or hook failed: ${e.message}")
        }
        
        if (Build.DISPLAY.contains("flyme", true) || Build.USER.contains("flyme", true)) {
            fakeProperty("ro.build.display.id" to "")
            fakeProperty("ro.build.user" to "")
            fakeProperty("ro.build.flyme.version" to "")
            fakeProperty("ro.flyme.version.id" to "")
        }

        //public java.lang.String com.bytedance.common.network.DefaultNetWorkClient.post(java.lang.String,java.util.List,java.util.Map,com.bytedance.common.utility.NetworkClient$ReqContext)
        XLog.d(TAG, "Searching for AppLogNetworkClient class...")
        val classAppLogNetworkClient = try {
            val clazz = lpparam.classLoader.findClass("com.ss.android.ugc.aweme.statistic.AppLogNetworkClient")
            XLog.d(TAG, "✓ Found AppLogNetworkClient in aweme.statistic package")
            clazz
        } catch (_: Throwable) {
            try {
                val clazz = lpparam.classLoader.findClass("com.bytedance.common.utility.AppLogNetworkClient")
                XLog.d(TAG, "✓ Found AppLogNetworkClient in bytedance.common.utility package")
                clazz
            } catch (_: Throwable) {
                XLog.d(TAG, "✗ AppLogNetworkClient NOT FOUND in any package")
                null
            }
        }
        if (classAppLogNetworkClient == null) {
            XLog.d(TAG, "Aborting DouYin hook: AppLogNetworkClient is null")
            return false
        }

        XLog.d(TAG, "Searching for ReqContext class...")
        val classReqContext = try {
            val clazz = lpparam.classLoader.findClass("com.bytedance.common.utility.NetworkClient\$ReqContext")
            XLog.d(TAG, "✓ Found ReqContext class")
            clazz
        } catch (_: Throwable) {
            XLog.d(TAG, "✗ ReqContext NOT FOUND")
            null
        }
        if (classReqContext == null) {
            XLog.d(TAG, "Aborting DouYin hook: ReqContext is null")
            return false
        }
        
        XLog.d(TAG, "Hooking cloudpush methods on AppLogNetworkClient...")
        CLOUD_PUSH_METHOD_CANDIDATES.forEach { methodName ->
            runCatching {
                classAppLogNetworkClient.hookAllMethods(methodName) {
                    doBefore {
                        val url = extractCloudPushUrl(args) ?: return@doBefore
                        XLog.i(
                            TAG,
                            "cloudpush request pkg=${lpparam.packageName} proc=${lpparam.processName} " +
                                "method=$methodName path=${summarizeUrl(url)} args=${summarizeArgs(args)}"
                        )
                    }
                    doAfter {
                        val url = extractCloudPushUrl(args) ?: return@doAfter
                        val path = summarizeUrl(url)
                        val json = result as? String
                        if (json == null) {
                            XLog.w(
                                TAG,
                                "cloudpush response missing pkg=${lpparam.packageName} proc=${lpparam.processName} " +
                                    "method=$methodName path=$path resultType=${result?.javaClass?.name}"
                            )
                            return@doAfter
                        }
                        if (!url.contains(UPDATE_SENDER_SEGMENT)) {
                            XLog.d(
                                TAG,
                                "cloudpush response pkg=${lpparam.packageName} proc=${lpparam.processName} " +
                                    "method=$methodName path=$path allowPushList=${extractAllowPushListSummary(json)}"
                            )
                            if (path.contains("/cloudpush/promotion/keep_alive/")) {
                                ForceMiPushRegister.nudgeAfterCloudPushHandshake(
                                    packageName = lpparam.packageName,
                                    processName = lpparam.processName,
                                    classLoader = lpparam.classLoader
                                )
                            }
                            return@doAfter
                        }

                        val beforeSummary = extractAllowPushListSummary(json)
                        XLog.i(
                            TAG,
                            "intercept update_sender pkg=${lpparam.packageName} proc=${lpparam.processName} " +
                                "method=$methodName path=$path allowPushListBefore=$beforeSummary"
                        )

                        try {
                            val obj = JSONObject(json)
                            val allowPushList = obj.optJSONArray("allow_push_list") ?: JSONArray()
                            val newArray = tryInsertMiPushChannel(allowPushList)
                            obj.put("allow_push_list", newArray)
                            result = obj.toString()
                            val afterSummary = extractAllowPushListSummary(result as? String)
                            XLog.i(
                                TAG,
                                "update_sender modified pkg=${lpparam.packageName} proc=${lpparam.processName} " +
                                    "method=$methodName path=$path allowPushListAfter=$afterSummary"
                            )
                            ForceMiPushRegister.nudgeAfterCloudPushHandshake(
                                packageName = lpparam.packageName,
                                processName = lpparam.processName,
                                classLoader = lpparam.classLoader
                            )
                        } catch (e: Throwable) {
                            XLog.e(TAG, "parse response error", e)
                        }
                    }
                }
            }.onFailure {
                XLog.d(TAG, "skip hook $methodName on AppLogNetworkClient: ${it.javaClass.simpleName}")
            }
        }
        return true
    }

    private fun summarizeUrl(url: String): String {
        return url.substringBefore('?').take(160)
    }

    private fun extractCloudPushUrl(args: Array<Any?>): String? {
        args.forEach { arg ->
            when (arg) {
                is String -> if (arg.contains(CLOUD_PUSH_SEGMENT)) return arg
                is Iterable<*> -> arg.forEach { item ->
                    if (item is String && item.contains(CLOUD_PUSH_SEGMENT)) {
                        return item
                    }
                }
                is Map<*, *> -> {
                    arg.keys.forEach { key ->
                        if (key is String && key.contains(CLOUD_PUSH_SEGMENT)) return key
                    }
                    arg.values.forEach { value ->
                        if (value is String && value.contains(CLOUD_PUSH_SEGMENT)) return value
                    }
                }
            }
        }
        return null
    }

    private fun summarizeArgs(args: Array<Any?>): String {
        return args.mapIndexed { index, value ->
            "arg$index=${summarizeValue(value)}"
        }.joinToString(prefix = "[", postfix = "]")
    }

    private fun summarizeValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> value.take(180)
            is Map<*, *> -> value.entries.take(8).joinToString(
                prefix = "map(",
                postfix = if (value.size > 8) ",...)" else ")"
            ) { "${it.key}=${summarizeValue(it.value)}" }
            is Iterable<*> -> value.take(8).joinToString(
                prefix = "list(",
                postfix = if (value.count() > 8) ",...)" else ")"
            ) { summarizeValue(it) }
            else -> value.toString().take(180)
        }
    }

    private fun extractAllowPushListSummary(json: String?): String {
        if (json.isNullOrBlank()) return "missing"
        return runCatching {
            JSONObject(json).optJSONArray("allow_push_list")?.toString() ?: "absent"
        }.getOrElse { "parse_error:${it.javaClass.simpleName}" }
    }

    private fun tryInsertMiPushChannel(originArray: JSONArray): JSONArray {
        val array = ArrayList<Int>()
        for (i in 0 until originArray.length()) {
            array.add(originArray.getInt(i))
        }
        array.remove(1)
        array.add(0, 1)
        return JSONArray(array)
    }
}
