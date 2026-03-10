import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class MipushAndroidComposePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        project.dependencies {
            add("implementation", project.dependencies.platform("androidx.compose:compose-bom:${project.version("compose-bom")}"))
            add("implementation", "androidx.compose.ui:ui")
            add("implementation", "androidx.compose.material3:material3:${project.version("material3")}")
            add("implementation", "androidx.compose.material:material-icons-extended")
            add("implementation", "androidx.compose.ui:ui-tooling-preview")
            add("debugImplementation", "androidx.compose.ui:ui-tooling")
            add("implementation", "androidx.lifecycle:lifecycle-runtime-ktx:${project.version("lifecycle")}")
            add("implementation", "androidx.activity:activity-compose:${project.version("activity-compose")}")
            add("implementation", "androidx.navigation:navigation-compose:${project.version("navigation")}")
            add("implementation", "androidx.lifecycle:lifecycle-viewmodel-compose:${project.version("lifecycle")}")
            add("implementation", "androidx.lifecycle:lifecycle-runtime-compose:${project.version("lifecycle")}")
        }
    }
}
