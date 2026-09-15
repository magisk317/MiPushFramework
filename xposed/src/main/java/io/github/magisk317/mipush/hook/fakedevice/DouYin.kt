package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.xposed.LoadParam
import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.findClass
import io.github.magisk317.xposed.hookAllMethods
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject


/**
 * ByteDance-specific compatibility hooks. The MiPush provider capability gate itself is handled
 * by [DouyinMiuiGateHook] ahead of the FakeDevice brand guard; this pipeline only rewrites the
 * cloud-push channel policy (`allow_push_list`) observed by Douyin's own log network client so
 * the Xiaomi channel survives the server-side rollout configuration.
 *
 * Removed as dead or duplicated history: the `socialbase.appdownloader.util.MIUIUtils` forcing
 * hooks (the verified provider gate is `com.ss.android.message.util.ToolUtils.isMiui`; the
 * downloader predicate never gated push initialization and forcing it only altered downloader
 * behavior), and the flyme property block (fully covered by `Common.fakeAllBuildInProperties`,
 * which always clears the flyme keys and fakes display.id/user on exactly the non-Xiaomi
 * devices where that block could fire).
 */
class DouYin : Common() {
    companion object {
        private const val TAG = "DouYin"
        private const val CLOUD_PUSH_SEGMENT = "/cloudpush/"
        private const val UPDATE_SENDER_SEGMENT = "/cloudpush/update_sender/"
        private val CLOUD_PUSH_METHOD_CANDIDATES = listOf("post", "get", "request", "execute")
    }

    override fun fake(lpparam: LoadParam): Boolean {
        super.fake(lpparam)
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
                            val root = Json.parseToJsonElement(json).jsonObject.toMutableMap()
                            val allowPushList = root["allow_push_list"]?.jsonArray
                            val newArray = tryInsertMiPushChannel(allowPushList)
                            root["allow_push_list"] = newArray
                            result = kotlinx.serialization.json.JsonObject(root).toString()
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
            Json.parseToJsonElement(json).jsonObject["allow_push_list"]?.jsonArray?.toString() ?: "absent"
        }.getOrElse { "parse_error:${it.javaClass.simpleName}" }
    }

    private fun tryInsertMiPushChannel(originArray: JsonArray?): JsonArray {
        val list = mutableListOf<Int>()
        if (originArray != null) {
            for (element in originArray) {
                element.jsonPrimitive.intOrNull?.let(list::add)
            }
        }
        list.remove(1)
        list.add(0, 1)
        return JsonArray(list.map(::JsonPrimitive))
    }
}
