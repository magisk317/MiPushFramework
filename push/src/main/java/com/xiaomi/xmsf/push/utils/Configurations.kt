package com.xiaomi.xmsf.push.utils

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.common.utils.Singleton
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException

import javax.inject.Inject
import javax.inject.Singleton as JavaxSingleton
import io.github.magisk317.mipush.app.ConfigCenter

@JavaxSingleton
class Configurations @Inject constructor(
    internal var loader: ConfigurationsLoader
) {
    // No-arg fallback for legacy Singleton access.
    constructor() : this(ConfigurationsLoader(io.github.magisk317.mipush.common.utils.Singleton.instance<ConfigCenter>()))

    init {
        // Capture Hilt instance for static access
        hiltInstance = this
    }

    fun init(context: android.content.Context?, treeUri: android.net.Uri?): Boolean =
        loader.init(context, treeUri, this)

    @Throws(JSONException::class)
    fun load(json: String) {
        loader.load(json, this)
    }

    @Throws(
        JSONException::class,
        NoSuchFieldException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        NoSuchMethodException::class
    )
    fun handle(packageName: String, data: XmPushActionContainer): Set<String> {
        val checkPkgs = arrayOf("^", packageName, "$")
        val operations = hashSetOf<String>()
        for (pkg in checkPkgs) {
            val configs = loader.getConfigs()[pkg]
            val stop = doHandle(data, configs, operations)
            if (stop) {
                return operations
            }
        }
        return operations
    }

    @Throws(
        JSONException::class,
        NoSuchFieldException::class,
        InvocationTargetException::class,
        IllegalAccessException::class,
        NoSuchMethodException::class
    )
    private fun doHandle(
        data: XmPushActionContainer,
        configs: MutableList<Any>?,
        operations: MutableSet<String>
    ): Boolean = doHandle(data, configs, operations, mutableListOf())

    @Throws(
        NoSuchFieldException::class,
        IllegalAccessException::class,
        JSONException::class,
        NoSuchMethodException::class,
        InvocationTargetException::class
    )
    private fun doHandle(
        data: XmPushActionContainer,
        configs: MutableList<Any>?,
        operations: MutableSet<String>,
        matched: MutableList<Any>
    ): Boolean {
        if (configs != null && !matched.contains(configs)) {
            matched.add(configs)
            for (configItem in configs) {
                if (configItem is PackageConfig) {
                    val walker = configItem.getWalker(data)
                    if (walker.match()) {
                        walker.replace()
                        operations.addAll(configItem.operation)
                        if (configItem.stop) {
                            return true
                        }
                    }
                } else {
                    var refConfigs: MutableList<Any>? = null
                    if (configItem is JSONArray) {
                        val value = evaluate(configItem, data)
                        if (value is JSONObject) {
                            refConfigs = mutableListOf(loader.parseConfig(value, this))
                        } else if (value != null) {
                            refConfigs = loader.getConfigs()[value.toString()]
                        }
                    } else {
                        refConfigs = loader.getConfigs()[configItem]
                    }
                    if (refConfigs != null) {
                        val stop = doHandle(data, refConfigs, operations)
                        if (stop) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    @Throws(NoSuchFieldException::class, IllegalAccessException::class)
    private fun match(
        data: XmPushActionContainer,
        configs: MutableList<Any>?,
        matched: MutableList<Any>
    ): Boolean {
        if (configs != null && !matched.contains(configs)) {
            matched.add(configs)
            for (configItem in configs) {
                if (configItem is PackageConfig) {
                    val walker = configItem.getWalker(data)
                    if (walker.match()) {
                        return true
                    }
                } else {
                    val refConfigs = loader.getConfigs()[configItem]
                    return match(data, refConfigs, matched)
                }
            }
        }
        return false
    }

    internal fun evaluate(expr: Any?, env: PackageConfig.Walker): Any? =
        evaluate(expr, env, null)

    private fun evaluate(expr: Any?, env: XmPushActionContainer): Any? =
        evaluate(expr, null, env)

    private fun evaluate(
        expr: Any?,
        configWalker: PackageConfig.Walker?,
        data: XmPushActionContainer?
    ): Any? = Lisp.evaluate(expr, ConfigurationEvaluator(configWalker, data))

    inner class ConfigurationEvaluator(
        val configWalker: PackageConfig.Walker?,
        val data: XmPushActionContainer?
    ) : Lisp.Evaluable {
        override fun evaluate(expr: Any?): Any? {
            if (expr is JSONObject) {
                return expr
            }
            if (expr is JSONArray) {
                return evaluate(expr)
            }
            return null
        }

        private fun evaluate(expr: JSONArray): Any? {
            val method = expr.opt(0) as? String ?: return null
            if (method == "cond") {
                return evaluateCond(expr)
            }

            val evaluated = expr
            val methods = hashMapOf<String, () -> Any?>(
                "$" to {
                    configWalker?.matchGroup?.get(evaluated.optString(1))
                }
            )

            val ret = methods[method]
            if (ret != null) {
                return try {
                    ret()
                } catch (e: Exception) {
                    logger.e(method, e)
                    null
                }
            }
            return null
        }

        private fun evaluateCond(expr: JSONArray): Any? {
            try {
                val length = expr.length()
                for (i in 1 until length) {
                    val clause = expr.optJSONArray(i) ?: return null
                    val test = clause.opt(0)
                    if (test is JSONObject) {
                        val config = loader.parseConfig(test, this@Configurations)
                        if (config.getWalker(data).match()) {
                            return clause.opt(1)
                        }
                    }
                    if (test is String) {
                        val container = data ?: return null
                        if (match(container, loader.getConfigs()[test], mutableListOf())) {
                            return clause.opt(1)
                        }
                    }
                }
            } catch (e: Exception) {
                logger.e("evaluateCond", e)
            }
            return null
        }
    }

    companion object {
        private val TAG = Configurations::class.java.simpleName
        private val logger = object {
            fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
        }
        @Volatile private var hiltInstance: Configurations? = null

        @JvmStatic
        fun getInstance(): Configurations {
            val hilt = hiltInstance
            if (hilt != null) {
                hilt.loader.reInitIfDirectoryUpdated(hilt)
                return hilt
            }
            // Fallback to manual singleton
            val instance = Singleton.instance<Configurations>()
            instance.loader.reInitIfDirectoryUpdated(instance)
            return instance
        }
    }
}
