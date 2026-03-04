import org.gradle.api.Project
import java.util.Properties

/**
 * 从 libs.versions.toml 读取版本的辅助类
 * 直接解析 TOML 文件，避免版本信息重复维护
 */
object VersionProvider {
    private val versions = mutableMapOf<String, String>()
    private var initialized = false

    fun init(rootProject: Project) {
        if (initialized) return
        
        val tomlFile = rootProject.file("gradle/libs.versions.toml")
        if (!tomlFile.exists()) {
            throw IllegalStateException("libs.versions.toml not found at ${tomlFile.absolutePath}")
        }
        
        val content = tomlFile.readText()
        val versionSection = content.substringAfter("[versions]").substringBefore("[").trim()
        
        versionSection.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim().removeSurrounding("\"").removeSurrounding("'")
                    versions[key] = value
                }
            }
        }
        
        initialized = true
    }
    
    fun get(name: String): String {
        return versions[name] ?: throw IllegalArgumentException("Version '$name' not found in libs.versions.toml")
    }
    
    fun getInt(name: String): Int {
        return get(name).toInt()
    }
}

/**
 * Project 扩展函数
 */
fun Project.version(name: String): String {
    VersionProvider.init(rootProject)
    return VersionProvider.get(name)
}

fun Project.versionInt(name: String): Int {
    VersionProvider.init(rootProject)
    return VersionProvider.getInt(name)
}
