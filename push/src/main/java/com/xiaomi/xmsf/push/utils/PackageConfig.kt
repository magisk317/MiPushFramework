@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.push.utils

import android.os.Build
import com.magisk317.Global
import org.apache.thrift.TBase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import java.util.regex.Pattern

class PackageConfig(private val configurations: Configurations) {
    var cfgMatch: JSONObject? = null
    var cfgReplace: JSONObject? = null
    var operation: MutableSet<String> = hashSetOf()
    var stop: Boolean = true

    fun getWalker(data: TBase<*, *>?): Walker = Walker(data)

    inner class Walker(val data: TBase<*, *>?) {
        var matchGroup: MutableMap<String, String>? = null

        @Throws(NoSuchFieldException::class, IllegalAccessException::class)
        fun match(): Boolean {
            matchGroup = match(data, cfgMatch)
            return matchGroup != null
        }

        @Throws(NoSuchFieldException::class, IllegalAccessException::class)
        fun replace() {
            if (matchGroup != null) {
                replace(data, cfgReplace, configurations, this)
            }
        }

        fun replace(value: String): String {
            val pattern = Pattern.compile("\\${2}|\\$\\{([^}]+)\\}")
            val matcher = pattern.matcher(value)
            val sb = StringBuilder(value)
            class RPair {
                var start: Int = 0
                var end: Int = 0
                var str: String? = null
            }

            val pairs = mutableListOf<RPair>()
            while (matcher.find()) {
                val pair = RPair()
                pair.start = matcher.start()
                pair.end = matcher.end()
                if (matcher.groupCount() == 0) {
                    pair.str = "$"
                } else {
                    val groupName = matcher.group(1)
                    if (!groupName.isNullOrEmpty() && matchGroup?.containsKey(groupName) == true) {
                        pair.str = matchGroup?.get(groupName)
                    }
                }
                if (pair.str != null) {
                    pairs.add(pair)
                }
            }

            for (i in pairs.size - 1 downTo 0) {
                val pair = pairs[i]
                sb.replace(pair.start, pair.end, pair.str ?: "")
            }
            return sb.toString()
        }
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

        @Throws(NoSuchFieldException::class, IllegalAccessException::class)
        private fun match(data: TBase<*, *>?, cfgMatch: JSONObject?): MutableMap<String, String>? {
            return match(data, data, cfgMatch, arrayOf())
        }

        @Throws(NoSuchFieldException::class, IllegalAccessException::class)
        private fun match(
            root: TBase<*, *>?,
            data: TBase<*, *>?,
            cfgMatch: JSONObject?,
            path: Array<String>
        ): MutableMap<String, String>? {
            val matchGroup = hashMapOf<String, String>()
            if (cfgMatch == null || data == null) {
                return matchGroup
            }
            val cfgKeys = cfgMatch.keys()
            while (cfgKeys.hasNext()) {
                val cfgKey = cfgKeys.next()
                val field = data.javaClass.getDeclaredField(cfgKey)
                val newPath = concat(path, arrayOf(cfgKey))
                val value = Global.ConfigValueConverter().convert(root, newPath, field.get(data))

                val isMap = value is Map<*, *>
                val isTBase = value is TBase<*, *>

                var cfgSubObj: JSONObject? = null
                if (isMap || isTBase) {
                    try {
                        cfgSubObj = cfgMatch.getJSONObject(cfgKey)
                    } catch (e: JSONException) {
                        throw NoSuchFieldException(
                            "The type of field \"$cfgKey\" is ${value.javaClass.simpleName}, not ${cfgMatch.opt(cfgKey)?.javaClass}"
                        )
                    }
                }

                if (isMap) {
                    @Suppress("UNCHECKED_CAST")
                    val subMap = value as Map<String, Any?>
                    val cfgSubKeys = cfgSubObj!!.keys()
                    while (cfgSubKeys.hasNext()) {
                        val cfgSubKey = cfgSubKeys.next()
                        val subPath = concat(newPath, arrayOf(cfgSubKey))
                        if (mismatchField(
                                cfgSubObj,
                                cfgSubKey,
                                Global.ConfigValueConverter().convert(root, subPath, subMap[cfgSubKey]),
                                matchGroup
                            )
                        ) {
                            return null
                        }
                    }
                } else if (isTBase) {
                    val group = match(root, value, cfgSubObj, newPath) ?: return null
                    matchGroup.putAll(group)
                } else {
                    if (mismatchField(cfgMatch, cfgKey, value, matchGroup)) {
                        return null
                    }
                }
            }
            return matchGroup
        }

        @Throws(NoSuchFieldException::class, IllegalAccessException::class)
        private fun replace(
            data: TBase<*, *>?,
            cfgReplace: JSONObject?,
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

                var cfgSubObj: JSONObject? = null
                if (isMap || isTBase) {
                    try {
                        cfgSubObj = cfgReplace.getJSONObject(cfgKey)
                    } catch (e: JSONException) {
                        throw NoSuchFieldException(
                            "The type of field \"$cfgKey\" is ${field.type.simpleName}, not ${cfgReplace.opt(cfgKey)?.javaClass}"
                        )
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
                            if (cfgSubVal is JSONArray) {
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
                    val evaluated = cfgValueObj is JSONArray
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

        private fun mismatchField(
            obj: JSONObject,
            cfgKey: String,
            value: Any?,
            matchGroup: MutableMap<String, String>
        ): Boolean {
            if (obj.isNull(cfgKey)) {
                return value != null
            } else if (value == null) {
                return true
            }
            val regex = obj.optString(cfgKey)
            val pattern = Pattern.compile(regex)
            val matcher = pattern.matcher(value.toString())
            if (!matcher.find()) {
                return true
            }
            val groups = getNamedGroupCandidates(regex)
            for (i in groups.indices) {
                val name = groups[i]
                matchGroup[name] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    matcher.group(name) ?: ""
                } else {
                    matcher.group(i + 1) ?: ""
                }
            }
            return false
        }

        private fun getNamedGroupCandidates(regex: String): ArrayList<String> {
            val namedGroups = arrayListOf<String>()
            val m = Pattern.compile("(?<!\\\\)\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>").matcher(regex)
            while (m.find()) {
                val groupName = m.group(1)
                if (!groupName.isNullOrEmpty()) {
                    namedGroups.add(groupName)
                }
            }
            return namedGroups
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
