package io.github.magisk317.mipush.utils

/**
 * Compatibility constants for package configurations.
 */
object PackageConfig {
    const val OPERATION_WAKE = "wake"
    const val OPERATION_IGNORE = "ignore"
    const val OPERATION_OPEN = "open"

    /**
     * Replaces placeholders in the format ${name} with values from matchGroup.
     * $$ is replaced with $.
     */
    @JvmStatic
    fun replacePlaceholders(value: String, matchGroup: Map<String, String>): String {
        var result = value.replace("$$", "$")
        matchGroup.forEach { (k, v) ->
            result = result.replace("${"$"}{$k}", v)
        }
        return result
    }
}
