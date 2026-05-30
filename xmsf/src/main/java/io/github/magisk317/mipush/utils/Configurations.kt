package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.app.ConfigCenter
import io.github.magisk317.mipush.common.configurations.ConfigJsonArray
import io.github.magisk317.mipush.common.configurations.ConfigJsonException
import io.github.magisk317.mipush.common.configurations.ConfigJsonObject
import io.github.magisk317.mipush.common.configurations.Lisp
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.lang.reflect.InvocationTargetException

class Configurations constructor(
    internal var loader: ConfigurationsLoader
) {
    init {
        injectedInstance = this
    }

    fun init(context: android.content.Context?, treeUri: android.net.Uri?): Boolean =
        loader.init(context, treeUri, this)

    @Throws(ConfigJsonException::class)
    fun load(json: String) {
        loader.load(json, this)
    }

    @Throws(
        ConfigJsonException::class,
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
        ConfigJsonException::class,
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
        ConfigJsonException::class,
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
                    if (configItem is ConfigJsonArray) {
                        val value = evaluate(configItem, data)
                        if (value is ConfigJsonObject) {
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
            if (expr is ConfigJsonObject) {
                return expr
            }
            if (expr is ConfigJsonArray) {
                return evaluate(expr)
            }
            return null
        }

        private fun evaluate(expr: ConfigJsonArray): Any? {
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
                    logE(method, e)
                    null
                }
            }
            return null
        }

        private fun evaluateCond(expr: ConfigJsonArray): Any? {
            try {
                val length = expr.length()
                for (i in 1 until length) {
                    val clause = expr.optConfigJsonArray(i) ?: return null
                    val test = clause.opt(0)
                    if (test is ConfigJsonObject) {
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
                logE("evaluateCond", e)
            }
            return null
        }
    }

    companion object {
        private val TAG = Configurations::class.java.simpleName
        @Volatile private var injectedInstance: Configurations? = null

        @JvmStatic
        fun getInstance(): Configurations {
            val injected = injectedInstance
            if (injected != null) {
                injected.loader.reInitIfDirectoryUpdated(injected)
                return injected
            }
            val instance = AppDependencies.get(Configurations::class)
            instance.loader.reInitIfDirectoryUpdated(instance)
            return instance
        }
    }
}
