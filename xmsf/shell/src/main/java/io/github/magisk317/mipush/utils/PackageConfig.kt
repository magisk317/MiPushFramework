package io.github.magisk317.mipush.utils

import android.os.Build
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.common.configurations.ConfigJsonArray
import io.github.magisk317.mipush.common.configurations.ConfigJsonException
import io.github.magisk317.mipush.common.configurations.ConfigJsonObject
import io.github.magisk317.mipush.common.configurations.ConfigFieldAccessor
import io.github.magisk317.mipush.common.configurations.ConfigFieldValue
import io.github.magisk317.mipush.common.configurations.ConfigRulePlans
import org.apache.thrift.TBase
import java.lang.reflect.InvocationTargetException

class PackageConfig(private val configurations: Configurations) {
    var cfgMatch: ConfigJsonObject? = null
    var cfgReplace: ConfigJsonObject? = null
    var operation: MutableSet<String> = hashSetOf()
    var stop: Boolean = true

    fun getWalker(data: TBase<*, *>?): Walker = Walker(data)

    inner class Walker(val data: TBase<*, *>?) {
        var matchGroup: MutableMap<String, String>? = null

        fun match(): Boolean {
            matchGroup = Companion.match(data, cfgMatch)
            return matchGroup != null
        }

        @Throws(NoSuchFieldException::class, IllegalAccessException::class)
        fun replace() {
            if (matchGroup != null) {
                Companion.replace(data, cfgReplace, configurations, this)
            }
        }

        fun replace(value: String): String = replacePlaceholders(value, matchGroup)
    }

    companion object {
        const val KEY_MATCH = "match"
        const val KEY_REPLACE = "replace"
        const val KEY_META_INFO = "metaInfo"
        const val KEY_NEW_META_INFO = "newMetaInfo"
        const val KEY_OPERATION = "operation"
        const val KEY_STOP = "stop"

        const val OPERATION_OPEN = "open"
        const val OPERATION_IGNORE = "ignore"
        const val OPERATION_NOTIFY = "notify"
        const val OPERATION_WAKE = "wake"

        internal fun replacePlaceholders(value: String, matchGroup: Map<String, String>?): String =
            ConfigRulePlans.replacePlaceholders(value, matchGroup)

        private fun match(data: TBase<*, *>?, cfgMatch: ConfigJsonObject?): MutableMap<String, String>? {
            if (cfgMatch == null || data == null) return hashMapOf()
            val groups = ConfigRulePlans.match(cfgMatch, ConfigFieldAccessor { path ->
                readField(data, path)
            }) ?: return null
            return groups.toMutableMap()
        }

        private fun readField(root: TBase<*, *>, path: List<String>): ConfigFieldValue {
            var current: Any? = root
            val traversed = mutableListOf<String>()
            for (key in path) {
                val raw = when (current) {
                    is Map<*, *> -> current[key]
                    is TBase<*, *> -> {
                        val field = current.javaClass.declaredFields.firstOrNull { it.name == key }
                            ?: return ConfigFieldValue(null)
                        field.get(current)
                    }
                    else -> null
                }
                traversed += key
                current = Global.configValueConverter().convert(
                    root,
                    traversed.toTypedArray(),
                    raw,
                )
            }
            return ConfigFieldValue(
                value = current,
                isContainer = current is Map<*, *> || current is TBase<*, *>,
            )
        }

        @Throws(NoSuchFieldException::class, IllegalAccessException::class)
        private fun replace(
            data: TBase<*, *>?,
            cfgReplace: ConfigJsonObject?,
            configurations: Configurations,
            configWalker: Walker
        ) {
            if (cfgReplace == null || data == null) {
                return
            }
            val cfgKeys = cfgReplace.keys()
            while (cfgKeys.hasNext()) {
                val cfgKey = cfgKeys.next()
                val field = data.javaClass.getDeclaredField(cfgKey)

                val isMap = Map::class.java.isAssignableFrom(field.type)
                val isTBase = TBase::class.java.isAssignableFrom(field.type)

                var cfgSubObj: ConfigJsonObject? = null
                if (isMap || isTBase) {
                    try {
                        cfgSubObj = cfgReplace.getJSONObject(cfgKey)
                    } catch (e: ConfigJsonException) {
                        throw NoSuchFieldException(
                            "The type of field \"$cfgKey\" is ${field.type.simpleName}, not ${cfgReplace.opt(cfgKey)?.javaClass}"
                        ).initCause(e)
                    }
                }

                if (isMap) {
                    @Suppress("UNCHECKED_CAST")
                    val subMap = field.get(data) as MutableMap<String, Any?>
                    val cfgSubKeys = cfgSubObj!!.keys()
                    while (cfgSubKeys.hasNext()) {
                        val cfgSubKey = cfgSubKeys.next()
                        if (cfgSubObj.isNull(cfgSubKey)) {
                            subMap.remove(cfgSubKey)
                        } else {
                            val cfgSubVal = cfgSubObj.opt(cfgSubKey)
                            if (cfgSubVal is ConfigJsonArray) {
                                val value = configurations.evaluate(cfgSubVal, configWalker)
                                if (value == null) {
                                    subMap.remove(cfgSubKey)
                                } else {
                                    subMap[cfgSubKey] = value.toString()
                                }
                            } else {
                                var cfgValue = cfgSubObj.optString(cfgSubKey)
                                cfgValue = configWalker.replace(cfgValue)
                                subMap[cfgSubKey] = cfgValue
                            }
                        }
                    }
                } else if (isTBase) {
                    replace(field.get(data) as TBase<*, *>?, cfgSubObj, configurations, configWalker)
                } else {
                    var cfgValueObj = cfgReplace.opt(cfgKey)
                    val evaluated = cfgValueObj is ConfigJsonArray
                    if (evaluated) {
                        cfgValueObj = configurations.evaluate(cfgValueObj, configWalker)
                    }
                    if (cfgReplace.isNull(cfgKey) || cfgValueObj == null) {
                        try {
                            val capitalizedKey = cfgKey.substring(0, 1).uppercase() + cfgKey.substring(1)
                            val method = data.javaClass.getDeclaredMethod("unset$capitalizedKey")
                            method.invoke(data)
                        } catch (_: NoSuchMethodException) {
                        } catch (_: InvocationTargetException) {
                        }
                    } else {
                        var value = cfgValueObj.toString()
                        if (!evaluated) {
                            value = configWalker.replace(value)
                        }
                        val fieldType = field.type
                        val typedValue: Any = when (fieldType) {
                            Long::class.javaPrimitiveType -> value.toLong()
                            Int::class.javaPrimitiveType -> value.toLong().toInt()
                            Boolean::class.javaPrimitiveType -> value.toBoolean()
                            else -> value
                        }
                        field.set(data, typedValue)
                    }
                }
            }
        }

        @JvmStatic
        fun concat(first: Array<String>, second: Array<String>): Array<String> {
            val result = Array(first.size + second.size) { "" }
            System.arraycopy(first, 0, result, 0, first.size)
            System.arraycopy(second, 0, result, first.size, second.size)
            return result
        }
    }
}
