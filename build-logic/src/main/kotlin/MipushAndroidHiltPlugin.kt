import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class MipushAndroidHiltPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // 应用 KSP 插件（必须先于 Hilt）
        project.pluginManager.apply("com.google.devtools.ksp")
        // 应用 Hilt Android 插件
        project.pluginManager.apply("com.google.dagger.hilt.android")

        project.dependencies {
            add("implementation", "com.google.dagger:hilt-android:${project.version("hilt")}")
            add("ksp", "com.google.dagger:hilt-compiler:${project.version("hilt")}")
            add("implementation", "androidx.hilt:hilt-navigation-compose:1.3.0")
        }
    }
}
