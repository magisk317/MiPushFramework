package io.github.magisk317.mipush.common.island

/** Selects the process that owns SystemUI island visual rendering. */
enum class IslandRendererMode(val wireValue: String) {
    AUTO("auto"),
    MIPUSH("mipush"),
    HYPERISLAND("hyperisland");

    companion object {
        fun parse(value: String?): IslandRendererMode = entries.firstOrNull {
            it.wireValue.equals(value, ignoreCase = true)
        } ?: AUTO
    }
}
