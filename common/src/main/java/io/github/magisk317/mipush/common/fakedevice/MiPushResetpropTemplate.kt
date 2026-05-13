package io.github.magisk317.mipush.common.fakedevice

object MiPushResetpropTemplate {
    fun mergedCustomProps(baseProps: Map<String, String>): LinkedHashMap<String, String> {
        val merged = LinkedHashMap(baseProps)
        GeneratedMiPushPropTemplate.customProps.forEach { (key, value) ->
            when (value) {
                "__DELETE__" -> merged.remove(key)
                "__EMPTY__" -> merged[key] = ""
                else -> merged[key] = value
            }
        }
        return merged
    }

    fun defaultCustomProps(): Map<String, String> = GeneratedMiPushPropTemplate.customProps
}
