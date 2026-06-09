package io.github.magisk317.mipush.config

internal data class ConfigLocalPath(
    val segments: List<String>,
) {
    val path: String = segments.joinToString("/")
    val fileName: String = segments.last()
    val parentSegments: List<String> = segments.dropLast(1)
    val name: String = path.dropLast(ConfigLocalPathSupport.JSON_SUFFIX.length)
}

internal object ConfigLocalPathSupport {
    const val ICON_DIRECTORY = "icon"
    const val JSON_SUFFIX = ".json"

    fun parseOrNull(path: String): ConfigLocalPath? = runCatching { parse(path) }.getOrNull()

    fun parse(path: String): ConfigLocalPath {
        val normalized = path.trim().replace('\\', '/')
        require(normalized.isNotEmpty()) { "Configuration path is empty" }
        require(!normalized.startsWith("/") && !normalized.endsWith("/")) {
            "Unsupported configuration path: $path"
        }
        val segments = normalized.split('/')
        require(segments.none { it.isBlank() || it == "." || it == ".." || '\u0000' in it }) {
            "Unsupported configuration path: $path"
        }
        require(segments.size == 1 || (segments.size == 2 && segments[0] == ICON_DIRECTORY)) {
            "Unsupported configuration path: $path"
        }
        require(segments.last().lowercase().endsWith(JSON_SUFFIX)) {
            "Configuration file must be a JSON document: $path"
        }
        return ConfigLocalPath(segments)
    }
}
